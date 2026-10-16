---
name: bien-tap-changelog
description: Biên tập docs/CHANGELOG.md cho người dùng thường (Haiku 5.5, chạy định kỳ). Mỗi khi có phiên bản mới phát hành mà mục chưa có dấu bien-tap, viết lại các gạch đầu dòng bằng tiếng Việt dễ hiểu rồi mở PR. Trang monika.aow.vn/lich-su.html đọc thẳng file này.
---

# Biên tập CHANGELOG cho người thường

Người đọc là người chơi game Việt hóa, **không** biết lập trình. Trang `monika.aow.vn/lich-su.html` hiển thị trực tiếp `docs/CHANGELOG.md` trên `main`, nên chữ ở đây là chữ họ đọc.

Lượt chạy định kỳ: không có gì mới thì **dừng ngay, không ghi, không báo** (luật ngân sách trong CLAUDE.md).

## 1. Tìm việc (rẻ)
```
git fetch origin main && git checkout -B nova/bien-tap-<tag> origin/main   # hoặc nhánh haiku/…
python3 scripts/changelog-pending.py
```
- Không in gì và thoát 0 → dừng (hết việc).
- Thoát 1 (không đọc được danh sách GitHub Releases; phiên phải gắn với repo) → đây là **lỗi chạy**, không phải hết việc: báo 1 dòng ở #90 rồi dừng, không đi đường vòng qua proxy.
- In ra tối đa 3 tag, mới nhất trước. Mặc định chỉ xét từ `v0.8.0` trở lên; bản cũ hơn chỉ làm khi sếp bảo.
- Một lượt = **một PR**, gộp các tag in ra.

## 2. Lấy sự thật
Với mỗi tag: đọc mục hiện có trong `docs/CHANGELOG.md` (đã có PR/commit nguồn) và, khi dòng nào chưa rõ người dùng thấy gì, mở PR/commit đó (`get_commit`, `pull_request_read`) hoặc `grep` trong diff. **Không bịa**: không có nguồn thì không viết; không chắc hiệu ứng thì ghi vào dòng "Nội bộ" hoặc để nguyên và hỏi PM ở #90.

## 3. Cách viết
| Quy tắc | Ví dụ |
|---|---|
| Mỗi dòng một ý, tối đa ~25 chữ, nói **người chơi thấy gì** | "Mở game Nintendo DS lâu thì màn chờ hiện từng bước (tải, giải nén, nạp game) thay vì đứng yên." |
| Mở đầu bằng **Mới:** hoặc **Sửa lỗi:** (in đậm) | `- **Sửa lỗi:** Chạm màn hình lúc game NDS đang nạp không còn làm app treo.` |
| Không dùng từ kỹ thuật: libretro, SIGSEGV, looper, CI, test, build, API, config, manifest, buildId, driver, Compose, XP3, JNI… Nếu bắt buộc nhắc tên (hệ máy, game), giữ tên người chơi biết: NDS, 3DS, PSP, Kirikiri, RPG Maker, Ren'Py, game Java | "bàn phím ảo cho game Java" chứ không "bàn phím J2ME" |
| Nói rõ máy/hệ máy nào ảnh hưởng và việc cần làm (nếu có) | "Chỉ ảnh hưởng 3DS. Không cần làm gì, cập nhật là xong." |
| Người đọc là **người dùng**: chỉ nói **đã sửa gì** và **có gì mới**. Không ghi "chưa kiểm", "chưa rõ nguyên nhân", "chưa chạy thật", ghi chú về cách làm hay giới hạn của nhóm (sếp chốt 16/10). Cần ghi thì đưa vào dòng `Nội bộ:` | Sai: "Tốc độ thật trên máy chưa kiểm." Đúng: bỏ câu đó |
| Giữ cụm nguồn cuối dòng `([#200](https://github.com/…/pull/200))` hoặc `([abc1234](…))` | trang tự ẩn cụm này, nhưng người làm cần truy vết |

**Nội bộ**: thay đổi người dùng không thấy (kiểm thử, CI, tài liệu, giấy phép, tái cấu trúc, chẩn đoán cho người làm) và mọi ghi chú của nhóm → mỗi mục một dòng bắt đầu `- Nội bộ: …`, viết ngắn. **Trang Lịch sử cập nhật không hiển thị các dòng này** (chỉ còn trong tệp để người làm truy vết); trang cũng tự bỏ các câu có "chưa kiểm/chưa rõ/chưa chạy thật" nếu còn sót.

Ví dụ (v0.8.3, trước → sau):
- Trước: "3DS: Monika không còn gọi lớp nạp driver GPU tùy biến (`libhook_impl.so`) khi mở lõi, nên máy bị văng ngay sau khi nạp lõi sẽ dùng driver hệ thống…"
- Sau: "- **Sửa lỗi:** Một số máy mở game 3DS bị văng ngay khi vào game. Bản này dùng cách nạp ổn định hơn nên các máy đó chơi được. (nguồn giữ nguyên)"

## 4. Định dạng bắt buộc (trang đọc theo đúng khuôn này)
```
## v0.8.3 (Bản thử)
<!-- bien-tap: v0.8.3 -->
> Phát hành ngày dd/mm/yyyy (UTC), bản thử nghiệm.

- **Mới:** …
- **Sửa lỗi:** …
- Nội bộ: …
```
- Giữ nguyên dòng tiêu đề `## vX.Y.Z …` (trang tìm số phiên bản ở đây) và chỉ dùng gạch `- ` cho các ý.
- Dòng `<!-- bien-tap: vX.Y.Z -->` ngay dưới tiêu đề là **dấu đã biên tập**; script dựa vào nó. Thiếu dấu thì lượt sau làm lại.
- Dòng `>` thay các ghi chú nội bộ cũ như "Chưa phát hành / danh sách đối chiếu PR… / chưa thử trên máy thật". Ngày lấy từ `get_release_by_tag` (`published_at`), đổi sang dd/mm/yyyy; `prerelease=true` thì ghi "bản thử nghiệm".
- Không thêm/xóa mục của phiên bản khác; không đổi mục đã có dấu.

## 5. Mở PR
- Chỉ sửa `docs/CHANGELOG.md` (docs-only: không cần `./gradlew testDebugUnitTest`).
- Tiêu đề `[viec-changelog] Biên tập v0.8.x cho người thường`; body ngắn: các tag đã làm, nguồn đã đối chiếu, dòng nào để "Nội bộ" vì không chắc.
- Mở nháp, theo dõi PR; Nova tiền duyệt (`tien-duyet`). **Không** đẩy `main`, không tự gộp, không ghi "PM duyệt".
- Cấm đưa vào văn bản: khóa, token, secret, link game/ROM.

## 6. Báo cáo
Tối đa 3 dòng ở #90: `Biên tập v0.8.x xong, PR #N, k dòng để Nội bộ vì thiếu nguồn.` Không "đã nhận", không báo CI xanh.
