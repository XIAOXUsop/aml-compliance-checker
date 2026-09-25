import io
import tempfile
import unittest
import zipfile
from pathlib import Path

from check_release_zip import check_zip


class ReleaseZipTest(unittest.TestCase):
    def make_zip(self, directory: Path, file_version: str, metadata_version: str) -> Path:
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, "w") as jar:
            jar.writestr(
                "META-INF/plugin.xml",
                f"<idea-plugin><version>{metadata_version}</version>"
                "<idea-version since-build='252'/></idea-plugin>",
            )
        path = directory / f"aml-compliance-checker-{file_version}.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(
                f"aml-compliance-checker/lib/aml-compliance-checker-{file_version}.jar",
                jar_bytes.getvalue(),
            )
        return path

    def test_valid_plugin_zip(self):
        with tempfile.TemporaryDirectory() as temp:
            path = self.make_zip(Path(temp), "0.4.6", "0.4.6")
            self.assertEqual(check_zip(path, "0.4.6", "252"), [])

    def test_plugin_metadata_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            path = self.make_zip(Path(temp), "0.4.6", "0.4.5")
            self.assertTrue(any("plugin.xml version" in problem
                                for problem in check_zip(path, "0.4.6", "252")))

    def test_corrupt_zip_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "aml-compliance-checker-0.4.6.zip"
            path.write_bytes(b"broken")
            self.assertTrue(any("cannot inspect" in problem
                                for problem in check_zip(path, "0.4.6", "252")))


if __name__ == "__main__":
    unittest.main()
