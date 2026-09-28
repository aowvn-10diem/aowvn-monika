#!/usr/bin/env bash
# Up file lên Pixeldrain, in link tải.
# Dùng: PIXELDRAIN_API_KEY=... scripts/pixeldrain-upload.sh <file> [tên-trên-pixeldrain]
# Key lấy ở pixeldrain.com/user/api_keys. KHÔNG ghi key vào repo.
set -euo pipefail

file="${1:?Thiếu đường dẫn file}"
name="${2:-$(basename "$file")}"
key="${PIXELDRAIN_API_KEY:?Thiếu biến môi trường PIXELDRAIN_API_KEY}"
[ -f "$file" ] || { echo "Không thấy file: $file" >&2; exit 1; }

# Tên file có thể có ký tự đặc biệt → mã hóa URL
enc=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$name")
resp=$(curl -sS --fail-with-body -m 1800 -u ":$key" -T "$file" "https://pixeldrain.com/api/file/$enc") || {
  echo "Up lỗi: $resp" >&2; exit 1; }
id=$(printf '%s' "$resp" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])') || {
  echo "Up lỗi: $resp" >&2; exit 1; }

echo "https://pixeldrain.com/u/$id"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "APK: https://pixeldrain.com/u/$id" >> "$GITHUB_STEP_SUMMARY"
exit 0
