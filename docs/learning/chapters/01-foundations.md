# 1. How to learn from your own application

This book explains how your Fuel & Expense Tracker works and how to maintain an application like it. You do not need to know Kotlin before starting. You do need to practise: read a small section, predict what the code will do, run a controlled experiment, and compare the result with your prediction.

The reference application is version **1.5.4**, Android version code **11**, package **my.fuelledger**, at source checkpoint **1f13482bfc550da77e5c950e48075f62de12620f**. The material was prepared on **21 October 2026**. References to “current implementation” mean that checkpoint. Later improvements may change file names, line numbers or behavior. Historical investigation reports describe their own earlier checkpoints.

Your app is a substantial example: expenses, refunds, refuelling, journey estimates, recurring commitments, monthly allocations, charts, local receipt recognition, encrypted persistence, backups, accessibility and release management all interact. Learning why those interactions are safe is more useful than memorizing every line.

The guide has four kinds of material:

- **Explanation:** a concept, the reason for using it, and its tradeoffs.
- **Worked example:** invented financial records with calculations you can reproduce.
- **Source reading:** a real file or function in the accompanying source snapshot.
- **Practice:** an exercise on a disposable learning branch or test fixture.

Short teaching snippets are intentionally smaller than production implementations. They are labelled as examples, not replacements for whole source files. The source-reading appendix contains exact excerpts. The companion archive contains the actual source, tests, configuration, migration schemas and relevant project documentation. It excludes financial records, credentials, build caches and APK binaries.

## A manageable learning route

For a first pass, study Chapters 1–8, then money and expenses in Chapters 9–10. You should be able to explain how a button press becomes a saved record before moving to receipts and encryption. On a second pass, study fuel, planning, journeys and analytics. On a third pass, study security, debugging, testing and releases.

A suggested eight-week route is:

| Week | Focus | Evidence that you understand |
|---|---|---|
| 1 | Tools, Kotlin, types and functions | Explain a small function without reading its comments |
| 2 | Compose, state and architecture | Trace amount entry from UI to repository |
| 3 | Money, expenses, Room and migrations | Write a test that catches incorrect sen conversion |
| 4 | Fuel, journeys and planning | Work out expected results by hand |
| 5 | Receipt parsing and files | Explain why an ambiguous receipt needs review |
| 6 | Privacy, encryption and backups | Distinguish five different kinds of key or password |
| 7 | Debugging, tests and accessibility | Reproduce and isolate a fault in a fixture |
| 8 | Git, Codex and release management | Deliver one small reviewed change with evidence |

Go slower if necessary. A useful learning session ends with a concrete result: a diagram, a passing test, an explained function or a corrected misunderstanding.

## What “complete” means here

This is a complete learning path through the major engineering responsibilities of this particular app. It is not a substitute for every Android API reference or an exhaustive computer-science degree. Native Android development also includes subjects this app does not need, such as multiplayer networking, payment processing and server authentication.

The book distinguishes implemented behavior, historical test evidence and proposed exercises. A screenshot does not prove calculation correctness. A passing unit test does not prove a camera works on every phone. A successful build does not prove a safe update.

**First exercise:** explain the difference between a record, a screen and a calculation. An expense record is persisted data; the Expenses screen displays a selection of records; a monthly total is a calculation over those records. Changing the display order should not rewrite the records.

# 2. Why Kotlin, Compose and these supporting technologies?

The app uses **Kotlin** because it is a well-supported language for native Android development and works directly with Jetpack Compose, coroutines and Android libraries. Kotlin offers concise data classes, nullable types, collection operations and good Java interoperability. Those features reduce repeated code and make some errors easier to detect.

Kotlin does not automatically produce correct financial calculations, secure storage or an attractive interface. A badly designed Kotlin program can lose data just as easily as a badly designed Java program. The quality comes from explicit rules, careful implementation and verification.

## Why not another language?

Java remains a valid Android language and is useful for understanding older libraries. Kotlin makes the current Compose and coroutine code more natural to express. Flutter with Dart can share a UI across Android and iOS; React Native with JavaScript or TypeScript can share application code with a web-oriented team. Those approaches have their own tooling and native-integration tradeoffs.

