#!/usr/bin/env python3
"""Check resolved runtime coordinates against OSV; reviewed exceptions are exact and expire."""
import datetime
import json
import pathlib
import sys
import urllib.error
import urllib.request


def packages_from_inventory(text):
    packages = set()
    for line in text.splitlines():
        parts = line.strip().split(' -- ')[0].split(':')
        if len(parts) >= 5 and parts[-1] in ('compile', 'runtime'):
            packages.add((parts[0] + ':' + parts[1], parts[-2]))
    if not packages:
        raise ValueError('No resolved runtime Maven packages were found')
    return sorted(packages)


def reviewed(package, version, advisory, assessments, today):
    return any(item['id'] == advisory and item['package'] == package and item['version'] == version
               and item['status'] == 'not_affected' and datetime.date.fromisoformat(item['review_by']) >= today
               for item in assessments)


def evaluate(packages, response, assessments, today):
    results = response.get('results')
    if not isinstance(results, list) or len(results) != len(packages):
        raise ValueError('OSV returned an incomplete audit response')
    failures = []
    for (package, version), result in zip(packages, results):
        for advisory in result.get('vulns', []):
            identifier = advisory['id']
            if reviewed(package, version, identifier, assessments, today):
                print(f'REVIEWED not affected: {package}@{version} {identifier}')
            else:
                failures.append(f'{package}@{version} {identifier}')
    return failures


def main():
    if len(sys.argv) != 2:
        raise ValueError('Usage: python3 scripts/audit_maven.py MAVEN_INVENTORY.txt')
    packages = packages_from_inventory(pathlib.Path(sys.argv[1]).read_text())
    config = pathlib.Path(__file__).resolve().parents[1] / '.github/advisory-assessments.json'
    assessments = json.loads(config.read_text())['assessments']
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch', method='POST',
        data=json.dumps({'queries': [{'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
                                   for name, version in packages]}).encode(),
        headers={'Content-Type': 'application/json', 'User-Agent': 'AtlasGlobal-development'})
    with urllib.request.urlopen(request, timeout=45) as response:
        body = response.read(5_000_001)
    if len(body) > 5_000_000:
        raise ValueError('OSV response exceeds the audit limit')
    failures = evaluate(packages, json.loads(body), assessments, datetime.datetime.now(datetime.timezone.utc).date())
    print(f'Checked {len(packages)} resolved runtime Maven packages; unreviewed advisories: {len(failures)}')
    for failure in failures:
        print('FAIL ' + failure)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError, urllib.error.URLError) as error:
        print(f'Audit failed: {error}', file=sys.stderr)
        sys.exit(1)
