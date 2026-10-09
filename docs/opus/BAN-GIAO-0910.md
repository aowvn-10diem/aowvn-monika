# Bàn giao 09/10/2026: kịch bản khi Opus, Sonnet, Haiku 1 hết hạn mức

**Cập nhật 08:05 (sếp):** PM Opus **vẫn làm tiếp** tới khi hết hạn mức; Sonnet và Haiku 1 đã dừng. Kịch bản dưới đây **chỉ kích hoạt** khi PM Opus im: không có commit trên `docs/opus-tra-loi`, không comment `[PM` và không gộp PR nào trong **2 giờ** (06:00–23:00) hoặc **3 giờ** (ban đêm). Khi đó Sol ghi một dòng ở issue #119 "PM im từ <giờ>, Sol nhận PM tạm thời" rồi làm theo mục 4. PM Opus quay lại thì ghi ở #119 và nhận lại.

**Kết luận:** khi kích hoạt, đội còn **Sol, Luna, Luna Ultra, Haiku-2, Nova**. Sếp có thể thêm **Opus phụ** làm PM dự phòng (prompt: `docs/opus/hop-thu/PROMPT-OPUS-PM-PHU.md`). Khi chưa có Opus phụ, **Sol làm PM tạm thời** theo đúng các luật dưới đây. Đích không đổi: **bản ổn định v0.7.8 trước 11/10 18:00**, dự kiến khoảng 10/10 22:00.

Mọi giờ trong tài liệu này là giờ VN (UTC+7), trừ khi ghi UTC.

## 1. Ảnh chụp trạng thái (09/10, 08:00)
- **Main:** `ab0011e`. Bản thử mới nhất: tag `v0.7.7` (prerelease, `3387671`), chưa có bản sửa crash Kirikiri trên Android 17 (`fc095be`) và chưa có V70a.
- **Bảng tiến độ:** `docs/opus/pm/bang-tien-do/state.json` trên nhánh `docs/opus-tra-loi`, khoảng 89/111 việc xong. Kế hoạch chi tiết ở `docs/opus/KE-HOACH.md`.
- **PR đang mở:**

| PR | Việc | Tác giả | Đang chờ |
|---|---|---|---|
| #163 | V70a: bộ nút chung, rung, hiệu ứng lún | Sol | Luna tiền duyệt, rồi Luna Ultra duyệt UX lần hai |
| #164 | V68-a: hạ `contents: write` → `read` trong `emulator-test.yml` | Sol | Luna, rồi Luna Ultra duyệt lần hai |
| #167 | V72: versionCode 43, versionName 0.7.8, CHANGELOG | Sol | CI, Luna, Luna Ultra. **Gộp sau cùng**, sau V70a/V70b |
| #165 | V52: tài liệu giấy phép | Luna Ultra | Luna tiền duyệt (chỉ tài liệu, A12) |
| #166 | N09-H1f: test | Haiku 1 (đã dừng) | Luna Ultra tiền duyệt. Cần sửa thì **Haiku-2 nhận lại** |
| #151 | V45a: CI gói Ren'Py 8 + bài vào menu | Nova | Nova ghi run `37818795069` vào `V45.md`, bỏ nháp; Luna kết luận lại |
| #125 | L12: test đồng hồ hồ sơ | Luna | Luna sửa `ProfileCoverageTest.kt:26,39,47,53` theo Luna Ultra |

- **Chưa có PR:** V70b `GameQuickMenu` (Luna, hạn 10/10 20:00), V69 sửa 7 điểm UI (Sol), V73 bảng cổng phát hành (Luna Ultra, hạn 10/10 12:00), V45b config Ren'Py (Nova, hạn 10/10 20:00).

## 2. Ai làm gì