For this existing Android-only app, changing language or framework would introduce migration work without solving a demonstrated problem. Preserving the working architecture is the sensible engineering choice. If iOS later becomes a firm requirement, compare options using actual staffing, platform integration and maintenance costs.

## Which technology does which job?

| Technology | Job in this project | Why it is useful |
|---|---|---|
| Kotlin | Application and test logic | Types, null safety, Android integration |
| Jetpack Compose | Screens and interactive controls | UI is a function of observable state |
| Material 3 | Components, typography and theme roles | Consistent accessible building blocks |
| ViewModel and StateFlow | Screen state and ongoing work | State can survive Activity recreation |
| Room | Database mapping, queries and migrations | Checked SQL interfaces and observable queries |
| SQLCipher | Encrypted SQLite storage | Database contents are encrypted at rest |
| DataStore | Local application preferences | Observable, structured preference persistence |
| Bundled ML Kit | On-device text recognition | Recognition model is included in the APK |
| WorkManager | Deferrable recurring work | Android-managed background scheduling |
| Android Keystore | Protect local cryptographic keys | Keys need not be stored as plain app files |
| Tink | Authenticated streaming backup encryption | Maintained cryptographic construction |
| Gradle and KSP | Build orchestration and generated Room code | Repeatable compilation and schema processing |
| Python | Release verification and automation | Explicit artifact and metadata checks |

The project also uses XML for manifest declarations, adaptive icons, some resources and home-screen widgets. XML is not obsolete because the main UI uses Compose. Java appears in the build policy that checks and transforms a particular ML Kit dependency. SQL appears in Room queries and migrations. Each language serves a specific layer.

## Understand versions before changing them

At the reference checkpoint, the project uses Kotlin 2.3.20, Gradle 9.6 and Android Gradle Plugin 9.4.1. These are snapshot facts, not a recommendation to put these versions into every future project.

The environment uses **JDK 21**, while the app targets **Java/Kotlin bytecode level 17**. These are different from **minSdk 26**, **compileSdk 37** and **targetSdk 37**:

- The JDK runs the compiler and build tools.
- The bytecode target controls a compilation compatibility level.
- minSdk specifies the oldest supported Android API level.
- compileSdk supplies Android APIs available at compilation.
- targetSdk declares the Android behavior level the app has been prepared to support.

Raising compileSdk does not automatically make new APIs safe on older devices. Calls introduced after API 26 may need a version check or a compatible library.

**Common mistake:** upgrade every dependency at once after a build error. That creates several moving targets. First identify the failing tool, inspect the pinned configuration, and change the smallest necessary part.

**Source reading:** `app/build.gradle.kts`, root `build.gradle.kts`, `buildSrc/build.gradle.kts` and the wrapper properties. This snapshot declares dependencies in Gradle files; do not assume a version catalog exists.

# 3. Requirements, invariants and honest product design

Before designing a screen, define what the application is responsible for. This app records financial events and calculates estimates. It is not connected to a bank and cannot know a real account balance merely from entered expenses.

A **requirement** describes desired behavior. An **invariant** is a condition that must remain true through every permitted operation. “Add a refuelling form” is a requirement. “A refuelling purchase must contribute only once to total expenditure” is an invariant.

Important invariants in this app include:

- Money is stored in exact sen, not floating-point ringgit.
- A linked refuel has at most one linked expense.
- A refund cannot exceed the remaining refundable amount of its purchase.
- A recurring occurrence cannot create the same payment twice.
- Allocation percentages total exactly 100% before saving.
- Planned reserves are not actual transactions.
- An ambiguous receipt cannot bypass review through smart auto-save.
- Updating appearance does not rewrite financial records.
- An app update preserves package identity, signing identity and compatible data.

These rules belong in the data/domain layer as well as the UI. A disabled Save button helps the user, but it is not enough: imports, background jobs, tests and future screens can call the repository directly.

## Write acceptance criteria you can observe

For cents-first entry, an observable criterion is: “Starting at zero, pressing 1, 2, 3 displays RM 1.23; pressing backspace displays RM 0.12.” “Make entry intuitive” is a design goal, but not a sufficient test.

