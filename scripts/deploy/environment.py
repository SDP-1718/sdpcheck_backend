"""Read/update only the dedicated production environment; never print its values."""
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
from urllib.parse import urlsplit

DIRECTORY = Path('/home/ubuntu/sdpcheck-config')
ENVIRONMENT = DIRECTORY / '.env.prod'
CORE = ('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'JWT_SECRET',
        'JWT_ACCESS_TOKEN_EXPIRATION', 'JWT_REFRESH_TOKEN_EXPIRATION', 'KAKAO_CLIENT_ID')
EDITABLE = {'KAKAO_REDIRECT_URI', 'FRONTEND_AUTH_CALLBACK_URL', 'JWT_REFRESH_COOKIE_SAME_SITE',
            'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET', 'OPENAI_ENABLED', 'OPENAI_MODEL', 'OPENAI_API_KEY',
            'CORS_ALLOWED_ORIGINS'}


def load():
    if DIRECTORY.is_symlink() or ENVIRONMENT.is_symlink():
        raise ValueError('Symlink configuration refused')
    metadata = ENVIRONMENT.stat()
    if metadata.st_uid != os.getuid() or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise ValueError('Environment owner or permissions need checking')
    values = {}
    for line in ENVIRONMENT.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        values[key.strip()] = json.loads(value) if value.startswith('"') else value
        values[key.strip()] = values[key.strip()].replace('$$', '$')
    return values


def https_url(value):
    parsed = urlsplit(value)
    return bool(parsed.scheme == 'https' and parsed.hostname and not parsed.username
                and not parsed.password and not parsed.fragment)


def cors_origins(value):
    origins = []
    for item in value.split(','):
        item = item.strip()
        if not item:
            continue
        parsed = urlsplit(item)
        if (parsed.scheme not in ('http', 'https') or not parsed.hostname
                or not re.fullmatch(r'[A-Za-z0-9.:-]+', parsed.hostname)
                or any(c in item for c in '*\\ \t') or parsed.username or parsed.password
                or parsed.path not in ('', '/') or parsed.query or parsed.fragment
                or (parsed.port is not None and not 0 <= parsed.port <= 65535)):
            raise ValueError('CORS requires exact HTTP(S) origins without paths or wildcards')
        origin = item.rstrip('/')
        if origin not in origins:
            origins.append(origin)
    return origins


def status(values):
    core = all(values.get(key, '').strip() for key in CORE)
    core = core and len(values.get('JWT_SECRET', '').encode('utf-8')) >= 32
    for key in ('JWT_ACCESS_TOKEN_EXPIRATION', 'JWT_REFRESH_TOKEN_EXPIRATION'):
        core = core and values.get(key, '').isdigit() and int(values[key]) > 0
    enabled_value = values.get('OPENAI_ENABLED', 'false')
    enabled = enabled_value == 'true'
    credentials = bool(values.get('OPENAI_API_KEY', '').strip() and values.get('OPENAI_MODEL', '').strip())
    core = core and enabled_value in ('true', 'false') and (not enabled or credentials)
    backend = values.get('KAKAO_REDIRECT_URI', '')
    frontend = values.get('FRONTEND_AUTH_CALLBACK_URL', '')
    origins = cors_origins(values.get('CORS_ALLOWED_ORIGINS', ''))
    return {'core_ready': bool(core), 'login_urls_ready': bool(https_url(backend)
            and urlsplit(backend).path == '/login/oauth2/code/kakao' and https_url(frontend)),
            'openai_enabled': enabled, 'openai_credentials_present': credentials,
            'cors_configured': bool(origins)}


