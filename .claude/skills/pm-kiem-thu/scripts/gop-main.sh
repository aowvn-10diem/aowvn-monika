#!/usr/bin/env bash
# Gộp origin/main vào nhánh PM (mặc định docs/opus-tra-loi); DỪNG NGAY nếu có xung đột.
# Dùng: gop-main.sh [nhánh]   — không push; push sau khi đã ghi KE-HOACH/trạng thái.
set -euo pipefail
BR="${1:-docs/opus-tra-loi}"
git fetch origin main "$BR"
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "DỪNG: cây làm việc đang có thay đổi chưa commit." >&2; exit 3; }
git checkout "$BR" 2>/dev/null || git checkout -B "$BR" "origin/$BR"
git merge --ff-only "origin/$BR" >/dev/null 2>&1 || true   # lấy commit mới của nhánh PM nếu có
if git merge-base --is-ancestor origin/main HEAD; then echo "main đã nằm trong $BR: không có gì để gộp."; exit 0; fi
if git merge --no-edit origin/main; then
  echo "Đã gộp main vào $BR (chưa push)."; exit 0
fi
# Merge thất bại: liệt kê file xung đột TRƯỚC khi sửa hay commit bất cứ gì.
echo "DỪNG: xung đột ở các file:" >&2
git diff --name-only --diff-filter=U >&2
echo "Giải quyết rồi commit, hoặc hủy bằng: git merge --abort" >&2
exit 1
