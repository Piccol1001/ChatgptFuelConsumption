import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "releases").mkdir()
        (self.root / "downloads").mkdir()
        (self.root / "version.properties").write_text("versionName=1.4.0\nversionCode=6\n")
        self.history = [dict(path="downloads/old.apk", versionName="1.4.0", versionCode=6,
                             certificateSha256="original", sha256="old", firstRecorded="2026-10-16", releaseDate=None)]
        (self.root / "releases/history.json").write_text(json.dumps(self.history))
        (self.root / "CHANGELOG.md").write_text("# Changelog\n\nOld information is retained.\n")
        self.before = {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        self.notes = "### New features\nCalendar.\n### Bug fixes\nSwitch.\n### UI improvements\nDashboard."
        self.payload = self.root / "app/build/outputs/apk/debug/app-debug.apk"
        self.payload.parent.mkdir(parents=True)
        self.payload.write_bytes(b"fake apk used only with a mocked binary inspector")

    def unchanged(self):
        for p, original in self.before.items():
            self.assertEqual(original, (self.root / p).read_bytes(), p)
        self.assertFalse((self.root / "downloads/FuelExpenseTracker_v1.5.0.apk").exists())

    def inspect(self, path):
        self.assertEqual(self.payload, path)
        return dict(applicationId=r.APP_ID, versionName="1.5.0", versionCode=7, certificateSha256="original")

    def release(self, build=lambda props, variant: None, inspect=None):
        with contextlib.redirect_stdout(io.StringIO()):
            return r.perform_release(self.root, "minor", self.notes, "2026-10-17", "debug", build, inspect or self.inspect)

    def test_semantic_bumps_and_strict_monotonic_codes_include_unlisted_artifacts(self):
        self.assertEqual("2.0.0", r.bump_version("1.4.9", "major"))
        self.assertEqual("1.5.0", r.bump_version("1.4.9", "minor"))
        self.assertEqual("1.4.10", r.bump_version("1.4.9", "patch"))
        self.assertEqual(("2.1.1", 100), r.next_version(("1.4.0", 6), self.history,
                         [dict(versionName="2.1.0", versionCode=99)], "patch"))
        for invalid in ["1.4", "01.4.0", "1.04.0", "1.4.00", "-1.0.0", "1.4.0-debug"]:
            with self.assertRaises(ValueError): r.semver(invalid)

    def test_failed_build_does_not_increment_or_publish(self):
        def failing(*args): raise subprocess.CalledProcessError(1, "gradle")
        with self.assertRaises(subprocess.CalledProcessError): self.release(build=failing)
        self.unchanged()

    def test_wrong_certificate_or_binary_version_cannot_publish(self):
        for key, value in [("certificateSha256", "different"), ("versionCode", 6), ("versionName", "1.4.0"), ("applicationId", "other.package"), ("debuggable", False)]:
            def invalid(path):
                meta = self.inspect(path); meta[key] = value; return meta
            with self.assertRaises(ValueError): self.release(inspect=invalid)
            self.unchanged()

    def test_success_records_verified_metadata_and_preserves_history(self):
        calls = []
        record = self.release(build=lambda props, variant: calls.append((props, variant)))
        self.assertEqual((["-PledgerVersionName=1.5.0", "-PledgerVersionCode=7"], "debug"), calls[0])
        self.assertEqual(("1.5.0", 7), r.read_version(self.root))
        saved = json.loads((self.root / "releases/history.json").read_text())
        self.assertEqual(self.history, saved[:-1])
        self.assertEqual("2026-10-17", saved[-1]["releaseDate"])
        self.assertEqual(record["sha256"], __import__('hashlib').sha256(self.payload.read_bytes()).hexdigest())
        self.assertIn("Old information is retained.", (self.root / "CHANGELOG.md").read_text())
        self.assertIn("original", (self.root / "RELEASES.md").read_text())

    def test_missing_or_different_external_key_is_rejected_before_build(self):
        r.verify_debug_signing("Variant: debug\nConfig: debug\nSHA-256: AA:BB:CC\nVariant: release\n", "aabbcc")
        for report in ["Variant: debug\nError: Missing keystore\n", "Variant: debug\nSHA-256: DD:EE:FF\n"]:
            with self.assertRaises(ValueError): r.verify_debug_signing(report, "aabbcc")
        self.unchanged()

    def test_existing_filename_is_never_overwritten(self):
        destination = self.root / "downloads/FuelExpenseTracker_v1.5.0.apk"
        destination.write_bytes(b"original historical APK")
        with self.assertRaises(ValueError): self.release()
        self.assertEqual(b"original historical APK", destination.read_bytes())
        self.assertEqual(("1.4.0", 6), r.read_version(self.root))


if __name__ == "__main__": unittest.main()
