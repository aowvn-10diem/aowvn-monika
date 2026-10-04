#!/usr/bin/env bash
# Mỗi PR mở in 1 dòng: số PR | nhánh | sha | CI theo head | Kết luận của Luna tiền duyệt.
# Dùng: .claude/skills/pm-kiem-thu/scripts/pr-tom-tat.sh   (REPO=owner/repo để đổi kho)
# Chỉ dùng `gh api repos/...` (search API bị proxy chặn). Cần gh + jq.
set -euo pipefail
REPO="${REPO:-aowvn-10diem/aowvn-monika}"

gh api "repos/$REPO/pulls?state=open&per_page=100" --paginate |
  jq -r -s 'add // [] | .[] | [.number, .head.ref, .head.sha] | @tsv' |
while IFS=$'\t' read -r n ref sha; do
  # CI theo head: đỏ nếu có job thất bại; đang-chạy nếu còn job chưa xong; xanh nếu tất cả xong không lỗi.
  ci=$(gh api "repos/$REPO/commits/$sha/check-runs?per_page=100" --jq '
    .check_runs | if length == 0 then "chưa-có"
    elif any(.status != "completed") then "đang-chạy(\(length))"
    elif any(.conclusion | IN("failure","cancelled","timed_out","action_required","startup_failure")) then "đỏ(\(length))"
    else "xanh(\(length))" end') || ci="lỗi-gọi-API"

  # Comment tiền duyệt cuối cùng của Luna: dòng đầu "Luna tiền duyệt (commit <sha>)", có dòng "Kết luận: ...".
  body=$(gh api "repos/$REPO/issues/$n/comments?per_page=100" --paginate |
    jq -r -s 'add // [] | map(select(.body | test("^[#*\\s]*Luna tiền duyệt"))) | last | .body // ""')
  if [ -z "$body" ]; then
    luna="Luna: chưa tiền duyệt"
  else
    lsha=$(printf '%s' "$body" | grep -oE -m1 'commit [0-9a-f]{7,40}' | head -1 | awk '{print $2}' || true)
    kl=$(printf '%s' "$body" | grep -m1 'Kết luận:' | sed -E 's/^.*Kết luận:[[:space:]]*//' | cut -c1-160 || true)
    stale=""
    if [ -n "$lsha" ] && [[ "$sha" != "$lsha"* ]]; then stale=" CŨ"; fi
    luna="Luna(${lsha:-?}$stale): ${kl:-<không có dòng Kết luận>}"
  fi
  printf '#%s | %s | %s | CI %s | %s\n' "$n" "$ref" "${sha:0:7}" "$ci" "$luna"
done
