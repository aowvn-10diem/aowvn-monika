# ds — giao việc cho DeepSeek (Tokenize) để tiết kiệm token Claude

Cổng gọi API, chỉ dùng thư viện chuẩn Python. Cấu hình ở `ds-config.json` (model, trần gói, lề an toàn).

## Key
Đặt biến môi trường `TOKENIZE_API_KEY` (hoặc `TOKENIZE_KEY_FILE` trỏ tới file chứa key). **Không bao giờ commit key**, không in ra log.

## File được phép gửi
Mọi file đưa vào `-s`, `-i` hoặc `files` phải nằm trong repo và không khớp `blockedGlobs` (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `.env*`, `local.properties`, `keystore.properties`, `google-services.json`, ...; xem `ds-config.json`). Thư mục `.git` và symlink trỏ tới file bị chặn cũng bị từ chối. Cần dùng thư mục tạm ngoài repo thì đặt `DS_EXTRA_ROOTS=/đường/dẫn`. Kiểm: `python3 -m unittest discover -s tools/ds -p test_ds.py`.

## Phân luồng
| `tier` | Model | Dùng cho |
|---|---|---|
| `simple` | `deepseek-v4.1-flash` | Dịch/việt hóa chuỗi, tóm tắt log, test mẫu, soạn nháp |
| `hard` | `deepseek-v4-pro` | Việc cần suy luận sâu, rà code |

Việc đụng `AppGraph.kt`, ký APK, engine native, phát hành: không giao qua đây.

## Lệnh
```
python3 tools/ds/ds.py run -t simple -s he-thong.txt -i a.kt -p "chỉ thị" -o ra.txt
python3 tools/ds/ds.py batch jobs.jsonl -w 4     # mỗi dòng: {"id","tier","system_file","files","prompt","out"}
python3 tools/ds/ds.py usage                     # mức dùng theo cửa sổ 5h/24h/7d
python3 tools/ds/ds.py mcp                       # MCP stdio (đã khai báo trong .mcp.json)
```
Với `-o`/`out`, nội dung không vào ngữ cảnh của agent gọi, chỉ in một dòng tóm tắt (token mới, cache %).

## Quy tắc dùng để rẻ và trúng cache
- Phần cố định (quy ước, glossary, ngữ cảnh lớn) đặt vào **`system`** (`-s`), không đưa qua `-i`. Đo thực tế: system lớn trúng cache ~74% từ lần gọi thứ hai, qua `-i` chỉ ~33%.
- Chạy tuần tự hoặc `batch` (đã xếp việc cùng `system_file` cạnh nhau). Lần đầu một tiền tố luôn miss.
- Mỗi lần gọi mất ~2.000 token mới do Tokenize chèn sẵn nên gom việc nhỏ thành một lần gọi.
- Cỡ mỗi request lý tưởng khoảng 21–32 nghìn token mới (trần token ÷ trần request của gói).
- Bộ đếm (`~/.cache/ds/ledger.jsonl`) giữ dưới 85% trần mỗi cửa sổ, chạm trần thì dừng và báo. Sổ này chỉ nằm trong máy hiện tại; phiên cloud mới bắt đầu từ 0.
- Kết quả của model khác phải được người/agent rà trước khi commit.

## Mức suy nghĩ và streaming
- `-e/--effort none|low|high|max` (hoặc trường `effort` trong job `batch` và tool MCP) đặt `reasoning_effort`. `none` tắt suy nghĩ; bỏ trống thì dùng mặc định của Tokenize (cũng có suy nghĩ). Đo thực tế: dùng `high`; `max` hay bị server cắt kết nối và chưa thấy tốt hơn. Khối suy nghĩ vẫn bị tính token.
- `DS_STREAM=1` bật streaming: server gửi dữ liệu liên tục nên request dài (đầu vào lớn, effort cao) không bị cắt kết nối (`Remote end closed connection`, `Connection reset by peer`). Nên bật cho mọi việc dài.
- Việc `hard` với đầu vào lớn: đặt `--max-tokens 30000`. Nếu DS nghĩ hết trần thì tệp ra rỗng (cờ `CẮT-CỤT`).
- Tối đa ~3 request song song; chạy 8 cùng lúc từ nguội làm cache 0% và dễ bị cắt kết nối.
