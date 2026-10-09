# Trả lời Sol 026: gọi lại sau khi hết hạn mức (09/10/2026, 07:45 giờ VN)

**Kết luận:** chào Sol trở lại. Từ đêm qua đã gộp V66, V71, V56-quota. #163 (V70a) và #164 (V68-quyền) đang chờ Luna. Việc của Sol theo thứ tự:

| Ưu tiên | Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|---|
| 1 | V70a, V68-a | Theo dõi #163 và #164. Luna hoặc Luna Ultra yêu cầu sửa thì sửa ngay trong lượt đó, đẩy commit mới, không force push | Luna + Luna Ultra Đạt | 10/10 20:00 |
| 2 | **V72** | PR chuẩn bị bản ổn định **v0.7.8** [A19]: `versionCode` 42 → 43, `versionName` "0.7.8"; thêm mục `## v0.7.7` và `## v0.7.8` vào `docs/CHANGELOG.md` (thay đổi người dùng thấy được từ v0.7.6, mỗi dòng dẫn tới commit). Nhánh `sol/V72-phat-hanh`. **Không tạo tag, không chạy `release.yml`** | CI xanh, Luna + Luna Ultra Đạt; PM gộp sau cùng, sau V70a/V70b | 10/10 18:00 |
| 3 | V69 | 7 điểm UI/UX trong thư 021 | như thư 021 | 10/10 20:00 |
| 4 | V70c | Trình sửa bố cục, làm song song, vào bản sau ổn định | như thư 022 | 12/10 20:00 |

**[A19]** Tag `v0.7.7` đã là bản thử (tạo 08/10 lúc 22:48 giờ VN, commit `3387671`, chưa có bản sửa crash Kirikiri trên Android 17 và chưa có V70a). `release.yml` (V66) tạo tag theo `versionName` và không cho ghi đè tag cũ. Vì vậy bản ổn định sẽ mang số **v0.7.8**: PM cắt bản thử v0.7.8 sau mốc V70a/V70b (10/10 20:00), rồi chuyển thành bản ổn định sau ít nhất 12 giờ không có lỗi chặn. V70c chuyển sang bản kế tiếp.

WIP tối đa 4 PR mở. Chạy `./gradlew testDebugUnitTest` trước mỗi push có mã.
