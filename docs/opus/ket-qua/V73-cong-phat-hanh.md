# V73 — Bảng cổng phát hành v0.7.8

**Ảnh chụp:** nhánh main đang trỏ tới `ab0011e76dc5f07db39d5a353aa655aa3105ad61`. Đây là trạng thái tại thời điểm đối chiếu; cập nhật lại sau mỗi lần gộp PR mã. Việc giao V73: [issue #119, comment 6071943689](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6071943689). Cổng A14/A19/A20 theo [biên bản bàn giao](https://github.com/aowvn-10diem/aowvn-monika/blob/22cce7104d121e230db95990bd78979ec95bd183/docs/opus/BAN-GIAO-0910.md).

## Kết luận

**Cổng phát hành v0.7.8 hiện chưa đạt.** Build, Coverage, CodeQL/analyze và periodic-check đều xanh trên main hiện tại. Tuy vậy, bằng chứng Test Lab Robo của V60 mới có trên main cũ `9dc977417368e0678aa7a4ad47dcd2cdad3c7f96`; chưa xác nhận run Robo trên `ab0011e`. Version 0.7.8 và CHANGELOG mới chỉ nằm trong PR #167; PR này chưa có Luna/Luna Ultra duyệt. #163/#164 cũng chưa có tiền duyệt Luna, V70b chưa có PR. Chưa tạo tag hoặc chạy release.

## Đối chiếu cổng

| Cổng | Bằng chứng | Trạng thái |
|---|---|---|
| CI trên main đúng SHA | Main Build [37863738884](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37863738884), Coverage [37863738870](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37863738870), CodeQL/analyze [37863738921](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37863738921) đều thành công trên `ab0011e`; hai analyze Java/Kotlin và JavaScript/TypeScript đều success. Periodic-check [37865693922](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37865693922) cũng success trên `ab0011e`, gồm unit, links, packs, remote-config và issue. N04 script-tests success ở [37853677233](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37853677233) trên main `5ff6f0d`; compare `5ff6f0d..ab0011e` chỉ có hai file test app thay đổi ([compare](https://github.com/aowvn-10diem/aowvn-monika/compare/5ff6f0d97ad8e68b817318a0e2fddc69e02db547...ab0011e76dc5f07db39d5a353aa655aa3105ad61)). Workflow N04 chỉ kích hoạt khi sửa scripts hoặc chính workflow ([script-tests.yml](https://github.com/aowvn-10diem/aowvn-monika/blob/ab0011e76dc5f07db39d5a353aa655aa3105ad61/.github/workflows/script-tests.yml)). | Build/Coverage/CodeQL/periodic đạt; N04 có run success ở tổ tiên, không có run gắn đúng SHA `ab0011e` **[CHƯA KIỂM nếu áp điều kiện exact-SHA cho N04]**. |
| V60 periodic-check + Test Lab Robo | Periodic-check hiện tại ở trên xanh đúng `ab0011e`. Hồ sơ [V60.md](https://github.com/aowvn-10diem/aowvn-monika/blob/ab0011e76dc5f07db39d5a353aa655aa3105ad61/docs/opus/ket-qua/V60.md) ghi Test Lab Robo [37796105693](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37796105693) thành công trên `9dc9774`, không phải main hiện tại. Truy vấn workflow Test Lab Robo cho nhánh main chưa thấy run mới. | periodic-check đạt; Test Lab Robo trên exact main **[CHƯA KIỂM]**, nên cổng V60 chưa đạt. |
| versionName/versionCode | Main hiện tại ghi versionCode 42, versionName 0.7.7 tại [app/build.gradle.kts](https://github.com/aowvn-10diem/aowvn-monika/blob/ab0011e76dc5f07db39d5a353aa655aa3105ad61/app/build.gradle.kts#L24-L25). PR [#167](https://github.com/aowvn-10diem/aowvn-monika/pull/167), head `2e17c43714ecf5f9781c8e6d93c3a25f307be260`, đề xuất 43/0.7.8; chưa được gộp. Tra cứu ref tag `v0.7.8` trả 404. | Chưa đạt trên main. |
| CHANGELOG | Main còn mục “Ứng viên 0.7.7” đối chiếu với main cũ `9dc9774`, chưa có mục v0.7.8. Diff #167 thêm mục v0.7.7 theo tag phát hành thật và mục v0.7.8 theo main `ab0011e`; các thay đổi còn chờ gộp được ghi rõ. | Chưa đạt trên main; nội dung mới đang ở #167. |
| CI trên PR #167 | Head `2e17c437`: Build [37867131970](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37867131970) và [37867093663](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37867093663), Coverage [37867131886](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37867131886), CodeQL/analyze [37867131956](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37867131956), prepare [37867131908](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37867131908) đều success. | CI PR đạt; chưa đủ điều kiện gộp. |
| Hai lượt duyệt PR đường phát hành | Review submissions của #167 đang trống. #163 head `4c1ae0a` và #164 head `2909070` cũng chưa có review submission của Luna; nhận xét “Rà định kỳ” không thay review Luna/Luna Ultra theo biên bản bàn giao. Chưa có PR V70b trong danh sách PR mở. | Chưa đạt. |
| A14/A20 | Biên bản yêu cầu gộp V70a, V70b, V68-a rồi mới gộp V72; sau bản thử v0.7.8 cần đủ 2 giờ không lỗi chặn trước khi chạy bản ổn định. Tag v0.7.8 chưa tồn tại nên thời gian chờ A20 chưa bắt đầu. | Chưa tới bước phát hành. |

## Hành động còn thiếu trước khi cổng đạt

1. Có run Test Lab Robo V60 thành công gắn với đúng SHA main dùng để cắt bản.
2. Gộp PR đường phát hành theo thứ tự A14; main phải có versionCode 43, versionName 0.7.8 và mục CHANGELOG v0.7.8.
3. Luna tiền duyệt rồi Luna Ultra duyệt lần hai từng PR đường phát hành; chờ PM gộp V72 sau V70a/V70b/V68-a.
4. Chỉ PM chạy release.yml khi đủ cổng; sau RC cần theo dõi đủ 2 giờ theo A20.

Không dispatch workflow, không tạo tag, không phát hành trong lượt rà này.
