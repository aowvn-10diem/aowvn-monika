# Hàng việc đội ChatGPT ×5 (sếp chốt 13/10/2026) [A39]

**Kết luận:** đội ChatGPT vừa được tăng hạn mức, nên chạy tối đa. Chế độ tiết kiệm A36 **chỉ còn áp cho đội Claude**. Mỗi người có sẵn hàng việc dưới đây: **xong việc nào thì lấy ngay việc kế, không chờ PM giao**. Kênh lệnh vẫn là issue #119.

## Luật chạy ×5
- **WIP:** Sol 6 PR mở, Sol High 6, Luna 3 và Luna Ultra mỗi người 4 PR tự làm (ngoài việc duyệt).
- **Nhịp:** kiểm #119 và PR của mình mỗi **20 phút** khi đang mở phiên. Có việc thì làm liền nhiều việc trong một lượt.
- **Duyệt trong ≤ 1 giờ:** Luna 3 tiền duyệt, Luna Ultra duyệt lượt hai PR app hoặc đường phát hành. Duyệt là ưu tiên số 1 của hai Luna, trước việc tự làm.
- **Mô tả PR sai là nit** (A22). Bị "Cần sửa" thì sửa trong cùng lượt.
- **Giữ nguyên:** gộp PR và phát hành chỉ PM làm; không đụng khóa, token, Secrets; không commit game, ROM hay link game; chạy `./gradlew testDebugUnitTest` trước mỗi push có mã.
- Cần secret mới (ví dụ `CRASH_ADMIN_TOKEN` cho Actions) thì ghi ở #119 để sếp tạo. Trong lúc chờ, làm phần dùng dữ liệu giả trước.

## Sol (app, CI, workflow)
| Thứ tự | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V100 | **Bot gom báo cáo lỗi**: workflow theo lịch mỗi giờ, đọc báo cáo trên Worker, gom theo chữ ký (lõi + loại + 3 frame đầu). Chữ ký mới hoặc tăng ≥ 5 lần thì mở/cập nhật issue nhãn `crash-moi`, gán người theo lõi (azahar/kirikiri/mkxp → Sonnet; còn lại → Sol). Test bằng JSON giả | Test offline xanh; chạy thật khi sếp có secret |
| 2 | V101 | Báo cáo `lowmem` và `anr` phải ghi lõi, hệ máy, giai đoạn đang chạy (78/143 báo cáo hiện thiếu) | Test đơn vị; báo cáo mẫu có đủ trường |
| 3 | V102 | Script và workflow tự tăng `versionCode`/`versionName` và sinh mục CHANGELOG từ tiêu đề PR đã gộp kể từ tag trước; **mở PR, không tag** | Chạy thử ra PR đúng mẫu CHANGELOG |
| 4 | V103 | Cổng ổn định: script hỏi Worker xem bản X có chữ ký lỗi mới nào trong N giờ không. Có thì dừng, kèm danh sách | Test offline; PM dùng trước khi chuyển bản ổn định |

## Sol High (test, thiết bị, nâng cấp)
| Thứ tự | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V104 | **Sửa tận gốc 2 test chập chờn**: `LibraryStorageAccessTest.kt:72/76`, `V69UxTest.kt:84/88` (không cách ly, không bỏ qua) | Chạy 20 lần liên tiếp xanh, ghi kết quả vào PR |
| 2 | V105 | Ma trận máy Test Lab theo dữ liệu lỗi: thêm Snapdragon 8 Gen 3 và một máy MediaTek đời mới (đang văng 3DS/Kirikiri), giữ quota 5 lượt/ngày | Workflow chạy được, ghi rõ máy trong V105.md |
| 3 | V106 | Flow Maestro cho NDS (vào game, chạm khi đang nạp, thoát rồi vào lại) và 3DS (vào game) bằng ROM tự sinh hoặc homebrew hợp lệ | Flow xanh trên CI emulator |
| 4 | V107 | Sau khi v0.8.3 ổn định: thử nâng Kotlin 2.4 (#216) và AGP 9.4 (#217) trên nhánh riêng | CI xanh hoặc báo cáo chỗ vỡ |

## Luna 3 (duyệt trước, rồi tài liệu và test)
| Thứ tự | Mã | Việc |
|---|---|---|
| 0 | L07 | Tiền duyệt mọi PR của Sol, Sol High, Sonnet, Nova trong ≤ 1 giờ |
| 1 | V108 | Cập nhật `docs/TEST-THUC-TE.md` cho v0.8.3: thêm mục 3DS (vào game, không văng), NDS (thời gian nạp, vào lại không nạp lại), Kirikiri gửi báo lỗi |
| 2 | V109 | Test đơn vị cho V97 (cache nạp NDS) và V98 (cờ driver GPU) nếu PR gốc chưa đủ |

## Luna Ultra (duyệt lượt hai, rà sâu)
| Thứ tự | Mã | Việc |
|---|---|---|
| 0 | L07-U | Duyệt lượt hai PR app hoặc đường phát hành trong ≤ 1 giờ sau Luna 3 |
| 1 | V110 | Bảng cổng phát hành v0.8.3 (theo V73): CI đúng SHA, CHANGELOG, version, 2 lượt Đạt |
| 2 | V111 | Báo cáo lỗi theo phiên bản (0.8.2 và 0.8.3): số báo cáo theo lõi và giai đoạn, top 5 chữ ký, đề xuất việc sửa. Cập nhật mỗi ngày trong `docs/opus/ket-qua/V111.md` |
