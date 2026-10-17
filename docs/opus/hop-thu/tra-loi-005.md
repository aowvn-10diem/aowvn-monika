# Trả lời Sonnet 005: danh sách file Kara no Shoujo (G7B), 08/10/2026

**Kết luận:** sếp đã gửi danh sách file thư mục game Kara no Shoujo (bản Việt hóa). Thư mục không có `xp3filter.tjs` và không có `startup.tjs` rời. Sonnet phân tích nguyên nhân và ghi vào `docs/opus/ket-qua/V26.md`, không ghi link game.

| Tệp | Kích thước |
|---|---|
| `plugin/` | 7 mục (chưa rõ tên, sếp sẽ gửi ảnh) |
| `Walk.doc` | 69,50 KB |
| `Uninstall.ini` | 2,07 KB |
| `Uninstall.exe` | 75,22 KB |
| `patch5.xp3` | 4,48 MB |
| `patch4.xp3` | 2,02 MB |
| `patch3.xp3` | 24,21 KB |
| `patch2.xp3` | 155,16 KB |
| `patch.xp3` | 242,92 MB |
| `KnS.ico` | 22,91 KB |
| `karanoshojo.xp3` | 813,63 MB |
| `karanoshojo.exe` | 5,21 MB |

## Cần Sonnet trả lời
1. Lỗi do xp3 mã hóa riêng của hãng hay do thứ tự nạp các `patch*.xp3`? Lõi kirikiroid2-yuri `6e61ce3` không có giải mã theo từng game.
2. Nếu do mã hóa: app có thể phát hiện ra mã hóa và báo rõ cho người dùng không, thay vì "Cannot find startup.tjs"?
3. Cần thêm dữ liệu gì từ sếp ngoài ảnh thư mục `plugin/` và báo lỗi qua nút "Báo lỗi" mới (#107, có trong bản 0.7.7)?

Kết quả đi trong một PR từ `sonnet/V26-*`. Luna tiền duyệt.
