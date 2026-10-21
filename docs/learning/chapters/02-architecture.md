# 6. Architecture: follow one expense through the application

The app uses a practical layered structure. Compose displays state and emits user actions. ViewModels coordinate screen state and asynchronous work. Repositories enforce operations across storage. Room DAOs read and write records. Pure calculation functions turn records into summaries.

This resembles common MVVM and unidirectional-data-flow patterns, but it is not a perfectly isolated textbook architecture. Some models and calculations live together in `data`, and Android-aware ViewModels are used. Describe what exists before deciding to reorganize it.

```text
User taps Save
    |
Compose editor validates the visible draft
    |
ViewModel starts the operation and reports its state
    |
Repository validates business rules
    |
Room transaction / DAO writes encrypted database
    |
Observed Flow emits changed records
    |
ViewModel exposes state -> Compose redraws summaries
```

The downward path is an instruction. The upward path is observed data. A dashboard should not manually add the entered amount to a separate total and hope that its arithmetic stays synchronized with Room. Instead, derive the total from the same authoritative records.

## Dependency composition

`LedgerApplication` creates shared dependencies lazily. Its repositories use the same database. This is **manual dependency injection**: a class receives the objects it needs rather than creating a fresh database whenever a function runs. A large dependency-injection framework is not required to understand the principle.

For tests, replace a dependency with a controlled implementation. The `PreferencesStore` interface lets a test supply predictable settings behavior. For UI tests, fixture state can be supplied without accessing a person's real financial records.

Never create a new Room database or ViewModel inside each pager page during every composition. The page is a display surface, not a separate application. Activity-owned ViewModels and application-owned repositories keep work and state coherent.

## A detailed Save trace

Imagine entering RM 12.50 in Food, paying Cash, on 20 October. The editor stores 1250 sen in its draft. It must have a valid category and payment selection. Pressing Save passes a complete proposed record through a callback.

The ViewModel launches work in its scope and exposes progress or errors. `LedgerRepository.saveExpense` validates the record, checks that the category exists, applies rules for edits or refunds, records appropriate Undo information and writes through the DAO inside a transaction.

Room invalidates relevant observable queries. The ViewModel's collected state now contains the record. The Expenses list and monthly reporting functions calculate their results from that state. Success feedback is an event, not another financial transaction.

If validation fails, no partial record should appear. If storage fails, the UI should preserve the draft so the user can retry. If the UI closes during work, cancellation and operation completion must be handled intentionally.

## Ownership prevents hidden coupling

Ask “Who owns this value?” The editor owns an unsaved amount. The repository owns permission to commit a financial operation. Room owns persisted records. Theme settings belong to settings storage. A screen may display all of them without becoming their permanent owner.

**Common mistakes:** writing SQL directly from a Composable; storing one monthly total in several places; using a global mutable list as a database; putting every operation into a huge Activity; changing a category's stable ID when editing its display name.

**Practice:** draw the same trace for editing an existing expense. Mark where the ID stays unchanged, where validation runs again and where the old/new values support Undo.

# 7. State, lifecycle, coroutines and Flow

Android can recreate an Activity when configuration changes. It can also kill the app's process while it is in the background. A design that survives rotation may still lose unsaved work after process death. These are different cases.

| Mechanism | Appropriate role | Important limit |
|---|---|---|
| `remember` | Values retained during composition | Lost when that composition is removed |
| `rememberSaveable` | Small restorable UI values | Requires saveable types; not a file store |
| ViewModel | Screen state and operations | Does not inherently survive process death |
| SavedStateHandle | Small ViewModel restoration state | Not a substitute for persistent records |
| Room | Durable structured financial data | Requires successful commit |
| DataStore | Durable settings | Coordinate changes and migrations |

Use `rememberSaveable` for practical form restoration and scrolling state where supported. Do not put receipt bitmaps, entire database exports or huge transaction lists into saved-instance state. Save a small reference and reconstruct the view.

## Recomposition is normal

Compose may run a Composable function many times to reflect state changes. Recomposition is not equivalent to recreating the entire app. The function must be safe to execute repeatedly.

This is a dangerous teaching anti-example:

```kotlin
@Composable
fun BadScreen(repository: LedgerRepository) {
    // Do not start saves, OCR or database creation here.
}
```

Perform side effects in callbacks or lifecycle-aware effect APIs. `LaunchedEffect(key)` starts a coroutine associated with a particular composition and key; changing the key cancels and restarts it. `DisposableEffect` installs a resource or observer and cleans it up. The press-feedback implementation uses observer cleanup when monitoring system animation settings.

## Coroutines are structured asynchronous work

A `suspend` function can suspend without blocking its thread. It is not automatically a background-thread function. Blocking file access or image processing still needs an appropriate dispatcher. UI work belongs on the main thread; storage and blocking I/O commonly use `Dispatchers.IO`.

