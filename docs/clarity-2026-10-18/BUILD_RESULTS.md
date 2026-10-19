# CLARITY final build

Successful release gate:19 October 2026 (Malaysia), source checkpoint `a093f634838ceb17c75391d019a293e7b74f1fa0`.

```sh
source /workspace/.tooling/fuel-ledger-env.sh
python scripts/release.py --bump patch --notes docs/clarity-2026-10-18/PATCH_NOTES.md --variant debug
```

| Sequential stage | Result |
| --- | --- |
| Host tests, debug APK, instrumentation APK | BUILD SUCCESSFUL,1m21s;316 tests across39 suites, no failures/errors/skips |
| Optimized unsigned release | BUILD SUCCESSFUL,2m6s |
| Final debug lint | BUILD SUCCESSFUL,1m5s;0 errors,4 warnings,1 hint |
| Binary release checks | Package/version, original certificate,16KiB alignment and offline permission policy pass |

The4 warnings are the preserved SQLite update suggestion, state-backed offset overload suggestion and2 optional KTX suggestions. The hint recommends primitive state. Gradle deprecation advisories remain. The initial1.5.3 build had an additional Configuration screen-width warning;1.5.4 uses current Compose window metrics and removes that warning. No dependency update was made merely to silence an advisory.

Final artifact: `downloads/FuelExpenseTracker_v1.5.4.apk`, **1.5.4/code11**, **76,275,157 bytes**, signed debug with the original certificate. SHA256: `92ec7a5887651e60cf78dd6601c522fc14ffef574880e248ca5a002b6e50d1ca`.

Actual packaged metadata/privacy findings: [APK_AUDIT.json](APK_AUDIT.json), [signature verification](v1.5.4-signature.txt), [permissions](v1.5.4-permissions.txt).21 bundled OCR assets remain; no INTERNET/network-state/public-storage permissions or DataTransport runtime/CCT/Firebase Analytics/Crashlytics markers are present. Actual manifest has `allowBackup=false` and references the existing backup exclusion rules.

All historical artifacts remain immutable.1.5.2 is the audit release;1.5.3 is the initial CLARITY release;1.5.4 separately records the subsequent window-metrics correction. Failed attempts did not consume versions. Build-policy tests4/4 and Python workflow tests6/6 also pass; [host per-suite results](TEST_RESULTS.md) and [native binary provenance](NATIVE_RESULTS.md) are separate.
