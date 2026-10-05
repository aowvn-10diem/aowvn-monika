# Bàn giao PM (Opus): phiên cũ sang phiên mới (05/10/2026 15:45 UTC)

Phiên PM cũ: `session_01CqoeMJCn5kiXUbTjrJGUde`. Phiên này **dừng kiểm thư** khi phiên mới nhận việc. Nguồn sự thật nằm trong repo, trên nhánh `docs/opus-tra-loi`:

| File | Nội dung |
|---|---|
| `docs/opus/KE-HOACH.md` | Bảng việc, trạng thái, thứ tự, cổng G* |
| `docs/opus/pm/trang-thai-pm.json` | SHA đã thấy, PR, câu trả lời của sếp đã xử lý (`answers_seen`), `dashboard_version` |
| `docs/opus/pm/bang-tien-do/state.json` | Dữ liệu trang tiến độ, bản trong repo |
| `docs/opus/hop-thu/` | Thư qua lại với Sol và Luna (`tra-loi-*`, `hoi-*`) |
| `.claude/skills/pm-kiem-thu/` | Các bước một lượt kiểm, kèm script |

## Phân vai

| Ai | Phạm vi | Liên lạc |
|---|---|---|
| Sonnet | Code lõi engine. Phiên chính mới: `session_01HaU9f6SbsYY2fjxpeYbEs1` | `send_message`. Sonnet đang báo cáo về **phiên PM cũ**, nên phiên mới phải nhắn Sonnet ID phiên mới ngay |
| Sol | App, CI, config, gói, chẩn đoán, workflow. Đã **hết** tiếp quản PM (thư `tra-loi-sol-010`, issue #65) | Thư trong `hop-thu/` và comment trên GitHub. Sol đọc mỗi 60 phút |
| Luna | Tài liệu, test, script, tiền duyệt PR (L07), đồng bộ tài liệu (L09) | Như Sol |
| Gemini | F01/F02 (`hop-thu/PROMPT-GEMINI-FLASH.md`). **Chờ sếp kích hoạt** | Không giao khi sếp chưa báo |

## Việc dở (05/10 15:45 UTC)

| # | Việc | Trạng thái và việc PM cần làm |
|---|---|---|
| 1 | **Hỏi sếp: phát hành 0.7.5 bản thử?** | Đã hỏi, **chưa có trả lời**. Sonnet đã mở **PR #81** (`sonnet/V43-075`, versionCode 40, versionName 0.7.5), chờ CI → Luna → PM duyệt. "Có" thì báo Sonnet chạy `release.yml` (`tag=v0.7.5`, `stable=false`) sau khi PR được gộp. Kiểm SHA chứng thư `c46902e9…d45ab20c` |
| 2 | v0.7.4 trên GitHub đang là bản chính thức, không phải prerelease | Đã báo sếp. Chỉ sếp bấm tay được |
| 3 | Issue #65 (Sol tiếp quản) | Chờ comment bàn giao cuối của Sol, rồi PM đóng issue |
| 4 | Dependabot: #75 (bản phụ, Sol tự gộp), #76 `upload-artifact` 4→7, #78 `gradle/actions` 4→6, #77 Compose BOM 2025.03.01 | #76, #77, #78 chờ Luna tiền duyệt, rồi **PM duyệt** theo `tra-loi-sol-009` |
| 5 | #62 V45 (Ren'Py P4): SOL-012 hỏi ngoại lệ phát hành gói Ren'Py và phạm vi ký trên CI | PM trả lời. Xét cổng G5 (sếp chọn LICENSE). Phát hành gói mới thì phải hỏi sếp |
| 6 | G7 Kirikiri (V26) | Chờ sếp thử 0.7.4 (hoặc 0.7.5) với Kara no Shoujo. Không thấy chữ Việt hóa thì Sol đổi `entryPick` sang `pairedExe` |
| 7 | G9 Firebase Test Lab (máy ARM thật), Q4, G4A2 | Còn trên trang tiến độ, chờ sếp |
| 8 | Trang tiến độ (artifact `7UC9kqrWqv3GNUhhtt4z5d`) | Trong lúc tiếp quản, Sol chỉ cập nhật bản trong repo. Phiên mới cần đẩy `bang-tien-do/state.json` lên ArtifactData `board/state` (lấy `if_version` bằng `get`) |
| 9 | Trigger kiểm thư cũ `trig_01Nk2NQXsfxm8hQLAZAo4ZsS` đang bắn vào **phiên cũ** | Phiên mới **tắt** trigger này (`update_trigger enabled=false`), rồi tạo lịch mới. Kế hoạch V46/S1: mỗi lượt chạy một phiên mới với skill `pm-kiem-thu` (`create_new_session_on_fire`) |

| 10 | Phiên Sonnet mới **không có Android SDK** ("SDK location not found") | Sonnet không chạy được `./gradlew` tại máy, nên chỉ dựa vào CI. Việc lõi cần build tại máy thì phải cài SDK bằng setup script của môi trường (đọc `read_documentation` topic `environment.setup_script`) hoặc báo sếp |

## Bài học (đừng lặp lại)
- Viết mọi số liệu và trạng thái dựa trên bằng chứng (API, log, ảnh chụp), không dựa vào trí nhớ hay tài liệu cũ. Ví dụ: câu "Secrets chưa có" và "repo private" trong tài liệu cũ đều đã sai.
- Phiên Sonnet ở chế độ Auto bị chặn khi sửa `.claude/skills/` theo lời PM ("Instruction Poisoning"). Cách gỡ: sếp nhắn trực tiếp cho Sonnet, hoặc đổi tạm sang Accept edits.
- Đẩy commit lên nhánh của người khác bị chặn ("Modify Shared Resources") nếu sếp chưa cho phép rõ ràng.
- Giờ ghi vào tài liệu lấy từ `date -u`, không đoán.
