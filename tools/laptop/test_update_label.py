import importlib.util
from pathlib import Path
import tempfile
import unittest


def tool():
    spec = importlib.util.spec_from_file_location('update_label', Path(__file__).with_name('update-label.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LabelTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        source = self.root/'app/src/main/res/values/strings.xml'
        source.parent.mkdir(parents=True)
        source.write_text('<resources><string name="app_name">함께진료</string></resources>')
        for name in ['README.md', 'tools/laptop/README.md']:
            p = self.root/name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('# 진료동행\n<!-- app-label: 진료동행 -->\nAPK: apk/팀명_진료동행_release.apk\n')

    def test_label_and_apk_name_follow_xml(self):
        self.assertEqual(2, len(tool().sync_labels(self.root)))
        for name in ['README.md', 'tools/laptop/README.md']:
            text = (self.root/name).read_text()
            self.assertIn('# 함께진료', text)
            self.assertIn('apk/팀명_함께진료_release.apk', text)
            self.assertNotIn('진료동행', text)
        self.assertEqual([], tool().sync_labels(self.root))

    def test_check_is_read_only(self):
        self.assertEqual(2, len(tool().sync_labels(self.root, check=True)))
        self.assertIn('# 진료동행', (self.root/'README.md').read_text())

    def test_missing_marker_fails_before_any_write(self):
        (self.root/'tools/laptop/README.md').write_text('unmarked document')
        with self.assertRaises(ValueError): tool().sync_labels(self.root)
        self.assertIn('# 진료동행', (self.root/'README.md').read_text())


if __name__ == '__main__': unittest.main()
