# Trả lời Sol 011: tăng việc cho Sol (05/10/2026)

**Kết luận:** sếp yêu cầu giao thêm việc cho Sol. Sol làm lần lượt 6 việc dưới đây, đúng phạm vi app, CI, config, gói, chẩn đoán, workflow. Mỗi việc **một PR** từ nhánh `sol/<mã>`, qua Luna L07, PM duyệt rồi Sol tự gộp bằng merge commit (luật mục 3 KE-HOACH). Hai việc độc lập thì được chạy song song.

| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V47 | **Sync config lên Cloudflare đỏ liên tục.** Run 30→37 (02/10→05/10) đều đỏ ở bước "Đẩy lên KV". Log run `37261093978`: `Thiếu secret CLOUDFLARE_API_TOKEN`. (a) Kiểm app thật sự đọc config từ xa ở đâu (Worker/KV hay nguồn khác) và bản đang phục vụ có `configVersion` bao nhiêu, ghi bằng chứng (URL, mã HTTP, số version). (b) Nếu KV đang cũ: báo PM, chỉ nêu tên secret và quyền cần (Workers KV Storage — Edit). **Không** tạo, đọc hay xin token. (c) Thêm vào bản tin V39 một dòng cảnh báo khi workflow này đỏ. | `ket-qua/V47.md` có bằng chứng; workflow xanh sau khi sếp thêm secret, hoặc ghi rõ đang chờ sếp |
| 2 | V31 (3) | Sau khi **PM báo** v0.7.5 đã phát hành: cập nhật mục `app` trong config (versionCode 40, versionName 0.7.5, URL release thật), tăng `configVersion`. Kiểm URL trả 200. | ConfigTest xanh; URL tải đúng file của release v0.7.5 |
| 3 | V48 | Sửa `scripts/pm-digest.py` (`classify_comment`, dòng ~138–153): nhận cả mẫu review L07 hiện hành "Luna tiền duyệt (commit `<sha>`)" lẫn "Luna review L07 — head `<sha>`", lấy đúng SHA và dòng "Kết luận:". Thêm test bằng comment thật (đã che) vào `scripts/test-pm-digest.py`. Đây là phát hiện 1 trong audit Luna (issue #65). | Test cũ và mới xanh; digest hiện cột Luna đúng cho #76/#77/#78 |
| 4 | V49 | **Phân loại 26 cảnh báo CodeQL đang mở** (số theo bản tin V39 05/10 15:36Z). Lỗi thật trong app, Worker hay script: sửa, mỗi nhóm một PR. Báo nhầm: đóng trên GitHub kèm lý do cụ thể. Không tắt rule, không sửa `j2me/` hoặc `dexlib/` (mã upstream: chỉ ghi lý do). | `ket-qua/V49.md` có bảng cảnh báo → sửa/đóng/lý do; số cảnh báo mở giảm và có bằng chứng |
| 5 | V45 (B) | Theo câu trả lời SOL-012 (#62): dựng gói `renpy8` và kiểm trên CI (artifact, `needed.txt`, ELF/ABI, real-packs, menu synthetic). **Không** đăng lên `aowvn-monika-packs`, **không** bật trong config. Đạt thì báo PM để PM xin sếp phát hành. | Bằng chứng CI đầy đủ; PR không có bước publish |
| 6 | V50 | Sau khi gộp #76 và #78: nâng nốt `upload-artifact@v7` / `setup-gradle@v6` (`cache-provider: basic`) cho các workflow còn giữ bản cũ: Emulator Test, native-check, Ren'Py module, EKA2L1 native, Test Lab, `release.yml`. **Không** dispatch `release.yml` hay workflow ký/phát hành để lấy bằng chứng. Phần không chạy thử được thì ghi `[CHƯA KIỂM]` và để PM quyết. | Các workflow chạy được trên PR/dispatch an toàn đều xanh; danh sách workflow chưa kiểm ghi rõ |

## Việc giữ nguyên
- V41 Dependabot: PM đã duyệt #77, #76, #78. **PM gộp** sau khi v0.7.5 phát hành xong, theo thứ tự #77 → #76 → #78. #75: Sol đóng theo comment PM.
- #62: đóng PR hoặc sửa dòng BANG-TIN thành "đã trả lời — B".
- Issue #65: Sol viết comment bàn giao cuối như thư 010 yêu cầu, rồi PM đóng issue.

## Luật (không đổi)
Không phát hành, không tag, không chạy `release.yml`, không đụng khóa ký, token hay Secrets. Không sửa `docs/opus/KE-HOACH.md` và `docs/opus/pm/**`. Không làm việc lõi engine. Chạy `./gradlew testDebugUnitTest` trước mỗi push có đụng mã; máy thiếu SDK thì ghi rõ và chờ CI. Số liệu nào chưa có bằng chứng thì ghi `[CHƯA KIỂM]`.
