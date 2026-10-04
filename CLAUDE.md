# AowVN Monika — ghi chú cho Claude

App Android (Kotlin, Jetpack Compose) của aow.vn: đọc bài (Blogger feed), thông báo bài mới, tải và chạy game. Chủ repo ("sếp") giao tiếp tiếng Việt; tài liệu và chuỗi giao diện viết tiếng Việt.

## Nguyên tắc bắt buộc
- **Config-first**: thứ gì đổi được (link, lõi, hệ máy, host, hướng dẫn) nằm trong `config/monika-config.json`, không hard-code. Trường mới trong `MonikaConfig.kt` luôn có giá trị mặc định. `config/` là nguồn duy nhất (Gradle đóng gói vào assets).
- Sửa config → **tăng `configVersion`** (bản từ xa thấp hơn bản trong APK bị bỏ qua) và đồng bộ Cloudflare.
- Mọi thành phần tạo trong `AppGraph.kt` (DI thủ công, không Hilt/Koin). Phiên bản thư viện chỉ sửa trong `gradle/libs.versions.toml` (ngoại lệ: `j2me/`, `dexlib/`).
- Giao diện chỉ dùng token `Monika.*`, `Radius`, `primaryGradient()` và `ui/theme/Components.kt`; animation lấy thời lượng từ `Monika.motion`.
- Mỗi phiên bản chỉ phát hành **1 APK: universal**.
- Thêm thư viện có JNI/reflection → thêm luật `-keep` vào `app/proguard-rules.pro`.

## Luật an toàn
- Không bao giờ commit hay in ra log/chat: keystore, mật khẩu ký, token, secret, API key. Không tạo khóa ký mới; mất khóa thì hỏi chủ repo.
- Không commit game/ROM/file người dùng.
- Không đẩy thẳng `main` khi chưa được duyệt, không phát hành khi PM chưa báo.
- Muốn báo "thiếu khóa/secret/công cụ" → đọc skill `phat-hanh-apk` trước.

## Lệnh
- `./gradlew testDebugUnitTest` — chạy trước mọi push (gồm test config và test giao diện Robolectric).
- `./gradlew assembleDebug` — build APK.

## Chỉ đường (nạp skill khi cần)
| Việc | Skill |
|---|---|
| Build/ký/gửi APK, Pixeldrain, engine Azahar | `phat-hanh-apk` |
| Dựng/kiểm/đăng gói engine native | `dung-engine` |
| Thêm hệ máy, lõi, runner, link, host | `them-he-may` |
| Crash log, `Diagnostics`, retrace | `bao-loi-diag` |
| Giải nén zip/rar/7z | `giai-nen` |
| Game Java (J2ME Loader) | `j2me-loader` |
| Màn hình, theme, animation, test UI | `giao-dien-monika` |
| Feed/Cloudflare/Pixeldrain, vá ROM, backlog | `tham-khao-du-an` |
| PM kiểm hộp thư, bảng tiến độ | `pm-kiem-thu` |

Phiên mất/mới? Đọc `docs/opus/HANDOFF-SONNET.md` trước. Tiến độ: `docs/opus/KE-HOACH.md`. Bàn giao Opus: `docs/GIAO-TIEP-VOI-OPUS.md`.
