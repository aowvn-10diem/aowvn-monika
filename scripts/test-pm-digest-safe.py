#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import unittest
s=importlib.util.spec_from_file_location('safe',Path(__file__).with_name('pm-digest-safe.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class FakeDB:
    def __init__(self,exists):self.exists=exists;self.calls=[]
    def request(self,method,route,payload=None):
        self.calls.append((method,route,payload))
        if method=='GET':return {'ref':m.BOT_REF} if self.exists else None
        return {'sha':'a'*40}
class SafeDigestTest(unittest.TestCase):
    def test_scrubs_before_json_and_markdown(self):
        data={'repository':m.REPOSITORY,'open_prs':[{'number':1,'branch':'sol/V39','title':'x test@example.test token=secret123 /home/alice/file.txt','body':'không được sao chép','comments':[]}], 'main':{'commits':[{'title':'github_pat_testSecret'}]}, 'failed_runs_24h':[{'url':'https://github.com/aowvn-10diem/aowvn-monika/actions/runs/123'}]}
        safe=m.sanitize(data);text=json.dumps(safe)+m.digest.render_markdown(safe)
        for bad in ['test@example.test','secret123','/home/alice','github_pat_testSecret','không được sao chép']:self.assertNotIn(bad,text)
        self.assertIn('sol/V39',text);self.assertIn('/actions/runs/123',text)
    def test_existing_bot_only_force_and_single_commit(self):
        api=FakeDB(True);m.publish(api,{'main':{},'open_prs':[]})
        commit=next(p for method,route,p in api.calls if route=='/git/commits')
        self.assertEqual(commit['parents'],[])
        tree=next(p for method,route,p in api.calls if route=='/git/trees')
        self.assertEqual({x['path'] for x in tree['tree']},{'docs/trang-thai/digest.json','docs/trang-thai/digest.md'})
        self.assertEqual(api.calls[-1][0:2],('PATCH','/git/refs/heads/bot/trang-thai'))
        self.assertTrue(api.calls[-1][2]['force'])
        self.assertNotIn('/git/refs/heads/main',[r for _,r,_ in api.calls])
    def test_top_ten_endpoints_do_not_walk_history(self):
        api=m.BoundedDigestAPI(m.REPOSITORY,'')
        calls=[]
        def request(route):
            calls.append(route);return list(range(20)), {'Link':'<https://api.github.com/next>; rel="next"'}
        api._request_json=request
        self.assertEqual(api.list_all('/commits?sha=main&per_page=10'),list(range(10)))
        self.assertEqual(len(calls),1)
    def test_numeric_pagination_is_rewritten_only_for_this_repository(self):
        api=m.BoundedDigestAPI(m.REPOSITORY,'')
        self.assertEqual(api._url('https://api.github.com/repositories/1392088306/actions/runs?per_page=100&page=2'),
                         'https://api.github.com/repos/'+m.REPOSITORY+'/actions/runs?per_page=100&page=2')
        for bad in ['https://api.github.com/repositories/1/actions/runs?page=2',
                    'https://api.github.com/repositories/13920883060/actions/runs',
                    'https://evil.test/repositories/1392088306/actions/runs',
                    'https://user@api.github.com/repositories/1392088306/actions/runs',
                    'https://api.github.com/repositories/1392088306/../1/actions/runs',
                    'https://api.github.com/repositories/1392088306/%2e%2e/actions/runs',
                    'https://api.github.com/repositories/1392088306/actions/runs#x']:
            with self.assertRaises(RuntimeError):api._url(bad)
    def test_numeric_second_page_keeps_run_records(self):
        api=m.BoundedDigestAPI(m.REPOSITORY,'');urls=[]
        def request(route):
            url=api._url(route);urls.append(url)
            return ({'workflow_runs':[{'id':len(urls)}]},
                    {'Link':'<https://api.github.com/repositories/1392088306/actions/runs?page=2>; rel="next"'} if len(urls)==1 else {})
        api._request_json=request
        self.assertEqual(api.list_all('/actions/runs?per_page=100','workflow_runs'),[{'id':1},{'id':2}])
        self.assertEqual(len(urls),2)
    def test_creates_only_bot_ref_when_missing(self):
        api=FakeDB(False);m.publish(api,{'main':{}})
        self.assertEqual(api.calls[-1][2]['ref'],m.BOT_REF)
if __name__=='__main__':unittest.main()
