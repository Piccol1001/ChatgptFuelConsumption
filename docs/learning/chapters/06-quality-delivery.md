# 22. Background work, widgets and performance

An offline app still performs background work. Recurring reconciliation, reminders and home-screen widgets need scheduling and careful data access. They must not become alternate financial engines with different rules.

`RecurringWorker` uses WorkManager's unique periodic work. Repeated scheduling keeps one intended job rather than adding a new job on every app launch. Reconciliation relies on database idempotency, because a worker can retry or run later than expected.

Notifications are a user-visible permission boundary. On Android versions that require notification permission, denial should disable notification delivery without breaking expense entry. A reminder announces a plan; it does not prove payment occurred.

Widgets use Android's widget infrastructure and XML/RemoteViews resources. They cannot use arbitrary Compose components as though they were an Activity. Widget privacy settings should control exposed values, and widget click intents must validate their requested action.

## Measure before optimizing

Performance includes cold startup, interaction latency, list scrolling, image decoding, database queries and memory. A slow emulator boot is not the same as a slow chart recomposition. A long OCR scan is not necessarily caused by the parser.

Separate stages with timestamps or sanitized instrumentation. Measure a release-like build on representative hardware where possible. Debug builds, software emulation and test instrumentation can alter timing.

The project has a documented software-emulator startup timeout history. A longer controlled startup check passed, but this does not establish a production startup improvement. Reporting “fixed performance” after only increasing a timeout would be misleading.

## Practical improvements with evidence

Use stable IDs in lazy lists. Avoid sorting and filtering large histories repeatedly inside every row. Precompute lookup sets when repeatedly checking whether expenses belong to fixed commitments; set membership avoids repeated linear searches.

Keep expensive image work off the main thread. Decode bounded images, release native resources and avoid retaining several full-size bitmap copies longer than necessary. Do not load every retained receipt merely to render a transaction list.

Avoid premature caching of financial totals. A cache with unclear invalidation can be faster and wrong. Start with a correct source of truth, measure a bottleneck, then design a cache whose invalidation includes edits, refunds, restores and category changes.

Database queries and Flow collections also have costs. Share observed state at suitable lifetimes and avoid creating a new collector or database from every row.

**Practice:** suppose a monthly report checks each of 10,000 expenses against a list of 2,000 fixed IDs. Explain why building a set once is useful. Also explain why the expected result must remain byte-for-byte or value-for-value equivalent after the optimization.

# 23. Testing: choose evidence that matches the claim

Tests are executable statements about behavior. Good tests protect rules that can realistically fail. A test that copies the implementation's formula line for line may reproduce the same mistake rather than detect it.

## A useful test ladder

| Level | Example in this app | What it cannot prove alone |
|---|---|---|
| Pure function | Sen keypad, allocation remainder, receipt parsing | Android camera or persistence |
| Repository/Room | Atomic refuel linkage, refund limits, duplicate save | Every rendered layout |
| Host Compose/Robolectric | Entry controls, navigation, matched screenshots | All native/OEM behavior |
| Android instrumentation | Bundled OCR, encrypted reopen, real startup | Every physical receipt/device |
| Manual physical test | Camera focus, TalkBack, system back, actual upgrade | Exhaustive numerical correctness |

Use synthetic data. Never clear the owner's app to prepare a test. This repository's native workflow uses isolated temporary Android test users when supported. A separate emulator is another appropriate learning environment.

## Assertions should express meaning

For allocation tests, assert that the sum equals the available positive sen, invalid totals are rejected, rounding is deterministic and spending is not double counted. Do not merely assert that the function returns a nonempty list.

For a refuel repository test, assert both rows exist with the correct link and amount. Simulate an invalid edit and assert both old rows remain unchanged. A test that checks only the refuel table can miss a broken expense summary.

For duplicate scanning, include concurrent requests and repository validation, not just two sequential taps disabled by the UI.

For persistence, create records, close the relevant storage/process boundary and reopen. Reading an in-memory ViewModel value after a click is not proof of disk persistence.

