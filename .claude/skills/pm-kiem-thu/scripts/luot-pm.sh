#!/usr/bin/env bash
# [A40 / E1] Một lượt PM tự động, đọc rẻ (≤15 dòng), cập nhật cho cơ chế 3 phiên OpenCode + Argon từ 18/11/2026:
#   GOP-SAFE    #N <sha>        PR đủ 2 lượt Đạt đúng head + CI xanh + không chạm tệp nhạy cảm (--apply-safe tự gộp, tốn 0 token model)
#   GOP-NHAYCAM #N <sha>        Như GOP-SAFE nhưng chạm workflow/gradle/proguard/CODEOWNERS → cần PM/sếp duyệt cuối
#   XUNGDOT     #N <sha>        Đủ Đạt + CI xanh nhưng mergeable_state=dirty → nhắc người làm gộp main
#   NHAP        #N <sha>        Đủ Đạt + CI xanh nhưng còn draft
#   PMXEM       #N              Review cuối ghi "Cần PM xem" hoặc base != main
#   CHO         #N <sha7> thieu-Dat <ai>   CI xanh, có 1 Đạt nhưng thiếu lượt duyệt còn lại > 24 giờ
#   TAU         cat|len-on-dinh <chi tiết> Nhịp phát hành 2 ngày
#   LOI         <chữ ký> x<số> <bản>       Chữ ký lỗi mới kể từ lượt trước
#   THU         #337 <link> | <tóm tắt>    Tin mới từ Trưởng nhóm OpenCode / Argon gửi PM (đã lọc bỏ nhịp tim rỗng)
#   SEP         noibo#20 <link> <trích>    Comment mới của sếp ở bảng Việc cần sếp làm
#   DUNG        <đội> tu=<giờ UTC> <n>h    Đội OpenCode (Thi công / Duyệt / Lỗi) im ≥ 2 giờ trong 7–23 giờ VN
# Không in gì ngoài dòng "YEN" = lượt này không có việc (model PM dừng ngay, 0 tool call).
set -uo pipefail
APPLY_SAFE=false
[ "${1:-}" = "--apply-safe" ] && APPLY_SAFE=true

R=aowvn-10diem/aowvn-monika
DIR=$(cd "$(dirname "$0")/../../../.." && pwd)
STATE=${STATE:-$HOME/.cache/monika-pm/luot.json}
GIU_FILE="$(dirname "$0")/../giu.txt"
mkdir -p "$(dirname "$STATE")"; [ -f "$STATE" ] || echo '{}' > "$STATE"
out=()

SENSITIVE_RE='^(\.github/workflows/|\.github/CODEOWNERS|build\.gradle|app/build\.gradle|gradle/libs\.versions\.toml|gradle/wrapper/|app/proguard-rules\.pro|scripts/sign-config\.py)'

