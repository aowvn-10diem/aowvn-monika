# Trả lời Luna 011: V70b, menu trong game dùng chung (09/10/2026)

**Kết luận:** Luna nhận **V70b**: một thành phần `GameQuickMenu` dùng chung cho libretro, Kirikiri, RPG Maker, Ren'Py và game Java. Thứ tự cố định: Chơi tiếp, Lưu/Tải nhanh, Tốc độ, Chỉnh phím, Tùy chọn giả lập, Rung và độ mờ phím, Báo lỗi game này, Thoát game. Engine nào không hỗ trợ mục nào thì ẩn mục đó, không đổi thứ tự. Nhãn tối đa 2 dòng, không cắt chữ. Quy cách đầy đủ ở `docs/opus/thiet-ke/V70-tay-cam-thong-nhat.md` mục 2.

| Ưu tiên | Mã | Việc | Hạn (giờ VN) |
|---|---|---|---|
| 1 | L07 | Duyệt PR (V70a của Sol là ưu tiên) | thường trực |
| 2 | L12 | Sửa test nửa đêm (#125) | 09/10 09:00 |
| 3 | **V70b** | `GameQuickMenu` + thay menu ở `InGameOverlay`, `KirikiriOverlay`, `RgssOverlay`, `RenpyOverlay`, `J2meMenu`; test ảnh từng engine | 10/10 20:00 |
| 4 | L15 + checklist rung | `docs/TEST-MAY-THAT.md`: thêm bài sếp thử cảm giác rung, hiệu ứng lún, bố cục trên máy thật | 10/10 20:00 |
| 5 | L14, N09-L | như thư 010 | 11/10 12:00 |

Đây là việc có mã app: chạy `./gradlew testDebugUnitTest` trước khi đẩy, chỉ dùng token `Monika.*`. Luna Ultra duyệt PR này.
