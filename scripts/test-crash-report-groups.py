#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
s=importlib.util.spec_from_file_location('g',Path(__file__).with_name('crash-report-groups.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class GroupsTest(unittest.TestCase):
    def test_version_and_fingerprint_separate_with_counts(self):
        a=m.group({'reports':[{'fp':'same','a':'1','t':2000,'count':3},{'fp':'same','a':'1','t':1000}, {'fp':'same','a':'2','t':3000},{'fp':'other','a':'1','t':4000}]})
        self.assertEqual(len(a),3);self.assertEqual((a[0]['count'],a[0]['first'],a[0]['last']),(4,1000,2000))
    def test_empty_fingerprints_do_not_merge(self):
        self.assertEqual(len(m.group([{'id':'a'},{'id':'b'}])),2)
if __name__=='__main__':unittest.main()
