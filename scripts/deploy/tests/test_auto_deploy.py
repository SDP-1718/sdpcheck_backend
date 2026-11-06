import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location('auto_deploy', Path(__file__).parents[1] / 'auto-deploy.py')
agent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(agent)
OLD = 'a' * 40
NEW = 'b' * 40


class ReleaseTests(unittest.TestCase):
    def test_only_published_production_tags_are_candidates(self):
        self.assertIsNone(agent.candidate([
            {'tag_name': 'production-' + NEW, 'draft': True}, {'tag_name': 'v1.0'}]))
        release = {'tag_name': 'production-' + NEW, 'published_at': '2026-11-06'}
        self.assertEqual(agent.candidate([release]), release)

    def test_manifest_rejects_path_traversal_missing_and_duplicate_entries(self):
        for data in [b'a' * 64 + b'  ../../evil\n', b'a' * 64 + b'  image.tar.gz\n',
                     (b'a' * 64 + b'  image.tar.gz\n') * 2]:
            with self.assertRaises(RuntimeError):
                agent.checksum_manifest(data)

    def test_manifest_accepts_exact_two_assets(self):
        data = f"{'a'*64}  image.tar.gz\n{'b'*64}  runtime.tar.gz\n".encode()
        self.assertEqual(set(agent.checksum_manifest(data)), {'image.tar.gz', 'runtime.tar.gz'})

    def test_download_checks_github_digest_and_removes_corrupt_partial_file(self):
        asset = {'browser_download_url': f'https://github.com/{agent.REPO}/releases/download/production-{NEW}/image.tar.gz',
                 'size': 3, 'digest': 'sha256:' + '0' * 64}
        with tempfile.TemporaryDirectory() as directory, patch.object(agent, 'request', return_value=io.BytesIO(b'bad')):
            path = Path(directory) / 'image.tar.gz'
            with self.assertRaises(RuntimeError):
                agent.download(asset, path, 100)
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_download_rejects_external_url_before_request(self):
        with patch.object(agent, 'request') as request:
            with self.assertRaises(RuntimeError):
                agent.download({'browser_download_url': 'https://example.com/evil'}, Path('unused'), 100)
            request.assert_not_called()

    def test_runtime_cannot_extract_links_or_unexpected_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'bundle.tar.gz'
            for bad_name in ['../escape', 'environment.py']:
                with tarfile.open(archive, 'w:gz') as tar:
                    for name in agent.RUNTIME:
                        member = tarfile.TarInfo(name if name != 'environment.py' else bad_name)
                        if name == 'environment.py':
                            member.type = tarfile.SYMTYPE
                            member.linkname = '/etc/passwd'
                        tar.addfile(member)
                with self.assertRaises(RuntimeError):
                    agent.unpack_runtime(archive, root)


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.deployment = agent.Deployment(root / 'deploy', root / 'config')
        self.deployment.root.mkdir()
        (self.deployment.config / 'tools').mkdir(parents=True)
        (self.deployment.root / 'current-release').write_text(OLD)
        self.release = self.deployment.root / 'releases' / NEW
        self.release.mkdir(parents=True)
        (self.release / 'image.tar.gz').write_bytes(b'image')
        (self.release / 'auto-deploy.py').write_text('# new agent\n')
        self.deployment.run = Mock(return_value=b'')
        self.deployment.docker = Mock(side_effect=lambda *a, **kw: b'healthy' if a[0] == 'inspect' else NEW.encode() if a[0] == 'image' else b'')
        self.deployment.compose = Mock(return_value=b'')
        self.deployment.backup = Mock()
        self.deployment.healthy = Mock(return_value=True)
        self.deployment.cleanup = Mock()

    def test_success_marks_commit_and_preserves_previous_for_cleanup(self):
        self.deployment.deploy(NEW)
        self.assertEqual(self.deployment.current(), NEW)
        self.deployment.backup.assert_called_once_with(NEW)
        self.deployment.compose.assert_any_call(NEW, 'up', '-d', '--no-build', '--no-deps', 'app')
        self.deployment.cleanup.assert_called_once_with({OLD, NEW})
        self.assertFalse((self.release / 'image.tar.gz').exists())

    def test_unhealthy_app_rolls_back_without_changing_current_marker(self):
        self.deployment.healthy.side_effect = [False, True]
        with self.assertRaisesRegex(RuntimeError, 'previous application is healthy'):
            self.deployment.deploy(NEW)
        self.assertEqual(self.deployment.current(), OLD)
        self.deployment.compose.assert_any_call(OLD, 'up', '-d', '--no-build', '--no-deps', 'app')
        self.assertEqual((self.deployment.root / 'failed-auto-release').read_text().strip(), NEW)
        self.deployment.cleanup.assert_not_called()

    def test_compose_failure_also_attempts_rollback(self):
        self.deployment.compose.side_effect = [b'', RuntimeError('start failed'), b'']
        with self.assertRaisesRegex(RuntimeError, 'previous application is healthy'):
            self.deployment.deploy(NEW)
        self.assertEqual(self.deployment.current(), OLD)

    def test_failed_rollback_requires_manual_intervention(self):
        self.deployment.healthy.return_value = False
        with self.assertRaisesRegex(RuntimeError, 'manual intervention required'):
            self.deployment.deploy(NEW)
        self.assertEqual(self.deployment.current(), OLD)

    def test_backup_failure_never_replaces_app(self):
        self.deployment.backup.side_effect = RuntimeError('backup failed')
        with self.assertRaisesRegex(RuntimeError, 'backup failed'):
            self.deployment.deploy(NEW)
        self.assertEqual(self.deployment.current(), OLD)
        self.assertEqual(self.deployment.compose.call_count, 1)  # config validation only

    def test_stale_release_never_downloads_or_deploys(self):
        with patch.object(agent, 'api', side_effect=[[
            {'tag_name': 'production-' + NEW}], {'object': {'sha': OLD}}]), patch.object(agent, 'download') as download:
            self.deployment.poll()
            download.assert_not_called()

    def test_failed_release_is_not_retried_automatically(self):
        (self.deployment.root / 'failed-auto-release').write_text(NEW)
        with patch.object(agent, 'api', return_value=[{'tag_name': 'production-' + NEW}]) as api:
            self.deployment.poll()
            self.assertEqual(api.call_count, 1)

    def test_current_release_is_noop(self):
        with patch.object(agent, 'api', return_value=[{'tag_name': 'production-' + OLD}]) as api:
            self.deployment.poll()
            self.assertEqual(api.call_count, 1)
        self.deployment.backup.assert_not_called()

    def test_low_disk_never_downloads_or_changes_app(self):
        release = {'tag_name': 'production-' + NEW, 'assets': [
            {'name': name, 'size': 100} for name in agent.LIMITS]}
        with patch.object(agent, 'api', side_effect=[[release], {'object': {'sha': NEW}}]), \
             patch.object(agent.shutil, 'disk_usage', return_value=Mock(free=100)), \
             patch.object(agent, 'download') as download:
            with self.assertRaisesRegex(RuntimeError, 'disk space'):
                self.deployment.poll()
            download.assert_not_called()
        self.assertEqual(self.deployment.current(), OLD)


if __name__ == '__main__':
    unittest.main()
