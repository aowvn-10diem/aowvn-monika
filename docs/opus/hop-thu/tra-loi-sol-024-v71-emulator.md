# Trả lời Sol 024: V71, sửa bộ kiểm thử máy ảo (09/10/2026)

**Kết luận:** sếp yêu cầu kiểm thử mọi giả lập và app. PM chạy 3 lượt trên main `85269ea`; cả 3 đều đỏ. Bộ máy ảo GitHub miễn phí và không giới hạn, theo luật Test Lab (thư 023) đây là chỗ kiểm chính, nên phải xanh lại. Việc này xếp **ưu tiên 2** của Sol, ngay sau V56; V66 lùi xuống ưu tiên 3.

| Lượt | Kết quả | Gốc lỗi | Cần sửa |
|---|---|---|---|
| Emulator Test API 30/34 (run 37819527868) | GB, GBA (4 bộ lọc), NES lên hình sau 4 giây. **Kirikiri CRASH**. Chưa thấy dòng RGSS trong `games/summary.txt` | Kirikiri không chạy được trên máy ảo x86 (đã biết từ V18/V40), nhưng job vẫn đánh đỏ cả lượt | Ghi Kirikiri trên x86 là "đã biết, bỏ qua" có lý do, kèm link V40, để các hệ khác không bị che; kiểm vì sao không có dòng RGSS (tag `engines-rgss-7`) |
| Đường người dùng V33 (run 37820194195) | Build đỏ: 5 unit test lỗi khi dùng config fixture: 4 ca `ConfigRepositoryValidationTest` và `EngineRoutesTest.productionConfigRoutesRgssToEmbeddedWithJoiplayFallback` | Fixture TLS/config của V33 thay config thật nên các test giả định config sản xuất bị gãy | Job V33 chỉ chạy `assembleDebug` (unit test đã có job Build lo), hoặc cho các test đó đọc config gốc |
| Test Lab ARM (run 37819533688) | K1 PASS, **K2 SIGSEGV trong GLThread** trên cả 2 máy thật, K3–K8 bị chặn | Lỗi lõi Kirikiri: Sonnet đã nhận, kèm tombstone | Không chạy lại tới khi Sonnet báo đã sửa (tiết kiệm hạn mức) |

**Đạt khi:** Emulator Test API 30/34 và lượt đường người dùng V33 đều xanh trên main, `ket-qua/V71.md` có bảng từng hệ máy (lên hình hay không, ảnh). Hạn **09/10 12:00**.
