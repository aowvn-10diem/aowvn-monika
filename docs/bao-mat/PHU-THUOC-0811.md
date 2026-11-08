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
| `org.xerial:sqlite-jdbc` | 3.36.0 | 1 | HIGH 1 | test (Robolectric) |
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
| `com.google.protobuf:protobuf-java` | 3.22.3 | GHSA-735f-pc8j-v9w8 | HIGH | 4.28.2 | classpath (AGP) | #546 (CI định) |
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
| `org.xerial:sqlite-jdbc` | 3.36.0 | GHSA-6phf-6h5g-97j2 | HIGH | 3.41.2.2 | test (Robolectric) | #546 (test) |
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

## 5. Kế hoạch vá và hạn

| Việc | Nội dung | Phủ | Hạn |
|---|---|---|---|
| #471 (đã có, DeepSeek 6) | Ghim `netty` 4.1.137.Final (cả họ `io.netty`) + `freemarker` 2.3.35 trên classpath plugin | 41 cảnh báo netty (1 critical) + 1 critical freemarker | 11/11 — đang chờ gỡ chi tiết bảo mật khỏi mô tả + duyệt |
| #544 `phuthuoc/app-apk` | Ghim `guava` 33.7.2-jre, `commons-lang3` 3.18.0; loại `junit` khỏi runtime của `:j2me` | 1 high + 2 moderate **trong APK** | 11/11 (high) |
| #546 `phuthuoc/classpath` | Ghim nhóm chỉ build: `commons-io` 2.14.0, `jdom2` 2.0.6.1, `jose4j` 0.9.6, `guava` 33.7.2-jre, `sqlite-jdbc` (test); thử `protobuf-java`/`commons-compress` — CI đỏ thì bỏ dòng đó và ghi lý do | các high còn lại ở công cụ build | 11/11 |
| PR-4 (riêng) | Nâng Kotlin (Dependabot #216) — kéo `kotlin-gradle-plugin` lên bản vá | 1 moderate ở công cụ build | 15/11, cần PM chốt vì dễ vỡ build |
| Đóng có lý do | Cảnh báo không có bản vá tương thích với AGP 8.7.3 / Robolectric, hoặc chỉ công cụ build | phần còn lại | 15/11 |

Nguyên tắc: mỗi nhóm thư viện một PR; nâng phiên bản lớn (Kotlin, AGP, Gradle) tách PR riêng và ghi rõ rủi ro; PR nháp để CI build/test, máy sếp chỉ chạy Gradle khi cần tái hiện.

## 6. Việc cần sếp / PM

1. **Quyền đọc + đóng cảnh báo**: token của phiên này không đọc được API cảnh báo (403) nên không thể tự đóng. Cần token fine-grained có `Dependabot alerts: read` và `Code security: read/write` (hoặc sếp đóng tay theo mục 5). Trước khi có, mọi con số ở đây là số tự kiểm.
2. **CodeQL**: chưa đọc được danh sách cảnh báo code (cùng lý do quyền). Sẽ bổ sung khi có quyền.
3. **Chốt PR-4**: nâng Kotlin 2.0.21 → 2.4.x là thay đổi lớn (compose compiler, KSP, R8); cần PM đồng ý đánh đổi thời gian CI và rủi ro vỡ build trước 15/11.
4. PR Dependabot đang mở: #215 (`actions/checkout` 4 → 7), #290 (`actions/cache` 4 → 6), #216 (Kotlin), #217 (AGP 8.7.3 → 9.4.1). Hai PR actions không phải cảnh báo bảo mật — gộp riêng khi rảnh runner. #217 (AGP 9) là thay đổi lớn, không gộp cùng vá nhỏ.
