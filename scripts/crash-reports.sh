#!/bin/bash
# Xem báo lỗi; Java retrace theo mapping đúng phiên bản, native ghép theo BuildId.
# CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh [<id>]
# scripts/crash-reports.sh --file report.json --mapping mapping-v0.7.4.txt --r8-jar /path/r8.jar
# scripts/crash-reports.sh <id> --symbols-dir /path/symbols --symbolizer /path/llvm-symbolizer
set -euo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
U="${CRASH_URL:-https://aowvn-monika-crash.aowvn-system.workers.dev}"
REPORT_ID=""
REPORT_FILE=""
OPTIONS=()
BY_FP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --by-fp) BY_FP=1; shift ;;
    --file)
      [ $# -ge 2 ] || { echo "Thiếu đường dẫn sau --file" >&2; exit 2; }
      REPORT_FILE=$2; shift 2 ;;
    --mapping|--r8-jar|--symbols-dir|--symbolizer)
      [ $# -ge 2 ] || { echo "Thiếu giá trị sau $1" >&2; exit 2; }
      OPTIONS+=("$1" "$2"); shift 2 ;;
    --*) echo "Tùy chọn chưa hỗ trợ: $1" >&2; exit 2 ;;
    *)
      [ -z "$REPORT_ID" ] || { echo "Chỉ nhận một mã báo cáo" >&2; exit 2; }
      REPORT_ID=$1; shift ;;
  esac
done
if [ -n "$REPORT_FILE" ]; then
  [ -z "$REPORT_ID" ] || { echo "Chọn --file hoặc mã báo cáo" >&2; exit 2; }
  if [ "$BY_FP" = 1 ]; then exec python3 "$SCRIPT_DIR/crash-report-groups.py" "$REPORT_FILE"; fi
  exec python3 "$SCRIPT_DIR/crash-report-detail.py" --file "$REPORT_FILE" "${OPTIONS[@]}"
fi
if [ "$BY_FP" = 1 ] && [ -n "$REPORT_ID" ]; then echo '--by-fp không nhận mã báo cáo riêng' >&2; exit 2; fi
: "${CRASH_ADMIN_TOKEN:?Cần biến CRASH_ADMIN_TOKEN (mã quản trị)}"
H=(-H "authorization: Bearer $CRASH_ADMIN_TOKEN")
if [ "$BY_FP" = 1 ]; then
  echo 'Phạm vi: tối đa 100 báo cáo gần nhất từ Worker; không suy lịch sử đã bị gộp/xóa.'
  curl -fsS "${H[@]}" "$U/reports" | python3 "$SCRIPT_DIR/crash-report-groups.py"
  exit
fi
if [ -n "$REPORT_ID" ]; then
  REPORT_FILE=$(mktemp)
  trap 'rm -f "$REPORT_FILE"' EXIT
  ID=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$REPORT_ID")
  curl -fsS "${H[@]}" "$U/reports/$ID" > "$REPORT_FILE"
  python3 "$SCRIPT_DIR/crash-report-detail.py" --file "$REPORT_FILE" "${OPTIONS[@]}"
else
  echo "== Thống kê (lõi | loại | giai đoạn) =="; curl -fsS "${H[@]}" "$U/stats" | python3 -c 'import json,sys;d=json.load(sys.stdin);print("Tổng:",d["total"]);[print(f'"'"'{x["n"]:4}  {x["k"]}'"'"') for x in d["byCoreKindStage"]]'
  echo; echo "== Mới nhất =="; curl -fsS "${H[@]}" "$U/reports" | python3 -c 'import json,sys,datetime
for x in json.load(sys.stdin)["reports"][:30]: print(datetime.datetime.fromtimestamp(x.get("t",0)/1000).strftime("%d/%m %H:%M"),x.get("y",""),"|",x.get("c",""),"|",x.get("k",""),"|",x.get("g",""),"|",x.get("st",""),"|",x["id"])'
fi
