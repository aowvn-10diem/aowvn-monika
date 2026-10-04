#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location('snapshot', Path(__file__).with_name('core-snapshot.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
class ProvenanceTest(unittest.TestCase):
    def test_source_commit_embedded_only(self):
        for core, version in [('gambatte', b'v0.5.0-netlink d9d6cd0\x00'), ('mgba', b'0.11-212-7a12d6d\x00'), ('fceumm', b'(SVN) 7a542da\x00')]:
            self.assertEqual(len(m.binary_commit(core, version)), 7)
            with self.assertRaises(ValueError): m.binary_commit(core, b'HEAD abcdef0\x00')
        with self.assertRaises(ValueError): m.binary_commit('fceumm', b'(SVN) abcdef0\x00(SVN) abcdef1\x00')
    def test_elf_abi_must_match(self):
        for abi, (cls, machine) in m.ABIS.items():
            data = bytearray(64); data[:4] = b'\x7fELF'; data[4] = cls; data[5] = 1; data[18:20] = machine.to_bytes(2, 'little')
            m.check_elf(data, abi)
            other = next(a for a in m.ABIS if a != abi)
            with self.assertRaises(ValueError): m.check_elf(data, other)
        with self.assertRaises(ValueError): m.check_elf(b'not an ELF', 'x86')
    def test_only_snapshot_tag_family(self):
        for tag in ['main', 'v1', 'cores-gb-gba-nes-0', 'cores-gb-gba-nes-1;echo']:
            with self.assertRaises(ValueError): m.prepare(Path('missing'), Path('unused'), tag)
if __name__ == '__main__': unittest.main()
