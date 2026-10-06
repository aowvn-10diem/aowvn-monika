#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import py_compile
import sys
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('layout',Path(__file__).with_name('renpy-bytecode-layout.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class BytecodeTest(unittest.TestCase):
    def test_orphan_cache_becomes_importable_without_changing_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);source=root/'fixture.py';source.write_text('value = 17\n')
            cache=Path(py_compile.compile(str(source),doraise=True));original=cache.read_bytes()
            source.unlink()
            self.assertEqual(m.normalize(root,sys.implementation.cache_tag),1)
            out=root/'fixture.pyc';self.assertEqual(out.read_bytes(),original)
            spec=importlib.util.spec_from_file_location('fixture',out)
            loaded=importlib.util.module_from_spec(spec);spec.loader.exec_module(loaded)
            self.assertEqual(loaded.value,17)

    def test_source_module_keeps_its_cache(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);source=root/'fixture.py';source.write_text('value = 17\n')
            cache=Path(py_compile.compile(str(source),doraise=True))
            self.assertEqual(m.normalize(root,sys.implementation.cache_tag),0)
            self.assertTrue(cache.is_file())

    def test_wrong_tag_or_collision_fails_before_moving(self):
        for collision in [False,True]:
            with self.subTest(collision=collision),tempfile.TemporaryDirectory() as temporary:
                root=Path(temporary);source=root/'fixture.py';source.write_text('value = 17\n')
                cache=Path(py_compile.compile(str(source),doraise=True));source.unlink()
                if collision:(root/'fixture.pyc').write_bytes(b'existing')
                with self.assertRaises(RuntimeError):
                    m.normalize(root,sys.implementation.cache_tag if collision else 'cpython-999')
                self.assertTrue(cache.is_file())


if __name__=='__main__':unittest.main()
