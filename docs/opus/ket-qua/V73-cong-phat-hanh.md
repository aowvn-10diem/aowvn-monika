# V73 — Bảng cổng phát hành v0.7.8

**Ảnh chụp:** digest `2026-10-09T04:15:34.966635Z`; main `3b2329e53bda5a310794a0844567e8d8e65f6656`, sau khi gộp #163, #164, #165 và #168. Đây là bản làm mới theo yêu cầu PM [issue #119, comment 6074131101](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6074131101).

## Kết luận

**Cổng v0.7.8 chưa đạt.** Main vẫn là versionCode 42/versionName 0.7.7; chưa có ref tag v0.7.8. Trên exact main, API chỉ tìm thấy run digest V39 thành công và hai check `digest=success`, `preview=skipped`; Build, Coverage, CodeQL/analyze, periodic-check và Test Lab Robo chưa có run exact-head để xác nhận.

## Bảng cổng

| Cổng | Bằng chứng | Trạng thái |
|---|---|---|
| CI trên main exact SHA | [Run list theo SHA `3b2329e`](https://api.github.com/repos/aowvn-10diem/aowvn-monika/actions/runs?head_sha=3b2329e53bda5a310794a0844567e8d8e65f6656&per_page=100) chỉ có PM digest [37882919710](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37882919710), success trên đúng SHA. [Check runs](https://api.github.com/repos/aowvn-10diem/aowvn-monika/commits/3b2329e53bda5a310794a0844567e8d8e65f6656/check-runs?per_page=100): digest `113666597867` success, preview `113666599084` skipped. | Build, Coverage, CodeQL và hai analyze trên exact main: **[CHƯA KIỂM]**. Không coi Coverage fail cũ trên `d0abe49` (run [37871430585](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37871430585)) là trạng thái của SHA hiện tại. |
| V60 periodic-check và Test Lab Robo | API run list exact-main ở hàng trên không có hai workflow này. Periodic-check [37865693922](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37865693922) thuộc SHA `ab0011e`; Test Lab Robo [37796105693](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37796105693) thuộc SHA `9dc9774`. | Cả hai bằng chứng trên `3b2329e`: **[CHƯA KIỂM]**; cổng V60 chưa đạt. |
| version và CHANGELOG trên main | [app/build.gradle.kts](https://github.com/aowvn-10diem/aowvn-monika/blob/3b2329e53bda5a310794a0844567e8d8e65f6656/app/build.gradle.kts#L24-L25) vẫn ghi 42/0.7.7. [CHANGELOG](https://github.com/aowvn-10diem/aowvn-monika/blob/3b2329e53bda5a310794a0844567e8d8e65f6656/docs/CHANGELOG.md) còn mục “Ứng viên 0.7.7”; nội dung 0.7.8 chưa vào main. | Chưa đạt trên main. |
| PR V72 #167 | Head `0df7bd9a012b2c923a6d3351914ae53aaaa38c7a`, base `3b2329e`. Build PR [37885255945](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885255945), push [37885252156](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885252156), Coverage [37885255924](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885255924), CodeQL/analyze [37885255868](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885255868), prepare [37885255948](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885255948), Ren'Py module [37885252132](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885252132): đều success trên đúng head. Digest workflow [37885255951](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37885255951) thành công; job digest bị skipped. Prepare build debug/instrumentation thành công nhưng bước máy thật/GCP bị skipped. [Ultra đã rà CHANGELOG](https://github.com/aowvn-10diem/aowvn-monika/pull/167#issuecomment-6074658439); chưa thấy Luna review lại đúng `0df7bd9`. | CI PR đạt; cần Luna tiền duyệt exact head và PM gộp #167 sau V70b hoặc sau quyết định bỏ V70b. Review trên `2e17c43` không thay review head mới. |
| Thứ tự merge A14 | Main đã có V70a #163 (`0b4cdd2`) và V68-a #164 (`47b7a9f`). PM giữ #167 để gộp sau cùng. Chưa thấy PR riêng cho V70b; PM giao Luna làm phần này tại [comment 6074131101](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6074131101). | Chưa đạt: còn V70b hoặc quyết định PM bỏ phạm vi đó; cần các review exact-head trước khi gộp V72. |
| Tag và thời gian A20 | [Ref tag `v0.7.8`](https://api.github.com/repos/aowvn-10diem/aowvn-monika/git/ref/tags/v0.7.8) trả 404. [v0.7.7](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.7) còn là prerelease. | Chưa có v0.7.8; đồng hồ chờ 2 giờ A20 chưa bắt đầu. Không dispatch release, không tạo tag. |

## Hành động còn thiếu

1. Có Coverage xanh và Build/CodeQL/analyze được xác nhận trên exact main dùng để cắt bản.
2. Có periodic-check và Test Lab Robo V60 trên exact main, hoặc PM ghi rõ quyết định thay thế.
3. Hoàn tất V70b hoặc PM quyết định bỏ mục này; Luna rà #167 trên head `0df7bd9`, sau đó PM gộp V72 cuối cùng.
4. Chỉ sau khi version 0.7.8/code43 và CHANGELOG đã vào main, PM mới xem xét tạo prerelease; A20 bắt đầu tính sau prerelease.

Không chạy workflow release/sign/publish, không tạo tag và không có kết quả game/máy thật mới trong lượt này.
