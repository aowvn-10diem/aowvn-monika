#!/usr/bin/env bash
# V104: fresh Test worker per run; fail immediately instead of retrying a red test.
#
# Không gọi thẳng :app:testDebugUnitTest: tác vụ đó chỉ gộp hai bản flavor
# (app/build.gradle.kts: dependsOn testGithub/testPlay) nên không có action test,
# không nhận --tests và không sinh test-results/testDebugUnitTest/.
# Test thật nằm ở bản github (src/test/java chỉ gắn vào sourceSets["testGithub"]).
set -euo pipefail

readonly PASSES=20
readonly TASK_CANDIDATES=(testGithubDebugUnitTest testDebugUnitTest)
readonly RESULTS_ROOT='app/build/test-results'
readonly OUTPUT_DIR='app/build/compose-stability'

pick_test_task() {
  local tasks task
  tasks=$(./gradlew -q :app:tasks --all --console=plain 2>/dev/null || true)
  for task in "${TASK_CANDIDATES[@]}"; do
    if grep -qE "^${task}([[:space:]]|$)" <<<"$tasks"; then
      printf '%s\n' "$task"
      return 0
    fi
  done
  echo "Không thấy task unit test trong :app (đã dò: ${TASK_CANDIDATES[*]})" >&2
  return 1
}

TEST_TASK=$(pick_test_task)
echo "V104: dùng :app:${TEST_TASK}; XML tự dò trong ${RESULTS_ROOT}/"

mkdir -p "$OUTPUT_DIR"
for pass in $(seq 1 "$PASSES"); do
  ./gradlew ":app:${TEST_TASK}" --console=plain --rerun \
    --tests 'vn.aow.monika.ui.LibraryStorageAccessTest.revisitDoesNotExplainAgainButExplicitButtonRequestsAndGrantRemovesCard' \
    --tests 'vn.aow.monika.ui.V69UxTest.chipFadeDisappearsAtTheEnd'
  python3 - "$pass" "$TEST_TASK" "$RESULTS_ROOT" "$OUTPUT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET

pass_no = int(sys.argv[1])
task = sys.argv[2]
results_root = pathlib.Path(sys.argv[3])
output = pathlib.Path(sys.argv[4])
expected = {
    ('vn.aow.monika.ui.LibraryStorageAccessTest', 'revisitDoesNotExplainAgainButExplicitButtonRequestsAndGrantRemovesCard'),
    ('vn.aow.monika.ui.V69UxTest', 'chipFadeDisappearsAtTheEnd'),
}


def collect(directory):
    """Ca mong đợi trong một thư mục kết quả; đỏ/skip/trùng là hỏng ngay."""
    found = {}
    for xml in sorted(directory.glob('TEST-*.xml')):
        for case in ET.parse(xml).getroot().iter('testcase'):
            key = (case.attrib['classname'], case.attrib['name'])
            if key not in expected:
                continue
            assert not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')), key
            assert key not in found, key
            found[key] = xml
    return found


# Task thật có thể là bản flavor, nên ưu tiên thư mục mang tên task rồi mới dò các thư mục khác.
directories = [d for d in sorted(results_root.glob('*')) if d.is_dir()]
preferred = results_root / task
ordered = ([preferred] if preferred.is_dir() else []) + [d for d in directories if d != preferred]
matches = [(d, found) for d, found in ((d, collect(d)) for d in ordered) if found]
assert matches, f'không thấy XML của 2 ca test trong {results_root}'
source, seen = matches[0]
assert seen.keys() == expected, (sorted(seen), expected)

for xml in sorted(set(seen.values())):
    (output / f'{pass_no:02d}-{xml.name}').write_bytes(xml.read_bytes())
with (output / 'results.txt').open('a') as log:
    log.write(f'{pass_no:02d}/20 PASS: 2 tests, 0 skipped (:app:{task})\n')
print(f'V104 {pass_no}/20 PASS: 2 tests, 0 skipped (:app:{task})')
PY
done

# Workflow tải lên app/build/test-results/testDebugUnitTest/ (đường dẫn của task gộp).
# Task thật không sinh thư mục đó, chép XML thô vào để artifact vẫn đầy đủ mà không phải sửa workflow.
if [ "$TEST_TASK" != 'testDebugUnitTest' ] && compgen -G "${RESULTS_ROOT}/${TEST_TASK}/TEST-*.xml" > /dev/null; then
  mkdir -p "${RESULTS_ROOT}/testDebugUnitTest"
  cp -a "${RESULTS_ROOT}/${TEST_TASK}"/TEST-*.xml "${RESULTS_ROOT}/testDebugUnitTest/"
fi
