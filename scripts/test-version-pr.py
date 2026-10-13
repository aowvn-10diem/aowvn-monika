#!/usr/bin/env python3
import copy
import base64
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("version_pr", pathlib.Path(__file__).with_name("version-pr.py"))
v = importlib.util.module_from_spec(spec); spec.loader.exec_module(v)
SHA = "a"*40


def fixture():
    return {"repo":"o/r", "head":SHA, "build":'android {\n    versionCode = 47\n    versionName = "0.8.2"\n}\n',
            "changelog":"# Nhật ký\n\n## v0.8.2 (Bản thử)\n\n- cũ\n", "previous_tag":"v0.8.2", "date":"2026-10-13",
            "merged_prs":[{"number":221,"title":"[viec-V97] Chạm NDS không chặn UI", "merge_commit_sha":"b"*40,"on_main":True,"after_tag":True}]}


class VersionTest(unittest.TestCase):
    def test_dry_plan_has_two_files_increment_full_commit_and_draft(self):
        p = v.make_plan(fixture())
        self.assertEqual(set(p["files"]), {v.BUILD,v.CHANGELOG})
        self.assertEqual((p["version_code"],p["version"]),(48,"0.8.3"))
        self.assertIn('versionCode = 48',p["files"][v.BUILD]);self.assertIn('versionName = "0.8.3"',p["files"][v.BUILD])
        self.assertIn("/commit/"+"b"*40,p["files"][v.CHANGELOG]);self.assertTrue(p["draft"])
        self.assertNotIn("[viec-V97]",p["files"][v.CHANGELOG]);self.assertIn("- cũ",p["files"][v.CHANGELOG])
        self.assertEqual(p,v.make_plan(fixture()))
        self.assertEqual(p['files'][v.BUILD], fixture()['build'].replace('47','48').replace('0.8.2','0.8.3'))

    def test_snapshot_reads_final_page_peels_tag_and_checks_ancestry(self):
        class Fake:
            repo='o/r'
            def __init__(self, complete=True): self.complete=complete;self.paths=[]
            def call(self,path,method='GET',data=None):
                self.assert_get(method);self.paths.append(path)
                if path=='/git/ref/heads/main':return {'object':{'sha':SHA}}
                if path.startswith('/contents/'):
                    field='build' if path.startswith('/contents/app/') else 'changelog'
                    return {'encoding':'base64','content':base64.b64encode(fixture()[field].encode()).decode()}
                if path.startswith('/git/ref/tags/'):return {'object':{'type':'tag','sha':'t'*40}}
                if path.startswith('/git/tags/'):return {'object':{'type':'commit','sha':'c'*40}}
                if path.startswith('/commits/'):
                    return {'commit':{'committer':{'date':'2026-10-13T00:00:00Z' if path.endswith(SHA) else '2026-10-12T00:00:00Z'}}}
                if path.startswith('/pulls?'):
                    page=int(path.split('page=')[-1])
                    if page==1 or not self.complete:
                        return [{'number':page*100+n,'state':'closed','title':'old','merged_at':None} for n in range(100)]
                    return [{'number':221,'state':'closed','title':'fixed','merged_at':'2026-10-12T01:00:00Z','merge_commit_sha':'b'*40}]
                if path.startswith('/compare/'):return {'status':'ahead'}
                raise AssertionError(path)
            @staticmethod
            def assert_get(method):assert method=='GET'
        g=Fake();s=v.snapshot(g)
        self.assertEqual(len(s['merged_prs']),1);self.assertEqual(s['merged_prs'][0]['number'],221)
        self.assertTrue(any('page=2' in p for p in g.paths));self.assertIn('/compare/'+'c'*40+'...'+'b'*40,g.paths)
        with self.assertRaises(ValueError):v.snapshot(Fake(False))

    def test_existing_valid_branch_reuses_closed_pr_without_new_commit_or_pr(self):
        class Fake:
            repo='o/r'
            def __init__(self):self.writes=[]
            def call(self,path,method='GET',data=None):
                if method!='GET':self.writes.append(path)
                if path=='/git/ref/heads/main':return {'object':{'sha':SHA}}
                if path=='/git/commits/'+SHA:return {'tree':{'sha':'old'}}
                if path=='/git/trees':return {'sha':'newtree'}
                if path.startswith('/git/ref/heads/bot/'):return {'object':{'sha':'existing'}}
                if path=='/git/commits/existing':return {'tree':{'sha':'newtree'},'parents':[{'sha':SHA}]}
                if path.startswith('/pulls?'):return [{'number':9,'state':'closed'}]
                raise AssertionError(path)
        g=Fake();self.assertEqual(v.apply(g,v.make_plan(fixture())),9);self.assertEqual(g.writes,['/git/trees'])

    def test_duplicate_missing_ancestry_empty_range_and_pending_version_block(self):
        for field, value in [("on_main",False),("after_tag",False),("merge_commit_sha","bad")]:
            f=fixture();f["merged_prs"][0][field]=value
            with self.assertRaises(ValueError):v.make_plan(f)
        for change in [{"merged_prs":[]},{"pending_versions":["0.8.3"]},{"previous_tag":"v0.8.1"}]:
            f=fixture();f.update(change)
            with self.assertRaises(ValueError):v.make_plan(f)
        f=fixture();f["merged_prs"]*=2
        with self.assertRaises(ValueError):v.make_plan(f)

    def test_invalid_nonincreasing_or_ambiguous_version_and_existing_header_block(self):
        for version in ["0.8.2","0.8.1","v0.8.3","0.8.3;curl"]:
            with self.assertRaises(ValueError):v.make_plan(fixture(),version)
        f=fixture();f["build"]+='versionCode = 999\n'
        with self.assertRaises(ValueError):v.make_plan(f)
        f=fixture();f["changelog"]+='## v0.8.3\n'
        with self.assertRaises(ValueError):v.make_plan(f)

    def test_title_is_data_not_shell_or_markdown_link(self):
        f=fixture();f["merged_prs"][0]["title"]='[V] Fix [x](https://evil.test/game)\n## heading $(touch /tmp/never)'
        text=v.make_plan(f)["files"][v.CHANGELOG]
        self.assertNotIn('https://evil.test',text);self.assertNotIn('\n## heading',text);self.assertIn('$(touch /tmp/never)',text)

    def test_apply_only_creates_draft_branch_pr_never_merge_tag_release_or_force(self):
        class Fake:
            repo='o/r'
            def __init__(self):self.writes=[]
            def call(self,path,method='GET',data=None):
                if method!='GET':self.writes.append((path,data))
                if path=='/git/ref/heads/main':return {'object':{'sha':SHA}}
                if path=='/git/commits/'+SHA:return {'tree':{'sha':'old'}}
                if path=='/git/trees':return {'sha':'newtree'}
                if path.startswith('/git/ref/heads/bot/'):raise v.Missing()
                if path=='/git/commits':return {'sha':'newcommit'}
                if path=='/git/commits/newcommit':return {'tree':{'sha':'newtree'},'parents':[{'sha':SHA}]}
                if path=='/git/refs':return {}
                if path.startswith('/pulls?'):return []
                if path=='/pulls':return {'number':999}
                raise AssertionError(path)
        g=Fake();self.assertEqual(v.apply(g,v.make_plan(fixture())),999)
        self.assertEqual([p for p,d in g.writes],['/git/trees','/git/commits','/git/refs','/pulls'])
        self.assertTrue(g.writes[-1][1]['draft']);self.assertEqual(g.writes[1][1]['parents'],[SHA])
        self.assertFalse(any('force' in data for p,data in g.writes))

    def test_main_move_or_existing_branch_difference_prevents_pr_creation(self):
        class Fake:
            repo='o/r'
            def __init__(self, moved):self.moved=moved;self.writes=[]
            def call(self,path,method='GET',data=None):
                if method!='GET':self.writes.append(path)
                if path=='/git/ref/heads/main':return {'object':{'sha':'c'*40 if self.moved else SHA}}
                if path=='/git/commits/'+SHA:return {'tree':{'sha':'old'}}
                if path=='/git/trees':return {'sha':'newtree'}
                if path.startswith('/git/ref/heads/bot/'):return {'object':{'sha':'existing'}}
                if path=='/git/commits/existing':return {'tree':{'sha':'different'},'parents':[{'sha':SHA}]}
                raise AssertionError(path)
        for moved in [True,False]:
            g=Fake(moved)
            with self.assertRaises(ValueError):v.apply(g,v.make_plan(fixture()))
            self.assertNotIn('/git/refs',g.writes);self.assertNotIn('/pulls',g.writes)


if __name__ == '__main__':unittest.main()
