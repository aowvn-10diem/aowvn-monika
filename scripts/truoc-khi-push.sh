#!/usr/bin/env bash
# Kiểm đủ hai flavor trước khi đẩy; giữ mã lỗi của bước không đạt.
set -euo pipefail

task_stage="chuẩn bị"
task_finish() {
    task_exit=$?
    trap - EXIT
    if [ "$task_exit" -eq 0 ]; then
        printf 'ĐẠT: test GitHub/Play, build debug/Play và quét APK Play.\n'
    else
        printf 'LỖI: %s (mã %s).\n' "$task_stage" "$task_exit" >&2
    fi
    exit "$task_exit"
}
trap task_finish EXIT

task_root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd -- "$task_root"

for task_stage in testDebugUnitTest assembleDebug assemblePlayDebug; do
    ./gradlew "$task_stage" --console=plain
done
task_stage="quét APK Play"
python3 -X utf8 scripts/check-play-apk.py \
    app/build/outputs/apk/play/debug/app-play-universal-debug.apk
