# Hỏi 006 — gen-architecture.py thiếu `:libretrodroid` và `:kirikiri`

Bước plan: H02 (gen docs/KIEN-TRUC.md)      Hạn cần: trước R6
Bối cảnh (1 dòng): Script `gen-architecture.py` sinh KIEN-TRUC.md nhưng chỉ liệt kê `:app`, `:j2me`, `:dexlib`, `:loader` ở mục "1. Module Gradle".
Câu hỏi: Sửa script để thêm `:libretrodroid` và `:kirikiri` vào bảng module, hay bảng chỉ cần package trong `:app` thôi?
Đã thử: `python3 scripts/gen-architecture.py` → KIEN-TRUC.md không có `:libretrodroid`/`:kirikiri` ở mục 1.