`viewModelScope.launch` ties a coroutine to the ViewModel lifetime. Child operations should generally remain within structured scopes rather than escaping into `GlobalScope`. On cancellation, release resources. Do not accidentally swallow `CancellationException` in a broad catch and continue writing.

`Mutex` serializes cooperating coroutine operations. A database transaction protects a different boundary: related database changes commit together or roll back together. One does not replace the other. A mutex in one repository is not a magical lock covering every repository and process.

## Flow and StateFlow

A `Flow` represents values over time. A Room observable query can emit when relevant tables change. A `StateFlow` holds a current state value and exposes subsequent updates. `stateIn` shares an upstream flow in a scope; the project uses subscription policies such as `WhileSubscribed(5000)` in its ViewModel state.

`collectAsStateWithLifecycle` connects collection to an appropriate Android lifecycle. This reduces unnecessary collection when the UI is inactive. It does not mean all upstream computation is free or all background work stops.

State and one-time events differ. The transaction list is state. “Expense saved” and an Undo offer are events. The project uses channels for some messages. Replaying a save operation merely because a screen re-collects state would be a serious bug.

## Avoid stale settings writes

Suppose two controls both read settings S. One changes the theme; the other changes haptics. If each writes a complete modified copy of S, the second can erase the first change. The repository's `updateSettings` reads the latest settings and applies a transformation under its write coordination.

A preference toggle should also not activate a global full-screen busy overlay. Small operations need appropriately scoped feedback; otherwise unrelated controls blink or appear to reload.

**Practice:** name which state should survive (a) recomposition, (b) rotation, (c) closing a form, and (d) process death. There is no universal “put everything in ViewModel” answer.

# 8. Room, relational data and migrations

A relational database stores tables of rows linked by keys. An expense has an ID, amount, category ID, timestamp and other fields. A category has a stable ID and a display name. The relationship uses the ID so a name can change without breaking historical associations.

Room maps Kotlin entities to SQLite tables and generates DAO implementations. SQLCipher supplies encrypted SQLite storage underneath. Room itself is not the encryption algorithm.

## Keys, indexes and relationships

A **primary key** uniquely identifies a row. A **foreign key** requires a referenced row to exist, subject to the declared relationship. An **index** speeds selected lookups; a unique index also prohibits duplicate non-null values for its key.

In the current model, expenses reference categories with deletion restricted and refuels with cascade deletion. The unique `fuelId` index prevents two expenses from owning the same refuel. Many ordinary expenses can have a null `fuelId`; SQLite permits multiple null values in such a unique index.

Deleting a custom category is therefore not just deleting its row. The repository first reassigns relevant records and mappings to Others. Deleting a refuel removes its linked expense through the intended relationship. Those are deliberate product semantics, not interchangeable defaults.

An index has a cost: extra storage and work during writes. Add one for a demonstrated query or constraint rather than indexing every column.

## Queries and transactions

```sql
-- Teaching example: use bound parameters, not string concatenation.
SELECT * FROM expenses
WHERE timestamp >= :start AND timestamp < :endExclusive
ORDER BY timestamp DESC
```

Bound parameters separate values from SQL syntax and avoid injection from ordinary input. A DAO can return a `Flow<List<Expense>>` for observed records or use a suspending function for a one-time result.

A transaction is needed when multiple rows form one logical operation. Saving a refuel and its linked expense must succeed together. Receipt acceptance writes an expense and duplicate-tracking information together. If only one write succeeds, the application can become inconsistent.

Transactions do not make all future operations valid. Undo must validate against current records, not only trust the values valid when the journal entry was written.

## Schema evolution

The current Room schema is version 5. Explicit migrations preserve records:

| Transition | Main additions |
|---|---|
| 1 → 2 | Fuel type, station location and receipt reference |
| 2 → 3 | Expense receipt/recurrence fields and finance tables |
| 3 → 4 | Recurrence uniqueness, income/manual rule metadata, merchant/scan, journey and planning tables |
| 4 → 5 | Merchant/refund fields, merchant preferences, favorites and Undo journal |

The exported JSON schemas are in `app/schemas`. They are a reviewable record of the expected schema, not a replacement for migration testing.

Adding a non-null column to an existing table usually requires an appropriate default for old rows. Renaming or changing a type may require a table-copy migration with explicit conversion. Never use destructive fallback just to silence a migration exception in an app holding real financial records.

## Three different compatibility questions

Can the new APK install over the old one? That depends on package/signing/version compatibility. Can the new database open old records? That depends on migration correctness. Can the new JSON model read old backup or journey payloads? That depends on serialization and model evolution.

Passing one of these does not prove the others.

**Practice:** design an optional “reference number” field. Decide whether it needs a column, how old rows read, what the backup validator accepts and which tests would demonstrate preservation. Do not implement it on the owner's data as a learning exercise.
