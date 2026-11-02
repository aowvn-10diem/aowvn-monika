# Prompt: Trưởng nhóm DeepSeek thử context-mode 1 ngày (02/11/2026)

> Dán nguyên văn vào phiên Trưởng nhóm DeepSeek (OpenCode). Sếp đã duyệt dùng thử.

---

[PM → Trưởng nhóm DeepSeek] Sếp duyệt **thử context-mode 1 ngày trên 1 phiên** để giảm token. Hôm qua đội hết quota vì đọc log, diff và kết quả lệnh dài. Đây là việc **V179**, ưu tiên sau V176.

## 1. Công cụ là gì

- `context-mode` (https://github.com/mksglu/context-mode) là plugin cho OpenCode. Kết quả lệnh dài được giữ trong sandbox, chỉ đưa bản tóm tắt vào ngữ cảnh. README tự nhận giảm tới 98% [CHƯA KIỂM].
- Plugin thêm các lệnh `ctx_*`, ví dụ `ctx_execute`: viết script in ra kết quả ngắn thay vì đọc cả tệp.
- Giấy phép ELv2: được dùng làm công cụ nội bộ. **Không** chép mã hay tệp của nó vào repo Monika.

## 2. Cài cho đúng 1 phiên thử

Yêu cầu: Node.js ≥ 22.5. Kiểm bằng `node -v`.

1. Tạo worktree riêng để thử: `git worktree add ..\monika-ctx main`. Làm mọi việc trong thư mục này.
2. Ghi số phiên bản chính thức trên npm: `npm view context-mode version`. Kiểm `npm view context-mode repository.url` phải trỏ về `github.com/mksglu/context-mode`. Lệch thì **dừng**, báo PM.
3. Tạo `opencode.json` **chỉ trong worktree thử**, không dùng tệp global ở `~/.config/opencode/`:
   ```json
   {
     "$schema": "https://opencode.ai/config.json",
     "plugin": ["context-mode"]
   }
   ```
4. Chép luật điều hướng nếu cần, theo README: `node_modules/context-mode/configs/opencode/AGENTS.md`, đặt vào worktree thử.
5. Chặn commit nhầm. Thêm 2 dòng `opencode.json` và `AGENTS.md` vào `.git/info/exclude`. **Không** sửa `.gitignore` của repo. Chạy `git status`: 2 tệp này không được hiện ra.
6. Khởi động lại OpenCode trong worktree thử, gõ `ctx stats`. Các lệnh `ctx_*` phải hiện ra.

## 3. Cách đo (so trước/sau, cùng loại việc)

| Chỉ số | Cách lấy |
|---|---|
| Token vào/ra mỗi việc | Bảng sử dụng của nhà cung cấp DeepSeek, hoặc thống kê phiên OpenCode. Ghi số **trước khi bật** (phiên thường, 1 việc cùng loại) và **sau khi bật** |
| Tiết kiệm theo plugin | `ctx stats` cuối ngày |
| Chất lượng | Số lần Luna trả "Cần sửa" và số lần push lỗi build, so với hôm qua |
| Lỗi do plugin | Lệnh bị chặn sai, mất ngữ cảnh, treo |

Việc dùng để thử là việc thật trong bảng, bắt đầu với **V176** hoặc **V173**. Không mở việc mới chỉ để đo.

## 4. Luật an toàn

- Không đưa khóa ký, mật khẩu, token, secret, API key vào lệnh `ctx_*`. Không index thư mục chứa `.env`, `keystore*`, `local.properties`.
- Không bật `ctx_fetch_and_index` cho link game, ROM hay Pixeldrain.
- Plugin chặn sai một lệnh cần thiết → tắt plugin bằng cách xóa `opencode.json` trong worktree thử, làm tiếp bình thường, ghi lại lỗi.
- Không cài cho phiên khác hay cho đội Duyệt khi chưa có kết quả.

## 5. Báo kết quả

Cuối ngày (hoặc sau 3 việc), đăng 1 comment ở #337:

```
[Trưởng nhóm DeepSeek → PM] V179 context-mode: bản <x.y.z>
| Việc | Token trước | Token sau | Giảm |
| Luna Cần sửa / push lỗi: <số> (hôm qua: <số>)
| Lỗi plugin: <không / mô tả>
| Đề xuất: áp cả đội / bỏ
```

PM quyết định áp cho cả đội hay bỏ dựa trên số liệu này.
