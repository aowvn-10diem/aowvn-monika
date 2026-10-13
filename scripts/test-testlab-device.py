import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('selector', Path(__file__).with_name('select-testlab-device.py'))
selector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selector)
profiles = json.loads(selector.PROFILES.read_text())


class DeviceSelector(unittest.TestCase):
    def model(self, **changes):
        return dict(dict(id='s24-ultra', name='Galaxy S24 Ultra', manufacturer='Samsung',
                         form='PHYSICAL', supportedAbis=['arm64-v8a'], supportedVersionIds=['34', '35']), **changes)

    def test_exact_ultra_selects_latest_api(self):
        picked = selector.select([self.model()], profiles['snapdragon8gen3'])
        self.assertEqual(('s24-ultra', 35), (picked['model'], picked['version']))

    def test_mediatek_matches_explicit_model_not_brand(self):
        model = self.model(id='14t-pro', name='Xiaomi 14T Pro', manufacturer='Xiaomi')
        self.assertEqual('14t-pro', selector.select([model], profiles['mediatek-modern'])['model'])
        with self.assertRaisesRegex(ValueError, 'absent'):
            selector.select([dict(model, name='Xiaomi 14')], profiles['mediatek-modern'])

    def test_base_s24_does_not_substitute_for_snapdragon_ultra(self):
        with self.assertRaisesRegex(ValueError, 'absent'):
            selector.select([self.model(name='Galaxy S24')], profiles['snapdragon8gen3'])

    def test_absent_or_duplicate_blocks(self):
        for models in ([], [self.model(), self.model(id='another')]):
            with self.assertRaises(ValueError):
                selector.select(models, profiles['snapdragon8gen3'])

    def test_virtual_x86_old_api_and_unsafe_id_block(self):
        for changes in (dict(form='VIRTUAL'), dict(supportedAbis=['x86_64']),
                        dict(supportedVersionIds=['33']), dict(id='bad,locale=xx')):
            with self.assertRaises(ValueError):
                selector.select([self.model(**changes)], profiles['snapdragon8gen3'])

    def test_existing_pixel_pins_api37(self):
        model = self.model(id='cubs', supportedVersionIds=['36', '37'])
        self.assertEqual(37, selector.select([model], profiles['cubs'])['version'])
        with self.assertRaisesRegex(ValueError, 'Pinned API'):
            selector.select([dict(model, supportedVersionIds=['36'])], profiles['cubs'])


if __name__ == '__main__':
    unittest.main()