# 1. PR: kết luận mới nhất trên đúng head + CI bắt buộc (build, coverage, n04-tests, analyze).
for p in $(gh api "repos/$R/pulls?state=open&per_page=50" --jq '.[]|select(.user.login|test("dependabot")|not)|.number'); do
  read -r sha draft mst base href < <(gh api "repos/$R/pulls/$p" --jq '"\(.head.sha) \(.draft) \(.mergeable_state) \(.base.ref) \(.head.ref)"')
  [ "$base" != "main" ] && { out+=("PMXEM #$p base=$base (không phải main: KHÔNG gộp)"); continue; }
  grep -qw "$p" "$GIU_FILE" 2>/dev/null && continue
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || continue
  ci=$(gh api "repos/$R/commits/$sha/check-runs?per_page=60" --jq '[.check_runs[]|select(.name|test("^(build|coverage|n04-tests|analyze)"))|.conclusion//"chay"]|unique|join(",")')
  raw=$( { gh api "repos/$R/pulls/$p/reviews?per_page=100" --jq ".[]|select(.commit_id==\"$sha\")|[.submitted_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; \
           gh api "repos/$R/issues/$p/comments?per_page=100" --jq ".[]|select(.body|test(\"${sha:0:7}\"))|[.created_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; } | sort)
  verdicts=$(echo "$raw" | grep -oE 'Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem)' | sed -E 's/.*(Đạt|Cần sửa|Cần PM xem)/\1/')
  last=$(echo "$verdicts" | tail -1)
  # Chuẩn hóa định danh người duyệt trên đúng head ([A104]: Argon, Hydro, PM + tương thích ngược DeepSeek L1/L2, Luna).
  REVIEWER_RE='^ *(\[Duyệt lượt hai DeepSeek[^]]*\]|\[Duyệt DeepSeek[^]]*\]|\[Argon duyệt[^]]*\]|\[Hydro duyệt[^]]*\]|\[PM duyệt[^]]*\]|Luna [0-9A-Za-z]+)'
  conlai=$(echo "$raw" | grep -E 'Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem)' | cut -f2- \
    | sed -E "s/${REVIEWER_RE}.*Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem).*/\1\t\2/;t;s/^ *(\[[^]]+\]|.{0,24}).*Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem).*/\1\t\2/" \
    | awk -F'\t' '{v[$1]=$2} END{for(k in v) if(v[k]!="Đạt") print k"="v[k]}' | paste -sd, -)
  [ -n "$conlai" ] && [ "$last" = "Đạt" ] && last="Cần sửa"
  files=$(gh api "repos/$R/pulls/$p/files?per_page=100" --jq '.[].filename')
  need=1; echo "$files" | grep -qvE '^(docs/|.*\.md$)' && need=2
  # [A104] Không tự duyệt PR của chính mình (nhánh argon/* bỏ phiếu Argon, nhánh hydro/* bỏ phiếu Hydro).
  self_re='^$'
  [[ "$href" == argon/* ]] && self_re='^\[Argon duyệt'
  [[ "$href" == hydro/* ]] && self_re='^\[Hydro duyệt'
  dat=$(echo "$raw" | grep -E 'Kết luận:?\**:? *Đạt' | cut -f2- \
    | grep -oE "$REVIEWER_RE" \
    | sed -E 's/^ +//; s/ +$//' | grep -vE "$self_re" | sort -u | grep -c .)
  if [ "$last" = "Cần PM xem" ]; then out+=("PMXEM #$p"); continue; fi
  echo "$ci" | grep -qE 'failure|cancelled|chay' && continue
  if [ "$last" = "Đạt" ] && [ "$dat" -ge "$need" ]; then
    [ "$mst" = "dirty" ] && { out+=("XUNGDOT #$p $sha"); continue; }
    [ "$draft" = "true" ] && { out+=("NHAP #$p $sha"); continue; }
    if echo "$files" | grep -qE "$SENSITIVE_RE"; then
      out+=("GOP-NHAYCAM #$p $sha")
    else
      if [ "$APPLY_SAFE" = "true" ] && [ "$mst" = "clean" ]; then
        if gh api -X PUT "repos/$R/pulls/$p/merge" -f merge_method=merge -f sha="$sha" >/dev/null 2>&1; then
          out+=("DA-GOP-SAFE #$p ${sha:0:7}")
        else
          out+=("GOP-SAFE #$p $sha")
        fi
      else
        out+=("GOP-SAFE #$p $sha")
      fi
    fi
    continue
  fi
  # Kẹt im lặng > 24h: nhắc đúng lượt duyệt còn thiếu.
  if [ "$last" = "Đạt" ] && [ "$dat" -lt "$need" ]; then
    hage=$(( ( $(date -u +%s) - $(date -u -d "$(gh api "repos/$R/commits/$sha" --jq .commit.committer.date)" +%s) ) / 3600 ))
    if [ "$hage" -ge 24 ]; then
      dat_by=$(echo "$raw" | grep -E 'Kết luận:?\**:? *Đạt')
      miss=""
      echo "$dat_by" | grep -qE '\[Duyệt DeepSeek|\[Hydro duyệt' || miss="Duyet_L1"
      echo "$dat_by" | grep -qE '\[Duyệt lượt hai DeepSeek|\[Argon duyệt|\[PM duyệt' || miss="${miss:+$miss+}Duyet_L2"
      out+=("CHO #$p ${sha:0:7} thieu-Dat ${miss:-Duyet_L2} tuoi=${hage}h")
    fi
  fi
done

# 2. Nhịp phát hành: ≥ 44 giờ từ bản gần nhất và main có commit mã app mới → cắt bản.
read -r tag tagdate < <(gh api "repos/$R/releases?per_page=30" --jq '[.[]|select(.draft|not)|select(.tag_name|test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))][0]|"\(.tag_name) \(.published_at)"')
age_h=$(( ( $(date -u +%s) - $(date -u -d "$tagdate" +%s) ) / 3600 ))
ahead=$(gh api "repos/$R/compare/$tag...main" --jq '.ahead_by' 2>/dev/null || echo 0)
dl=$(gh api "repos/$R/releases/tags/$tag" --jq '[.assets[]|select(.name|endswith(".apk"))|.download_count]|add // 0')
pre=$(gh api "repos/$R/releases/tags/$tag" --jq '.prerelease')
appchg=$(gh api "repos/$R/compare/$tag...main" --jq '[.files[]?.filename|select(test("^(app/src/main/|app/build\\.gradle|j2me/|dexlib/|reader/|libretrodroid/|gradle/libs)"))]|length' 2>/dev/null || echo 0)
if [ "$ahead" -gt 0 ] && [ "$age_h" -ge 44 ] && [ "${appchg:-0}" -gt 0 ]; then out+=("TAU cat $tag+${ahead}commit app=${appchg}tep tuoi=${age_h}h")
elif [ "$pre" = "true" ] && [ "$age_h" -ge 24 ] && [ "$dl" -ge 3 ]; then out+=("TAU len-on-dinh $tag tai=$dl tuoi=${age_h}h (dùng promote-release.yml V209)")
fi

# 3. Hộp thư #337 (Sonnet / Argon / Hydro / OpenCode gửi PM):
#    Lọc bỏ nhịp tim thuần túy ("nhịp:" không kèm CẦN PM / CHẶN / Xong / Đề xuất) và in kèm 120 ký tự đầu để PM không phải gọi API đọc lại toàn bộ comment.
since337=$(python3 -c "import json,time;print(json.load(open('$STATE')).get('mail337',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-2*3600))))")
rows337=""
for pg in 1 2 3 4 5; do
  page=$(gh api "repos/$R/issues/337/comments?since=$since337&per_page=100&page=$pg" \
    --jq ".[]|select(.created_at>\"$since337\")|select(.body|test(\"^\\\\[(Trưởng nhóm |Argon|Hydro|Sonnet|DeepSeek)[^\\\\]]*→ *PM\\\\]\"))|select((.body|test(\"nhịp:\") and ((.body|test(\"CẦN PM|CHẶN|Xong|Đề xuất|DỪNG\";\"i\"))|not))|not)|\"\(.created_at) \(.html_url) | \(.body|gsub(\"[\\r\\n]+\";\" \")|.[0:120])\"" 2>/dev/null)
  rows337+="${page:+$page$'\n'}"
  [ "$(gh api "repos/$R/issues/337/comments?since=$since337&per_page=100&page=$pg" --jq length 2>/dev/null || echo 0)" -lt 100 ] && break
done
rows337=$(printf '%s' "$rows337" | sed '/^$/d')
if [ -n "$rows337" ]; then
  while IFS= read -r l; do out+=("THU #337 ${l#* }"); done <<<"$rows337"
  python3 - "$STATE" "mail337" "$(echo "$rows337" | tail -1 | cut -d' ' -f1)" <<'PY'
import json,sys; p,k,v=sys.argv[1:]; s=json.load(open(p)); s[k]=v; json.dump(s,open(p,'w'))
PY
fi

# 4. Bảng "Việc cần sếp làm" (noibo#20): comment mới của sếp → SEP.
NB=aowvn-10diem/aowvn-monika-noibo
since20=$(python3 -c "import json,time;print(json.load(open('$STATE')).get('sep20',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-6*3600))))")
rows20=$(gh api "repos/$NB/issues/20/comments?since=$since20&per_page=100" \
  --jq ".[]|select(.created_at>\"$since20\")|select((.body|startswith(\"[\"))|not)|select(.body|test(\"Generated by\")|not)|\"\(.created_at) \(.html_url) \(.body|gsub(\"\\n\";\" \")|.[0:100])\"" 2>/dev/null)
if [ -n "$rows20" ]; then
  while IFS= read -r l; do out+=("SEP noibo#20 ${l#* }"); done <<<"$rows20"
  python3 - "$STATE" sep20 "$(echo "$rows20" | tail -1 | cut -d' ' -f1)" <<'PY'
import json,sys; p,k,v=sys.argv[1:]; s=json.load(open(p)); s[k]=v; json.dump(s,open(p,'w'))
PY
fi

# 5. Canh 3 phiên OpenCode (Thi công, Duyệt, Lỗi) trong 7–23 giờ VN: im ≥ 2 giờ → DUNG.
h_vn=$(TZ=Asia/Ho_Chi_Minh date +%-H)
if [ "$h_vn" -ge 7 ] && [ "$h_vn" -le 23 ]; then
  act=$(gh api "repos/$R/activity?per_page=100" --jq '.[]|"\(.ref) \(.timestamp)"' 2>/dev/null)
  since24=$(date -u -d '-24 hours' +%FT%TZ)
  cmt337=$(gh api "repos/$R/issues/337/comments?since=$since24&per_page=100" 2>/dev/null)
  for doi in "DeepSeek|deepseek[0-9]*/|\\[(Trưởng nhóm )?DeepSeek" "Duyệt DeepSeek|duyet/|\\[(Trưởng nhóm )?Duyệt( lượt hai)? DeepSeek" "Nhóm Lỗi|loi/|\\[(Trưởng nhóm )?Lỗi"; do
    IFS='|' read -r ten nhanh tien <<<"$doi"
    t1=$(echo "$act" | grep -E "^refs/heads/$nhanh" | awk '{print $2}' | sort | tail -1)
    tien_jq=${tien//\\/\\\\}
    t2=$(echo "$cmt337" | jq -r ".[]|select(.body|test(\"^$tien_jq\"))|.updated_at" 2>/dev/null | sort | tail -1)
    last=$(printf '%s\n%s\n' "$t1" "$t2" | sort | tail -1)
    [ -z "$last" ] && continue
    tu_s=$(date -u -d "$last" +%s); mo_s=$(TZ=Asia/Ho_Chi_Minh date -d 'today 07:00' +%s)
    [ "$tu_s" -lt "$mo_s" ] && tu_s=$mo_s
    gio=$(( ( $(date -u +%s) - tu_s ) / 3600 ))
    [ "$gio" -ge 2 ] && out+=("DUNG $ten tu=$last ${gio}h")
  done
fi

# 6. Chống lặp 12 giờ.
if [ ${#out[@]} -gt 0 ]; then
  sig=$(printf '%s\n' "${out[@]}" | sed -E 's/tuoi=[0-9]+h//; s/ [0-9]+h$//' | sort | sha1sum | cut -c1-12)
  if python3 - "$STATE" "$sig" <<'PY'
import json,sys,time; p,sig=sys.argv[1:]; s=json.load(open(p)); now=int(time.time())
same = s.get('lastSig')==sig and now-s.get('lastSigAt',0) < 12*3600
if not same: s['lastSig']=sig; s['lastSigAt']=now; json.dump(s,open(p,'w'))
sys.exit(0 if same else 1)
PY
  then out=(); fi
fi

[ ${#out[@]} -eq 0 ] && echo YEN || printf '%s\n' "${out[@]}"
