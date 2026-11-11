import contextlib
import importlib.util
import io
import pathlib
import stat
import subprocess
import tempfile
import unittest
from unittest import mock


spec = importlib.util.spec_from_file_location('configure_neon', pathlib.Path(__file__).parents[1] / 'configure_neon.py')
configure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(configure)
CONNECTION = 'postgresql://atlas:pass%40word%3A%3D%23@ep-test.us-east-2.aws.neon.tech/neondb?sslmode=verify-full&channel_binding=require'


class ConfigureNeonTest(unittest.TestCase):
    def test_direct_connection_decodes_credentials_without_treating_special_characters_as_structure(self):
        values = configure.connection_fields(CONNECTION + '\n')
        self.assertEqual(values['DB_HOST'], 'ep-test.us-east-2.aws.neon.tech')
        self.assertEqual(values['DB_PASSWORD'], 'pass@word:=#')
        self.assertEqual(values['DB_NAME'], 'neondb')

    def test_invalid_tls_pooling_host_database_or_multiline_values_never_echo_secrets(self):
        for connection in [CONNECTION.replace('verify-full', 'require'),
                           CONNECTION.replace('ep-test.', 'ep-test-pooler.'),
                           CONNECTION.replace('.neon.tech', '.neon.tech.evil.example'),
                           CONNECTION.replace('/neondb?', '/db%3Fsslmode%3Ddisable?'),
                           CONNECTION.replace('pass%40word%3A%3D%23', 'secret%0AINJECTED%3Dyes'),
                           'postgresql://atlas:secret@localhost/neondb?sslmode=verify-full']:
            with self.assertRaises(configure.SetupError) as failure:
                configure.connection_fields(connection)
            self.assertNotIn('secret', str(failure.exception))
            self.assertNotIn('postgresql://', str(failure.exception))

    def test_private_atomic_output_does_not_overwrite_and_omits_unassigned_origin(self):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / 'backend.env'
            configure.write_environment(output, configure.connection_fields(CONNECTION))
            original = output.read_text()
            self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
            self.assertIn('DB_PASSWORD=pass@word:=#\n', original)
            self.assertNotIn('\nCORS_ALLOWED_ORIGINS=', original)
            self.assertNotIn('DATABASE_URL=', original)
            self.assertEqual(list(output.parent.glob('.atlas-neon-*')), [])
            with self.assertRaises(configure.SetupError):
                configure.write_environment(output, {'DB_PASSWORD': 'replacement'})
            self.assertEqual(output.read_text(), original)

    def test_cli_errors_are_sanitized_and_disable_debug_output(self):
        with mock.patch.object(configure.subprocess, 'run', return_value=subprocess.CompletedProcess(
                [], 1, stdout=CONNECTION, stderr='secret')) as run:
            with self.assertRaises(configure.SetupError) as failure:
                configure.run_neon('neon', ['connection-string'], '/private/config')
            self.assertNotIn('secret', str(failure.exception))
            self.assertNotIn('postgresql://', str(failure.exception))
            self.assertEqual(run.call_args.kwargs['env']['DEBUG'], '')
            self.assertEqual(run.call_args.kwargs['stdin'], subprocess.DEVNULL)

    def test_project_ownership_is_checked_before_retrieving_credentials(self):
        with mock.patch.object(configure.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)), \
             mock.patch.object(configure, 'run_neon', return_value='{"id":"requested","org_id":"other"}') as cli:
            with self.assertRaises(configure.SetupError):
                configure.main(['--project-id', 'requested', '--org-id', 'expected', '--output', '/unused/nonexistent.env'])
            self.assertEqual(cli.call_count, 1)

    def test_complete_flow_fetches_only_requested_project_and_never_prints_secrets(self):
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(configure.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)), \
             mock.patch.object(configure, 'run_neon', side_effect=['{"id":"project","org_id":"organization"}', CONNECTION]) as cli:
            output = pathlib.Path(directory) / 'backend.env'
            capture = io.StringIO()
            with contextlib.redirect_stdout(capture):
                self.assertEqual(configure.main(['--project-id', 'project', '--org-id', 'organization',
                    '--frontend-origin', 'https://atlas.pages.dev', '--output', str(output)]), 0)
            self.assertIn('CORS_ALLOWED_ORIGINS=https://atlas.pages.dev\n', output.read_text())
            self.assertIn('--no-pooled', cli.call_args.args[1])
            self.assertIn('verify-full', cli.call_args.args[1])
            self.assertNotIn('pass', capture.getvalue())
            self.assertNotIn('postgresql://', capture.getvalue())

    def test_production_origin_excludes_previews_paths_and_wildcards(self):
        for origin in ['http://atlas.pages.dev', 'https://*.pages.dev', 'https://preview.atlas.pages.dev',
                       'https://atlas.pages.dev/', 'https://atlas.pages.dev/path']:
            with self.assertRaises(configure.SetupError):
                configure.frontend_origin(origin)


if __name__ == '__main__':
    unittest.main()
