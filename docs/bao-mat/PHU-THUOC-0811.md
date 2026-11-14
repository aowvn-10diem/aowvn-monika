# 08/11/2026 — Cảnh báo phụ thuộc: danh sách, phân loại, kế hoạch vá

Trạng thái: **ĐANG LÀM**. Người lập: Trưởng nhóm Phụ thuộc (DeepSeek). Nhận: PM ([#337](https://github.com/aowvn-10diem/aowvn-monika/issues/337)), sếp.
Mốc kiểm: `main@6fa3e1188977d72abafba5abb3ff67594d47856e` (08/11/2026). Số liệu lấy tại đúng mốc này; cảnh báo mới sau mốc chưa có trong bảng.

## 1. Nguồn số

- Dependabot báo 05/11: **53 cảnh báo mở** (2 critical, 23 high, 26 moderate, 2 low).
- [CHƯA ĐỌC ĐƯỢC] API cảnh báo: token của phiên này **thiếu quyền** `vulnerability_alerts: read` — `GET /repos/aowvn-10diem/aowvn-monika/dependabot/alerts` trả 403, GraphQL `vulnerabilityAlerts` trả `totalCount: 0`. Vì vậy số dưới đây là **tự kiểm**, không phải số Dependabot.
- Cách tự kiểm: SBOM của dependency graph (`GET /repos/aowvn-10diem/aowvn-monika/dependency-graph/sbom`) đối chiếu cơ sở dữ liệu OSV (Maven, querybatch).
- Nơi đóng gói: `:app:dependencies --configuration githubReleaseRuntimeClasspath` và `buildEnvironment` (classpath plugin Gradle) tại cùng mốc.
- Kết quả tự kiểm: **54 cảnh báo / 18 tổ hợp gói+phiên bản** — 2 critical, 24 high, 26 moderate, 2 low. Lệch 1 high so với Dependabot vì `guava` có 2 phiên bản trong cây (một trong APK, một trên classpath build).
- Bảng chỉ ghi tên gói, mã cảnh báo và mức. Không ghi cách khai thác ở repo công khai; chi tiết để ở kho nội bộ.

## 2. Cảnh báo nằm trong APK — ưu tiên 1 (3 cảnh báo)

Đây là phần người dùng thực sự cầm trên máy; vá trước, không chờ.

| Gói | Đang dùng | Mã cảnh báo | Mức | Bản vá | Vào APK qua | Kế hoạch |
|---|---|---|---|---|---|---|
| `com.google.guava:guava` | 32.0.1-jre | GHSA-xxph-c9ww-hj94 | HIGH | 33.7.2 | `:j2me` → `ch.acra:acra-http` → `auto-service` → guava | #544: ghim 33.7.2-jre |
| `org.apache.commons:commons-lang3` | 3.16.0 | GHSA-j288-q9x7-2f5v | MODERATE | 3.18.0 | `commons-compress:1.27.1` | #544: ghim 3.18.0 |
| `junit:junit` | 4.12 | GHSA-269g-pwp5-87pp | MODERATE | 4.13.1 | `:j2me` → `pngj:2.2.3` (POM khai `compile`) | #544: loại khỏi runtime, giữ 4.13.2 cho test |

`junit` không phải thư viện chạy của app: `pngj` chỉ cần nó lúc biên dịch, nên cách đúng là **loại khỏi runtime** thay vì nâng phiên bản. Guava và commons-lang3 thì phải nâng.

Hai cảnh báo critical **không** nằm trong APK: `netty-handler` và `freemarker` chỉ có trên classpath plugin Gradle (AGP kéo `grpc-netty`, Kover kéo freemarker).

## 3. Cảnh báo chỉ ở công cụ build / test — không đóng gói (51 cảnh báo)

Không vào APK. Vá được thì vá; không vá được thì đóng bằng lý do "chỉ ở công cụ build, không đóng gói" (cần quyền đóng cảnh báo, xem mục 6).

| Gói | Đang dùng | Số cảnh báo | Mức | Nằm ở đâu |
|---|---|---|---|---|
| `io.netty:netty-handler` | 4.1.93.Final | 7 | CRITICAL 1, HIGH 4, MODERATE 2 | classpath (`grpc-netty` của AGP) |
| `org.freemarker:freemarker` | 2.3.32 | 1 | CRITICAL 1 | classpath (Kover) |
| `com.google.guava:guava` | 32.0.1-jre | 1 | HIGH 1 | APK — `:j2me` → `acra-http` → `auto-service` |
| `com.google.guava:guava` | 33.4.8-jre | 1 | HIGH 1 | classpath (AGP) |
| `com.google.protobuf:protobuf-java` | 3.22.3 | 1 | HIGH 1 | classpath (AGP) |
| `commons-io:commons-io` | 2.13.0 | 1 | HIGH 1 | classpath (AGP) |
| `io.netty:netty-codec` | 4.1.93.Final | 3 | HIGH 2, MODERATE 1 | classpath (`grpc-netty` của AGP) |
| `io.netty:netty-codec-http` | 4.1.93.Final | 19 | HIGH 6, MODERATE 12, LOW 1 | classpath (`grpc-netty` của AGP) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | 9 | HIGH 5, MODERATE 4 | classpath (`grpc-netty` của AGP) |
| `org.bitbucket.b_c:jose4j` | 0.9.5 | 1 | HIGH 1 | classpath (AGP) |
| `org.jdom:jdom2` | 2.0.6 | 1 | HIGH 1 | classpath (AGP) |
| `org.xerial:sqlite-jdbc` | 3.36.0 | 1 | HIGH 1 | classpath plugin (SBOM: `androidx.room:room-compiler` 2.5.2) |
| `io.netty:netty-common` | 4.1.93.Final | 2 | MODERATE 2 | classpath (`grpc-netty` của AGP) |
| `junit:junit` | 4.12 | 1 | MODERATE 1 | APK — `:j2me` → `pngj:2.2.3` |
| `org.apache.commons:commons-compress` | 1.21 | 2 | MODERATE 2 | classpath (AGP) |
| `org.apache.commons:commons-lang3` | 3.16.0 | 1 | MODERATE 1 | APK — `commons-compress` + classpath |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.0.21 | 1 | MODERATE 1 | classpath (KGP) |
| `io.netty:netty-handler-proxy` | 4.1.93.Final | 1 | LOW 1 | classpath (`grpc-netty` của AGP) |

## 4. Bảng đầy đủ (54 dòng)

| Gói | Đang dùng | Mã cảnh báo | Mức | Bản vá | Nằm ở đâu | PR dự kiến |
|---|---|---|---|---|---|---|
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-c4c3-7fpv-j4q5 | CRITICAL | 4.2.17.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `org.freemarker:freemarker` | 2.3.32 | GHSA-27j2-h3m2-8237 | CRITICAL | 2.3.35 | classpath (Kover) | #471 (có) |
| `com.google.guava:guava` | 32.0.1-jre | GHSA-xxph-c9ww-hj94 | HIGH | 33.7.2 | APK — `:j2me` → `acra-http` → `auto-service` | #544 (APK) / #546 (build) |
| `com.google.guava:guava` | 33.4.8-jre | GHSA-xxph-c9ww-hj94 | HIGH | 33.7.2 | classpath (AGP) | #544 (APK) / #546 (build) |
| `com.google.protobuf:protobuf-java` | 3.22.3 | GHSA-735f-pc8j-v9w8 | HIGH | 3.25.5 (nhánh 3.x) | classpath (AGP) | #546 (CI định) |
| `commons-io:commons-io` | 2.13.0 | GHSA-78wr-2p64-hpwj | HIGH | 2.14.0 | classpath (AGP) | #546 |
| `io.netty:netty-codec` | 4.1.93.Final | GHSA-558v-64gr-wgg4 | HIGH | 4.1.136.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec` | 4.1.93.Final | GHSA-mj4r-2hfc-f8p6 | HIGH | 4.1.133.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-57rv-r2g8-2cj3 | HIGH | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-6jqx-86gh-f27w | HIGH | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-f6hv-jmp6-3vwv | HIGH | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-jppx-w49h-x2qq | HIGH | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-mvh2-crg5-v77c | HIGH | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-pwqr-wmgm-9rr8 | HIGH | 4.2.10.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-93wv-jw9v-4972 | HIGH | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-f6hv-jmp6-3vwv | HIGH | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-prj3-ccx8-p6x4 | HIGH | 4.2.4.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-w9fj-cfpg-grvv | HIGH | 4.2.11.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-xpw8-rcwv-8f8p | HIGH | 4.1.100.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-3qp7-7mw8-wx86 | HIGH | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-4g8c-wm8x-jfhw | HIGH | 4.1.118.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-c653-97m9-rcg9 | HIGH | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-x4gw-5cx5-pgmh | HIGH | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `org.bitbucket.b_c:jose4j` | 0.9.5 | GHSA-3677-xxcr-wjqv | HIGH | 0.9.6 | classpath (AGP) | #546 |
| `org.jdom:jdom2` | 2.0.6 | GHSA-2363-cqg2-863c | HIGH | 2.0.6.1 | classpath (AGP) | #546 |
| `org.xerial:sqlite-jdbc` | 3.36.0 | GHSA-6phf-6h5g-97j2 | HIGH | 3.41.2.2 | classpath plugin (SBOM: `androidx.room:room-compiler` 2.5.2) | #546 (ghim phòng ngừa — chưa chạm classpath plugin) |
| `io.netty:netty-codec` | 4.1.93.Final | GHSA-3p8m-j85q-pgmj | MODERATE | 4.1.125.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-38f8-5428-x5cv | MODERATE | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-4mp9-239f-g9hg | MODERATE | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-5jpm-x58v-624v | MODERATE | 4.1.108.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-6cqp-g7gg-8hr5 | MODERATE | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-84h7-rjj3-6jx4 | MODERATE | 4.2.8.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-8c42-7qj2-3j46 | MODERATE | 4.2.17.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-gcjf-9mgh-3p7g | MODERATE | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-hvcg-qmg6-jm4c | MODERATE | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-m4cv-j2px-7723 | MODERATE | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-q4f6-jm68-57ww | MODERATE | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-v8h7-rr48-vmmv | MODERATE | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-xxqh-mfjm-7mv9 | MODERATE | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-563q-j3cm-6jxm | MODERATE | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-5x3r-wrvg-rp6q | MODERATE | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-c2gf-v879-257j | MODERATE | 4.2.15.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-codec-http2` | 4.1.93.Final | GHSA-c69g-56f8-xwqj | MODERATE | 4.2.16.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-common` | 4.1.93.Final | GHSA-389x-839f-4rhx | MODERATE | 4.1.118.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-common` | 4.1.93.Final | GHSA-xq3w-v528-46rv | MODERATE | 4.1.115.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-6mjq-h674-j845 | MODERATE | 4.1.94.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler` | 4.1.93.Final | GHSA-fccg-mwvh-qqg4 | MODERATE | 4.2.17.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `junit:junit` | 4.12 | GHSA-269g-pwp5-87pp | MODERATE | 4.13.1 | APK — `:j2me` → `pngj:2.2.3` | #544 (APK) |
| `org.apache.commons:commons-compress` | 1.21 | GHSA-4265-ccf5-phj5 | MODERATE | 1.26.0 | classpath (AGP) | #546 (CI định) |
| `org.apache.commons:commons-compress` | 1.21 | GHSA-4g9r-vxhx-9pgx | MODERATE | 1.26.0 | classpath (AGP) | #546 (CI định) |
| `org.apache.commons:commons-lang3` | 3.16.0 | GHSA-j288-q9x7-2f5v | MODERATE | 3.18.0 | APK — `commons-compress` + classpath | #544 |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.0.21 | GHSA-r937-wjx7-w2jp | MODERATE | 2.4.20-Beta1 | classpath (KGP) | PR-4 (Kotlin) |
| `io.netty:netty-codec-http` | 4.1.93.Final | GHSA-fghv-69vj-qj49 | LOW | 4.2.5.Final | classpath (`grpc-netty` của AGP) | #471 (có) |
| `io.netty:netty-handler-proxy` | 4.1.93.Final | GHSA-45q3-82m4-75jr | LOW | 4.2.13.Final | classpath (`grpc-netty` của AGP) | #471 (có) |

Ghi chú cột "Bản vá": cột ghi bản vá của nhánh 4.2.x (họ `netty`) / 4.x (`protobuf-java`) do OSV trả về; cùng một cảnh báo thường có bản vá riêng cho nhánh đang dùng, nên cột này **không** có nghĩa phải nhảy major mới hết lỗi. `protobuf-java`: 3.25.5 (nhánh 3.x) và 4.27.5/4.28.2 (nhánh 4.x) — #546 ghim 3.25.5 để giữ nguyên major. Họ `netty`: 4.1.137.Final (nhánh 4.1.x, đúng bản #471 ghim) và 4.2.x (4.2.17.Final vá bản critical `netty-handler`).

## 5. Kế hoạch vá và hạn

| Việc | Nội dung | Phủ | Hạn |
|---|---|---|---|
| #471 (đã có, DeepSeek 6) | Ghim `netty` 4.1.137.Final (cả họ `io.netty`) + `freemarker` 2.3.35 trên classpath plugin | 41 cảnh báo netty (1 critical) + 1 critical freemarker | 11/11 — đang chờ gỡ chi tiết bảo mật khỏi mô tả + duyệt |
| #544 `phuthuoc/app-apk` | Ghim `guava` 33.7.2-jre, `commons-lang3` 3.18.0; loại `junit` khỏi runtime của `:j2me` | 1 high + 2 moderate **trong APK** | 11/11 (high) |
| #546 `phuthuoc/classpath` | Ghim nhóm chỉ build: `commons-io` 2.14.0, `jdom2` 2.0.6.1, `jose4j` 0.9.6, `guava` 33.7.2-jre, thử `protobuf-java`/`commons-compress` — CI đỏ thì bỏ dòng đó và ghi lý do; `sqlite-jdbc` chỉ ghim phòng ngừa (không chạm classpath plugin) | các high còn lại ở công cụ build | 11/11 |
| PR-4 (riêng) | Nâng Kotlin (Dependabot #216) — kéo `kotlin-gradle-plugin` lên bản vá | 1 moderate ở công cụ build | 15/11, cần PM chốt vì dễ vỡ build |
| Đóng có lý do | Cảnh báo không có bản vá tương thích với AGP 8.7.3 / Robolectric, hoặc chỉ công cụ build | phần còn lại | 15/11 |

Nguyên tắc: mỗi nhóm thư viện một PR; nâng phiên bản lớn (Kotlin, AGP, Gradle) tách PR riêng và ghi rõ rủi ro; PR nháp để CI build/test, máy sếp chỉ chạy Gradle khi cần tái hiện.

## 6. Việc cần sếp / PM

1. **Quyền đọc + đóng cảnh báo**: token của phiên này không đọc được API cảnh báo (403) nên không thể tự đóng. Cần token fine-grained có `Dependabot alerts: read` và `Code security: read/write` (hoặc sếp đóng tay theo mục 5). Trước khi có, mọi con số ở đây là số tự kiểm.
2. **CodeQL**: chưa đọc được danh sách cảnh báo code (cùng lý do quyền). Sẽ bổ sung khi có quyền.
3. **Chốt PR-4**: nâng Kotlin 2.0.21 → 2.4.x là thay đổi lớn (compose compiler, KSP, R8); cần PM đồng ý đánh đổi thời gian CI và rủi ro vỡ build trước 15/11.
4. PR Dependabot đang mở: #215 (`actions/checkout` 4 → 7), #290 (`actions/cache` 4 → 6), #216 (Kotlin), #217 (AGP 8.7.3 → 9.4.1). Hai PR actions không phải cảnh báo bảo mật — gộp riêng khi rảnh runner. #217 (AGP 9) là thay đổi lớn, không gộp cùng vá nhỏ.

## 7. Trạng thái và việc còn lại (cập nhật 09/11)

- **09/11 — hòa `main` sau khi gộp #544/#548**: #546 merge `origin/main` bằng merge commit (không rebase, không force push), giữ cả hai nhóm ghim trong `gradle/libs.versions.toml` (guava/commons-lang3 của #544; commons-io/jdom2/jose4j/protobuf/sqlite của #546). SBOM tự kiểm lúc 04:48Z (sau #544): **51 cảnh báo / 15 tổ hợp**, giảm 3 so với mốc 54 (hết `junit` 4.12 trong APK, hết `commons-lang3` 3.16.0 và `guava` 33.4.8 trên classpath); **không có cảnh báo mới**. `guava` 32.0.1-jre còn trong SBOM chỉ ở công cụ build (AGP/room-compiler/grpc), không vào APK. Dependabot API vẫn 403 nên số trên là tự kiểm.

- **#544/#546/#548 đều đã qua hai lượt duyệt** (lượt hai DeepSeek: #544 Đạt, #546 Đạt, #548 Cần sửa → đã sửa câu ghi chú cột "Bản vá" ở mục 4). CI trên head xanh; #544 và #546 `MERGEABLE` với `main`. Thứ tự gộp vẫn là **#544 trước**, rồi #546 hòa lại `main` và chạy lại CI trên head mới.
- **Ghim `sqlite-jdbc` là phòng ngừa, chưa đóng cảnh báo**: `dependencyInsight` không thấy `org.xerial:sqlite-jdbc` trong mọi cấu hình của `:app`; SBOM ghi 3.36.0 đến từ `androidx.room:room-compiler` 2.5.2 (classpath plugin). `force` trong `app/build.gradle.kts` không chạm classpath plugin, nên cảnh báo `GHSA-6phf-6h5g-97j2` chỉ đóng được khi cây plugin thật sự hết bản lỗi.
- **#544 và #546 CI xanh toàn bộ** (build hai flavor, unit test, CodeQL, kiểm gói thật). Đã kiểm lại cây sau khi ghim:
  `guava` 33.7.2-jre, `commons-lang3` 3.18.0, `commons-io` 2.14.0, `jdom2` 2.0.6.1, `jose4j` 0.9.6,
  `protobuf-java`/`protobuf-java-util` 3.25.5, `sqlite-jdbc` 3.41.2.2; `junit:junit` không còn trong runtime của APK.
- **Cảnh báo tự đóng sau khi gộp**: workflow *Automatic Dependency Submission (Gradle)* nộp lại cây phụ thuộc mỗi lần chạy; Dependabot đóng cảnh báo khi gói không còn bản lỗi. Phần đã vá không cần đóng tay.
- **Đề nghị đóng kèm lý do** (cần quyền, xem mục 6):
  - `org.apache.commons:commons-compress` 1.21 — 2 moderate. Đây là bản AGP kéo vào classpath plugin, **chỉ công cụ build**, không vào APK; nâng được chỉ khi lên AGP 9 (#217).
  - `org.jetbrains.kotlin:kotlin-gradle-plugin` 2.0.21 — 1 moderate. Chỉ công cụ build; nếu PM không duyệt PR-4 (nâng Kotlin) thì đóng bằng lý do này.
- **Chưa kiểm**: danh sách cảnh báo CodeQL — API trả 403 với token của phiên này.

## 8. Sau khi gộp #471 (09/11, 10:14Z) — phạm vi hẹp hơn kế hoạch

- PM gộp #471 lúc 10:14Z, `main` = `9112314`; workflow *Automatic Dependency Submission (Gradle)* chạy lại trên đúng head này (nộp SBOM 10:39Z). API Dependabot vẫn 403 nên số dưới đây là tự kiểm SBOM + OSV.
- **48 cảnh báo / 12 tổ hợp — 1 critical, 21 high, 24 moderate, 2 low**; không có mã cảnh báo nào ngoài bảng mục 4; **0 cảnh báo trong APK**.
- #471 đóng đúng phần `freemarker` 2.3.32 (1 critical) nhưng **không** chạm họ `netty` 4.1.93: SBOM sau gộp vẫn có đủ 41 cảnh báo netty, gồm critical `GHSA-c4c3-7fpv-j4q5` (`netty-handler`), cùng `guava` 32.0.1-jre, `protobuf-java` 3.22.3, `commons-io` 2.13.0, `commons-compress` 1.21, `sqlite-jdbc` 3.36.0 (`kotlin-gradle-plugin` 2.0.21 chờ #216/PR-4).
- Nguyên nhân: `force` trong `buildscript` chỉ áp cho cấu hình tên `classpath` của build script; AGP nạp qua `plugins {}` → `pluginManagement` nên cây `com.android.tools` 31.7.3 → `grpc-netty` 1.57.0 / `room-compiler` 2.5.2 vẫn giữ bản cũ. Gradle không có API force bắc cầu cho classpath plugin (chỉ `eachPlugin` đổi toạ độ plugin), nên **không mở PR mã mới**: sửa được chỉ khi lên AGP 9 (#217) hoặc đóng kèm lý do khi có quyền.
- Chỉ tiêu "hết họ netty" trong mục 5 dời sang #217; hạn 11/11 cho phần netty **không còn khả thi bằng ghim**, cần PM chốt hướng (AGP 9 hay đóng kèm lý do).

## 9. Sau khi gộp #575 (10/11, 00:14Z) — hết `sqlite-jdbc`, còn 47 cảnh báo

- PM gộp #575 (`0236007`): `:j2me` dùng Room 2.6.1, `org.xerial:sqlite-jdbc` lên 3.41.2.2 ngay tại nguồn (`room-compiler`), nên `GHSA-6phf-6h5g-97j2` (HIGH) **đã hết**; gói này không còn trong mục "đóng kèm lý do".
- SBOM mới nhất (đã có `room-compiler` 2.6.1 / `sqlite-jdbc` 3.41.2.2) đối chiếu OSV: **47 cảnh báo / 11 tổ hợp** — 1 critical, 20 high, 24 moderate, 2 low; không có mã nào ngoài bảng mục 4; vẫn **0 cảnh báo trong APK**. API Dependabot vẫn 403 nên đây là số tự kiểm.
- Phần còn lại không đổi: họ `netty` 4.1.93 (41 cảnh báo, 1 critical) cùng `guava` 32.0.1-jre, `protobuf-java` 3.22.3, `commons-io` 2.13.0, `commons-compress` 1.21, `kotlin-gradle-plugin` 2.0.21 — chỉ ở classpath công cụ build. Hướng xử lý vẫn là AGP 9 (#217, chờ PM chốt) hoặc đóng kèm lý do khi có quyền.

## 10. Lượt 10/11 (sau 08:14Z) — số không đổi; chỉ AGP 9 gỡ được phần còn lại

- API Dependabot vẫn 403 (thiếu `Dependabot alerts: read`); tự kiểm SBOM `main@9b55111` (đã có `room 2.6.1`) + OSV: **47 cảnh báo / 11 tổ hợp** — 1 critical, 20 high, 24 moderate, 2 low; **không mã mới**; **0 trong APK**. Không commit nào chạm `gradle/`, `*.gradle.kts`, `settings.gradle.kts` kể từ #575 (`0236007`), nên số trùng khớp lượt 00:58.
- Cả 47 cảnh báo đều ở classpath plugin Gradle: họ `netty` 4.1.93 (grpc-netty 1.57.0 của AGP 8.7.3), `protobuf-java` 3.22.3, `commons-io` 2.13.0, `commons-compress` 1.21, `guava` 32.0.1-jre, `kotlin-gradle-plugin` 2.0.21. `force` của build script không chạm classpath `plugins {}` nên không còn đường ghim.
- **Bằng chứng mới (POM Google Maven, 10/11)**: AGP **9.4.1 không còn phụ thuộc `io.grpc`/`io.netty`**; dùng `protobuf-java` 3.25.5, `commons-io` 2.16.1, `asm` 9.9. Còn AGP **8.13.2** chỉ lên grpc 1.69.1 → netty 4.1.110, vẫn dính cảnh báo cần 4.1.136, nên nâng trong dòng 8.x **không** đóng được critical. Vì vậy "PR kế tiếp" chỉ có nghĩa nếu là AGP 9.
- Hạn 11/11 **không đạt được bằng ghim**. Cần PM chốt một trong hai: (a) duyệt nhánh `phuthuoc/agp9` (Gradle 9 + AGP 9.4.1 + Kotlin ≥ 2.2, rủi ro vỡ build, tách khỏi A85), hoặc (b) đóng kèm lý do "chỉ công cụ build, không đóng gói" khi có token `Code security: write`.
- PR Dependabot đang mở (không có PR `phuthuoc/` nào): #216 (Kotlin) và #217 (AGP 9) đỏ CI từ 12/10; #215 (`actions/checkout`) đỏ `build`/`links`/`repeated-tests`; #290 (`actions/cache`) đỏ 1 job `build`.

## 11. Lượt 11/11 — số không đổi; công cụ tự kiểm thay cách làm tay

- API Dependabot vẫn **403** (thiếu `Dependabot alerts: read`). Tự kiểm SBOM `origin/main@266feabb` + OSV: **47 cảnh báo / 11 tổ hợp / 46 mã** — 1 critical, 20 high, 24 moderate, 2 low; **không mã mới**; **0 cảnh báo trong APK**. Trùng khớp lượt 02:50 vì 7 commit từ `d30bd47c` (gộp #586, #603, #604) **không chạm** `gradle/`, `*.gradle.kts`, `settings.gradle.kts`.
- 6 mã đã hết đúng như đã vá: `GHSA-27j2-h3m2-8237` (freemarker, #471), `GHSA-xxph`/`GHSA-j288`/`GHSA-269g` (guava/commons-lang3/junit, #544), `GHSA-3677`/`GHSA-2363`/`GHSA-6phf` (jose4j/jdom2/sqlite-jdbc, #546/#575) — không có mã nào quay lại.
- Thêm `scripts/kiem-phu-thuoc.py` (+ `scripts/test-kiem-phu-thuoc.py`, 27 test): đọc SBOM, hỏi OSV theo lô 1000, in bảng gói/phiên bản/mã/mức/bản vá và danh sách mã ngoài bảng; `--codes <tệp>` để so với mục 4. Lượt sau chỉ cần `gh api .../dependency-graph/sbom` rồi chạy một lệnh, không tự viết lại truy vấn OSV.
- Lượt hai duyệt DeepSeek bắt 5 lỗi, đã sửa hết: OSV trả thiếu kết quả ⇒ dừng với mã thoát 3 thay vì in "0 cảnh báo"; bọc `URLError`/`HTTPError`, thử lại 3 lần khi 429 hoặc 5xx; tự tính điểm CVSS 3.0/3.1 từ vector trần (OSV không kèm điểm) và quy vector 4.0 về 3.1 khi thiếu nhãn; `--codes` trỏ tệp thiếu trả 2 như lỗi SBOM; thêm `sbom.json` vào `.gitignore`; thêm `test-kiem-phu-thuoc.py` vào danh sách V57 để 27 test chạy trên CI — **token phiên này thiếu scope `workflow` nên 1 dòng đó chưa đẩy được, xem diff dán trong #605 để sếp áp**. **Bản Play: không áp dụng (công cụ).**
- PR Dependabot không đổi: #215 (4 đỏ: `build`, `links`, `repeated-tests`, `gate` huỷ), #216 (5 đỏ), #217 (4 đỏ), #290 (1 đỏ `build`); **0 review** cả bốn, head không đổi từ 09/11 (#215) và 12/10 (#216/#217) ⇒ **không mở PR ghim mới**.
- Vẫn chờ PM chốt hướng cho phần còn lại: (a) nhánh `phuthuoc/agp9`, hay (b) đóng kèm lý do "chỉ công cụ build, không đóng gói" khi có token `Code security: write`.

## 12. Lượt 12/11 — một tổ hợp mới ở classpath test của `:reader`, đã có PR ghim

- API Dependabot vẫn **403**; tự kiểm bằng `scripts/kiem-phu-thuoc.py` trên SBOM `origin/main@8310cc17`: **53 cảnh báo / 13 tổ hợp / 51 mã** — 3 critical, 23 high, 25 moderate, 2 low. So với lượt 11/11 (47/11/46) tăng đúng **một tổ hợp**: `org.bouncycastle:bcprov-jdk18on` **1.81** (critical `GHSA-574f-3g2m-x479`, `GHSA-9pwp-9qqc-pr26`; high `GHSA-p93r-85wp-75v3`, `GHSA-qp49-qgx5-5m26`; moderate `GHSA-c3fc-8qff-9hwx`).
- Nguồn: `:reader` (mô-đun mới của V142, gộp #457) khai `testImplementation(libs.robolectric)`; Robolectric 4.16.1 kéo `bcprov-jdk18on` 1.81 vào `debugUnitTestRuntimeClasspath`, còn `:reader` **không có** lệnh ghim BouncyCastle như `:app`. Đây là classpath **test**, không vào APK: `:app` ghim BouncyCastle 1.85 cho mọi cấu hình của nó và là nơi duy nhất đóng APK ⇒ vẫn **0 cảnh báo trong APK**.
- PR `phuthuoc/bc-test-1211`: thêm khối `force` BouncyCastle (số lấy từ `libs.versions.bouncycastle`) vào `reader/build.gradle.kts`, cùng kiểu với `:app`. Trước: `bcprov-jdk18on:1.81`; sau: `1.81 -> 1.85` trên `debugUnitTestRuntimeClasspath`. Hết 5 cảnh báo của tổ hợp này ⇒ dự kiến **48 cảnh báo / 12 tổ hợp / 46 mã** sau khi gộp.
- Bốn PR Dependabot vẫn **0 review**, head không đổi: #215 (3 đỏ `build`/`links`/`repeated-tests`), #216 (5 đỏ), #217 (5 đỏ), #290 (1 đỏ `build`). Vẫn chờ PM chốt (a) nhánh `phuthuoc/agp9` hay (b) đóng kèm lý do "chỉ công cụ build".
- Dòng `test-kiem-phu-thuoc.py` vào `.github/workflows/script-tests.yml` **vẫn chưa áp** trên `main` (token phiên thiếu scope `workflow`) — mục 11 còn nguyên hiệu lực.

### Việc còn lại cho sếp ở lượt này

Token phiên Trưởng nhóm Phụ thuộc thiếu scope `workflow`, nên 1 dòng dưới đây không đẩy được. Áp vào `.github/workflows/script-tests.yml` rồi gộp #605:

```diff
-      - name: Chạy đủ 10 test N04, báo lỗi sau khi chạy hết
+      - name: Chạy đủ test N04, báo lỗi sau khi chạy hết
             'test-crash-insight.py',
+            'test-kiem-phu-thuoc.py',
         )
```

Chưa áp thì V57 xanh **không** phải bằng chứng cho 27 test mới; vẫn chạy được tại chỗ bằng `python -X utf8 scripts/test-kiem-phu-thuoc.py`.

## 13. Lượt hai 12/11 — #609 đã gộp, số về đúng dự kiến, không có cảnh báo mới

- **#609 đã gộp** (`76ad5cae`, head `7557a0a3`). Tự kiểm trên SBOM `origin/main@ab8a2135`: **48 cảnh báo / 12 tổ hợp / 46 mã** — 1 critical, 21 high, 24 moderate, 2 low; **0 trong APK**. Đúng bằng con số dự kiến ở mục 12: 5 cảnh báo của `org.bouncycastle:bcprov-jdk18on` (2 critical, 2 high, 1 moderate) đã hết, `:reader` **không** còn khai 1.81 trên `debugUnitTestRuntimeClasspath`.
- **Không có mã mới**: chạy `scripts/kiem-phu-thuoc.py sbom.json --codes <63 mã ở mục 4>` trả *mã ngoài danh sách (0)*. Hai dòng `guava` 32.0.1-jre / 33.4.8-jre còn lại chỉ ở classpath công cụ build (AGP, `room-compiler`, `grpc-netty`), không đóng gói; critical duy nhất còn lại là `netty-handler` (classpath AGP), vẫn chờ #471.
- **Bốn PR Dependabot vẫn 0 review, head không đổi**: #215 `2ba1eae` (3 đỏ: `build`, `links`, `repeated-tests`), #216 `4e5f949` (**6 đỏ**, thêm `preview` so với lượt trước), #217 `ce0ac6a` (5 đỏ), #290 `5ce2b55` (1 đỏ `build`). Không có gì để vá thêm ở nhánh mình; vẫn chờ PM chốt (a) nhánh `phuthuoc/agp9` hay (b) đóng kèm lý do "chỉ công cụ build".
- **27 test V57 chạy tại chỗ OK** (`python -X utf8 scripts/test-kiem-phu-thuoc.py` → `Ran 27 tests … OK`). Thử đẩy dòng `test-kiem-phu-thuoc.py` vào `.github/workflows/script-tests.yml` lần này **vẫn bị từ chối** (`refusing to allow a Personal Access Token to create or update workflow … without workflow scope`) ⇒ diff ở mục 12 vẫn cần sếp áp, hoặc cấp scope `workflow` cho token của phiên.