## Boundary and property thinking

Test zero, one, maximum, just beyond maximum, empty input, missing fields, duplicate IDs, dates around month boundaries and malformed files. For a property, verify a rule over many inputs: `backspace(append(c,d)) == c` for allowed c and d, or allocation sum equals max(available, 0).

Properties supplement examples. They still need correct preconditions and do not automatically cover UI behavior or all stored-data relationships.

## Commands and environment

After loading the documented toolchain:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
python -m unittest discover -s scripts/tests -v
```

Run appropriate checks for the change and follow `AGENTS.md` for the repository's sequencing. Do not run a connected test command against an owner's phone without understanding its setup and isolation. An unsigned release build is a compilation artifact, not an installable signed distribution.

## What was actually recorded for 1.5.4?

The existing project evidence records 316 app host test executions across 39 suites, four build-policy Java tests and six Python release-workflow tests. Debug, instrumentation and optimized unsigned release builds passed. Lint recorded zero errors, four warnings and one hint.

Native evidence is split: a full ten-test run was on 1.5.3/code 10, and four targeted follow-ups ran against the exact 1.5.4/code 11 APK. Do not rename the older run as full coverage of the newer artifact. The reports also contain 45 host renders and a native Home capture.

These are **historical project results**, not tests rerun merely to write this book. Physical camera/gallery variability, TalkBack, OEM widgets and some system-gesture cases remain manual coverage limits.

**Practice:** write a test description that proves an invalid restore preserves existing records. Include a known preexisting row and assert it still exists with the same value after the attempted restore.

# 24. A debugging field guide and common mistakes

Debugging is a controlled search. Reproduce the symptom, reduce the case, identify the layer, form a hypothesis, gather evidence, make one focused change and rerun the relevant check.

Record expected and actual values in units. “Wrong fuel number” is vague; “600 km and 50 L produced 11.67 km/L instead of 12” points toward averaging interval ratios.

| Symptom | Likely mistake | First useful check |
|---|---|---|
| Expense is 100× too large | Stored sen multiplied again | Trace edit initialization and save conversion |
| Monthly total doubles fuel | Both refuels and linked expenses summed | Inspect source rows used by report |
| Allowance drops twice after paying rent | Reserved commitment deducted again | Inspect variable-spending exclusion/link |
| Refund exceeds purchase | Only latest refund checked | Sum all linked refunds inside transaction |
| Undo overwrites newer work | No expected-current-state guard | Compare journal after-state with current row |
| Recurring bill appears twice | Non-idempotent insertion | Inspect unique occurrence key and transaction |
| January-31 bill drifts to March-28 | Clipped date becomes new anchor | Compare anchor and occurrence calculations |
| Theme resets after restart | UI-only state or stale write | Reopen DataStore and inspect update flow |
| All buttons blink on one toggle | Global busy state reused | Separate preference write from full operation state |
| Journey is taller than references | Extra padding, empty child, wrapping | Measure matched bounds and typography roles |
| Swipe changes page while using slider | Parent takes unconsumed drag | Inspect nested-scroll ownership at boundaries |
| Receipt chooses cash tendered | Largest-number heuristic | Inspect labelled candidates and exclusions |
| Receipt is saved twice | UI-only duplicate prevention | Check atomic fingerprint uniqueness |
| Backup opens but later corrupts | Authentication not read through EOF | Test truncated/trailing-corrupt streams |
| App opens empty after key failure | Destructive fallback | Preserve files and inspect key/migration errors |
| APK cannot update installed app | Certificate/package/code mismatch | Inspect embedded metadata and signatures |
| Layout works only at default font | Hardcoded height or truncation | Render large font and narrow viewport |

## Read errors without chasing the wrong layer

An unresolved Kotlin reference is usually a compile-time problem: import, dependency, name or API mismatch. A Room schema exception is a persistence compatibility problem. A security exception may indicate URI or permission handling. An out-of-memory error after image import points toward decode bounds or retained bitmaps.

Capture the relevant exception and stack trace in a safe development fixture. Do not paste personal receipt text or secrets into an AI prompt just because they appear near an error.

## Fix causes, then add the right regression

The refund Undo and commitment-link defects are good examples because tests failed on invalid repository behavior before the fix. The Journey investigation is good because measured differences guided the change. A screenshot that merely “looks nicer” would not prove a root cause.

Do not weaken a test expectation to match a bug unless the product requirement itself has intentionally changed. Do not increase timeouts and claim speed improved. Do not delete a migration or saved file to make a fresh install pass.

**Practice:** choose one row in the table and write a five-step investigation that avoids changing unrelated code. Your first step should be reproduction, not “rewrite the screen.”

# 25. Git, GitHub and working effectively with Codex

**Git** tracks revisions locally. **GitHub** hosts a Git repository and related collaboration tools. Saving a file is not committing it, committing is not pushing, and pushing source code does not automatically create an APK.

```text
Working files -> staged changes -> local commit -> pushed remote commit
                               \
                                build/test -> verified APK artifact
