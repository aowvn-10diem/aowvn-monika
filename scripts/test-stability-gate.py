#!/usr/bin/env python3
import copy
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec=importlib.util.spec_from_file_location('stability',Path(__file__).with_name('stability-gate.py'))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
NOW=1791862200000
HOUR=3600000
def row(at, core='mgba', version='0.8.3', suffix='12345678', frame='Game.run'):
    return {'id':f'r:{at:013d}:{suffix}', 'report':{'app':version,'kind':'java','session':{'core':core},'detail':f'at {frame}(Game.kt:45)','log':[], 'count':1,'time':0,'device':'secret-fixture','image':{'data':'private-image'}}}
def data(*records):return {'complete':True,'records':list(records)}

class GateTest(unittest.TestCase):
    def test_new_signature_blocks_with_only_sanitized_list(self):
        out=v.gate(data(row(NOW-HOUR)), '0.8.3',24,NOW)
        self.assertFalse(out['allowed']);self.assertEqual(len(out['new_signatures']),1)
        text=json.dumps(out);self.assertNotIn('private-image',text);self.assertNotIn('Game.run',text);self.assertNotIn('secret-fixture',text)
        self.assertEqual(out['new_signatures'][0]['core'],'mgba')

    def test_old_signature_repeated_recently_is_not_new_and_other_version_is_not_baseline(self):
        old=row(NOW-25*HOUR);new=row(NOW-HOUR,suffix='abcdefgh'.replace('g','a').replace('h','b'))
        self.assertTrue(v.gate(data(old,new),'0.8.3',24,NOW)['allowed'])
        old['report']['app']='0.8.2';self.assertFalse(v.gate(data(old,new),'0.8.3',24,NOW)['allowed'])

    def test_boundary_first_received_time_ignores_device_clock(self):
        r=row(NOW-24*HOUR);r['report']['time']=NOW-100*HOUR
        self.assertFalse(v.gate(data(r),'0.8.3',24,NOW)['allowed'])
        r['id']=f'r:{NOW-24*HOUR-1:013d}:12345678';self.assertTrue(v.gate(data(r),'0.8.3',24,NOW)['allowed'])

    def test_incomplete_duplicate_future_empty_and_bad_count_block(self):
        r=row(NOW-HOUR)
        for f in [dict(data(r),complete=False),data(r,r),data(row(NOW+1)),data()]:
            with self.assertRaises(ValueError):v.gate(f,'0.8.3',24,NOW)
        for count in [True,0,-1,'1']:
            f=data(row(NOW));f['records'][0]['report']['count']=count
            with self.assertRaises(ValueError):v.gate(f,'0.8.3',24,NOW)

    def test_signature_v100_known_vector_first_three_frames_and_native_path_scrub(self):
        r=row(NOW)['report'];r['detail']='at A.one(A.kt:1)\nat B.two(B.kt:2)\nat C.three(C.kt:3)\nat D.four(D.kt:4)'
        expected=hashlib.sha256(json.dumps(['mgba','java',['A.one','B.two','C.three']],separators=(',',':')).encode()).hexdigest()
        self.assertEqual(v.signature(r)[0],expected)
        r['detail']='#00 pc 1234 /data/private/libengine.so (run+4)'
        s=v.signature(r)[0];r['detail']='#00 pc abcd /different/libengine.so (run+999)'
        self.assertEqual(v.signature(r)[0],s)
        for offset in ['0x20','0x44','0XABCD']:
            r['detail']=f'#00 pc abcd /different/libengine.so (run+{offset})'
            self.assertEqual(v.signature(r)[0],s)
        r['detail']='#00 pc abcd /different/libengine.so (other+0x20)'
        self.assertNotEqual(v.signature(r)[0],s)

    def test_pagination_filters_version_and_fetches_only_target_details(self):
        a,b=row(NOW-25*HOUR),row(NOW-HOUR,version='0.8.2',suffix='abcdef12')
        class Fake:
            def __init__(self):self.paths=[]
            def get(self,path):
                self.paths.append(path)
                if path=='/report-index':return {'complete':False,'cursor':'next/a','reports':[{'id':b['id'],'app':'0.8.2'}]}
                if path=='/report-index?cursor=next%2Fa':return {'complete':True,'cursor':None,'reports':[{'id':a['id'],'app':'0.8.3'}]}
                if path.startswith('/reports/'):return a['report']
                raise AssertionError(path)
        f=Fake();result=v.collect(f,'0.8.3');self.assertEqual(result,data(a));self.assertEqual(len(f.paths),3)
        self.assertTrue(v.gate(result,'0.8.3',24,NOW)['allowed'])

    def test_repeated_cursor_missing_app_detail_version_mismatch_and_budget_fail_closed(self):
        class Fake:
            def __init__(self,mode):self.mode=mode
            def get(self,path):
                if path.startswith('/reports/'):return {'app':'0.8.2'}
                if self.mode=='mismatch':return {'complete':True,'reports':[{'id':row(NOW)['id'],'app':'0.8.3'}]}
                if self.mode=='missing':return {'complete':True,'reports':[{'id':row(NOW)['id'],'app':''}]}
                return {'complete':False,'cursor':'same','reports':[]}
        for mode in ['mismatch','missing','cursor']:
            with self.assertRaises(ValueError):v.collect(Fake(mode),'0.8.3')

    def test_worker_configuration_rejects_credentials_http_and_redirect_origins(self):
        for origin in ['http://example.test','https://user:password@example.test','https://example.test/path','https://example.test/?token=a']:
            with self.assertRaises(ValueError):v.Worker(origin,'fixture-only')

    def test_gate_rejects_top_level_and_record_shapes(self):
        bad_values = [None, [], [row(NOW)], 'object', 1, True]
        for bad in bad_values:
            with self.subTest(top=bad), self.assertRaises(ValueError):
                v.gate(bad, '0.8.3', 24, NOW)
            with self.subTest(record=bad), self.assertRaises(ValueError):
                v.gate(data(bad), '0.8.3', 24, NOW)

    def test_api_wrong_page_or_row_shape_exits_blocked_before_detail_requests(self):
        bad_values = [None, [], 'object', 1, True]
        responses = bad_values + [{'complete': True, 'reports': [bad]} for bad in bad_values]
        for response in responses:
            class Fake:
                def __init__(self): self.paths = []
                def get(self, path):
                    self.paths.append(path)
                    if path != '/report-index': raise AssertionError('unexpected detail request')
                    return response
            with self.subTest(response=response), tempfile.TemporaryDirectory() as tmp:
                worker = Fake(); out = Path(tmp) / 'result.json'
                stderr, stdout = io.StringIO(), io.StringIO()
                with mock.patch.object(v, 'Worker', return_value=worker), mock.patch.object(v.sys, 'argv', ['gate', '--version', '0.8.3', '--out', str(out)]), contextlib.redirect_stderr(stderr), contextlib.redirect_stdout(stdout):
                    code = v.main()
                self.assertEqual(code, 2)
                self.assertEqual(worker.paths, ['/report-index'])
                self.assertFalse(out.exists())
                self.assertEqual(stdout.getvalue(), '')
                self.assertTrue(stderr.getvalue().startswith('BLOCKED:'))
                self.assertNotIn('Traceback', stderr.getvalue())

    def test_cli_wrong_fixture_shape_exits_blocked_without_result(self):
        bad_values = [None, [], 'object', 1, True]
        fixtures = bad_values + [data(bad) for bad in bad_values]
        for fixture in fixtures:
            with self.subTest(fixture=fixture), tempfile.TemporaryDirectory() as tmp:
                path, out = Path(tmp) / 'input.json', Path(tmp) / 'result.json'
                path.write_text(json.dumps(fixture)); stderr, stdout = io.StringIO(), io.StringIO()
                with mock.patch.object(v.sys, 'argv', ['gate', '--fixture', str(path), '--version', '0.8.3', '--now-ms', str(NOW), '--out', str(out)]), contextlib.redirect_stderr(stderr), contextlib.redirect_stdout(stdout):
                    code = v.main()
                self.assertEqual(code, 2)
                self.assertFalse(out.exists())
                self.assertEqual(stdout.getvalue(), '')
                self.assertTrue(stderr.getvalue().startswith('BLOCKED:'))
                self.assertNotIn('Traceback', stderr.getvalue())

if __name__=='__main__':unittest.main()