def main():
    values = load()
    action = sys.argv[1]
    if action == 'update':
        changes = json.load(sys.stdin)
        if not isinstance(changes, dict) or changes.keys() - EDITABLE:
            raise ValueError('Unsupported settings')
        for key, value in changes.items():
            if not isinstance(value, str) or any(c in value for c in '\r\n\x00'):
                raise ValueError('Only single-line values are supported')
            if key in ('KAKAO_REDIRECT_URI', 'FRONTEND_AUTH_CALLBACK_URL') and value and not https_url(value):
                raise ValueError('Production callback URLs must use HTTPS')
            if key == 'KAKAO_REDIRECT_URI' and value and urlsplit(value).path != '/login/oauth2/code/kakao':
                raise ValueError('Backend callback path mismatch')
            if key == 'JWT_REFRESH_COOKIE_SAME_SITE' and value not in ('Lax', 'None', 'Strict'):
                raise ValueError('Invalid SameSite value')
            if key == 'OPENAI_ENABLED' and value not in ('true', 'false'):
                raise ValueError('Invalid OpenAI enabled flag')
            if key == 'CORS_ALLOWED_ORIGINS':
                value = ','.join(cors_origins(value))
            values[key] = value
        if not status(values)['core_ready']:
            raise ValueError('Required settings incomplete')
        temp = DIRECTORY / ('.env.prod.' + os.urandom(8).hex() + '.tmp')
        try:
            fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as stream:
                stream.write('# Managed production settings. Never commit or print this file.\n')
                for key, value in values.items():
                    if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
                        raise ValueError('Invalid setting name')
                    stream.write(key + '=' + json.dumps(value.replace('$', '$$'), ensure_ascii=True) + '\n')
            os.replace(temp, ENVIRONMENT)
        finally:
            if temp.exists():
                temp.unlink()
    elif action == 'compose':
        result = subprocess.run(['sudo', '-n', 'env', 'DEPLOY_COMMIT=' + os.environ.get('DEPLOY_COMMIT', ''),
                                 'docker', 'compose', '--env-file', str(ENVIRONMENT),
                                 '-p', 'sdpcheck', '-f', sys.argv[2], '-f', sys.argv[3],
                                 'config', '--format', 'json'], capture_output=True, text=True)
        if result.returncode:
            raise ValueError('Compose configuration invalid')
        services = json.loads(result.stdout)['services']
        app = services['app']['environment']
        db = services['postgres']['environment']
        # Compose serializes literal dollars as $$ in config output, then sends $ to containers.
        def resolved(mapping, key):
            value = mapping.get(key)
            return value.replace('$$', '$') if isinstance(value, str) else value
        forwarded = ('JWT_SECRET', 'JWT_ACCESS_TOKEN_EXPIRATION', 'JWT_REFRESH_TOKEN_EXPIRATION',
                     'JWT_REFRESH_COOKIE_SAME_SITE', 'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET',
                     'KAKAO_REDIRECT_URI', 'FRONTEND_AUTH_CALLBACK_URL', 'CORS_ALLOWED_ORIGINS', 'OPENAI_ENABLED',
                     'OPENAI_API_KEY', 'OPENAI_MODEL')
        if any(resolved(app, key) != values.get(key, '') for key in forwarded):
            raise ValueError('App environment forwarding mismatch')
        if any(resolved(db, key) != values[key] for key in ('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')):
            raise ValueError('DB environment forwarding mismatch')
        if (resolved(app, 'DB_USERNAME') != values['POSTGRES_USER'] or resolved(app, 'DB_PASSWORD') != values['POSTGRES_PASSWORD']
                or app.get('DB_URL') != 'jdbc:postgresql://postgres:5432/' + values['POSTGRES_DB']):
            raise ValueError('App DB connection mismatch')
        if services['postgres']['volumes'][0]['source'] != 'postgres_prod_data':
            raise ValueError('Unexpected production DB volume')
        print(json.dumps({'compose_valid': True, 'environment_forwarding_valid': True}))
        return 0
    elif action != 'status':
        raise ValueError('Unsupported command')
    report = status(values)
    print(json.dumps(report))
    require_urls = len(sys.argv) > 2 and sys.argv[2] == '1'
    return 0 if report['core_ready'] and (not require_urls or report['login_urls_ready']) else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        print('Environment operation failed; check permissions and required formats. No values printed.', file=sys.stderr)
        sys.exit(1)
