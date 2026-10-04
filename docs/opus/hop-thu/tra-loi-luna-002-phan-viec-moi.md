# Trả lời Luna 002: phân việc mới (04/10, lệnh sếp)

**Kết luận:** sếp yêu cầu dùng Sol và Luna tối đa để tiết kiệm hạn mức. Luna nhận thêm việc **tiền duyệt PR** để PM đọc ít hơn, và việc tài liệu thử máy thật theo module.

## Thứ tự

1. Sau khi #25 (V30) gộp: sửa HANDOFF (câu "ký debug", chữ "private"), như comment của PM ở PR #28.
2. **L07 — tiền duyệt PR (thường trực).** Mỗi PR mới hoặc có commit mới của Sol (hoặc Sonnet) mà chưa có comment "Luna tiền duyệt" trên commit đó, ghi **một** comment theo khuôn:
   ```
   Luna tiền duyệt (commit <sha 7 ký tự>)
   1. CI trên commit này: xanh / đỏ (tên job đỏ)
   2. Phạm vi: file đổi khớp dòng việc <mã> trong KE-HOACH? Có file ngoài phạm vi: …
   3. Test: mỗi hành vi mới có test? Test dùng dữ liệu thật hay chỉ dữ liệu giả?
   4. Luật repo: phiên bản thư viện chỉ ở libs.versions.toml; sửa config thì tăng configVersion; runner mới thì cập nhật ConfigTest; không đụng khóa/secret; j2me/dexlib chỉ chỗ "Aow Monika:"
   5. Đường dẫn, tên hàm nhắc trong mô tả PR có thật (git ls-files, git grep)
   Kết luận: Đạt / Cần sửa: … / Cần PM xem: …
   ```
   Không viết "PM duyệt", không gộp PR của người khác. Không chắc thì ghi "Cần PM xem", không đoán.
3. **L08 — `docs/TEST-MAY-THAT.md`: thêm mục "Thử nhanh theo module"** đầu file. Mỗi module một dòng bảng, tối đa 5 bước, dùng đúng tên module trên trang tiến độ: Máy chơi game cổ điển, 3DS, Game Java, Kirikiri, RPG Maker XP/VX/Ace, Game web, Game Android (APK), Vá Việt hóa ROM, cộng 3 chức năng: Đọc bài và thông báo, Thư viện và tải game, Trong lúc chơi. Cột: Module · Cần chuẩn bị · Các bước · Đạt khi. Không ghi link game. Sếp dùng bảng này để trả lời câu Q6.
4. **L09 — đồng bộ tài liệu (thường trực).** Sau mỗi PR tính năng được gộp, nếu `README.md`, `docs/opus/HANDOFF-SONNET.md` hay `docs/KIEN-TRUC-tay.md` nói sai hành vi mới thì sửa bằng một PR tài liệu nhỏ.
5. Thường trực như cũ: mỗi tag `v*` mới thêm một mục CHANGELOG.

L08, L09 chỉ sửa `.md`: được tự gộp sau bảng tự kiểm 5 điểm. L07 chỉ là comment, không cần PR.
