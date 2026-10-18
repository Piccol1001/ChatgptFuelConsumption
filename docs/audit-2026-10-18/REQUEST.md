# Full Android Project Audit, Cleanup and Code Optimization

Act as a senior Android software architect, Kotlin developer, code reviewer and QA engineer.

Perform a COMPLETE review of my existing Android Fuel & Expense Tracker project.

The project has undergone many development iterations, UI redesigns, bug fixes and feature additions. I want the codebase to be clean, organized, maintainable and free of unnecessary files.

**Important: Review the entire repository, not just the most recently modified files.**

## 1. Complete Project Inspection

Inspect all relevant project files, including:

- Kotlin source files.
- Jetpack Compose UI components.
- ViewModels.
- Repositories.
- Room database entities, DAOs and migrations.
- Navigation components.
- Calculation utilities.
- Gradle configuration.
- AndroidManifest.xml.
- Resources, icons and images.
- Assets and fonts.
- Automated tests.
- Documentation.
- Build scripts.
- Release configurations.

Identify unused, duplicated, obsolete or unrelated content.

## 2. Remove Unnecessary Files

Identify and remove genuinely unnecessary content, including:

- Unused Kotlin files and classes.
- Abandoned UI components.
- Obsolete screens from earlier designs.
- Duplicate functions and utilities.
- Unused drawable resources.
- Unreferenced icons and images.
- Temporary development files.
- Accidental backup copies.
- Obsolete generated files.
- Unused dependencies.
- Outdated sample data embedded in production code.
- Unnecessary debugging code.
- Unrelated files not required by the project.

Do not remove files merely because they are not directly referenced by another Kotlin file. Check manifest entries, resource references, reflection, build configuration, tests and runtime usage.

## 3. Protect Important Project Files

DO NOT delete:

- Active source code.
- Database migration history.
- Existing user data.
- Signing keys or configurations required for updates.
- Necessary Gradle configuration.
- Required Android resources.
- Current tests.
- Encrypted backup functionality.
- Necessary licenses and documentation.
- Historical APK releases intentionally retained.
- Files required by dynamically loaded components.

Do not expose signing keys, passwords or sensitive information in logs or reports.

Before deleting anything:
1. Create a Git checkpoint if available.
2. Identify files that are safe to remove.
3. Distinguish definite junk from uncertain files.
4. Delete only files verified to be unnecessary.
5. Leave uncertain files unchanged and report them.

## 4. Review Code Quality

Inspect the entire codebase for:

- Duplicate logic.
- Unused imports.
- Unused variables.
- Unreachable code.
- Overly complex functions.
- Repeated Compose components.
- Inconsistent naming.
- Unnecessary state management.
- Potential memory leaks.
- Unnecessary recompositions.
- Inefficient database queries.
- Excessive object allocations.
- Deprecated APIs.
- Redundant dependencies.

Refactor where appropriate.

Avoid unnecessary architectural rewrites.

Keep the existing application behaviour unchanged unless fixing a verified defect.

## 5. Check Existing Features

Ensure the cleanup preserves all current features:

- Home dashboard.
- Expenses and income.
- Spending Calendar.
- Fuel Tracker.
- Journey Planner.
- Analysis.
- Monthly Money Planner.
- Fixed recurring expenses.
- Receipt Scanner.
- Home-screen widgets.
- Light, Dark and System themes.
- Horizontal swipe navigation.
- App lock and privacy features.
- Encrypted local backup.
- Financial report export.

Inspect existing functionality rather than assuming every listed feature is already implemented.

## 6. Optimize Jetpack Compose

Review:
- Shared UI components.
- Theme consistency.
- Animation performance.
- Recomposition behaviour.
- Modifier chains.
- State management.
- Navigation lifecycle.
- Gesture handling.
- HorizontalPager integration.

Remove duplicated visual components where reusable shared components are appropriate.

Ensure all five main screens maintain consistent sizing and spacing.

Do not reintroduce the previous Journey screen scaling problem or the Settings switch blinking issue.

## 7. Database and Calculation Safety

Review financial and fuel calculations carefully.

Ensure:
- Expenses are not double-counted.
- Fuel expenses remain correctly linked.
- Recurring transactions are not duplicated.
- Budget calculations remain accurate.
- Historical fuel estimates use the intended calculation method.
- Database migrations remain compatible.
- Existing records are never silently removed.

Do not delete database entities or fields simply because they are no longer visible in the UI.

Preserve historical data compatibility.

## 8. Offline Security Audit

Confirm that the application remains completely offline.

Verify:
- No INTERNET permission in the merged manifest.
- No unwanted network SDKs.
- No Firebase or cloud synchronization.
- No unnecessary analytics or telemetry.
- No sensitive data exposed in logs.
- No accidental cloud backups.
- Receipt recognition uses bundled on-device processing.

Remove unused dependencies that could introduce unnecessary security or privacy risks.

## 9. Gradle and Build Cleanup

Review:
- Unused Gradle plugins.
- Unnecessary dependencies.
- Duplicated dependency declarations.
- Outdated configuration.
- Unused build scripts.
- Incorrect version references.

Ensure the project builds reproducibly.

Update .gitignore where necessary so disposable build outputs and local machine-specific files are not committed.

Do not delete intentionally retained APK releases or signing materials.

## 10. Testing After Cleanup

Run available:
- Kotlin compilation.
- Android Gradle build.
- Unit tests.
- Android Lint.
- Relevant UI tests.

Verify:
- Main navigation works.
- Financial calculations remain accurate.
- Fuel calculations remain functional.
- Room migrations remain safe.
- The receipt scanner remains functional.
- Appearance settings persist.
- Screenshots remain enabled.
- The application still works offline.

Fix errors introduced by cleanup.

## 11. Project Organization

Organize the codebase using the existing architecture.

Prefer clear feature boundaries and shared components.

For example:
- home
- expenses
- fuel
- journey
- analysis
- settings
- data
- domain
- core
- ui/theme

Do not move everything into new packages merely for appearance.

Refactor only when it materially improves maintainability.

## 12. Final Audit Report

After cleanup, provide:

1. Files reviewed.
2. Files deleted and reasons.
3. Duplicate code removed.
4. Unused dependencies removed.
5. Code optimizations completed.
6. Project structure improvements.
7. Potential issues discovered.
8. Issues fixed.
9. Uncertain files intentionally retained.
10. Build and test results.
11. Confirmation that existing app data and functionality were preserved.

Create a CLEANUP_REPORT.md documenting these findings.

## 13. Version Management

If the cleanup results in a verified release-worthy change:
- Update the app version appropriately.
- Increment versionCode.
- Update CHANGELOG.md.
- Preserve applicationId and signing compatibility.
- Build an updated APK if the environment supports it.

Do not change the version solely because an unsuccessful build was attempted.

## Final Objective

Deliver a clean, organized, efficient and maintainable Android project without unnecessary files or obsolete code.

Prioritize:
1. Data preservation.
2. Functional correctness.
3. Safe cleanup.
4. Code maintainability.
5. Performance.
6. Build stability.

Do not add unrelated new features.

**Begin by auditing the complete repository, then safely perform the cleanup and verify the results.**