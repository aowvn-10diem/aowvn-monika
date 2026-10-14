"""V105: select ONE verified ARM64 physical catalog device; never fall back to another SoC."""
import argparse
import json
import re
from pathlib import Path

PROFILES = Path(__file__).resolve().parents[1] / '.github/testlab-devices.json'


def select(models, profile):
    def matches(model):
        if 'id' in profile:
            return model.get('id') == profile['id']
        # Consumer model name is more stable than Firebase's internal model ID.
        return (model.get('manufacturer', '').casefold() == profile['manufacturer'].casefold()
                and model.get('name', '').casefold() in {n.casefold() for n in profile['names']})

    candidates = [m for m in models if matches(m)]
    if len(candidates) != 1:
        raise ValueError('Requested hardware absent or ambiguous in live catalog; no substitute')
    model = candidates[0]
    if model.get('form') != 'PHYSICAL' or 'arm64-v8a' not in model.get('supportedAbis', []):
        raise ValueError('Requested device must be PHYSICAL / arm64-v8a')
    if not re.fullmatch(r'[A-Za-z0-9_-]+', model.get('id', '')):
        raise ValueError('Invalid catalog model ID')
    versions = [int(v) for v in model.get('supportedVersionIds', []) if str(v).isdecimal()]
    if 'api' in profile:
        version = profile['api']
        if version not in versions:
            raise ValueError('Pinned API unavailable')
    else:
        versions = [v for v in versions if v >= profile['minimumApi']]
        if not versions:
            raise ValueError('No supported API at/above minimum')
        version = max(versions)
    return {'model': model['id'], 'version': version, 'name': model.get('name', model['id']),
            'soc': profile.get('soc', '[CHƯA KIỂM]'), 'form': 'PHYSICAL', 'abi': 'arm64-v8a'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--github-output', type=Path, required=True)
    args = parser.parse_args()
    profiles = json.loads(PROFILES.read_text())
    if args.profile not in profiles:
        raise SystemExit('BLOCKED: unknown device profile')
    try:
        selected = select(json.loads(args.catalog.read_text()), profiles[args.profile])
    except ValueError as error:
        raise SystemExit('BLOCKED: ' + str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(selected, ensure_ascii=False) + '\n')
    with args.github_output.open('a') as output:
        output.write(f'device=model={selected["model"]},version={selected["version"]}\n')
    print(json.dumps(selected, ensure_ascii=False))


if __name__ == '__main__':
    main()
