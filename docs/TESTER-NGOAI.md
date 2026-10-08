# Hướng dẫn thử app — Tester ngoài

Cảm ơn bạn đã giúp thử app **AowVN Monika**. Bên dưới là các bước để kiểm tra từng loại game được hỗ trợ.

---

## 1. Kirikiri (KAG)

Kirikiri là engine chạy game visual novel (VN) với tệp startup.tjs.

**Bước thử:**
1. Mở app, chọn thư mục chứa game Kirikiri.
2. Nếu app phát hiện startup.tjs, game sẽ tải. Nếu không, app sẽ báo lỗi.
3. Nhấp "Chơi" để chạy game.
4. Kiểm tra: game hiển thị, có thể tương tác (bấm, swipe, chọn menu).
5. Nếu gặp lỗi, từ màn hình game, bấm nút menu → "Báo lỗi game này" → ghi thông tin.

---

## 2. RPG Maker (RGSS/RGSS2/RGSS3)

RPG Maker là engine phát triển game RPG (Data.system.bin, cấu hình RGSS hoặc RGSS2/3).

**Bước thử:**
1. Mở app, chọn thư mục game RPG Maker.
2. App sẽ kiểm tra loại RGSS (1/2/3) từ tệp game.
3. Nhấp "Chơi" để chạy game.
4. Kiểm tra: màn hình game hiển thị, nhân vật di chuyển, chiêu thức/item hoạt động.
5. Nếu gặp lỗi, từ màn hình game, bấm nút menu → "Báo lỗi game này" → ghi thông tin.

---

## 3. Game Boy / GBA / NES

Các trò chơi cổ điển chạy qua emulator: Game Boy (GB), Game Boy Advance (GBA), Nintendo Entertainment System (NES).

**Bước thử:**
1. Mở app, chọn thư mục chứa ROM (tệp .gb, .gba, .nes).
2. Nhấp tên game để mở emulator.
3. Kiểm tra: game chạy, âm thanh/hình ảnh bình thường, phím điều khiển hoạt động.
4. Kiểm tra các chế độ lưu: save state, load state (nếu có).
5. Nếu gặp lỗi, thoát emulator → quay lại app → bấm nút menu → "Báo lỗi game này" → ghi thông tin.

---

## 4. Java (J2ME)

Game Java cũ chạy qua J2ME Loader, sử dụng tệp .jar.

**Bước thử:**
1. Mở app, chọn thư mục chứa game Java (.jar).
2. Nhấp tên game để mở emulator J2ME.
3. Kiểm tra: game tải, giao diện game hiển thị, phím điều khiển hoạt động.
4. Chơi một vòng ngắn, kiểm tra âm thanh (nếu có).
5. Nếu gặp lỗi, thoát emulator → quay lại app → bấm nút menu → "Báo lỗi game này" → ghi thông tin.

---

## 5. ONScripter

ONScripter là engine chạy game với script (.ons hoặc tệp startup chỉ định ONScripter).

**Bước thử:**
1. Mở app, chọn thư mục game ONScripter.
2. App sẽ kiểm tra và xác nhận engine ONScripter.
3. Nhấp "Chơi" để chạy game.
4. Kiểm tra: game hiển thị, có thể đọc cốt truyện, menu tương tác.
5. Nếu gặp lỗi, từ màn hình game, bấm nút menu → "Báo lỗi game này" → ghi thông tin.

---

## Báo lỗi game này

Từ bất kỳ màn hình chơi game nào, bạn có thể báo lỗi:
- Nhấp nút menu (≡) → chọn "Báo lỗi game này".
- Hoặc: từ màn hình chính app → chọn game → nút "Báo lỗi game này" (dưới cùng).

Mẫu issue sẽ tự động hiển thị. Vui lòng điền đầy đủ: máy, phiên bản Android, bản app, các bước tái hiện, kết quả, ảnh chụp màn hình nếu có.
