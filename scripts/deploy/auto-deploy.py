#!/usr/bin/env python3
"""Pull tested public releases; keep credentials and all DB data on EC2."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time
from urllib.request import Request, urlopen

REPO = 'SDP-1718/sdpcheck_backend'
API = 'https://api.github.com/repos/' + REPO
SHA = re.compile(r'^[0-9a-f]{40}$')
RUNTIME = {'compose.prod.yaml', 'compose.prebuilt.yaml', 'environment.py', 'auto-deploy.py'}
LIMITS = {'image.tar.gz': 600 * 1024**2, 'runtime.tar.gz': 2 * 1024**2, 'checksums.sha256': 4096}


def log(message):
    print(message, flush=True)


def request(url):
    return urlopen(Request(url, headers={'User-Agent': 'sdpcheck-production-deploy',
                                        'Accept': 'application/vnd.github+json'}), timeout=30)


def api(path):
    with request(API + path) as response:
        return json.load(response)


def candidate(releases):
    valid = [r for r in releases if not r.get('draft') and
             re.fullmatch(r'production-[0-9a-f]{40}', r.get('tag_name', ''))]
    return max(valid, key=lambda r: r.get('published_at') or '', default=None)


def checksum_manifest(data):
    result = {}
    for line in data.decode('ascii').splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (image\.tar\.gz|runtime\.tar\.gz)', line)
        if not match or match[2] in result:
            raise RuntimeError('Invalid deployment checksum manifest')
        result[match[2]] = match[1]
    if set(result) != {'image.tar.gz', 'runtime.tar.gz'}:
        raise RuntimeError('Incomplete deployment checksum manifest')
    return result


def download(asset, destination, limit):
    expected_url = f'https://github.com/{REPO}/releases/download/'
    if not asset.get('browser_download_url', '').startswith(expected_url):
        raise RuntimeError('Unexpected release asset URL')
    digest = asset.get('digest', '')
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
        raise RuntimeError('Missing GitHub asset digest')
    size = asset.get('size', 0)
    if not 0 < size <= limit or destination.is_symlink():
        raise RuntimeError('Unsafe deployment asset size or path')
    temporary = destination.with_suffix('.part')
    if temporary.is_symlink():
        raise RuntimeError('Unsafe partial download path')
    sha = hashlib.sha256()
    count = 0
    try:
        with request(asset['browser_download_url']) as source, temporary.open('wb') as target:
            while chunk := source.read(1024 * 1024):
                count += len(chunk)
                if count > size:
                    raise RuntimeError('Deployment asset exceeded advertised size')
                sha.update(chunk)
                target.write(chunk)
        if count != size or 'sha256:' + sha.hexdigest() != digest:
            raise RuntimeError('Deployment asset checksum mismatch')
        temporary.replace(destination)
        return sha.hexdigest()
    finally:
        temporary.unlink(missing_ok=True)


def unpack_runtime(archive, destination):
    with tarfile.open(archive, 'r:gz') as bundle:
        entries = bundle.getmembers()
        if len(entries) != len(RUNTIME) or {m.name for m in entries} != RUNTIME:
            raise RuntimeError('Unexpected runtime bundle contents')
        if any(not m.isfile() or m.size > 1024**2 for m in entries):
            raise RuntimeError('Unsafe runtime bundle entry')
        for member in entries:
            target = destination / member.name
            if target.is_symlink():
                raise RuntimeError('Unsafe runtime destination')
            with bundle.extractfile(member) as source:
                target.write_bytes(source.read())


class Deployment:
    def __init__(self, root=Path('/home/ubuntu/sdpcheck'), config=Path('/home/ubuntu/sdpcheck-config')):
        self.root = root
        self.config = config
        self.env_file = config / '.env.prod'

    def run(self, command, **kwargs):
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
        if result.returncode:
            # Never echo subprocess output: it can contain credentials or request data.
            raise RuntimeError(f'{command[0]} failed (exit {result.returncode}); inspect server locally')
        return result.stdout

    def docker(self, *args, **kwargs):
        return self.run(['sudo', '-n', 'docker', *args], **kwargs)

    def compose(self, commit, *args):
        release = self.root / 'releases' / commit
        env = dict(os.environ, DEPLOY_COMMIT=commit)
        return self.run(['sudo', '-n', 'env', 'DEPLOY_COMMIT=' + commit,
                         'docker', 'compose', '--env-file', str(self.env_file), '-p', 'sdpcheck',
                         '-f', str(release / 'compose.prod.yaml'),
                         '-f', str(release / 'compose.prebuilt.yaml'), *args], env=env)

    def current(self):
        value = (self.root / 'current-release').read_text().strip()
        if not SHA.fullmatch(value):
            raise RuntimeError('Invalid current release marker')
        return value

    def healthy(self, commit):
        for _ in range(60):
            try:
                image = self.docker('inspect', '--format={{.Config.Image}}', 'sdpcheck-app').decode().strip()
                if image != 'sdpcheck-backend:' + commit:
                    raise RuntimeError('Unexpected application image')
                with urlopen('http://127.0.0.1:8080/actuator/health', timeout=5) as response:
                    if json.load(response).get('status') == 'UP':
                        for path in ('/v3/api-docs', '/swagger-ui/index.html'):
                            with urlopen('http://127.0.0.1:8080' + path, timeout=10) as check:
                                if check.status != 200:
                                    raise RuntimeError('API verification failed')
                        return True
            except Exception:
                pass
            time.sleep(3)
        return False

    def backup(self, commit):
        directory = self.config / 'backups'
        if directory.is_symlink():
            raise RuntimeError('Unsafe backup directory')
        directory.mkdir(mode=0o700, exist_ok=True)
        backup = directory / ('before-auto-' + commit + '-' + time.strftime('%Y%m%dT%H%M%SZ', time.gmtime()) + '.dump')
        # Exclusive creation, private permissions, no DB password in arguments/output.
        with backup.open('xb') as target:
            result = subprocess.run(['sudo', '-n', 'docker', 'exec', 'sdpcheck-postgres', 'sh', '-c',
                                     'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'],
                                    stdout=target, stderr=subprocess.PIPE)
        if result.returncode or backup.stat().st_size == 0:
            raise RuntimeError('Database backup failed; app was not changed')
        with backup.open('rb') as source:
            self.docker('exec', '-i', 'sdpcheck-postgres', 'pg_restore', '--file=/dev/null', stdin=source)
        log('Database backup verified: ' + backup.name)

    def deploy(self, commit):
        previous = self.current()
        release = self.root / 'releases' / commit
        self.run(['python3', str(release / 'environment.py'), 'status', '1'])
        self.compose(commit, 'config', '--quiet')
        self.run(['python3', str(release / 'environment.py'), 'compose',
                  str(release / 'compose.prod.yaml'), str(release / 'compose.prebuilt.yaml')])
        state = self.docker('inspect', '--format={{.State.Health.Status}}', 'sdpcheck-postgres').strip()
        if state != b'healthy':
            raise RuntimeError('PostgreSQL is not healthy')
        self.backup(commit)
        self.docker('load', '--input', str(release / 'image.tar.gz'))
        (release / 'image.tar.gz').unlink()
        label = self.docker('image', 'inspect', '--format={{index .Config.Labels "org.opencontainers.image.revision"}}',
                            'sdpcheck-backend:' + commit).decode().strip()
        if label != commit:
            raise RuntimeError('Image revision does not match release')
        try:
            self.compose(commit, 'up', '-d', '--no-build', '--no-deps', 'app')
            if not self.healthy(commit):
                raise RuntimeError('New application health check failed')
        except Exception:
            (self.root / 'failed-auto-release').write_text(commit + '\n')
            log('Deployment failed; restoring previous application image (database is not restored).')
            self.compose(previous, 'up', '-d', '--no-build', '--no-deps', 'app')
            if not self.healthy(previous):
                raise RuntimeError('Rollback health check failed; manual intervention required') from None
            raise RuntimeError('Deployment failed; previous application is healthy') from None
        marker = self.root / 'current-release.tmp'
        marker.write_text(commit + '\n')
        marker.replace(self.root / 'current-release')
        agent = self.config / 'tools' / 'auto-deploy.py'
        temporary = agent.with_suffix('.tmp')
        temporary.write_bytes((release / 'auto-deploy.py').read_bytes())
        temporary.chmod(0o700)
        temporary.replace(agent)
        log('DEPLOYED ' + commit + '; application health and Swagger checks passed')
        self.cleanup({commit, previous})

    def cleanup(self, keep):
        # Keep the active and immediate previous image for rollback. Never touch DB volumes/backups.
        for directory in (self.root / 'releases').iterdir():
            if SHA.fullmatch(directory.name) and directory.name not in keep and not directory.is_symlink():
                try:
                    self.docker('image', 'rm', 'sdpcheck-backend:' + directory.name)
                except RuntimeError:
                    continue  # Never force-remove an image used by a container.
                shutil.rmtree(directory)

    def poll(self, dry_run=False, retry=False):
        release_info = candidate(api('/releases?per_page=10'))
        if not release_info:
            log('No production release published yet')
            return
        commit = release_info['tag_name'].removeprefix('production-')
        if commit == self.current():
            return
        failed = self.root / 'failed-auto-release'
        if failed.exists() and failed.read_text().strip() == commit and not retry:
            log('Failed release remains blocked; inspect logs before using --retry')
            return
        if api('/git/ref/heads/main')['object']['sha'] != commit:
            log('Skipping release that is not the current main commit')
            return
        if dry_run:
            log('Candidate ' + commit + '; dry run made no changes')
            return
        assets = {a['name']: a for a in release_info.get('assets', [])}
        if set(assets) != set(LIMITS):
            raise RuntimeError('Incomplete or unexpected release assets')
        required = max(650 * 1024**2, assets['image.tar.gz']['size'] * 3 + 150 * 1024**2)
        if shutil.disk_usage(self.root).free < required:
            raise RuntimeError('Insufficient free disk space; current application was not changed')
        destination = self.root / 'releases' / commit
        if destination.is_symlink():
            raise RuntimeError('Unsafe release directory')
        destination.mkdir(mode=0o700, exist_ok=True)
        download(assets['checksums.sha256'], destination / 'checksums.sha256', LIMITS['checksums.sha256'])
        expected = checksum_manifest((destination / 'checksums.sha256').read_bytes())
        for filename, digest in expected.items():
            if download(assets[filename], destination / filename, LIMITS[filename]) != digest:
                raise RuntimeError('Release checksum manifest mismatch')
        unpack_runtime(destination / 'runtime.tar.gz', destination)
        # Do not deploy a stale build if main changed during the download.
        if api('/git/ref/heads/main')['object']['sha'] != commit:
            log('Main advanced during download; skipping old release')
            return
        self.deploy(commit)


def main():
    import fcntl
    parser = argparse.ArgumentParser()
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--retry', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    deployment = Deployment()
    if deployment.root.is_symlink() or deployment.config.is_symlink():
        raise RuntimeError('Unsafe deployment root')
    with (deployment.root / 'deploy.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            log('Another deployment is running; try next timer tick')
            return
        deployment.poll(args.dry_run, args.retry)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Only application-authored messages are safe to print.
        log(str(error) if type(error) is RuntimeError else 'Deployment check failed: ' + type(error).__name__)
        sys.exit(1)
