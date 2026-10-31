# Rà soát chuỗi giao diện (DS-02)

Phạm vi: `app/src/main/res/values/strings.xml` và các `Text("…")` trong `app/src/main/java/vn/aow/monika/**`, đối chiếu ở commit `8b3cf429` (30/10/2026). Chỉ báo cáo, không sửa `.kt`. Mọi mục có file:dòng để người duyệt tự kiểm.

**Cách làm:** hai mô hình DeepSeek (`deepseek-v4.1-flash` và `deepseek-v4-pro`, effort `high`) cùng rà một đầu vào có đủ ngữ cảnh (định hướng dự án, mẫu giọng văn, toàn bộ chuỗi và 3 dòng mã quanh mỗi `Text`). Hai bản được gộp và **từng mục được kiểm tay với mã thật**: giữ 6 mục dưới đây, loại các đề xuất mang tính sở thích (ví dụ thêm "rộng" trước "72 dp", đổi "Tốc độ đổi nhịp thời gian…"). Không phát hiện lỗi chính tả hay dấu rõ ràng nào.

## Thuật ngữ không nhất quán

| file:dòng | Hiện tại | Đề xuất | Lý do |
|---|---|---|---|
| `AchievementsScreen.kt:219` và `:220` | `:219` "Bật: khóa nạp state, **mã cheat** và chạy chậm…"; `:220` "Tắt (softcore): được dùng nạp state, **cheat**, chạy chậm." | Dùng "mã cheat" ở cả hai câu | Hai nhánh của cùng một công tắc Hardcore, hiện sát nhau; một bên có "mã", một bên không. Các nhãn khác trong app cũng là "Mã cheat" (`CheatUi.kt:46`, `AzaharCheats.kt:108`). |
| `AchievementsScreen.kt:179`, `:180` và `:212` | `:179` "Thành tựu vừa **mở** (7 ngày)"; `:180` "Chưa **mở** thành tựu nào trong 7 ngày qua."; `:212` "báo ngay lúc **mở khóa**" | Dùng "mở khóa" ở cả ba (ví dụ "Thành tựu vừa mở khóa (7 ngày)") | Cùng màn Thành tựu, cùng một khái niệm, hai cách viết; "mở" đứng một mình dễ đọc nhầm là "mở ra xem". Mức vừa: nếu muốn tiêu đề ngắn thì chỉ cần thống nhất hai dòng 179/180. |
| `WebCheatUi.kt:123` và `:124` | `:123` "Biến số của game (…)"; `:124` "Chưa có **biến dạng số**. Chơi một đoạn rồi mở lại." | "Chưa có biến số nào. Chơi một đoạn rồi mở lại." | Hai dòng sát nhau trên cùng màn: tiêu đề gọi "biến số", câu trống lại là "biến dạng số", đọc thành "biến dạng" + "số". Nếu "dạng số" cố ý chỉ kiểu dữ liệu số thì bỏ mục này. |

## Văn phong, dấu câu

| file:dòng | Hiện tại | Đề xuất | Lý do |
|---|---|---|---|
| `strings.xml:6` | "Game Kirikiri này được mã hóa, Monika chưa hỗ trợ. **Thư mục “%1$s”.** Hãy bấm…" | "… Monika chưa hỗ trợ. **Thư mục chứa game: “%1$s”.** Hãy bấm…" | "Thư mục “%1$s”." là câu cụt, thiếu vị ngữ; chuỗi cùng nhóm `strings.xml:5` viết đủ "trong thư mục “%1$s”". |
| `ExperimentalGameWarning.kt:39` | "… Gặp lỗi hãy bấm Báo lỗi" | "… Gặp lỗi hãy bấm Báo lỗi." | Câu cuối thiếu dấu chấm trong khi các hộp thoại cùng loại đều kết bằng dấu chấm (`LibraryScreen.kt:415`, `GameSaveUi.kt:97`). |
| `strings.xml:20` | "Kèm thông tin máy và nhật ký lỗi gần đây." | "Kèm thông tin máy và nhật ký lỗi gần đây" | Nếu là nhãn hộp kiểm cùng cấp với `strings.xml:18` ("Kèm ảnh game", không có dấu chấm) thì dấu chấm cuối là lệch; nếu là dòng mô tả riêng thì bỏ mục này. Mức thấp. |

## Ghi chú
- Đã cân nhắc và **cố ý không** đề xuất: "Aow Monika" so với "AowVN Monika" (tên thương hiệu), "file" so với "tệp" (cả hai tự nhiên, khác màn), "Hardcore"/"hardcore" (tên chế độ của RetroAchievements), "Khóa web API" (nhãn khớp trang cấp khóa), "cho bạn yêu 💖" (giọng thương hiệu), "Save cũ…" (viết hoa đầu câu là đúng).
- Chưa kiểm trên thiết bị; các đề xuất dựa trên đọc mã, người duyệt nên xem ngữ cảnh hiển thị trước khi sửa.
