#!/usr/bin/env python3
"""Read an existing Neon connection privately and create a Docker-compatible env file."""
import argparse
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import urllib.parse


class SetupError(Exception):
    """Messages deliberately exclude provider output and secret connection values."""


def run_neon(executable, arguments, config_dir=None):
    command = [executable, *arguments, '--no-analytics', '--no-color']
    if config_dir:
        command += ['--config-dir', config_dir]
    try:
        result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, timeout=60,
                                env={**os.environ, 'DEBUG': ''})
    except (OSError, subprocess.TimeoutExpired):
        raise SetupError('Neon CLI could not complete the request; check installation and authentication.') from None
    if result.returncode:
        raise SetupError('Neon CLI rejected the request; check authentication, project access, and selection.')
    return result.stdout


def connection_fields(connection):
    """Validate only fields supported by the existing strict Spring render profile."""
    try:
        value = connection.strip()
        if any(ord(character) < 32 for character in value):
            raise ValueError()
        uri = urllib.parse.urlsplit(value)
        host = uri.hostname or ''
        query = urllib.parse.parse_qs(uri.query, strict_parsing=True)
        if (uri.scheme not in ('postgres', 'postgresql') or uri.fragment
            or not re.fullmatch(r'[a-z0-9-]+(?:\.[a-z0-9-]+)*\.neon\.tech', host)
            or host.split('.')[0].endswith('-pooler') or uri.port not in (None, 5432)
            or query.get('sslmode') != ['verify-full']):
            raise ValueError()
        database = urllib.parse.unquote(uri.path.removeprefix('/'), errors='strict')
        username = urllib.parse.unquote(uri.username or '', errors='strict')
        password = urllib.parse.unquote(uri.password or '', errors='strict')
        if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}', database):
            raise ValueError()
        # Docker --env-file preserves quotes literally; serialize unquoted single-line values.
        if any(not field or field != field.strip() or any(ord(c) < 32 for c in field)
               for field in (username, password)):
            raise ValueError()
        return {'DB_HOST': host, 'DB_NAME': database, 'DB_USERNAME': username, 'DB_PASSWORD': password}
    except (ValueError, UnicodeError):
        raise SetupError('Neon returned an unsupported direct verified-TLS connection; no values were written.') from None


def frontend_origin(value):
    if value is not None and not re.fullmatch(r'https://[a-z0-9-]+\.pages\.dev', value):
        raise SetupError('Frontend origin must be one exact HTTPS production pages.dev origin.')
    return value


def write_environment(output, fields, origin=None):
    output = pathlib.Path(output)
    if output.exists() or output.is_symlink():
        raise SetupError('Output already exists; preserve it and select another ignored private path.')
    output.parent.mkdir(parents=True, exist_ok=True)
    values = {'SPRING_PROFILES_ACTIVE': 'render', **fields}
    if origin:
        values['CORS_ALLOWED_ORIGINS'] = frontend_origin(origin)
    lines = ['# Private Neon configuration; never commit or copy to the frontend.']
    if not origin:
        lines.append('# Add CORS_ALLOWED_ORIGINS with the allocated HTTPS Pages origin before starting the backend.')
    values.update({name: 'false' for name in ['WORLD_BANK_SCHEDULE_ENABLED',
                  'ADDITIONAL_SOURCES_SCHEDULE_ENABLED', 'POLICY_RATE_SCHEDULE_ENABLED']})
    lines.extend(f'{name}={value}' for name, value in values.items())
    temporary = None
    try:
        descriptor, temporary = tempfile.mkstemp(prefix='.atlas-neon-', dir=output.parent)
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            stream.write('\n'.join(lines) + '\n')
        # A hard link publishes atomically without replacing any existing file, including a race.
        os.link(temporary, output)
    except FileExistsError:
        raise SetupError('Output already exists; no configuration was replaced.') from None
    except OSError:
        raise SetupError('Private configuration could not be written.') from None
    finally:
        if temporary is not None:
            pathlib.Path(temporary).unlink(missing_ok=True)


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project-id', required=True)
    parser.add_argument('--org-id', required=True)
    parser.add_argument('--branch-id')
    parser.add_argument('--database-name')
    parser.add_argument('--role-name')
    parser.add_argument('--frontend-origin')
    parser.add_argument('--neon-cli', default='neon')
    parser.add_argument('--config-dir')
    parser.add_argument('--output', default='infrastructure/free-tier/backend.env')
    options = parser.parse_args(arguments)
    origin = frontend_origin(options.frontend_origin)
    output = pathlib.Path(options.output)
    if output.exists() or output.is_symlink():
        raise SetupError('Output already exists; no connection was fetched or configuration replaced.')
    ignored = subprocess.run(['git', 'check-ignore', '-q', '--', str(output)],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if ignored.returncode:
        raise SetupError('Output must be a Git-ignored private path.')
    raw_project = run_neon(options.neon_cli, ['projects', 'get', options.project_id, '--output', 'json'], options.config_dir)
    try:
        project = json.loads(raw_project)
        if project.get('id') != options.project_id or project.get('org_id') != options.org_id:
            raise ValueError()
    except (ValueError, AttributeError):
        raise SetupError('Project and organization do not match the requested existing Neon project.') from None
    command = ['connection-string']
    if options.branch_id:
        command += [options.branch_id]
    command += ['--project-id', options.project_id, '--ssl', 'verify-full', '--no-pooled', '--endpoint-type', 'read_write']
    for flag, value in [('--database-name', options.database_name), ('--role-name', options.role_name)]:
        if value:
            command += [flag, value]
    fields = connection_fields(run_neon(options.neon_cli, command, options.config_dir))
    write_environment(output, fields, origin)
    print('Private backend configuration created; no credentials were displayed.')
    if not origin:
        print('Production Pages origin is still required before backend startup.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except SetupError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
