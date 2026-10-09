# V73 — Bảng cổng phát hành v0.7.8

**Ảnh chụp:** digest `2026-10-09T07:13:49.286345Z`; main `62c7c7e758e3f67735deacd579634c52a059a251`, sau khi gộp #171. Theo giao việc V73 tại [issue #119, comment 6074131101](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6074131101).

## Kết luận

**Cổng phát hành v0.7.8 chưa đạt.** Build, Coverage, CodeQL và cả hai analyze đều thành công trên exact main `62c7c7e`. Tuy vậy, periodic-check và Test Lab Robo chưa có run gắn với SHA này; main vẫn là versionCode 42/versionName 0.7.7, CHANGELOG chưa có v0.7.8 và ref tag v0.7.8 trả 404. PR #167 đã có Luna và Ultra review trên head `0df7bd9`, nhưng PM vừa yêu cầu Sol nhập main mới vào nhánh đó; nếu head đổi thì cần kiểm tra lại CI và review.

## Bảng cổng

| Cổng | Bằng chứng | Trạng thái |
|---|---|---|
| CI trên main exact SHA | [Run list exact SHA `62c7c7e`](https://api.github.com/repos/aowvn-10diem/aowvn-monika/actions/runs?head_sha=62c7c7e758e3f67735deacd579634c52a059a251&per_page=100): Build [37897817311](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37897817311), Coverage [37897817282](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37897817282), CodeQL [37897817281](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37897817281) và analyze Java/Kotlin [113713111470](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37897817281/job/113713111470) + JS/TS [113713111738](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37897817281/job/113713111738) đều success. Check-runs [trên cùng SHA](https://api.github.com/repos/aowvn-10diem/aowvn-monika/commits/62c7c7e758e3f67735deacd579634c52a059a251/check-runs?per_page=100) còn ghi PM digest job `113713111714` success và preview `113713112758` skipped. | CI bắt buộc trên main xanh; digest/preview không chặn theo quyết định PM. |
| V60 periodic-check và Test Lab Robo | Run list exact-main ở trên không có hai workflow này. V60.md ghi periodic-check [37865693922](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37865693922) thuộc SHA `ab0011e`; Test Lab Robo [37796105693](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37796105693) thuộc `9dc9774`. | Bằng chứng cho hai workflow trên exact SHA `62c7c7e`: **[CHƯA KIỂM]**; cổng V60 chưa đạt. |
| version và CHANGELOG trên main | [app/build.gradle.kts tại `62c7c7e`](https://github.com/aowvn-10diem/aowvn-monika/blob/62c7c7e758e3f67735deacd579634c52a059a251/app/build.gradle.kts#L24-L25) vẫn là `42`/`0.7.7`. [CHANGELOG tại cùng SHA](https://github.com/aowvn-10diem/aowvn-monika/blob/62c7c7e758e3f67735deacd579634c52a059a251/docs/CHANGELOG.md) vẫn có mục `Ứng viên 0.7.7 (RC — chưa phát hành/tag)`; nội dung v0.7.8 còn ở #167. | Chưa đạt trên main. |
| PR V72 #167 | Head `0df7bd9a012b2c923a6d3351914ae53aaaa38c7a`; Luna review [6075844021](https://github.com/aowvn-10diem/aowvn-monika/pull/167#issuecomment-6075844021), Ultra rà CHANGELOG [6074658439](https://github.com/aowvn-10diem/aowvn-monika/pull/167#issuecomment-6074658439). CI exact-head được ghi trong PR và digest: Build, Coverage, CodeQL/analyze, prepare và Ren'Py đều success; physical/GCP steps skipped. Metadata PR hiện vẫn base `3b2329e`; PM yêu cầu Sol nhập main mới vào nhánh #167 tại [comment 6076281775](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6076281775). | Hai review hiện có trên `0df7bd9`; chưa áp dụng cho head sau cập nhật. Cần kiểm tra lại CI/review nếu head đổi; PM giữ #167 để gộp cuối cùng. |
| Thứ tự phát hành / V70b | Main đã nhận #171 ở SHA `62c7c7e`. PM ghi V70b `GameQuickMenu` còn thiếu và #167 gộp cuối tại [comment 6074131101](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6074131101); bàn giao mới nhất cho biết V70b chưa có PR tại [comment 6076230759](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6076230759). | Chưa đạt thứ tự A14; chờ hoàn tất V70b rồi PM gộp V72 sau cùng. |
| Tag và A20 | [API ref tag `v0.7.8`](https://api.github.com/repos/aowvn-10diem/aowvn-monika/git/ref/tags/v0.7.8) trả HTTP 404. A20 tính thời gian sau prerelease theo [biên bản bàn giao](https://github.com/aowvn-10diem/aowvn-monika/blob/22cce7104d121e230db95990bd78979ec95bd183/docs/opus/BAN-GIAO-0910.md). | Chưa có tag v0.7.8; đồng hồ A20 chưa bắt đầu. |

## Hành động còn thiếu

1. Hoàn tất V70b theo quyết định PM.
2. Sol cập nhật #167 theo main mới; sau mọi thay đổi head, xác nhận CI bắt buộc và lấy review Luna rồi Ultra trên đúng head. PM gộp #167 cuối cùng.
3. Chỉ PM cắt prerelease sau khi các cổng đạt; A20 bắt đầu tính từ prerelease.

Không dispatch workflow phát hành, không tạo tag, không ký hoặc publish trong lượt cập nhật này.