```

## A small, reviewable workflow

```sh
git status
git switch -c learning/clearer-error
# Make one focused change.
git diff
# Run the relevant tests.
git add path/to/changed-file.kt path/to/relevant-test.kt
git commit -m "Clarify invalid amount feedback"
git push -u origin learning/clearer-error
```

Review staged changes before committing. Prefer explicit paths to blindly staging everything when a workspace might contain screenshots, credentials or unrelated work. `git diff --staged` shows what the next commit contains.

A branch is a movable pointer to commits. A pull request proposes integrating one branch into another. A merge conflict means Git needs help combining changes; read both intentions instead of choosing “ours” or “theirs” mechanically.

`.gitignore` prevents matching untracked files from being added by ordinary workflows. It does not remove a secret already committed. If a real secret reaches history, revoke or rotate it appropriately and handle history cleanup deliberately.

## What to ask Codex

A strong task identifies the current behavior, desired behavior, protected invariants and acceptance checks.

```text
Inspect the existing amount editor.
Fix editing so stored sen initializes without another ×100 conversion.
Preserve entity fields, category IDs and receipt integration.
Add a regression using an existing RM 12.50 record.
Report changed files, tests and any unverified device behavior.
```

Ask the agent to read repository instructions and progress notes, inspect relevant files, implement the focused change and verify it. Providing the existing source is more useful than asking for a disconnected replacement app.

AI-generated code is a proposed implementation, not evidence of correctness. Review whether it invents APIs, changes units, drops historical fields, adds network dependencies or silently substitutes generic assets for supplied branding.

## Learn actively from generated changes

Before accepting a patch, explain one changed function in your own words. Ask: what input does it accept, what does it return or mutate, what can fail and what invariant does it preserve?

Request exact files and tests, not vague claims such as “optimized everything.” A useful handover includes the source checkpoint, completed work, remaining work, known failures and exact resume commands.

For a long task, `PROGRESS.md` provides continuity. It should preserve historical evidence rather than overwrite previous results with an unqualified success message. A source checkpoint is valuable before a release because binary metadata can then refer to a known revision.

**Practice:** explain why a GitHub repository can contain all source but no downloadable APK. The APK is generated by the Android build and must be separately published through the project's artifact workflow.

# 26. Version management, signing and safe APK delivery

The project separates ordinary builds from distributable releases. Rebuilding the same source for tests should not increment a release version. A failed attempt should not consume the recorded next release.

`versionName` is the human-readable semantic version. `versionCode` is Android's monotonically increasing release integer. Room's database version is a separate schema number.

Use MAJOR for significant architectural changes, MINOR for new functionality and PATCH for fixes/UI refinements according to the project's policy. Semantic versioning communicates scope; Android's version code controls upgrade ordering.

## The actual workflow

Read `RELEASING.md`. Finish and checkpoint the source, write reviewed notes, then use:

```sh
source /workspace/.tooling/fuel-ledger-env.sh
python scripts/release.py --bump patch \
  --notes path/to/reviewed-notes.md --variant debug
