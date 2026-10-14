# Bot gom báo lỗi V100

`python3 scripts/test-crash-issue-bot.py` chạy offline với JSON tự sinh, không đọc Worker, không tạo issue.
`crash-issue-bot.py --fixture fixture.json` trả kế hoạch JSON từ `{records, issues, owners}`.
Mỗi record gồm `{id, report}` theo hợp đồng Worker. Không dùng fixture với `--apply`.

Workflow chuẩn bị nhịp một giờ. PR/push chỉ chạy offline; job đọc Worker và ghi GitHub chỉ dành cho sự kiện schedule sau khi PM gộp/kích hoạt. Sol không dispatch hay chạy thật.
Chạy thật cần secret Actions `CRASH_ADMIN_TOKEN` tương ứng ADMIN_TOKEN của Worker hiện có, cùng hai repository variables `CRASH_ASSIGNEE_SOL` và `CRASH_ASSIGNEE_SONNET` là GitHub login được phép nhận issue. Không tạo secret hay suy login từ tên vai trò; thiếu cấu hình chặn trước đọc báo cáo. GITHUB_TOKEN chỉ cần contents:read + issues:write ở job collect.

Bot băm lõi + loại + tối đa ba frame đầu (Java bỏ vị trí dòng, native bỏ địa chỉ/offset). Không ghi stack, đường dẫn, game, ảnh, thiết bị hay nội dung báo cáo vào issue. Không có frame thì chữ ký chỉ còn lõi+loại; đây không phải kết luận nguyên nhân.
Chữ ký chưa có issue nhãn `crash-moi` mở issue. Issue đã có chỉ cập nhật khi số lần mới quan sát tăng ít nhất 5; marker lưu hash ID/count để lần đọc lại không nhân đôi. Phần ghi chú của người giữ nguyên, issue đã đóng không bị mở lại. Azahar/Kirikiri/mkxp giao login Sonnet; lõi khác giao login Sol. Nhãn crash-moi phải có sẵn trước lịch thật.

Giới hạn nguồn hiện tại: GET /reports của Worker chỉ trả 100 bản ghi mới nhất, không có pagination công khai. Bot đọc tối đa cửa sổ đó, không tuyên bố kiểm đủ lịch sử/5000 bản ghi. 1000 hash ID gần nhất trong marker, tối đa 500 issue qua 5 trang, 20 mutation/lượt; vượt ngân sách hoặc state sai thì dừng trước ghi. Có thể bỏ lỡ báo cáo đã rơi khỏi cửa sổ giữa hai lượt. Không tải artifact hay game; không đổi Worker/endpoint/KV cũ.
