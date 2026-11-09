#!/usr/bin/env bash
# [A40] Một lượt PM tự động, đọc rẻ: in tối đa ~15 dòng, mỗi dòng bắt đầu bằng nhãn việc cần làm.
#   GOP     #N <sha>        PR đủ luật gộp (Đạt đúng head + CI bắt buộc xanh, không còn "Cần sửa")
#   XUNGDOT #N <sha>        như GOP nhưng xung đột với main: không gộp, nhắc người làm gộp main (merge, không rebase) nếu chưa nhắc
#   NHAP    #N <sha>        như GOP nhưng PR còn draft (agent mặc định mở draft): đọc mô tả, không còn câu chờ sếp thì ready_for_review rồi gộp đúng sha
#   PMXEM   #N               review cuối ghi "Cần PM xem"
#   CHO     #N <sha7> thieu-Dat <ai>   CI xanh, có Đạt nhưng thiếu người duyệt, head > 24 giờ: nhắc người đó ở #119
#   TAU     cat|cho|len-on-dinh <chi tiết>   nhịp phát hành 2 ngày
#   LOI     <chữ ký> x<số> <bản>            chữ ký lỗi mới kể từ lượt trước
#   COVAN   #339 <link>                      Cố vấn gửi PM (trả lời hoặc CẢNH BÁO): đọc và xử lý
#   THU     #337 <link>                      DeepSeek báo PM: đọc và trả lời
#   SEP     <câu>                            chỉ khi chặn cả dự án
#   DUNG    <đội> tu=<giờ UTC> <số giờ>h        đội thi công im ≥ 2 giờ (7–23 giờ VN): báo sếp ngay qua [PM → sếp] + thread
# Không in gì ngoài dòng "YEN" = lượt này không có việc (kể cả khi kết quả y hệt lượt trước, chưa quá 12 giờ).
# Trạng thái giữa các lượt: $STATE (mặc định ~/.cache/monika-pm/luot.json, không commit).
set -uo pipefail
R=aowvn-10diem/aowvn-monika
DIR=$(cd "$(dirname "$0")/../../../.." && pwd)
STATE=${STATE:-$HOME/.cache/monika-pm/luot.json}
mkdir -p "$(dirname "$STATE")"; [ -f "$STATE" ] || echo '{}' > "$STATE"
out=()

