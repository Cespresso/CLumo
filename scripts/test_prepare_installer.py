import json
from pathlib import Path
import shutil
import tempfile
import unittest
from prepare_installer import FIRMWARE, prepare

ROOT = Path(__file__).resolve().parents[1]


class PrepareInstallerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for source in (ROOT / 'installer').glob('manifest-*.json'):
            shutil.copy2(source, self.directory / source.name)
        self.downloads = []

    def download(self, tag, name, directory):
        self.downloads.append((tag, name))
        (directory / name).write_bytes(b'firmware')

    def release(self, variants, apk=True):
        names = [f'clumo-{variant}.bin' for variant in variants]
        return {'tag_name': 'v1.1', 'assets': [{'name': n} for n in names + (['clumo.apk'] if apk else [])]}

    def availability(self):
        return json.loads((self.directory / 'availability.json').read_text())['firmware']

    def test_all_variants_use_same_release_and_offset_zero(self):
        prepare(self.directory, self.release(FIRMWARE), self.download)
        self.assertTrue(all(self.availability().values()))
        self.assertEqual(len(self.downloads), 3)
        for variant in FIRMWARE:
            m = json.loads((self.directory / f'manifest-{variant}.json').read_text())
            self.assertEqual(m['version'], 'v1.1')
            self.assertEqual(m['builds'][0]['parts'][0]['offset'], 0)
            self.assertEqual(m['builds'][0]['chipFamily'], 'ESP32-C3')

    def test_old_release_disables_stand_and_removes_stale_binary(self):
        (self.directory / 'clumo-headphone-stand.bin').write_bytes(b'stale')
        prepare(self.directory, self.release(FIRMWARE[:2]), self.download)
        self.assertFalse(self.availability()['headphone-stand'])
        self.assertTrue(self.availability()['standalone'])
        self.assertFalse((self.directory / 'clumo-headphone-stand.bin').exists())
        self.assertEqual(json.loads((self.directory / 'manifest-headphone-stand.json').read_text())['builds'], [])

    def test_stand_requires_matching_app(self):
        prepare(self.directory, self.release(FIRMWARE, apk=False), self.download)
        self.assertFalse(self.availability()['headphone-stand'])

    def test_no_release_has_no_installable_variants(self):
        prepare(self.directory, {}, self.download)
        self.assertFalse(any(self.availability().values()))
        self.assertEqual(self.downloads, [])

    def test_download_failure_aborts_publication(self):
        def fail(*args):
            raise RuntimeError('download failed')
        with self.assertRaises(RuntimeError):
            prepare(self.directory, self.release(FIRMWARE), fail)
        self.assertFalse((self.directory / 'availability.json').exists())

    def test_empty_download_aborts_publication(self):
        with self.assertRaises(RuntimeError):
            prepare(self.directory, self.release(FIRMWARE), lambda *args: None)
        self.assertFalse((self.directory / 'availability.json').exists())


if __name__ == '__main__':
    unittest.main()
