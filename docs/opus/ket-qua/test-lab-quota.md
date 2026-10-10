# Hạn mức Test Lab — UTC, mỗi máy thật tính một lượt

SOL023: tối đa5/ngày, kể cả FAIL; máy thật chỉ cho engine cần ARM. UI/Robo dùng máy ảo. Cả hai workflow tuần tự chung concurrency; V56 một máy/cách instrumentation, không chạy lại K2 SIGSEGV trước Sonnet sửa. K1–K8 đã chung một instrumentation; RGSS chung lượt chưa có runner [CHƯA KIỂM], không tự phát sinh lượt riêng.

| Ngày UTC | Run ID | Workflow | Máy | Mục đích / kết quả | Head | Đơn vị |
|---|---|---|---|---|---|---|
| 2026-10-08 | 37721419669 | test-lab.yml | [CHƯA KIỂM] | G9 Robo lịch sử; dự trữ bảo thủ2 theo workflow cũ, chưa đối chiếu model/usage thật | 59c15402174bdd23f38e759f11423a9353194f97 | 2 |
| 2026-10-08 | 37796105693 | test-lab.yml | cubs37,grizzly37 | V60: cả hai Passed, logs đã đọc; không gameplay | 9dc977417368e0678aa7a4ad47dcd2cdad3c7f96 | 2 |
| 2026-10-08 | 37819533688 | test-lab-engine-games.yml | cubs37,grizzly37 | PM/SOL024: K1 PASS, K2 SIGSEGV; chưa Sol đọc toàn bộ evidence, không claim gameplay đạt | 9f35f8d2c0dbca0b864d0caedaf29e8de7f42482 | 2 |

| 2026-10-08 | 37837095996 | test-lab-engine-games.yml | cubs37,grizzly37 yêu cầu trong workflow cũ; không claim máy đã chạy | [DỰ TRỮ] PM rerun sau #157: run FAIL/prepare113516805680, collection BLOCKED files0/bytes0; PM ghi hết quota. Dự trữ bảo thủ2, usage thật [CHƯA KIỂM] | fc095be131ac938441858171d1198864697742b7 | 2 |

Tổng dự trữ bảo thủ8Oct=8, trong đó2 lịch sử và2 lượt mới chỉ dự trữ, chưa xác minh usage thật; dừng máy thật, không nói đã dùng chính xác8 hay vượt quota Firebase thực tế. Các ngày sau phải đối chiếu mọi run đã bắt đầu với sổ này trước khi chạy. Không ghi project/bucket/token/URL ký. Mỗi kết quả mới do Sol cập nhật bằng PR, không workflow push main.

## CI chuẩn bị trên PR160

Head293c044: policy/Build/Coverage SUCCESS; instrumentation run37828610683/job113487830295 FAIL trước GCP ở bước dựng app: `platforms;android-33` tải lỗi `Error on ZipFile unknown archive` (license đã accepted). Cập nhật cài SDK33 rõ ràng và đọc kiểm android.jar trước Gradle; không rerun máy thật hoặc chuyển CI/review cũ sang head sửa mới. Chờ CI exact head mới.

## Chặn rerun theo review Luna/Ultra 08/10

Luna6067757250 và Ultra6068094261 chỉ ra cùng RUN_ID được GitHub giữ khi rerun. Chọn chặn mọi attempt vật lý khác1 (kể cả thiếu/không hợp lệ) **trước đọc Git/API/GCP**; chỉ lần đầu mới được loại RUN_ID hiện tại khỏi lịch sử. Không cho retry failed jobs/whole workflow để đi qua cổng. Muốn chạy sau ngày chờ vẫn phải là dispatch mới được PM cho phép, kiểm exact Build, delta và ledger; không tự dispatch trong lượt sửa này. Hai regression kiểm attempt2 dừng trước API/Git và attempt thiếu/hỏng/later failclosed. Robo máy ảo không chịu rào attempt vật lý, vẫn không tự rerun.

PM run37837095996 đã FAIL; metadata+logs collection đọc: BLOCKED0files/0bytes, không bằng chứng K2 mới. PM KEH ghi chặn quota và sẽ thử09Oct15:05VN; Sol không tạo lịch/dispatch thay PM. Dự trữ2 bảo thủ không phải claim Firebase đã tính thêm2. CI/review của head470 không chuyển sang head sửa này.

Lượt bị từ chối chưa chạy máy vẫn tính dự trữ trong budget, nhưng không trở thành baseline engine đã kiểm. Baseline giữ lần physical thực tế trước đó; thêm regression cho tách hai ý nghĩa này. Tổng7test preflight +6collector PASS local; CI exact head mới chưa kiểm.

## SOL025 — lịch sử mọi attempt

API `/runs/{id}/jobs` mặc định chỉ cho attempt mới nhất, nên attempt bị chặn có thể che attempt vật lý trước. Đọc rõ `/runs/{id}/attempts/{n}/jobs` cho mọi attempt, tối đa10/run, 40lượt API; lịch sử/jobs bị cắt hoặc ngày/attempt không rõ thì BLOCKED trước GCP. Run tạo hôm qua nhưng attempt mới bắt đầu hôm nay vẫn tính ngày UTC hôm nay.

Sổ phải có cả run lẫn đủ đơn vị cho mọi attempt trong ngày. Intent FAIL/BLOCKED cũng dự trữ: tên bước mới xác định một máy thì1; workflow cũ/không rõ số máy thì2 bảo thủ. Robo có tên bước máy ảo rõ ràng không tính vật lý; không suy từ job mới skipped rằng attempt cũ chưa chạy. Regression tái hiện attempt1physical/attempt2blocked/sổ thiếu hoặc thiếu đơn vị và kiểm cổng chặn; không claim đây là usage Firebase chính xác. Không dispatch/rerun để kiểm policy.