| Ai | Kênh nhận việc | Việc từ giờ |
|---|---|---|
| **Opus phụ** (nếu sếp thêm) | Sếp dán prompt | PM: gộp PR, giao việc, cập nhật bảng, phát hành theo A14/A20 |
| **Sol** | Thư `tra-loi-sol-*` | V70a, V68-a, V72, V69, rồi V70c. **Nhận thêm việc của Sonnet** ở phần app/CI/workflow phát hành. Lỗi crash lõi native mới: báo PM, không tự sửa nếu cần đụng C/C++. **PM tạm thời** khi chưa có Opus phụ (mục 4) |
| **Luna** | Thư `tra-loi-luna-*` | Tiền duyệt PR của Sol và Nova, cùng PR của Luna Ultra; V70b; L12 (#125); L15 checklist rung |
| **Luna Ultra** | Issue #119 | Tiền duyệt PR của Luna và Haiku-2; duyệt lần hai mọi PR đường phát hành (V70a, V68-a, V72); V73 bảng cổng phát hành |
| **Haiku-2** | Issue #120 | **Nhận toàn bộ việc của Haiku 1**: N09-H (test tăng độ phủ, bỏ qua phần browser cần tách UI), sửa #166 nếu bị yêu cầu, H03 |
| **Nova** | Issue #90 | Sửa #151 ngay, rồi V45b; tiền duyệt PR chỉ sửa tài liệu (A12) |
| Sonnet, Haiku 1 | — | Dừng, lịch đã tắt (mục 6). Opus (PM chính) làm tiếp tới khi hết hạn mức |

**Việc của Sonnet chuyển giao:** V26 (lõi Kirikiri, đang kẹt) và V06 (lặp) **hoãn sau v0.7.8**. Lỗi chặn ở lõi native trong lúc bản thử chạy thì Opus phụ (hoặc sếp) quyết: lùi tính năng bằng config (`config/monika-config.json`, tăng `configVersion`) thay vì sửa C/C++ gấp.

## 3. Luật gộp PR (không đổi)
- Gộp khi: người tiền duyệt đúng tuyến ghi **Đạt trên đúng head**, và CI bắt buộc xanh trên head đó.
- **CI bắt buộc:** build, coverage, n04-tests. CodeQL/analyze chỉ bắt buộc khi PR đụng `app/**`, `cloudflare/**` hoặc cấu hình CodeQL [A17]. `preview` và `digest` không chặn [A9]. PR chỉ sửa tài liệu không cần chờ Build/Coverage/CodeQL [A12].
- **PR đường phát hành** (workflow phát hành, ký, Test Lab, version, V70a): cần cả Luna **và** Luna Ultra Đạt.
- Gộp bằng merge commit, truyền SHA đầy đủ 40 ký tự của head. PR nháp thì chuyển sang ready trước.
- **"Rà định kỳ … Đạt"** (comment không ghi tên người duyệt) **không thay** được Luna hoặc Luna Ultra.
- **D4:** PR đỏ quá 6 giờ thì nhắc tác giả; quá 12 giờ thì đóng và giao lại.
- Script hỗ trợ: `.claude/skills/pm-kiem-thu/scripts/pr-tom-tat.sh` in mỗi PR một dòng (CI theo head, đã bỏ qua preview/digest; kết luận Luna và Luna Ultra; "CŨ" nghĩa là duyệt trên commit cũ).

## 4. Phát hành v0.7.8 [A14, A19, A20]
1. **Cổng** (đủ hết mới chạy): CI bắt buộc xanh đúng SHA trên main; V60 (periodic-check + Test Lab Robo) xanh trên SHA đó; `docs/CHANGELOG.md` có mục v0.7.8; versionCode 43 / versionName 0.7.8 đã vào main (#167); mọi PR đường phát hành có Luna + Luna Ultra Đạt. Luna Ultra lập bảng đối chiếu ở V73.
2. **Thứ tự gộp:** V70a (#163), V70b, V68-a (#164), rồi **V72 (#167) sau cùng**. Phần nào chưa xanh lúc 10/10 20:00 thì để sang bản sau, không giữ bản ổn định lại [A16].
3. **Bản thử:** chạy `release.yml` (`workflow_dispatch`) với SHA đầy đủ trên main và `prerelease=true`. Workflow tự tạo tag `v0.7.8` từ versionName, không ghi đè tag cũ [A19]. Chỉ **một** người chạy: Opus phụ, hoặc Sol khi chưa có Opus phụ. Người chạy ghi một dòng trong issue #119 trước khi bấm, để không ai chạy trùng.
4. **Ổn định:** sau **2 giờ** kể từ bản thử mà không có lỗi chặn [A20], chạy lại `release.yml` ở chế độ tag có sẵn: `tag=v0.7.8`, `prerelease=false` (để trống `sha`). Preflight còn đòi CodeQL/analyze xanh trên SHA của tag. Nếu bước đăng báo release đã tồn tại hoặc lỗi lạ: **dừng, hỏi sếp**, không xóa release hay tag. [CHƯA KIỂM] chế độ này chưa chạy thật lần nào. Lỗi chặn = app văng khi mở; không tải hoặc không giải nén được game; một hệ máy không mở được game nào; mất save; phím ảo không ăn.
5. **Kèm ra mắt:** danh sách thử thực tế `docs/TEST-THUC-TE.md` và phiếu tick https://claude.ai/artifact/A6H8zoG6KZkTVAW5L8xM6a (sếp chia sẻ). Nơi tester gửi báo cáo: chờ sếp chốt.
6. **Vẫn cấm:** đụng khóa ký, mật khẩu, token, GitHub Secrets hay in chúng ra; ghi đè hoặc xóa tag; phát hành từ nhánh khác main; push thẳng main; force push.

## 5. Mốc thời gian

| Khi (giờ VN) | Việc | Ai |
|---|---|---|
| 09/10 15:05 | Chạy lại Test Lab V56 trên máy ARM thật (1 thiết bị; quota free 5 lượt/ngày, đã đếm theo mọi lượt chạy) | PM Opus (lịch đã hẹn); Sol nếu PM im |
| 10/10 12:00 | V73 bảng cổng phát hành; PR (a) V45 gộp | Luna Ultra; Nova |
| 10/10 20:00 | Hạn V70a, V70b, V45b. Cắt bản thử v0.7.8 | Sol/Luna/Nova; PM |
| 10/10 ~22:00 | 2 giờ không lỗi chặn → bản ổn định v0.7.8 | PM |
| 11/10 18:00 | Hạn chót cứng của bản ổn định | — |
| 12/10 20:00 | V70c tự chỉnh bố cục (bản sau) | Sol |

## 6. Lịch tự động
Lịch của PM Opus (ngày :12, đêm :27, đêm nhanh :57, chạy lại Test Lab 15:05) **vẫn bật**. Lịch của Sonnet và Haiku 1 đã tắt:

### Danh sách lịch (để bật/tắt)
Tắt bằng `update_trigger enabled=false`, không xóa:
- PM ngày `trig_015WwbYtSvWe2Cm48yXemN6n`, PM đêm `trig_01Q7BiVvi3bbBTyHDf6NU38j`, PM đêm nhanh `trig_01SQv152WqxuScJXhhVy38HS`
- Chạy lại Test Lab `trig_01CWEkgHwz47iv4mat1yoG6R` (giao Sol ở mục 5)
- Sonnet đêm `trig_01W33J8mAaUbedqsAahFka3k`; Haiku `trig_01XGPSvv3b1Pm2dgb36PUs94`, Haiku đêm `trig_01EHYDknUpu3J8os51ccGx6a`

Hạn mức hằng tuần của Opus đặt lại vào **12/10 22:00** giờ VN.

## 7. Quyết định đang có hiệu lực
- [A14] PM tự phát hành bản thử và bản ổn định khi đủ cổng, không cần báo sếp.
- [A15] Đội ChatGPT chạy tối đa liên tục. WIP: Sol 4, Luna 3, Luna Ultra 3, người khác 2.
- [A16] V70a/V70b vào bản ổn định nếu xanh trước 10/10 20:00; V70c sang bản sau.
- [A17] Luật CodeQL ở mục 3.
- [A18] Quyền dispatch cấp repo cộng preflight V66 là đủ, không thêm environment gate.
- [A19] Bản ổn định mang số v0.7.8 vì tag v0.7.7 đã là bản thử.
- [A20] Bản thử 2 giờ không lỗi chặn → bản ổn định; ra mắt kèm danh sách thử thực tế.
- Đội ChatGPT im ≥ 3 giờ (`im-lang.py` thoát mã 2): báo sếp **một lần** mỗi đợt. Sếp chỉ còn 1 lượt đặt lại, và chỉ dùng khi hạn mức **hằng tuần** đã hết.
