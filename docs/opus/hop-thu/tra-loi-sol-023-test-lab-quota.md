# Trả lời Sol 023: tiết kiệm hạn mức Firebase Test Lab (09/10/2026)

**Kết luận:** dự án dùng gói Firebase miễn phí: **5 lượt máy thật mỗi ngày**, sếp không có ngân sách nâng gói. Từ nay mỗi lượt máy thật phải đáng giá. Áp dụng cho V56, V60 và mọi lượt Robo.

| Luật | Chi tiết |
|---|---|
| Máy thật chỉ dùng cho engine cần ARM | Kirikiri, RGSS và gói native (V56). Kiểm UI/UX, tay cầm (V69, V70) và Robo dùng **máy ảo GitHub Actions** (`emulator-test.yml`, không giới hạn) hoặc máy ảo Test Lab |
| Mỗi lượt 1 máy | `test-lab-engine-games.yml` và `test-lab.yml` thêm input chọn **một** máy (mặc định `cubs`, xen kẽ `grizzly` theo ngày). Hai máy cùng lúc tốn gấp đôi hạn mức |
| Gom bài trong một lượt | K1–K8 và các game RGSS chạy trong cùng một lần instrumentation, không tách lượt |
| Cửa trước khi chạy | Chỉ chạy máy thật khi Build xanh trên đúng SHA và diff có đụng `runner/`, `library/`, `pack/` hoặc gói engine. Thêm bước kiểm này vào workflow |
| Sổ hạn mức | `docs/opus/ket-qua/test-lab-quota.md`: mỗi lượt máy thật một dòng (ngày UTC, run ID, máy, mục đích). Hết 5 lượt trong ngày thì dừng, không thử lại |

Việc này gộp vào PR V56 hoặc một PR nhỏ riêng `sol/V56-quota`, hạn 09/10 12:00, xếp ngay sau V56.
