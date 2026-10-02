# Hộp thư Sonnet ⇄ Opus

Kênh trao đổi duy nhất giữa Sonnet (thi công) và Opus (cố vấn ngoài), qua commit. Mục tiêu: nhanh, ít token.

## Quy ước
- **Một thư = một file = một câu hỏi.** Tên: `hoi-<số 3 chữ số>-<chủ-đề>.md` (Sonnet hỏi), `tra-loi-<số>.md` (Opus trả lời, cùng số).
- **Ngắn:** tối đa ~15 dòng. Dẫn `đường/dẫn/file:dòng` thay vì dán mã. Log lỗi chỉ lọc ≤ 20 dòng.
- **Không bí mật:** cấm khóa ký, token, mật khẩu, GitHub Secrets.
- **Bảng tin:** mỗi thư có một dòng ở `../BANG-TIN.md`. Opus chỉ cần đọc file đó trước, rồi `git log --grep` từ lần đọc trước.
- **Tiền tố commit:** `[hỏi-opus]` (Sonnet gửi), `[opus]` (Opus trả lời), `[xong-opus]` (Sonnet đã làm theo).
- **Chỉ hỏi khi tắc thật** và gộp theo lô. Việc đã có đáp án trong `docs/opus/*.md` thì tự làm, không hỏi.
- Việc cần quyết của sếp (game thử, chấp nhận kết quả "chỉ máy ảo", đổi hướng) **không** gửi Opus: báo sếp.

## Khuôn thư hỏi (`hoi-*.md`)
```
# Hỏi 00X — <chủ đề>
Bước plan: <vd. R1>      Hạn cần: <ngay | trước R3 | không gấp>
Bối cảnh (1 dòng):
Câu hỏi:
Đang cân nhắc: A) …  B) …  C) …
Đã thử và hỏng: <lệnh/kết quả ngắn, file:dòng>
```

## Khuôn thư trả lời (`tra-loi-*.md`)
```
# Trả lời 00X
Kết luận (1 dòng):
Lý do + đánh đổi:
Các bước (mỗi bước có cách kiểm):
Điều chưa chắc [CHƯA KIỂM]:
```

## Đánh thức Opus
Opus tự kiểm tra định kỳ theo `PROMPT-OPUS.md` (so SHA đầu main, đổi mới đọc `BANG-TIN.md`); sếp không phải nhắc. Sonnet cũng tự quét `tra-loi-*.md` mới trong mỗi lượt kiểm tra CI của mình.
