# V41 — Dependabot #78 setup-gradle v6

Phạm vi: bảy workflow Build/check-packs/Periodic/CodeQL(java)/R6/Signals/UserReport. Release, Emulator Test, native-check, Ren'Py module, EKA2L1 native và Test Lab giữ phiên bản main; chưa chạy được trên bản nâng. Không dispatch thêm signing/publish để lấy coverage.

Đạt khi: Luna đọc breaking changes và review độc lập; Build đúng head xanh, từng step setup-gradle đổi đã chạy thành công và các workflow trong phạm vi không đỏ; acting PM duyệt riêng trước merge. Gộp lần lượt sau #76 nếu cùng workflow.

Release notes [v6.0.0](https://github.com/gradle/actions/releases/tag/v6.0.0) đổi cache sang component đóng nguồn và bỏ hỗ trợ configuration-cache cũ; notes [v6.3.0](https://github.com/gradle/actions/releases/tag/v6.3.0) có cache protocol v2. Chọn `cache-provider: basic` cho toàn bộ step v6 được đổi: [DISTRIBUTION.md](https://github.com/gradle/actions/blob/v6/DISTRIBUTION.md) nói basic dùng actions/cache/MIT và không nạp component proprietary. Repo không bật configuration-cache trong các workflow này. Không đổi Gradle wrapper hay quyền.

Head cũ43d653b: Build37261312593, CodeQL37261312564, R637261312570, Signals37261312573, pack37261312568, Periodic37261312816 SUCCESS. UserReport37261312562 FAIL; không gọi cả PR xanh.

Đã đọc artifact11325117402 user-report-api34: ảnh cuối là launcher; game pid3022 tự gửi SIG9 sau SDL onPause/onStop/onDestroy lúc04:04:28; không có report JSON. Gradle unit/assemble đều SUCCESS. Nguyên nhân activity kết thúc [CHƯA KIỂM], chưa kết luận do Gradle hay runner, chưa hạ assertion/chạy lại liên tục. CI head mới sẽ kiểm lần nữa sau thay provider và nhập main.

Local YAML kiểm7 step/provider PASS; `./gradlew testDebugUnitTest` thiếu Android SDK. CI head mới và Luna [CHƯA KIỂM]. Giả định: thu hẹp major upgrade theo đường kiểm được, chọn basic nguồn mở thay default enhanced (PM phản đối thì đổi). Không sửa app/native/game hay đóng dấu gameplay từ trạng thái PID.
