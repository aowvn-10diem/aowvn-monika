---
name: bao-loi-diag
description: Bắt, ghi và đọc crash log của Aow Monika (Diagnostics, breadcrumb, retrace R8, giải ký hiệu native theo BuildId, crash-reports.sh). Dùng khi thêm mã mới cần báo lỗi/vệt sự kiện, hoặc khi phân tích báo cáo crash của người dùng.
---

# Chẩn đoán lỗi (`app/src/main/java/vn/aow/monika/diag/`)

`Diagnostics` là kho báo cáo duy nhất. Mọi báo cáo có: `component` (`pack:<tên>`, `engine:libretro:<lõi>`, `engine:onsyuri`, `ra`, `net`, `app:<mục>`), `env` (RAM/heap/đĩa/mạng), `crumbs` (vệt sự kiện), `count` (lỗi trùng dấu vân tay được gộp).

## Nguồn báo cáo
Lỗi Java (`CrashReporter`) · game chết (phiên + `ApplicationExitInfo`) · chết của tiến trình chính (`collectProcessDeaths`: native/ANR/hết RAM) · lỗi bắt được (`recordHandled`, không bật hộp thoại). Báo cáo native trên API 30 giữ log PID chết và dòng tag `DEBUG` ±5 giây quanh `ApplicationExitInfo.timestamp` (`TombstoneParser.kt`, `NativeCrashLogcat.kt`); [CHƯA KIỂM] đọc được DEBUG của crash_dump qua logd trên API 30/máy thật.

## Thêm mã mới
- Ghi vệt: `Diagnostics.crumb(ctx, tag, msg)`.
- Lỗi nuốt-được: `recordHandled(ctx, "pack:x", "mô tả", e)`.
- Nội dung báo cáo luôn qua `scrub` (che đường dẫn/email/token). Đừng đưa dữ liệu nhạy cảm vào `msg`.
- Vệt sự kiện nằm ở file `diag/crumbs/<pid>.log` → sống sót cả khi native crash.
- Phiên chơi: `Diagnostics.begin/coreInfo/stage/heartbeat/end`.

## Đọc báo cáo
- Gửi về Worker (`CRASH_URL`, mặc định `https://aowvn-monika-crash.aowvn-system.workers.dev`; mã `cloudflare/`). Mã quản trị `CRASH_ADMIN_TOKEN` là bí mật: không in, không commit.
- Danh sách + thống kê: `CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh`; một báo cáo: `scripts/crash-reports.sh <id>`.
- Từ JSON cục bộ (không gửi): `scripts/crash-reports.sh --file report.json --mapping mapping-vX.Y.Z.txt --r8-jar /path/r8.jar`.
- Native: `scripts/crash-reports.sh <id> --symbols-dir /path/symbols --symbolizer /path/llvm-symbolizer`. Chỉ ghép khung có PC + BuildId, không đoán địa chỉ hay ghép theo tên `.so`.
- Chi tiết in/retrace: `scripts/crash-report-detail.py`; test: `scripts/test-crash-symbols.py --require-tools`.

## Mapping và ký hiệu
- **Mapping R8**: `crash-reports.sh <id>` lấy mapping đúng phiên bản trong báo cáo bằng `gh release download` (cần quyền đọc repo + R8 JAR; đặt `R8_JAR` đúng compiler nếu cache có nhiều bản). Khi phát hành: sau build + kiểm chữ ký, chép `app/build/outputs/mapping/release/mapping.txt` thành `mapping-<tag>.txt` và đính đúng release cùng APK. Không còn mapping bản cũ → ghi [CHƯA KIỂM], không lấy mapping bản mới thay.
- **Ký hiệu native**: workflow dựng gói (`dung-engine`) lưu ZIP `symbols-<gói>-<run>-<abi>.zip` (`build-ids.tsv`; mức `debug`/`symtab`/`dynamic-only`). `librenpython.so` của RAPT dựng sẵn có thể chỉ `dynamic-only` (không phục hồi tên hàm/dòng). `symbols-rgss-*` là release nháp tới khi G8 xong (V24).
- Chi tiết: `docs/opus/ket-qua/D1.md`; phương án: `docs/opus/2026-10-03-kiem-thu-chan-doan.md`.