For theme persistence: “Choose Dark, close and recreate the app, and observe Dark without changing the stored expenses.” For duplicate receipts: “Submit two concurrent save requests with the same normalized fingerprint; no more than one receipt transaction is committed.”

Specify failure behavior too. If the amount is zero, Save is disabled. If a database key is unavailable, existing files are preserved and the failure is reported. If OCR cannot read a date, the interface asks for review rather than inventing today's date.

## Design with the user’s mental model

Use plain labels with units: “Distance (km),” “Consumption (L/100 km)” and “Monthly allocation.” Distinguish *expected* income from *received* income. Distinguish an estimate from a measurement. A beautiful monetary figure with an unclear meaning is a poor financial interface.

One-handed operation favors accessible actions near the lower part of the screen. It does not justify hiding important validation. Large text should remain readable; permit scrolling and wrapping rather than shrinking every element to make a screenshot look tidy.

Choose data retention intentionally. Receipt photos can contain personal information. The app allows the user to retain a private copy or remove an image while preserving the financial transaction. Those are different user decisions and need different operations.

## Scope and change control

The project has evolved through many requests. An older request may say “Home, Fuel, Expenses,” while the current implementation may use a later navigation order. When learning, inspect the actual current code and dated reports. Do not silently treat every historical request as the final specification.

Before implementing a change, write: the problem, affected behavior, protected invariants, files likely involved and verification method. This five-line brief is often more valuable than immediately generating hundreds of lines.

**Practice:** propose a “duplicate expense” action. Decide which fields should copy and which must change. A new record needs a new ID. It should not inherit receipt duplicate ownership, a refuel link, a refund relationship or a recurring occurrence link without explicit rules.

# 4. Kotlin fundamentals using financial examples

A program is a set of precise instructions operating on data. Kotlin's type system tells the compiler what kinds of values are permitted. Reading the type often explains more than reading a variable's name.

```kotlin
// Teaching example: exact values, not a replacement entity.
val lunchSen: Long = 1250L
val description: String = "Lunch"
val hasReceipt: Boolean = false
var selectedCategoryId: String = "food"
```

`val` prevents assigning a different value to that variable. `var` permits reassignment. A `val` referring to a mutable list does not make the list immutable; prefer immutable data and explicit replacement when passing state through the UI.

`Long` is a signed 64-bit integer. `Double` is a floating-point number. Money in this app uses `Long`; physical measurements such as litres and distance use validated `Double` values. A type cannot supply units by itself: `7.0` could mean kilometres, litres or L/100 km. Names and function boundaries must make units clear.

## Functions and return values

```kotlin
// Teaching example.
fun remainingSen(allocatedSen: Long, spentSen: Long): Long {
    return allocatedSen - spentSen
}
```

This function takes two arguments and returns one result. It does not write a database, display a screen or change its inputs. Such **pure functions** are easy to test. In production, define valid ranges and overflow behavior as well as the formula.

An expression-body function can omit braces and `return`:

```kotlin
fun isOverspent(remainingSen: Long) = remainingSen < 0L
```

Use `if` and `when` for decisions. Do not duplicate a calculation in every branch when only the display label differs.

## Nullable values

`String?` means a string or `null`. A missing merchant is different from a merchant whose name is the literal word “Unknown.” `Double?` is useful when no valid fuel interval exists; zero efficiency would communicate a different and usually false fact.

```kotlin
val merchant: String? = null
val label = merchant ?: "Review merchant"
val characterCount: Int? = merchant?.length
```

`?:` supplies a fallback. `?.` calls only if the value is non-null. Avoid `!!`, which asserts non-null and crashes if the assertion is wrong. A fallback suitable for display may be unsuitable for saving. “Review merchant” must not silently become the stored merchant.

## Data classes, equality and copying

```kotlin
data class EntryDraft(
    val cents: Long,
    val categoryId: String,
    val note: String = ""
)
val original = EntryDraft(1200L, "food")
val revised = original.copy(note = "Team lunch")
```

A data class generates structural equality and `copy`. The revised value leaves the original unchanged. This supports predictable UI state and conflict-aware Undo. `==` checks value equality; `===` checks object identity. `copy` is shallow: nested mutable objects are still shared unless separately copied.

