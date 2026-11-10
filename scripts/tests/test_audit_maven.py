import datetime
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('audit_maven', pathlib.Path(__file__).parents[1] / 'audit_maven.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class MavenAuditTest(unittest.TestCase):
    def test_resolved_runtime_coordinates_include_classifiers_and_ignore_test_scope(self):
        result = audit.packages_from_inventory('''
            org.example:core:jar:1.2:compile -- module example
            org.example:native:jar:linux-x86_64:2.0:runtime
            org.example:testing:jar:3.0:test
        ''')
        self.assertEqual(result, [('org.example:core', '1.2'), ('org.example:native', '2.0')])
        with self.assertRaises(ValueError):
            audit.packages_from_inventory('No usable inventory')

    def test_reviews_expire_and_never_cover_other_versions_or_new_advisories(self):
        items = [{'id': 'GHSA-TEST', 'package': 'org.example:core', 'version': '1.2',
                  'status': 'not_affected', 'review_by': '2026-12-10'}]
        today = datetime.date(2026, 11, 10)
        self.assertTrue(audit.reviewed('org.example:core', '1.2', 'GHSA-TEST', items, today))
        self.assertFalse(audit.reviewed('org.example:core', '1.3', 'GHSA-TEST', items, today))
        self.assertFalse(audit.reviewed('org.example:core', '1.2', 'GHSA-NEW', items, today))
        self.assertFalse(audit.reviewed('org.example:core', '1.2', 'GHSA-TEST', items, datetime.date(2026, 12, 11)))

    def test_unrecognized_or_partial_results_fail_the_audit(self):
        packages = [('org.example:core', '1.2')]
        with self.assertRaises(ValueError):
            audit.evaluate(packages, {'results': []}, [], datetime.date(2026, 11, 10))
        for partial in [{'error': {'code': 500}}, {'next_page_token': 'more-results'}, {'vulns': None}]:
            with self.assertRaises(ValueError):
                audit.evaluate(packages, {'results': [partial]}, [], datetime.date(2026, 11, 10))
        self.assertEqual(audit.evaluate(packages, {'results': [{'vulns': [{'id': 'GHSA-NEW'}]}]}, [],
                                        datetime.date(2026, 11, 10)), ['org.example:core@1.2 GHSA-NEW'])


if __name__ == '__main__':
    unittest.main()