# 1. PR: kết luận mới nhất trên đúng head + CI bắt buộc.
for p in $(gh api "repos/$R/pulls?state=open&per_page=50" --jq '.[]|select(.user.login|test("dependabot")|not)|.number'); do
  read -r sha draft mst base < <(gh api "repos/$R/pulls/$p" --jq '"\(.head.sha) \(.draft) \(.mergeable_state) \(.base.ref)"')
  [ "$base" != "main" ] && { echo "PMXEM #$p base=$base (không phải main: KHÔNG gộp, đổi base hoặc đợi PR nền gộp)"; continue; }
  grep -qw "$p" "$(dirname "$0")/../giu.txt" 2>/dev/null && continue   # PR PM tạm giữ (vd. ngoài A85): không báo
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || continue   # API lỗi/hạn mức: bỏ qua, không bao giờ in GOP thiếu sha
  ci=$(gh api "repos/$R/commits/$sha/check-runs?per_page=60" --jq '[.check_runs[]|select(.name|test("^(build|coverage|n04-tests|analyze)"))|.conclusion//"chay"]|unique|join(",")')
  # Kết luận Luna 3 / Luna Ultra trên đúng head (review hoặc comment có "Kết luận").
  raw=$( { gh api "repos/$R/pulls/$p/reviews?per_page=100" --jq ".[]|select(.commit_id==\"$sha\")|[.submitted_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; \
           gh api "repos/$R/issues/$p/comments?per_page=100" --jq ".[]|select(.body|test(\"${sha:0:7}\"))|[.created_at,(.body|gsub(\"\\n\";\" \"))]|@tsv"; } | sort)
  verdicts=$(echo "$raw" | grep -oE 'Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem)' | sed -E 's/.*(Đạt|Cần sửa|Cần PM xem)/\1/')
  last=$(echo "$verdicts" | tail -1)
  # Kết luận mới nhất của TỪNG người duyệt (04/11): còn ai Cần sửa trên đúng head thì không gộp dù người sau Đạt.
  conlai=$(echo "$raw" | grep -E 'Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem)' | cut -f2- | sed -E 's/^ *(\[Duyệt DeepSeek|Luna [0-9A-Za-z]+|\[[^]]+\]|.{0,24}).*Kết luận:?\**:? *(Đạt|Cần sửa|Cần PM xem).*/\1\t\2/' | awk -F'\t' '{v[$1]=$2} END{for(k in v) if(v[k]!="Đạt") print k"="v[k]}' | paste -sd, -)
  [ -n "$conlai" ] && [ "$last" = "Đạt" ] && last="Cần sửa"
  files=$(gh api "repos/$R/pulls/$p/files?per_page=100" --jq '.[].filename')
  need=1; echo "$files" | grep -qvE '^(docs/|.*\.md$)' && need=2   # app/CI cần Luna 3 + Luna Ultra
  # Đếm Đạt theo NGƯỜI duyệt (Luna 3/4/5/Ultra, Duyệt DeepSeek…): một người đăng 2 lần trên cùng head chỉ tính 1.
  dat=$(echo "$raw" | grep -E 'Kết luận:?\**:? *Đạt' | cut -f2- | grep -oE '^ *(\[Duyệt DeepSeek|Luna [0-9A-Za-z]+|\[[^]]+\]|.{0,24})' | sed -E 's/ +$//' | sort -u | grep -c .)
  # Đội Duyệt DeepSeek (PM chốt 01/11): tính tối đa 1 Đạt, PR mã vẫn cần ≥ 1 Đạt của Luna.
  ds=$(echo "$raw" | grep -E '\[Duyệt DeepSeek' | grep -cE 'Kết luận:?\**:? *Đạt')
  [ "$ds" -gt 1 ] && ds=1   # dat đã đếm theo người, Duyệt DeepSeek chỉ là 1 người
  [ "$need" -eq 2 ] && [ $(( dat - (ds > 0 ? 1 : 0) )) -lt 1 ] && dat=1
  if [ "$last" = "Cần PM xem" ]; then out+=("PMXEM #$p"); continue; fi
  echo "$ci" | grep -qE 'failure|cancelled|chay' && continue
  [ "$last" = "Đạt" ] && [ "$dat" -ge "$need" ] && [ "$mst" = "dirty" ] && { out+=("XUNGDOT #$p $sha"); continue; }
  [ "$last" = "Đạt" ] && [ "$dat" -ge "$need" ] && { [ "$draft" = "true" ] && out+=("NHAP #$p $sha") || out+=("GOP #$p $sha"); continue; }
  # Kẹt im lặng: CI xanh, có Đạt nhưng chưa đủ người duyệt, head đã quá 24 giờ → nhắc đúng người còn thiếu.
  if [ "$last" = "Đạt" ] && [ "$dat" -lt "$need" ]; then
    hage=$(( ( $(date -u +%s) - $(date -u -d "$(gh api "repos/$R/commits/$sha" --jq .commit.committer.date)" +%s) ) / 3600 ))
    if [ "$hage" -ge 24 ]; then
      dat_by=$(echo "$raw" | grep -E 'Kết luận:?\**:? *Đạt')
      miss=""; echo "$dat_by" | grep -q 'Luna 3' || miss="Luna 3"; echo "$dat_by" | grep -q 'Luna Ultra' || miss="${miss:+$miss+}Luna Ultra"
      out+=("CHO #$p ${sha:0:7} thieu-Dat ${miss// /_} tuoi=${hage}h")
    fi
  fi
done

# 2. Nhịp phát hành: ≥ 44 giờ từ bản gần nhất và main có commit mới → cắt bản.
read -r tag tagdate < <(gh api "repos/$R/releases?per_page=30" --jq '[.[]|select(.draft|not)|select(.tag_name|test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))][0]|"\(.tag_name) \(.published_at)"')
age_h=$(( ( $(date -u +%s) - $(date -u -d "$tagdate" +%s) ) / 3600 ))
ahead=$(gh api "repos/$R/compare/$tag...main" --jq '.ahead_by' 2>/dev/null || echo 0)
dl=$(gh api "repos/$R/releases/tags/$tag" --jq '[.assets[]|select(.name|endswith(".apk"))|.download_count]|add // 0')
pre=$(gh api "repos/$R/releases/tags/$tag" --jq '.prerelease')
# Chỉ cắt khi có đổi mã chạy trong APK (không tính test, docs, workflow, config: config đi qua Cloudflare).
appchg=$(gh api "repos/$R/compare/$tag...main" --jq '[.files[]?.filename|select(test("^(app/src/main/|app/build\\.gradle|j2me/|dexlib/|gradle/libs)"))]|length' 2>/dev/null || echo 0)
if [ "$ahead" -gt 0 ] && [ "$age_h" -ge 44 ] && [ "${appchg:-0}" -gt 0 ]; then out+=("TAU cat $tag+${ahead}commit app=${appchg}tep tuoi=${age_h}h")
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

# 4. Hộp thư gửi PM, comment mới kể từ lượt trước (regex jq trên đầu comment):
#    Cố vấn #339 (COVAN), DeepSeek #337 (THU), Nova #90, Haiku #94, đội ChatGPT #119 (HOP).
#    #119: mọi tin [Sol… → PM] (Sol, Sol 2, Sol High); tin Luna chỉ khi có "Cần PM". Sếp duyệt 27/10.
for box in "339 COVAN ^\\\\[Cố vấn" "337 THU ^\\\\[DeepSeek" "90 HOP ^\\\\[Nova" "94 HOP ^\\\\[Haiku" \
           "119 HOP ^\\\\[Sol[^\\\\]]*→ *PM|^\\\\[Luna[^\\\\]]*→ *PM\\\\].*Cần PM"; do
  read -r n lab pat <<<"$box"
  # Hộp thư chưa có mốc thì bắt đầu từ 6 giờ trước (tránh dội lại tin cũ).
  since=$(python3 -c "import json,time;print(json.load(open('$STATE')).get('mail$n',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-6*3600))))")
  # Phân trang tay (gh --paginate hỏng qua proxy): lặp page tới khi trang < 100.
  rows=""; for pg in 1 2 3 4 5; do
    page=$(gh api "repos/$R/issues/$n/comments?since=$since&per_page=100&page=$pg" \
      --jq ".[]|select(.created_at>\"$since\")|select(.body|gsub(\"\\n\";\" \")|test(\"$pat\"))|\"\(.created_at) \(.html_url)\"")
    rows+="${page:+$page$'\n'}"
    [ "$(gh api "repos/$R/issues/$n/comments?since=$since&per_page=100&page=$pg" --jq length)" -lt 100 ] && break
  done
  rows=$(printf '%s' "$rows" | sed '/^$/d')
  [ -z "$rows" ] && continue
  while IFS= read -r l; do out+=("$lab #$n ${l#* }"); done <<<"$rows"
  python3 - "$STATE" "mail$n" "$(echo "$rows" | tail -1 | cut -d' ' -f1)" <<'EOF'
import json,sys; p,k,v=sys.argv[1:]; s=json.load(open(p)); s[k]=v; json.dump(s,open(p,'w'))
EOF
done

# 4a. Bảng "Việc cần sếp làm" (noibo#20, sếp yêu cầu 09/11): comment mới của sếp → SEP.
#     Agent cũng đăng bằng tài khoản aowvn-10diem nên lọc: bỏ comment mở đầu "[" hoặc có chân "Generated by".
NB=aowvn-10diem/aowvn-monika-noibo
since=$(python3 -c "import json,time;print(json.load(open('$STATE')).get('sep20',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-6*3600))))")
rows=$(gh api "repos/$NB/issues/20/comments?since=$since&per_page=100" \
  --jq ".[]|select(.created_at>\"$since\")|select((.body|startswith(\"[\"))|not)|select(.body|test(\"Generated by\")|not)|\"\(.created_at) \(.html_url) \(.body|gsub(\"\\n\";\" \")|.[0:80])\"" 2>/dev/null)
if [ -n "$rows" ]; then
  while IFS= read -r l; do out+=("SEP noibo#20 ${l#* }"); done <<<"$rows"
  python3 - "$STATE" sep20 "$(echo "$rows" | tail -1 | cut -d' ' -f1)" <<'EOF'
import json,sys; p,k,v=sys.argv[1:]; s=json.load(open(p)); s[k]=v; json.dump(s,open(p,'w'))
EOF
fi

# 4b. Canh đội thi công (sếp 01/11): trong 7–23 giờ VN, đội im (không push nhánh, không comment) ≥ 2 giờ → DUNG.
#     1 lượt gọi /activity + 1 lượt đọc comment mỗi hộp thư; nhãn giữ nguyên "tu=" nên chống lặp 12 giờ vẫn chặn báo trùng.
h_vn=$(TZ=Asia/Ho_Chi_Minh date +%-H)
TANG_CUONG_DEN="2026-11-02T00:00:00Z"   # đêm tăng cường (sếp 01/11): canh cả 0–7 giờ VN tới mốc này
if { [ "$h_vn" -ge 7 ] && [ "$h_vn" -le 23 ]; } || [ "$(date -u +%s)" -lt "$(date -u -d "$TANG_CUONG_DEN" +%s)" ]; then
  act=$(gh api "repos/$R/activity?per_page=100" --jq '.[]|"\(.ref) \(.timestamp)"' 2>/dev/null)
  since=$(date -u -d '-24 hours' +%FT%TZ)
  # Đọc comment toàn repo 1 lượt (mới nhất trước): đội có thể báo ở hộp thư khác hoặc trên PR (Sol 2 báo #337, DeepSeek báo trên PR).
  cmt=$(gh api "repos/$R/issues/comments?since=$since&per_page=100&sort=updated&direction=desc" 2>/dev/null)
  for doi in "DeepSeek|deepseek[0-9]*/|337|\\[(Trưởng nhóm )?DeepSeek" "Duyệt DeepSeek|duyet/|337|\\[(Trưởng nhóm )?Duyệt DeepSeek" "Nhóm Lỗi|loi/|337|\\[Trưởng nhóm Lỗi" "Nhóm Phụ thuộc|phuthuoc/|337|\\[Trưởng nhóm Phụ thuộc"; do
    IFS='|' read -r ten nhanh hop tien <<<"$doi"
    t1=$(echo "$act" | grep -E "^refs/heads/$nhanh" | awk '{print $2}' | sort | tail -1)
    tien=${tien//\\/\\\\}   # jq cần "\\[" (một "\[" là escape sai, lỗi bị nuốt nên trước đây chỉ đọc được lần push)
    t2=$(echo "$cmt" | jq -r ".[]|select(.body|test(\"^$tien\"))|.updated_at" 2>/dev/null | sort | tail -1)
    # 100 comment mới nhất không có tin của đội → đọc riêng hộp thư (tin cũ hơn vẫn phải tính để báo DUNG).
    [ -z "$t2" ] && t2=$(gh api "repos/$R/issues/$hop/comments?since=$since&per_page=100" --jq ".[]|select(.body|test(\"^$tien\"))|.updated_at" 2>/dev/null | sort | tail -1)
    last=$(printf '%s\n%s\n' "$t1" "$t2" | sort | tail -1)
    [ -z "$last" ] && continue   # đội chưa hoạt động trong 24 giờ (chưa khởi động): không báo
    # Đêm 0–7 giờ VN đội nghỉ là bình thường: tính giờ im từ 07:00 VN hôm nay nếu tin cuối cũ hơn.
    tu_s=$(date -u -d "$last" +%s); mo_s=$(TZ=Asia/Ho_Chi_Minh date -d 'today 07:00' +%s)
    [ "$h_vn" -ge 7 ] && [ "$tu_s" -lt "$mo_s" ] && tu_s=$mo_s
    gio=$(( ( $(date -u +%s) - tu_s ) / 3600 ))
    [ "$gio" -ge 2 ] && out+=("DUNG $ten tu=$last ${gio}h")
  done
fi

# 5. Chống lặp (tiết kiệm token): kết quả y hệt lượt trước và chưa quá 12 giờ → coi như YEN,
#    vì PM đã xử lý hoặc đang chờ người khác. Đổi bất kỳ dòng nào (sha mới, PR mới) là in lại ngay.
if [ ${#out[@]} -gt 0 ]; then
  sig=$(printf '%s\n' "${out[@]}" | sed -E 's/tuoi=[0-9]+h//; s/ [0-9]+h$//' | sort | sha1sum | cut -c1-12)
  if python3 - "$STATE" "$sig" <<'EOF'
import json,sys,time; p,sig=sys.argv[1:]; s=json.load(open(p)); now=int(time.time())
same = s.get('lastSig')==sig and now-s.get('lastSigAt',0) < 12*3600
if not same: s['lastSig']=sig; s['lastSigAt']=now; json.dump(s,open(p,'w'))
sys.exit(0 if same else 1)
EOF
  then out=(); fi
fi

[ ${#out[@]} -eq 0 ] && echo YEN || printf '%s\n' "${out[@]}"
