#!/usr/bin/env bash
# V104: fresh Test worker per run; fail immediately instead of retrying a red test.
set -euo pipefail
mkdir -p app/build/compose-stability
for pass in $(seq 1 20); do
  ./gradlew :app:testDebugUnitTest --console=plain --rerun \
    --tests 'vn.aow.monika.ui.LibraryStorageAccessTest.revisitDoesNotExplainAgainButExplicitButtonRequestsAndGrantRemovesCard' \
    --tests 'vn.aow.monika.ui.V69UxTest.chipFadeDisappearsAtTheEnd'
  python3 - "$pass" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
source = pathlib.Path('app/build/test-results/testDebugUnitTest')
expected = {
    ('vn.aow.monika.ui.LibraryStorageAccessTest', 'revisitDoesNotExplainAgainButExplicitButtonRequestsAndGrantRemovesCard'),
    ('vn.aow.monika.ui.V69UxTest', 'chipFadeDisappearsAtTheEnd'),
}
seen = set()
for xml in source.glob('TEST-*.xml'):
    for case in ET.parse(xml).getroot().iter('testcase'):
        key = (case.attrib['classname'], case.attrib['name'])
        if key in expected:
            assert not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')), key
            assert key not in seen, key
            seen.add(key)
assert seen == expected, (seen, expected)
output = pathlib.Path('app/build/compose-stability')
for xml in source.glob('TEST-*.xml'):
    (output / f'{int(sys.argv[1]):02d}-{xml.name}').write_bytes(xml.read_bytes())
with (output / 'results.txt').open('a') as log:
    log.write(f'{int(sys.argv[1]):02d}/20 PASS: 2 tests, 0 skipped\n')
print(f'V104 {sys.argv[1]}/20 PASS: 2 tests, 0 skipped')
PY
done
