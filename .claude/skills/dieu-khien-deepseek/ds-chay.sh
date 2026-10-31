#!/bin/bash
# Gọi DeepSeek (Tokenize) qua tools/ds/ds.py: tự dựng bản ds.py đã vá (streaming + --effort) ngoài repo,
# bật streaming, tự thử lại khi bị cắt kết nối, in một dòng tóm tắt.
#   ds-chay.sh <simple|hard> <none|low|high|max> <system.txt> <ra.md> "<chỉ thị>" [-- tệp ngữ cảnh ...]
# Mọi tệp tạm (system, đầu vào, đầu ra) đặt trong $DS_WORK (mặc định: thư mục scratchpad của phiên). KHÔNG đặt trong repo.
set -u
[ $# -ge 5 ] || { sed -n '2,6p' "$0"; exit 2; }
tier=$1; effort=$2; sys=$3; out=$4; prompt=$5; shift 5; [ "${1:-}" = "--" ] && shift
SKILL=$(cd "$(dirname "$0")" && pwd); REPO=$(git rev-parse --show-toplevel)
WORK=${DS_WORK:?đặt DS_WORK=<thư mục scratchpad của phiên>}; mkdir -p "$WORK/ds/tools/ds"; DS=$WORK/ds/tools/ds/ds.py
if [ ! -f "$DS" ]; then
  for ref in ${DS_REF:-origin/main origin/ccr-37c8378a-bgfkia}; do
    if git -C "$REPO" cat-file -e "$ref:tools/ds/ds.py" 2>/dev/null; then
      for f in ds.py ds-config.json; do git -C "$REPO" show "$ref:tools/ds/$f" > "$WORK/ds/tools/ds/$f"; done; break
    fi
  done
  [ -f "$DS" ] || { echo "không tìm thấy tools/ds/ds.py (git fetch origin main ccr-37c8378a-bgfkia)"; exit 3; }
fi
grep -q post_stream "$DS" || (cd "$WORK/ds" && patch -p1 -s < "$SKILL/ds-stream-effort.patch") || { echo "áp bản vá lỗi"; exit 3; }
[ -n "${TOKENIZE_API_KEY:-}${TOKENIZE_KEY_FILE:-}" ] || { echo "thiếu key: TOKENIZE_API_KEY chưa có trong môi trường phiên (báo sếp, đừng tự tìm key)"; exit 4; }
mt=${DS_MAX_TOKENS:-30000}  # DS nghĩ rất dài: trần 16.000 làm nhiều lượt rỗng
export DS_STREAM=1 DS_EXTRA_ROOTS="$REPO:$WORK"
for i in 1 2 3; do
  msg=$(python3 "$DS" run -t "$tier" -e "$effort" -s "$sys" ${1:+-i "$@"} --max-tokens "$mt" -o "$out" -p "$prompt" 2>&1) && { echo "$msg"; exit 0; }
  echo "$msg" >&2
  # chỉ thử lại lỗi mạng/quá tải; lỗi chặn tệp, thiếu key, HTTP 4xx là chắc chắn, không thử lại
  echo "$msg" | grep -q -E 'closed connection|reset by peer|timed out|HTTP (429|5[0-9][0-9])' || exit 1
  echo "thử lại $i" >&2; sleep $((i * 8))
done; exit 1
