"""Offline tests: run the real Bash scripts with mocked adb/SSH/VC transport."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
LAPTOP = ROOT / 'tools/laptop'
VC = ROOT / 'tools/vc'


class ScriptTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        self.env = dict(os.environ, PATH=str(self.path) + os.pathsep + os.environ['PATH'],
                        TEST_CALLS=str(self.path/'calls'), VC_RUN_DIR=str(self.path/'vc'))
        self.stub('sleep', 'exit 0')

    def stub(self, name, body):
        p = self.path/name
        p.write_text('#!/bin/bash\n' + body + '\n')
        p.chmod(0o755)
        return str(p)

    def run_script(self, script, *args):
        return subprocess.run(['bash', str(script), *args], env=self.env, capture_output=True, text=True, timeout=15)

    def calls(self):
        return (self.path/'calls').read_text()

    def test_shell_syntax(self):
        files = sorted(LAPTOP.glob('*.sh')) + [VC/'vc.sh']
        for p in files:
            with self.subTest(script=p.name):
                result = subprocess.run(['bash', '-n', str(p)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_powershell_utf8_bom(self):
        for p in LAPTOP.glob('*.ps1'):
            with self.subTest(script=p.name):
                self.assertTrue(p.read_bytes().startswith(b'\xef\xbb\xbf'))
                p.read_bytes().decode('utf-8-sig')

    def mock_adb(self, good):
        self.env['ADB'] = self.stub('mock-adb', '''echo "$*" >> "$TEST_CALLS"
case "$*" in
 'shell pidof dev.carevisit.app') echo 1234 ;;
 logcat*) echo 'I/CareVisit( 1234): %s' ;;
esac''' % ('demo started: visit' if good else 'unrelated message'))

    def test_demo_start_and_matching_log(self):
        self.mock_adb(True)
        result = self.run_script(LAPTOP/'demo-smoke.sh', '--local')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('PASS:', result.stdout)
        self.assertIn('shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit', self.calls())
        self.assertIn('logcat -d --pid=1234 -v brief -s CareVisit:I *:S', self.calls())

    def test_demo_missing_log_fails(self):
        self.mock_adb(False)
        result = self.run_script(LAPTOP/'demo-smoke.sh', '--local')
        self.assertEqual(1, result.returncode)
        self.assertIn('FAIL:', result.stderr)

    def test_sync_rejects_shell_metacharacters_and_other_repos(self):
        for dest in ['repo', 'repo-other', 'repo-visit-okay;whoami', 'repo-visit-x/../repo', 'repo-visit-']:
            self.env['LAPTOP_REPO'] = dest
            result = self.run_script(LAPTOP/'sync.sh')
            self.assertNotEqual(0, result.returncode)
            self.assertIn('LAPTOP_REPO', result.stderr)

    def test_sync_defaults_to_visit_and_preserves_signing(self):
        self.env.pop('LAPTOP_REPO', None)
        self.stub('tar', 'exit 0')
        for tool in ['scp', 'ssh']: self.stub(tool, 'echo "$*" >> "$TEST_CALLS"')
        result = self.run_script(LAPTOP/'sync.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('sync-repo-visit.tar', self.calls())
        self.assertIn('robocopy sync-stage-repo-visit repo-visit /MIR', self.calls())
        self.assertIn("if ($LASTEXITCODE -ne 0) { throw 'tar extraction failed", self.calls())
        self.assertIn('/XF local.properties keystore.properties *.jks *.keystore', self.calls())

    def test_vc_all_four_scenarios_use_supported_commands(self):
        self.stub('curl', 'echo "$*" >> "$TEST_CALLS"\nprintf 200')
        for name in ['drive', 'dropoff', 'wait', 'return']:
            with self.subTest(scenario=name):
                result = self.run_script(VC/'vc.sh', 'play', str(VC/f'visit-{name}.vc'))
                self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        for fragment in ['/vehicle/gear?gear=Drive', '/vehicle/gear?gear=Park',
                         '/vehicle/controls?speed=40', '/vehicle/controls?speed=30',
                         '/vehicle/body?door_fr=true', '/vehicle/body?door_fr=false']:
            self.assertIn(fragment, calls)
        self.assertNotIn('seat', calls)

    def test_vc_stops_after_first_failure(self):
        self.stub('curl', 'echo "$*" >> "$TEST_CALLS"\nprintf 500')
        result = self.run_script(VC/'vc.sh', 'play', str(VC/'visit-return.vc'))
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(self.calls().splitlines()))

    def test_vc_unknown_command_fails(self):
        result = self.run_script(VC/'vc.sh', 'made-up-command')
        self.assertEqual(2, result.returncode)


if __name__ == '__main__':
    unittest.main()
