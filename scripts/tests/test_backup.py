import os
import pathlib
import stat
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'backup.sh'


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.output = self.root / 'backup.dump'
        self.dump = self.root / 'pg_dump'
        self.dump.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
        self.dump.chmod(0o700)
        self.environment = {**os.environ, 'PATH': str(self.root) + os.pathsep + os.environ['PATH'],
                            'PGSERVICE': 'atlas-test'}

    def run_backup(self, *arguments):
        return subprocess.run(['bash', str(SCRIPT), '--remote', str(self.output), *arguments],
                              env=self.environment, text=True, capture_output=True)

    def test_remote_backup_forces_verified_tls_and_private_output(self):
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('service=atlas-test sslmode=verify-full', self.output.read_text())
        self.assertEqual(stat.S_IMODE(self.output.stat().st_mode), 0o600)
        self.assertEqual(list(self.root.glob('.atlas-backup.*')), [])

    def test_failed_dump_never_publishes_a_partial_backup(self):
        self.dump.write_text('#!/bin/sh\nprintf "partial fixture"\nexit 1\n')
        result = self.run_backup()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob('.atlas-backup.*')), [])

    def test_existing_backup_is_preserved(self):
        self.output.write_text('original fixture')
        result = self.run_backup()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.output.read_text(), 'original fixture')

    def test_remote_service_cannot_inject_connection_options(self):
        for service in ['', 'atlas sslmode=disable', 'atlas\npassword=secret']:
            self.environment['PGSERVICE'] = service
            result = self.run_backup()
            self.assertEqual(result.returncode, 2)
            self.assertFalse(self.output.exists())
        self.environment['PGSERVICE'] = 'atlas-test'
        self.assertEqual(self.run_backup('-f', 'compose.yml').returncode, 2)


if __name__ == '__main__':
    unittest.main()
