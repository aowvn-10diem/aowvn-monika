#!/usr/bin/env python3
"""Sinh catalog Play từ nguồn config duy nhất, bỏ dữ liệu tải ngoài và ủng hộ."""
import argparse
import copy
import json
from pathlib import Path


def for_play(source):
    cfg = copy.deepcopy(source)
    for key in list(cfg):
        if key.startswith('_'):
            cfg.pop(key)
    cfg['app'] = {}
    account = cfg.get('account', {})
    cfg['account'] = {k: v for k, v in account.items() if k in ('enabled', 'apiKey', 'databaseUrl', 'loginUrl')}
    cfg['downloadHosts'] = []
    cfg['externalApps'] = []
    for core in cfg.get('cores', {}).values():
        core['url'] = ''
        core['artifacts'] = {}
        core['systemFiles'] = None
    for module in cfg.get('modules', {}).values():
        module['url'] = ''
    for system in cfg.get('systems', []):
        system['allowExternalApp'] = False
        system.pop('externalApp', None)
    cfg['systems'] = [s for s in cfg.get('systems', []) if s.get('runner') != 'apk']
    # Web players tải mã từ ngoài cũng dành cho V151 thay bằng phân phối qua Play.
    cfg['webPlayers'] = {}
    return cfg


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(for_play(json.loads(args.source.read_text(encoding='utf-8'))), ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
    )
