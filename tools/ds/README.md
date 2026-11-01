# ds — giao việc cho DeepSeek (Tokenize) để tiết kiệm token Claude

Cổng gọi API, chỉ dùng thư viện chuẩn Python. Cấu hình ở `ds-config.json` (model, trần gói, lề an toàn).

## Key
Đặt biến môi trường `TOKENIZE_API_KEY` (hoặc `TOKENIZE_KEY_FILE` trỏ tới file chứa key). **Không bao giờ commit key**, không in ra log.

## File được phép gửi
Mọi file đưa vào `-s`, `-i`, `files` và nơi ghi `-o`/`out`/`out_file` phải nằm trong repo và không khớp `blockedGlobs` (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `.env*`, `local.properties`, `keystore.properties`, `google-services.json`, ...; xem `ds-config.json`). Thư mục `.git` và symlink trỏ tới file bị chặn cũng bị từ chối. Không có ngoại lệ cho đường dẫn ngoài repo: tệp tạm (system, đầu vào, đầu ra) đặt trong `tools/ds/.work/` (đã có trong `.gitignore`, không commit). Kiểm: `python3 -m unittest discover -s tools/ds -p test_ds.py`.

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

## Mức suy nghĩ (`--effort`) và thử lại
- `-e/--effort none|low|high|max` (cũng là trường `effort` trong job `batch` và tool MCP `deepseek_chat`) đặt `reasoning_effort`. `none` tắt suy nghĩ; bỏ trống thì dùng mặc định của Tokenize (cũng có suy nghĩ). Đo thực tế: dùng `high`; `max` hay làm server cắt kết nối và chưa thấy tốt hơn. Khối suy nghĩ vẫn bị tính token; việc `hard` với đầu vào lớn nên đặt `--max-tokens 30000`.
- `post()` tự thử lại (tối đa 4 lần, chờ tăng dần) khi bị cắt kết nối (`ConnectionError`, `RemoteDisconnected`), hết thời gian, hoặc phản hồi JSON bị cắt giữa chừng; lỗi 429/5xx cũng thử lại. Lỗi 4xx khác báo ngay, không thử lại. **Hạch toán bảo thủ:** mỗi lượt gửi đều vào sổ đếm: lượt mất phản hồi (server có thể đã xử lý) tính đủ token ước tính, lỗi 5xx cũng tính đủ ước tính (chưa có quy tắc từ Tokenize rằng 5xx không tính phí), lỗi 429 và 4xx chỉ tính 1 request, lượt cuối tính số thật; chạm trần thì dừng, không gửi thêm.

## Quy tắc dùng để rẻ và trúng cache
- Phần cố định (quy ước, glossary, ngữ cảnh lớn) đặt vào **`system`** (`-s`), không đưa qua `-i`. Đo thực tế: system lớn trúng cache ~74% từ lần gọi thứ hai, qua `-i` chỉ ~33%.
- Chạy tuần tự hoặc `batch` (đã xếp việc cùng `system_file` cạnh nhau). Lần đầu một tiền tố luôn miss.
- Mỗi lần gọi mất ~2.000 token mới do Tokenize chèn sẵn nên gom việc nhỏ thành một lần gọi.
- Cỡ mỗi request lý tưởng khoảng 21–32 nghìn token mới (trần token ÷ trần request của gói).
- Bộ đếm (`~/.cache/ds/ledger.jsonl`) giữ dưới 85% trần mỗi cửa sổ, chạm trần thì dừng và báo. Sổ này chỉ nằm trong máy hiện tại; phiên cloud mới bắt đầu từ 0.
- Kết quả của model khác phải được người/agent rà trước khi commit.

## Streaming
- `DS_STREAM=1` bật streaming: server gửi dữ liệu liên tục nên request dài (đầu vào lớn, effort cao) không bị cắt kết nối (`Remote end closed connection`, `Connection reset by peer`). Nên bật cho mọi việc dài.
- Việc `hard` với đầu vào lớn: đặt `--max-tokens 30000`. Nếu DS nghĩ hết trần thì tệp ra rỗng (cờ `CẮT-CỤT`).
- Tối đa ~3 request song song; chạy 8 cùng lúc từ nguội làm cache 0% và dễ bị cắt kết nối.