```

The environment path is cloud-specific. Choose minor or major when justified; do not copy “patch” for every change.

The release script checks the existing signing identity, audits historical APK metadata, chooses a higher code and builds with temporary version overrides. It verifies package, actual embedded version, signature, alignment and no-network policy. Only after success does it publish the new named artifact and update version properties, changelog, release ledger and checksums.

Historical APKs stay unchanged. Renaming an old binary to `v1.6.0.apk` does not change the version inside it. If a replacement is needed, build a new compatible artifact and document its actual metadata.

## Signing identity matters

An update must preserve application ID and compatible signing identity. A new key with the same filename or password is not the old key. Losing the original private key can block a compatible update; asking users to uninstall would risk local records and is not an acceptable silent workaround.

The current downloadable 1.5.4 APK is explicitly **signed debug**, preserving the existing development certificate. An optimized unsigned release build was also verified, but it is not the distributed signed APK. Do not call the download a production-signed release merely because it builds.

For an optimized signed distribution, the documented workflow accepts an artifact signed with the original key and verifies it. Keys and passwords remain outside Git.

## Verify delivery, not just compilation

Inspect the APK itself using Android build tools for package/version metadata and `apksigner verify --print-certs` for signing information. Compute SHA-256, publish the intended artifact and compare the public download's checksum with the local artifact.

Settings → About reads the installed package version, avoiding a stale handwritten label. Main screen headers omit the app/version title.

For an upgrade test, use an isolated fixture with representative records, install a newer compatible APK without clearing data and verify records, settings, encrypted reopening and migrations. Higher version code alone cannot guarantee data safety.

**Practice:** distinguish these failures: same certificate with a lower code; higher code with a different certificate; correct APK with a broken Room migration. Each requires a different diagnosis.

# 27. Guided practical projects and answer key

Complete these in a learning branch or isolated fixture. They are small extensions of reasoning, not permission to modify the owner's data or publish a new release.

## Lab A — Trace an expense

Locate the amount state, save callback, ViewModel method, repository validator and DAO write. Write the stored value for RM 45.67 at each boundary.

**Answer:** after valid conversion it is 4567 sen throughout the storage path. Formatting returns RM 45.67 for display. There should be no second multiplication by 100. The same ID persists during an edit.

## Lab B — Keypad boundary test

Test clear, leading zeros, append and backspace. Test appending a digit when the bound would be exceeded.

**Answer:** clear returns zero; leading zeroes remain zero; integer division removes the last digit. `appendSenDigit` returns null for an excessive proposed result. The UI should preserve the previous valid amount, not wrap or crash.

## Lab C — Fuel aggregation

For intervals of 500 km/40 L and 100 km/10 L, calculate combined efficiency.

**Answer:** 600/50 = 12 km/L and 50/600×100 = 8.333 L/100 km. The arithmetic mean of 12.5 and 10 is 11.25 and is not the combined efficiency. State the equal-tank-level assumption.

## Lab D — Allocation remainder

Allocate 101 sen with 20%, 30%, 15% and 35%.

**Answer:** exact shares are 20.2, 30.3, 15.15 and 35.35 sen. Floors total 100; the largest fractional remainder is 0.35, so final amounts are 20, 30, 15 and 36 sen. Their sum is 101.

## Lab E — Refund conflict

Create the RM 10 → RM 20 edit, then a RM 15 refund in a repository fixture. Attempt Undo.

**Answer:** Undo rejects restoration to RM 10, preserves current valid records and retains the journal entry. The test should inspect all three facts. A UI-only disabled button would not prove repository safety.

## Lab F — Receipt ambiguity

Parse a synthetic receipt containing `TOTAL RM 20.00` and `GRAND TOTAL RM 25.00`.

**Answer:** ranking can suggest the stronger total, but the distinct candidates set ambiguity. Smart acceptance must require review. A suggested value and permission to auto-save are separate decisions.

## Lab G — Date boundary

Make one expense at 23:59 local on the last day of a month and another at 00:00 on the next day.

**Answer:** the first belongs to the first month and the second to the next. An end-exclusive next-month boundary avoids overlap. Test with an explicit zone.

## Lab H — Theme and process lifetime

Set Dark, recreate the Activity, then reopen persistent settings in the fixture.

**Answer:** both checks should retain Dark, but they prove different things. Activity recreation alone can retain ViewModel state and does not prove a DataStore write. Also test System against different device theme settings.

## Lab I — Small UI change without financial change

Move a heading relative to a filter row. Define what must remain identical.

**Answer:** category IDs, selection behavior, filtering result, transaction records, calculations and Add Expense controls. Verify hierarchy/spacing and the preserved behavior. Do not rewrite the database.

## Lab J — A migration design

Propose an optional reference text field with a length limit.

**Answer:** define a compatible default/nullability for old rows, migration SQL, exported schema, serializer compatibility, validation and UI behavior. Test an old database upgraded with records preserved. A clean-install-only test is insufficient.

## Lab K — Release investigation

Compare an APK filename with embedded metadata and signature.

**Answer:** report the actual embedded version and code. A filename is only a label. A compatible update needs the existing package/signing identity and a suitable code; schema compatibility still needs separate tests.

## Lab L — Capstone: improve one validation message

Choose a confusing validation message. Inspect every entry point that can trigger it. Improve the text without changing the financial rule, add or adjust a meaningful test and prepare a reviewed commit.

**Acceptance:** the message names the problem and next action, no sensitive value appears in logs, existing records remain unchanged on failure, relevant tests pass and the final diff is narrowly scoped.

## Self-assessment

You are ready for independent maintenance when you can explain the data path, calculate expected financial results by hand, locate the correct rule owner, write a failing regression, preserve historical data and honestly describe what your tests do not cover.

If one of those feels uncertain, return to that chapter and repeat its experiment. Understanding grows through verified changes, not through the number of pages read.

# 28. Connecting the remaining feature details

Several smaller features reuse the same rules. Understanding their connections prevents “minor” conveniences from introducing inconsistent financial behavior.

## Favorites, duplication and filters

Favorites are reusable expense-entry templates. They help populate a draft; they are not expenses until accepted. A duplicate action also needs a new transaction identity and carefully cleared relationship fields. Copying a receipt fingerprint, refuel ID or recurring occurrence would incorrectly claim ownership of an existing event.

Expense filtering can combine date, category, payment, type and text. Apply filters to displayed results without changing the source records. A displayed filtered total must be clearly distinguished from the month's unfiltered total.

The shared calendar links days to actual records and can seed a date for entry. Calendar selection is UI state; opening a day must not create a transaction. The header, chart selection and transaction list should agree on the local date.

## Allocation sliders and the flexible category

`moveAllocation` changes the selected allocation and a designated flexible remainder category. It clamps the requested value to what those two categories can share. Other allocations remain unchanged, and the total stays at 10,000 basis points.

If Food has 30% and flexible General has 35%, Food can increase at most to 65% through that interaction. Increasing Food to 40% leaves General at 25%. Removing a non-flexible allocation transfers its percentage to the flexible category. A user must choose another flexible category before removing the current one.

Do not independently bind every slider to an unrestricted percentage and expect the total to remain valid.

## Vehicle coverage and cost drivers

`VehicleAnalysis.kt` derives a fuel cost per kilometre from valid intervals. Recorded operating cost adds applicable unlinked transport expenses only within those covered intervals. Linked refuels are already represented and must not be added again.

This is a cost of **recorded coverage**, not a promise to include depreciation, insurance, repairs or every kilometre ever driven. Show the interval and included categories so users understand the scope.

Average fuel price is total cost divided by total litres, not the unweighted mean of purchase prices. Recent consumption change compares the last two valid intervals with at least two earlier ones. Insufficient history yields no comparison.

The cost-driver calculation separates a change into price and consumption contributions:

```text
price effect = (recent price − prior price) × prior litres/km
use effect = (recent litres/km − prior litres/km) × recent price
total effect = recent cost/km − prior cost/km
```

These two contributions add to the total under this chosen decomposition. They explain the recorded arithmetic, not causal proof of engine faults or driving style.

## Sharing, cleanup and branding

Journey sharing produces text or an image through an explicit action. It should communicate whether values are planned or actual. Local cleanup distinguishes deleting receipt images, historical transactions and Undo history; each has different consequences and requires appropriate UI explanation.

Launcher resources include legacy densities, adaptive foreground/background and a monochrome themed icon. A correct icon must be checked under circular and rounded-square masks. The selected Option 4 replacement remains deferred because the exact supplied assets were absent at the snapshot. This book does not invent a replacement or claim that branding change was completed.

**Practice:** identify which of these features should write a financial record. Selecting a filter, moving an allocation slider, sharing a journey and choosing a calendar day should not. Accepting a valid populated expense draft should.

# 29. Glossary and further study

| Term | Meaning in this project |
|---|---|
| APK | Installable Android package containing compiled code/resources |
| API level | Android platform compatibility identifier |
| Artifact | Output such as an APK, report or generated document |
| Atomic | Related changes commit together or do not commit |
| Basis point | One hundredth of one percentage point |
| Coroutine | Structured asynchronous computation that can suspend |
| DAO | Interface defining database access operations |
| DEX | Android executable bytecode format |
| dp / sp | Density-independent layout / scalable text units |
| Flow | Stream of values over time |
| Foreign key | Database relationship to another row |
| Idempotent | Repeating an operation does not add unintended effects |
| Invariant | Rule that must stay true through valid operations |
| KSP | Kotlin symbol processing used for generated code |
| Migration | Explicit conversion from an older data schema |
| Mutex | Coordination primitive serializing cooperating operations |
| Nonce | Value that must be unique as required by an encryption scheme |
| OCR | Optical character recognition: image to text |
| Recomposition | Compose reevaluating UI functions after state changes |
| Repository | Owner of storage operations and cross-record rules |
| Semantics | Machine-readable meaning for accessibility and UI tests |
| StateFlow | Observable state with a current value |
| Transaction | Database unit of commit/rollback |
| URI | Identifier for a resource, including provider-owned documents |
| ViewModel | Lifecycle-aware owner of UI state and operations |

## Official references

Use the documentation matching the versions in your checkout. These are further-reading links, not a claim that every latest API is used by this app.

- [Kotlin language documentation](https://kotlinlang.org/docs/home.html): types, functions, collections and coroutines.
- [Android Kotlin guidance](https://developer.android.com/kotlin): Kotlin on Android.
- [Compose documentation](https://developer.android.com/develop/ui/compose): layout, state, effects and accessibility.
- [Android architecture guidance](https://developer.android.com/topic/architecture): state ownership and data layers.
- [Room documentation](https://developer.android.com/training/data-storage/room): entities, DAOs and migrations.
- [DataStore documentation](https://developer.android.com/topic/libraries/architecture/datastore): typed storage and migrations.
- [WorkManager documentation](https://developer.android.com/topic/libraries/architecture/workmanager): durable scheduled work.
- [ML Kit text recognition on Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): bundled versus downloadable models.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): key protection and device limits.
- [SQLCipher documentation](https://www.zetetic.net/sqlcipher/documentation/): encrypted SQLite.
- [Tink streaming AEAD](https://developers.google.com/tink/streaming-aead): authenticated streaming encryption.
- [Android app signing](https://developer.android.com/studio/publish/app-signing): signing identity and updates.
- [Android testing](https://developer.android.com/training/testing): test strategies and tools.
- [Pro Git book](https://git-scm.com/book/en/v2): commits, branches and collaboration.
- [Semantic Versioning](https://semver.org/): version meaning and communication.

For this application, the source, `RELEASING.md`, `CLEANUP_REPORT.md`, `CLARITY_REPORT.md`, `VALIDATION.md` and dated verification reports are the primary evidence. External documentation explains the tools; the checked-in implementation determines what this app actually does.
