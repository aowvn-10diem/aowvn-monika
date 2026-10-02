# Kế hoạch: tách "Dịch màn hình" thành gói tải theo nhu cầu + mô hình offline cân bằng

> Hiện (0.6.1) dịch màn hình dùng Google ML Kit **đóng gói sẵn trong APK** (~27 MB thư viện native chưa nén: `libtranslate_jni.so` 16 MB + `libmlkit_google_ocr_pipeline.so` 11 MB). Mục tiêu: APK không chứa gì; **người dùng bật tính năng mới tải gói về**.
> Ghi **[CHƯA KIỂM]** = chưa đo/thử; phải kiểm trước khi dựa vào.

## 1. Vì sao không tách được ML Kit Translate
- Monika phát hành APK ngoài Google Play → **không dùng được Play Feature Delivery** (chỉ cho AAB trên Play).
- ML Kit **Translate** luôn đóng thư viện native vào app (không có bản "unbundled"). Chỉ phần **OCR** có bản unbundled (`play-services-mlkit-text-recognition*`) tải qua Google Play Services (cần máy có GMS).
- ⇒ Tách thật = thay ML Kit Translate bằng **gói riêng tự tải** (runtime + mô hình), nạp bằng DexClassLoader + `System.load` (cùng cách Monika đang làm với engine Azahar / bộ nạp J2ME).

## 2. Mô hình offline (đã kiểm trên Hugging Face ngày 01/10/2026)
| Mô hình | Giấy phép | Kích thước gốc | Ghi chú |
|---|---|---|---|
| `Helsinki-NLP/opus-mt-en-vi` | Apache-2.0 | 289 MB (fp32 ≈ 72 triệu tham số) | Anh→Việt trực tiếp. Lượng tử int8 ≈ 70–80 MB **[CHƯA KIỂM]**. |
| `Helsinki-NLP/opus-mt-ja-vi` | Apache-2.0 | 311 MB | Nhật→Việt **trực tiếp** (không qua tiếng Anh). int8 ≈ 80 MB **[CHƯA KIỂM]**. |
| `facebook/m2m100_418M` | MIT | ~1,9 GB fp32 | Chất lượng đa ngôn ngữ tốt hơn nhưng nặng (≈ 420 MB int8 **[CHƯA KIỂM]**) → không "cân bằng". |
| `facebook/nllb-200-distilled-600M` | **CC-BY-NC-4.0** | — | Cấm thương mại → **không dùng**. |
| `vinai/vinai-translate-en2vi-v2` | **AGPL-3.0** | — | Copyleft mạnh → **không dùng**. |
- **Đề xuất cân bằng:** hai gói nhỏ `opus-mt-en-vi` và `opus-mt-ja-vi` (mỗi gói ~75–80 MB int8, chỉ tải gói ngôn ngữ người dùng chọn).
- **[CHƯA KIỂM]** chất lượng với văn bản game (opus-mt huấn luyện trên dữ liệu OPUS, có thể dịch hộp thoại dài/lóng chưa mượt), tốc độ trên ARM64 (mô hình ~72 triệu tham số, câu ngắn: ước lượng hàng trăm mili-giây trên máy tầm trung, **chưa đo**).
- Có bản tinh chỉnh trên PhoMT (vd. `datnth1709/finetuned_HelsinkiNLP-opus-mt-en-vi_PhoMT`) có thể tốt hơn; **giấy phép và nguồn dữ liệu chưa kiểm** → chỉ dùng khi xác minh được.

## 3. Runtime chạy mô hình trên Android [CHƯA KIỂM — cần spike]
- Ứng viên: **ONNX Runtime** (MIT, có AAR Android chính thức, ~10–15 MB) chạy bản ONNX int8 xuất từ Optimum; hoặc **CTranslate2** (MIT, nhanh nhưng chưa rõ bản Android dựng sẵn).
- Tokenizer SentencePiece (Apache-2.0) cần bản Android (JNI nhỏ) cho `source.spm` / `target.spm`.

## 4. Các bước
1. **Spike (1 lượt, trên máy phiên, không đụng app):** xuất `opus-mt-en-vi` sang ONNX + lượng tử int8 (Optimum), đo kích thước và thời gian dịch 20 câu game mẫu (x86 chỉ để ước lượng tương đối). Ghi kết quả vào tài liệu này. Chất lượng: đưa 20 câu mẫu cho sếp chấm so với ML Kit.
2. **Đóng gói:** `translate-pack-<ngôn ngữ>-<phiên bản>.zip` = `runtime.dex` + `libs/<abi>/*.so` + `model.onnx` + `source.spm` + `target.spm` + `manifest.json` (phiên bản, SHA-256, kích thước).
3. **Máy chủ chứa gói** — ⛔ sếp quyết: GitHub Releases của repo (sau khi công khai) hoặc Cloudflare R2 qua Worker. Chưa có chỗ chứa thì không làm được bước 4.
4. **Trong app:** `translate/PackManager` (tải, kiểm SHA-256, giải nén vào `filesDir/packs/`, thanh tiến độ, xóa gói) + `translate/PackTranslator` (nạp gói, dịch). Cài đặt → Dịch màn hình: mục **"Mô hình offline (tải về ~80 MB)"** — bấm mới tải; chưa tải thì dùng cách khác (API của người dùng).
5. **Gỡ ML Kit Translate khỏi APK** khi gói chạy ổn; OCR chuyển sang bản ML Kit unbundled (`play-services-mlkit-text-recognition` + `-japanese`) hoặc OCR trong gói. Máy không có Google Play Services: báo rõ "cần gói OCR".
6. **Kiểm:** unit test (manifest/SHA/giải nén), Emulator Test không tải gì khi tính năng tắt, đo APK giảm bao nhiêu (so với 0.6.1), thử trên máy ARM64 thật.

## 5. Rủi ro
| Rủi ro | Cách xử lý |
|---|---|
| opus-mt dịch game chưa đạt | So sánh với ML Kit ở bước 1; không đạt thì giữ ML Kit làm cách mặc định, gói chỉ là tùy chọn |
| Nạp .so/dex ngoài APK bị Android chặn hoặc lỗi 16 KB page size | Đã làm được với engine Azahar; dùng lại cách đó, kiểm trên Android 14/15 |
| Máy yếu dịch chậm | Chỉ dịch khi bấm nút; dùng bộ nhớ dịch (`TranslationMemory`) |

---
## ✅ Chỗ chứa gói đã chốt (02/10/2026)
Repo công khai **`aowvn-10diem/aowvn-monika-packs`**. Gói đưa lên bằng workflow **Build engines** (tự đẩy sang repo phụ khi có secret `PACKS_TOKEN`) hoặc **Mirror pack** (chép gói có sẵn rồi kiểm link tải công khai + SHA-256). Link dạng `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/<tag>/<file>` đặt vào `config.modules.<tên>.url`. Azahar đã chuyển sang dùng link này (APK không còn đóng sẵn). Bản nhị phân GPL phát hành ở đó phải ghi cách lấy mã nguồn (repo + commit) trong mô tả Release / `manifest.json`.
