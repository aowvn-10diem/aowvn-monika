#!/bin/bash
# Chạy DeepSeek như một "nhân sự" trong một worktree riêng bằng Claude Code CLI lồng, trỏ về Tokenize.
#   ds-agent.sh <flash|pro> <thư-mục-worktree> <tệp-chỉ-thị.md> [số-lượt-tối-đa=30]
# AN TOÀN (đã kiểm bằng thử thật): môi trường SẠCH (env -i; KHÔNG thừa hưởng GH_TOKEN, khóa AWS/GCP, token phiên);
# chế độ dontAsk; chỉ Read/Edit/Write/Glob/Grep trong worktree (luật theo đường dẫn ./**: đọc ngoài thư mục bị từ chối);
# KHÔNG Bash, KHÔNG web, KHÔNG sub-agent => DS không chạy lệnh, không đẩy lên GitHub. Người điều khiển rà `git diff` rồi tự commit/push.
# Tạo worktree trước: git worktree add "$DS_WORK/wt-<mã>" -b deepseek/<mã> origin/main
set -u
[ $# -ge 3 ] || { sed -n '2,8p' "$0"; exit 2; }
case $1 in flash) M=deepseek-v4.1-flash;; pro) M=deepseek-v4-pro;; *) echo "model: flash|pro"; exit 2;; esac
WT=$(cd "$2" && pwd) || exit 2; PF=$3; TURNS=${4:-30}
git -C "$WT" rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "$WT không phải worktree git"; exit 2; }
WORK=${DS_WORK:?đặt DS_WORK=<thư mục scratchpad của phiên>}; HOMEDIR=$WORK/ds-home; mkdir -p "$HOMEDIR"
[ -n "${TOKENIZE_API_KEY:-}" ] || { echo "thiếu key: TOKENIZE_API_KEY chưa có trong môi trường phiên (báo sếp)"; exit 4; }
LOG=$WORK/agent-$(basename "$WT")-$1.json
RULES='Bạn là DeepSeek, nhân sự của dự án Aow Monika, làm việc trong thư mục hiện tại (một git worktree). Đọc CLAUDE.md ở đây trước (luật dự án). Bạn KHÔNG có Bash nên không chạy được test hay git: tự rà kỹ từng dòng như trình biên dịch Kotlin 2.0 trước khi kết thúc. Chỉ sửa/tạo đúng các tệp được giao, không đụng tệp khác, không thêm thư viện. Mẫu chuỗi "$a_b" ăn cả _b: viết "${a}_b". Không chắc kết quả thì bỏ ca đó. Khi xong, trả lời ngắn: tệp đã tạo/sửa, cách bạn đã kiểm, điều chưa chắc.'
( cd "$WT" && env -i PATH="$PATH" HOME="$HOMEDIR" CLAUDE_CONFIG_DIR="$HOMEDIR/.claude" \
    HTTPS_PROXY="${HTTPS_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" NO_PROXY="${NO_PROXY:-}" NODE_EXTRA_CA_CERTS="${NODE_EXTRA_CA_CERTS:-/root/.ccr/ca-bundle.crt}" \
    ANTHROPIC_BASE_URL=https://tokenize.vn ANTHROPIC_AUTH_TOKEN="$TOKENIZE_API_KEY" ANTHROPIC_MODEL=$M ANTHROPIC_SMALL_FAST_MODEL=$M \
    DISABLE_TELEMETRY=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
    timeout "${DS_TIMEOUT:-1500}" claude -p "$(cat "$PF")" --append-system-prompt "$RULES" --max-turns "$TURNS" --permission-mode dontAsk \
      --allowedTools "Read(./**)" "Edit(./**)" "Write(./**)" "Glob(./**)" "Grep(./**)" \
      --disallowedTools "Bash" "WebFetch" "WebSearch" "Task" --output-format json 2>&1 ) | sed "s/${TOKENIZE_API_KEY}/[K]/g" > "$LOG"
python3 -I - "$LOG" <<'PY'
import json,sys
for l in open(sys.argv[1],encoding="utf-8"):
    l=l.strip()
    if l.startswith('{"') and '"result"' in l:
        d=json.loads(l); u=d.get("usage",{})
        print("lượt:",d.get("num_turns"),"| token vào/ra:",u.get("input_tokens"),"/",u.get("output_tokens"),"| lỗi:",d.get("is_error"),"| từ chối:",[(x.get("tool_name"),str(x.get("tool_input"))[:60]) for x in d.get("permission_denials",[])])
        print("DS báo:",str(d.get("result"))[:700]); break
else: print("không đọc được kết quả JSON; xem",sys.argv[1])
PY
echo "--- thay đổi trong worktree:"; git -C "$WT" status --short
