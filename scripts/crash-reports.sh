#!/bin/bash
# Xem báo lỗi game/lõi người chơi gửi về.
#   CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh          → thống kê + 100 báo cáo mới nhất
#   CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh <id>     → chi tiết 1 báo cáo (id như r:1790664060430:93300910)
set -euo pipefail
U="${CRASH_URL:-https://aowvn-monika-crash.aowvn-system.workers.dev}"
: "${CRASH_ADMIN_TOKEN:?Cần biến CRASH_ADMIN_TOKEN (mã quản trị)}"
H=(-H "authorization: Bearer $CRASH_ADMIN_TOKEN")
if [ $# -ge 1 ]; then
  curl -fsS "${H[@]}" "$U/reports/$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$1")" | python3 -c '
import json,sys
r=json.load(sys.stdin); s=r.get("session",{})
print(r["title"]); print("Máy:",r["device"]); print("Bản app:",r["app"]); print("Lõi:",s.get("core"),"|",s.get("coreInfo")); print("Game:",s.get("game"),"| Hệ:",s.get("system"),"| Giai đoạn:",s.get("stage"))
print("Lý do:",r.get("reason")); print(); print(r.get("detail","")); print("\n-- log --"); print("\n".join(r.get("log",[])))'
else
  echo "== Thống kê (lõi | loại | giai đoạn) =="; curl -fsS "${H[@]}" "$U/stats" | python3 -c 'import json,sys;d=json.load(sys.stdin);print("Tổng:",d["total"]);[print(f'"'"'{x["n"]:4}  {x["k"]}'"'"') for x in d["byCoreKindStage"]]'
  echo; echo "== Mới nhất =="; curl -fsS "${H[@]}" "$U/reports" | python3 -c 'import json,sys,datetime
for x in json.load(sys.stdin)["reports"][:30]: print(datetime.datetime.fromtimestamp(x.get("t",0)/1000).strftime("%d/%m %H:%M"),x.get("y",""),"|",x.get("c",""),"|",x.get("k",""),"|",x.get("g",""),"|",x.get("st",""),"|",x["id"])'
fi