## Collections and lambdas

`filter` selects values, `map` transforms them, `groupBy` groups them, and `sumOf` totals a numeric property.

```kotlin
// Teaching example; real reporting also filters by date and zone.
val foodTotal = expenses
    .filter { it.categoryId == "food" }
    .sumOf { it.cents }
```

The braces contain a lambda; `it` is the current item. `List<Expense>` means a list whose elements are expenses. Sets are useful for repeated membership checks; maps connect IDs to values.

Use meaningful names in nested lambdas. A sequence of several `it` references can hide which object is being read. Also consider cost: filtering the entire list inside another loop can become quadratic.

## Exceptions and validation

`require(condition) { "Message" }` rejects invalid input with an exception. Repository callers convert expected validation failures into useful UI messages. Do not catch every exception and pretend the operation succeeded. Database, permission and cryptographic errors need different recovery paths.

Kotlin features to learn next include interfaces, extension functions, sealed classes, generics and annotations. In this project, `PreferencesStore` is an interface, `Expense.point()` is a transformation, and annotations such as `@Entity` and `@Composable` instruct tools about a declaration's role.

**Practice:** predict the result of `EntryDraft(1200, "food").copy(cents = 1300)`. Which properties change? Then explain why converting an absent amount to zero and saving it would violate the expense rules.

# 5. Reading the project and setting up a learning workspace

The top level contains build configuration, the Gradle wrapper, application code, scripts, documentation and release history. Inside `app/src/main`, Kotlin code and resources become the application. `app/src/test` contains host tests. `app/src/androidTest` contains tests that run on Android.

Start with this reading route:

1. `LedgerApplication.kt`: which long-lived dependencies are created?
2. `MainActivity.kt`: how does Android enter the Compose UI?
3. `ui/LedgerApp.kt`: how are screens, forms and navigation connected?
4. `LedgerViewModel.kt`: what state and operations does the UI observe?
5. `data/LedgerRepository.kt`: which rules apply when saving?
6. `data/Database.kt`: what is stored, queried and migrated?
7. `domain/Calculations.kt`: which formulas are independent of the UI?

You will not understand every symbol on the first pass. Identify the direction of calls, then return to unfamiliar details when you have a concrete question.

## Safe local setup

Use a separate clone or branch for experiments. Install Android Studio with the SDK versions required by the checked-out build, and use the project's wrapper rather than an unrelated global Gradle installation. Let Android Studio configure the local SDK path. Keep `local.properties`, signing files and personal test data out of Git.

```sh
git clone https://github.com/Piccol1001/ChatgptFuelConsumption.git
cd ChatgptFuelConsumption
git switch -c learning/first-exercise
./gradlew :app:testDebugUnitTest
```

On Windows, the wrapper command is `gradlew.bat`. The existing cloud setup has an environment script at `/workspace/.tooling/fuel-ledger-env.sh`; that path is specific to this environment and is not something a Windows learner should invent locally.

The source companion is a reproducible reading snapshot, not a bundle of every downloaded dependency. A first development build may need internet access to obtain Gradle, SDK components and Maven libraries. **Build-time downloads do not imply runtime networking in the app.** Conversely, an offline build is not proof that the app lacks network permissions.

## The build pipeline

Gradle configures tasks and resolves dependencies. Kotlin and Java are compiled. KSP generates Room implementations and schema information. Android resources and the manifest are merged. Bytecode is converted to DEX. A release build may shrink and optimize code and resources. Packaging and signing produce an APK.

When a task fails, identify the stage:

- Dependency resolution: repository access, version or cache issue.
- Compilation: invalid code or mismatched API usage.
- Resource processing: missing or malformed resource.
- Tests: behavior differs from the expected result.
- Packaging/signing: artifact or certificate problem.
- Installation: device compatibility, version or signature issue.

The first meaningful error is often more useful than the final “BUILD FAILED” line. Keep a small error diary: symptom, hypothesis, evidence, change and outcome.

**Practice:** locate the actual `versionName`, `versionCode`, `applicationId` and Room schema version. Explain why these four values serve different purposes. Do not change them merely to practise finding them.
