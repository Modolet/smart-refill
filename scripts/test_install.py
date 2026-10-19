"""@file test_install.py
@brief Verify replacement/rollback with disposable filesystem fixtures.
@author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
@date 2026-10-19
"""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import install


class InstallTests(unittest.TestCase):
    def test_install_and_rollback_preserve_files(self):
        with tempfile.TemporaryDirectory(prefix="smartrefill-install-") as folder:
            instance = Path(folder)
            (instance / "mods").mkdir()
            (instance / "config").mkdir()
            old = instance / "mods/stackrefill-neoforge-1.0.0.jar"
            old.write_bytes(b"old-jar-fixture")
            config = instance / "config/stackrefill-common.toml"
            config.write_text("fixture = true\n")
            other = instance / "mods/unrelated.jar"
            other.write_bytes(b"preserve-unrelated")
            with contextlib.redirect_stdout(io.StringIO()):
                install.install(instance, True)
            self.assertFalse(old.exists())
            self.assertEqual(other.read_bytes(), b"preserve-unrelated")
            new = instance / "mods/smartrefill-neoforge-1.21.1-0.1.1.jar"
            self.assertTrue(new.is_file())
            backup = next((instance / "smartrefill-backups").iterdir())
            self.assertEqual((backup / old.name).read_bytes(), b"old-jar-fixture")
            self.assertEqual(
                (backup / "config" / config.name).read_text(), "fixture = true\n"
            )
            with contextlib.redirect_stdout(io.StringIO()):
                install.rollback(backup)
            self.assertEqual(old.read_bytes(), b"old-jar-fixture")
            self.assertFalse(new.exists())
            self.assertEqual(other.read_bytes(), b"preserve-unrelated")

    def test_dry_run_has_no_writes(self):
        with tempfile.TemporaryDirectory(prefix="smartrefill-dry-") as folder:
            instance = Path(folder)
            (instance / "mods").mkdir()
            old = instance / "mods/stackrefill-neoforge-1.0.0.jar"
            old.write_bytes(b"old")
            with contextlib.redirect_stdout(io.StringIO()):
                install.install(instance, False)
            self.assertTrue(old.exists())
            self.assertFalse((instance / "smartrefill-backups").exists())

    def test_rollback_rejects_changed_installed_jar(self):
        with tempfile.TemporaryDirectory(prefix="smartrefill-reject-") as folder:
            instance = Path(folder)
            (instance / "mods").mkdir()
            (instance / "config").mkdir()
            with contextlib.redirect_stdout(io.StringIO()):
                install.install(instance, True)
            backup = next((instance / "smartrefill-backups").iterdir())
            (instance / "mods/smartrefill-neoforge-1.21.1-0.1.1.jar").write_bytes(
                b"changed"
            )
            with self.assertRaises(RuntimeError):
                install.rollback(backup)


if __name__ == "__main__":
    unittest.main()
