#!/usr/bin/env bash
# V123: bounded recovery from incomplete SDK downloads; no version/permission change.
set -euo pipefail
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
task_sdk_root=$(realpath -- "$ANDROID_HOME")
task_manager="$task_sdk_root/cmdline-tools/latest/bin/sdkmanager"
task_ndk_version=22.1.7171670
task_cmake_version=3.22.1
task_ndk="$task_sdk_root/ndk/$task_ndk_version"
task_cmake="$task_sdk_root/cmake/$task_cmake_version"
[[ -x "$task_manager" ]] || { echo '::error::sdkmanager unavailable'; exit 1; }
task_logs=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/monika-sdk-v123.XXXXXX")
trap 'rm -rf -- "$task_logs"' EXIT
revision_is() {
  [[ -f "$1/source.properties" ]] && awk -F= -v want="$2" '
    {gsub(/[[:space:]]/, "", $1); gsub(/[[:space:]]/, "", $2)}
    $1 == "Pkg.Revision" && $2 == want {found=1}
    END {exit !found}' "$1/source.properties"
}
ndk_ready() {
  revision_is "$task_ndk" "$task_ndk_version" &&
    [[ -f "$task_ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h" ]] &&
    [[ -x "$task_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] &&
    "$task_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version >/dev/null 2>&1
}
cmake_ready() {
  revision_is "$task_cmake" "$task_cmake_version" && [[ -x "$task_cmake/bin/cmake" ]] &&
    "$task_cmake/bin/cmake" --version >/dev/null 2>&1
}
for task_attempt in 1 2 3; do
  task_packages=()
  if ! ndk_ready; then
    # Remove only this invalid package, never other installed NDK versions or SDK temp caches.
    rm -rf -- "$task_ndk"
    task_packages+=("ndk;$task_ndk_version")
  fi
  if ! cmake_ready; then
    rm -rf -- "$task_cmake"
    task_packages+=("cmake;$task_cmake_version")
  fi
  if [[ ${#task_packages[@]} == 0 ]]; then
    echo "V123: NDK $task_ndk_version + CMake $task_cmake_version verified"
    exit 0
  fi
  echo "V123: SDK install attempt $task_attempt/3: ${task_packages[*]}"
  set +e
  yes | timeout --kill-after=10 300 "$task_manager" --install "${task_packages[@]}" > "$task_logs/$task_attempt.log" 2>&1
  task_status=${PIPESTATUS[1]} # yes normally receives SIGPIPE after successful sdkmanager.
  set -e
  if [[ "$task_status" == 0 ]] && ndk_ready && cmake_ready; then
    echo "V123: NDK $task_ndk_version + CMake $task_cmake_version verified (attempt $task_attempt/3)"
    exit 0
  fi
  echo "::warning::SDK installation incomplete (attempt $task_attempt/3, exit=$task_status)"
  tail -n 20 "$task_logs/$task_attempt.log"
  [[ "$task_status" != 0 ]] || task_status=1
  if [[ "$task_attempt" != 3 ]]; then sleep "$((task_attempt * 5))"; fi
done
echo '::error::NDK/CMake still incomplete after 3 attempts; Gradle must not run.'
exit "$task_status"
