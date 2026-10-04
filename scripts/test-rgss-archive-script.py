import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('archive', Path(__file__).with_name('rgss_archive_script.py'))
archive = importlib.util.module_from_spec(spec); spec.loader.exec_module(archive)

def fixture(entries):
    # Encoder fixture độc lập, cùng format native; dữ liệu hoàn toàn tự sinh.
    key = 0xDEADCAFE; output = bytearray(b'RGSSAD\0\x01')
    for name, data in entries:
        name = name.encode()
        output += struct.pack('<I', len(name) ^ key); key = (key * 7 + 3) & 0xffffffff
        for char in name:
            output.append(char ^ (key & 255)); key = (key * 7 + 3) & 0xffffffff
        output += struct.pack('<I', len(data) ^ key); key = (key * 7 + 3) & 0xffffffff
        data_key = key
        for i, char in enumerate(data):
            output.append(char ^ ((data_key >> (8 * (i % 4))) & 255))
            if i % 4 == 3: data_key = (data_key * 7 + 3) & 0xffffffff
    return bytes(output)

class ArchiveTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'fixture.rgss2a'
    def read(self, data):
        self.path.write_bytes(data)
        return archive.extract_script(self.path, 'Data/Scripts.rvdata')
    def test_target_after_other_resource_and_partial_word(self):
        for data in [b'x', b'abcd', b'abcde', b'\x04\x08synthetic marshal']:
            self.assertEqual(self.read(fixture([('Audio/ignored', b'skip'), ('Data\\Scripts.rvdata', data), ('Data/Other', b'after')])), data)
    def test_missing_duplicate_and_truncated_rejected(self):
        for data in [fixture([('Data/Other', b'x')]), fixture([('Data/Scripts.rvdata', b'a'), ('data/scripts.rvdata', b'b')]), fixture([('Data/Scripts.rvdata', b'abcde')])[:-1]]:
            with self.assertRaises(ValueError): self.read(data)
    def test_wrong_format_and_hostile_name_rejected(self):
        for data in [b'RGSSAD\0\x03', fixture([('../escape', b'x')]), fixture([('C:\\escape', b'x')]), b'RGSSAD\0\x01' + struct.pack('<I', 0xffffffff ^ 0xDEADCAFE)]:
            with self.assertRaises(ValueError): self.read(data)
    def test_script_budget_checked_before_allocation(self):
        old = archive.MAX_SCRIPT; archive.MAX_SCRIPT = 4
        try:
            with self.assertRaises(ValueError): self.read(fixture([('Data/Scripts.rvdata', b'12345')]))
        finally: archive.MAX_SCRIPT = old

if __name__ == '__main__': unittest.main()
