#!/usr/bin/env bash
# [A40] Một lượt PM tự động, đọc rẻ: in tối đa ~15 dòng, mỗi dòng bắt đầu bằng nhãn việc cần làm.
#   GOP     #N <sha>        PR đủ luật gộp (Đạt đúng head + CI bắt buộc xanh, không còn "Cần sửa")
#   PMXEM   #N               review cuối ghi "Cần PM xem"
#   TAU     cat|cho|len-on-dinh <chi tiết>   nhịp phát hành 2 ngày
#   LOI     <chữ ký> x<số> <bản>            chữ ký lỗi mới kể từ lượt trước
#   COVAN   #339 <link>                      Cố vấn gửi PM (trả lời hoặc CẢNH BÁO): đọc và xử lý
#   THU     #337 <link>                      DeepSeek báo PM: đọc và trả lời
#   SEP     <câu>                            chỉ khi chặn cả dự án
# Không in gì ngoài dòng "YEN" = lượt này không có việc.
# Trạng thái giữa các lượt: $STATE (mặc định ~/.cache/monika-pm/luot.json, không commit).
set -uo pipefail
R=aowvn-10diem/aowvn-monika
DIR=$(cd "$(dirname "$0")/../../../.." && pwd)
STATE=${STATE:-$HOME/.cache/monika-pm/luot.json}
mkdir -p "$(dirname "$STATE")"; [ -f "$STATE" ] || echo '{}' > "$STATE"
out=()

# 1. PR: kết luận mới nhất trên đúng head + CI bắt buộc.
for p in $(gh api "repos/$R/pulls?state=open&per_page=50" --jq '.[]|select(.user.login|test("dependabot")|not)|.number'); do
  read -r sha draft < <(gh api "repos/$R/pulls/$p" --jq '"\(.head.sha) \(.draft)"')
  [ "$draft" = "true" ] && continue
  ci=$(gh api "repos/$R/commits/$sha/check-runs?per_page=60" --jq '[.check_runs[]|select(.name|test("^(build|coverage|n04-tests|analyze)"))|.conclusion//"chay"]|unique|join(",")')
  # Kết luận Luna 3 / Luna Ultra trên đúng head (review hoặc comment có "Kết luận").
  verdicts=$( { gh api "repos/$R/pulls/$p/reviews?per_page=100" --jq ".[]|select(.commit_id==\"$sha\")|[.submitted_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; \
               gh api "repos/$R/issues/$p/comments?per_page=100" --jq ".[]|select(.body|test(\"${sha:0:7}\"))|[.created_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; } \
             | sort | grep -oE 'Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem)' | sed -E 's/.*(Đạt|Cần sửa|Cần PM xem)/\1/')
  last=$(echo "$verdicts" | tail -1)
  files=$(gh api "repos/$R/pulls/$p/files?per_page=100" --jq '.[].filename')
  need=1; echo "$files" | grep -qvE '^(docs/|.*\.md$)' && need=2   # app/CI cần Luna 3 + Luna Ultra
  dat=$(echo "$verdicts" | grep -c '^Đạt$')
  if [ "$last" = "Cần PM xem" ]; then out+=("PMXEM #$p"); continue; fi
  echo "$ci" | grep -qE 'failure|cancelled|chay' && continue
  [ "$last" = "Đạt" ] && [ "$dat" -ge "$need" ] && out+=("GOP #$p $sha")
done

# 2. Nhịp phát hành: ≥ 44 giờ từ bản gần nhất và main có commit mới → cắt bản.
read -r tag tagdate < <(gh api "repos/$R/releases?per_page=30" --jq '[.[]|select(.draft|not)|select(.tag_name|test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))][0]|"\(.tag_name) \(.published_at)"')
age_h=$(( ( $(date -u +%s) - $(date -u -d "$tagdate" +%s) ) / 3600 ))
ahead=$(gh api "repos/$R/compare/$tag...main" --jq '.ahead_by' 2>/dev/null || echo 0)
dl=$(gh api "repos/$R/releases/tags/$tag" --jq '[.assets[]|select(.name|endswith(".apk"))|.download_count]|add // 0')
pre=$(gh api "repos/$R/releases/tags/$tag" --jq '.prerelease')
if [ "$ahead" -gt 0 ] && [ "$age_h" -ge 44 ]; then out+=("TAU cat $tag+${ahead}commit tuoi=${age_h}h")
elif [ "$pre" = "true" ] && [ "$age_h" -ge 24 ] && [ "$dl" -ge 3 ]; then out+=("TAU len-on-dinh $tag tai=$dl tuoi=${age_h}h (kiểm LOI trước)")
fi

# 3. Chữ ký lỗi mới (so với lượt trước).
if [ -n "${CRASH_ADMIN_TOKEN:-}" ]; then
  seen=$(python3 -c "import json;print(json.load(open('$STATE')).get('lastReport',''))")
  newest=$("$DIR/scripts/crash-reports.sh" 2>/dev/null | sed -n '/Mới nhất/,$p' | grep -oE 'r:[0-9]+:[0-9a-f]+' | head -40)
  fresh=$(echo "$newest" | awk -v s="$seen" '$0==s{exit} {print}')
  if [ -n "$fresh" ]; then
    while IFS= read -r l; do out+=("$l"); done < <("$DIR/scripts/crash-reports.sh" 2>/dev/null | sed -n '/Mới nhất/,$p' | grep -F -f <(echo "$fresh") \
      | awk -F' \\| ' '{print $2" | "$3" | "$5}' | sort | uniq -c | sort -rn | head -5 | sed 's/^ */LOI x/')
    python3 - "$STATE" "$(echo "$newest" | head -1)" <<'EOF'
import json,sys; p,top=sys.argv[1:]; s=json.load(open(p)); s['lastReport']=top; json.dump(s,open(p,'w'))
EOF
  fi
fi

# 4. Hộp thư gửi PM: Cố vấn (#339, COVAN) và DeepSeek (#337, THU), comment mới kể từ lượt trước.
for box in "339 COVAN [Cố vấn" "337 THU [DeepSeek"; do
  read -r n lab pat <<<"$box"
  since=$(python3 -c "import json;print(json.load(open('$STATE')).get('mail$n','2026-10-24T00:00:00Z'))")
  rows=$(gh api "repos/$R/issues/$n/comments?since=$since&per_page=50" \
    --jq ".[]|select(.created_at>\"$since\")|select(.body|startswith(\"$pat\"))|\"\(.created_at) \(.html_url)\"")
  [ -z "$rows" ] && continue
  while IFS= read -r l; do out+=("$lab #$n ${l#* }"); done <<<"$rows"
  python3 - "$STATE" "mail$n" "$(echo "$rows" | tail -1 | cut -d' ' -f1)" <<'EOF'
import json,sys; p,k,v=sys.argv[1:]; s=json.load(open(p)); s[k]=v; json.dump(s,open(p,'w'))
EOF
done

[ ${#out[@]} -eq 0 ] && echo YEN || printf '%s\n' "${out[@]}"
