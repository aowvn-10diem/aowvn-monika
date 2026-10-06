# V41 — Dependabot #76 upload-artifact v7

Phạm vi: tám workflow `build`, `check-packs`, `periodic-check`, `pm-digest`, `publish-cores` (job prepare), `rgss-real-games`, `engine-signals`, `user-report`. Đường native/manual, Emulator Test, Test Lab và engine publication giữ phiên bản của main; chưa chạy được trên bản nâng này. Không dispatch phát hành để lấy coverage.

Đạt khi: Luna độc lập đọc release notes/kiểm phạm vi, Build xanh đúng head và các step upload bị đổi đã chạy thành công ít nhất một lần; acting PM duyệt riêng trước merge. Không gộp cùng lúc với #78 đụng cùng workflow.

Release notes đã đọc: [v6](https://github.com/actions/upload-artifact/releases/tag/v6.0.0), [v7](https://github.com/actions/upload-artifact/releases/tag/v7.0.0), [v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1). Node24 cần runner >=2.327.1 (runner Build thực tế2.337.0). v7 chuyển ESM, thêm upload file trực tiếp qua `archive:false`; PR không bật tính năng đó, giữ ZIP/tên/artifact output hiện tại và consumer download-artifact v4. Không đổi permissions/retention/fallback publish.

Bằng chứng head Dependabot cũ f242c5c: Build37259467267, Periodic37259467455, prepare37259467167, R6 37259467249, Signals37259467218, UserReport37259467254 đã SUCCESS. PMdigest37259467207 CANCELLED, preview cần kiểm trên head mới. Publish và issue mutation SKIPPED. Đây là cơ sở lựa chọn phạm vi; cần kiểm lại CI đúng head sau nhập main/thu hẹp diff, chưa coi head mới đạt.

`./gradlew testDebugUnitTest` local thiếu Android SDK; CI đúng head/Luna mới [CHƯA KIỂM]. Giả định: chia bản nâng lớn theo workflow đã có đường kiểm, phần còn lại giữ main trong cùng PR (PM phản đối thì đổi). Không phát hành hay thêm URL game.
