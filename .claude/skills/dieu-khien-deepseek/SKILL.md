---
name: dieu-khien-deepseek
description: Điều khiển DeepSeek (flash/pro) qua Tokenize bằng tools/ds/ds.py để giao việc cơ học cho DS (viết test, rà mã/tài liệu/chuỗi, đồng bộ tài liệu, người kiểm thử) với quy trình chọn model/effort, mẫu prompt đã chạy tốt, cách rà đầu ra và ngưỡng dừng. Dùng khi sếp hoặc PM nhắc "DeepSeek", "DS", "Tokenize", "Deepseek API", việc nhãn ds-ok (DS-01..), hoặc khi muốn tiết kiệm token Claude bằng cách giao việc cho model khác. Dùng TRƯỚC khi gọi ds.py lần đầu trong phiên.
---

# Điều khiển DeepSeek qua Tokenize

Số đo ở đây lấy từ lô DS-worker 30–31/10/2026 (PM-003, ~29 request, mẫu nhỏ). Gói Tokenize hết hạn **06/11/2026**; trần token mới 5h = 12,8 triệu / 400 request, 24h = 25,6 triệu / 1.200, 7 ngày = 128 triệu / 5.600 (ds.py giữ dưới 85%). Cả lô dùng ~3% trần 5h: **hạn mức không phải điểm nghẽn, nguồn việc phù hợp mới là**.

## 1. Vai và luật
- Bạn là phiên Sonnet điều khiển, nhãn **"Deepseek API"**. DS chỉ **sinh nháp**; bạn rà từng mục với mã thật rồi mới commit. Không tin lời DS về mã.
- Mở đầu mô tả PR: dòng `Deepseek API`. Thư hỏi PM: `Từ: Deepseek API` ở dòng đầu. Tiền tố commit: `[viec-DS-xx]`. Mỗi việc một PR nhỏ (nhánh `deepseek/DS-xx`), Luna rà, CI xanh, không đẩy `main`. Không đổi tiền tố commit đã có.
- Chỉ nhận việc nhãn `ds-ok` ở `docs/opus/KE-HOACH.md` (mục DS-worker). Muốn thêm việc: hỏi PM bằng `hoi-<số>-ds-<chủ-đề>.md`. Báo cáo PM `pm-tra-loi-<số>.md` tối đa 5 dòng: việc xong / token mới so với trần / cache % / tỉ lệ sửa / vướng.
- **Không giao DS:** `AppGraph.kt`, ký/phát hành APK, engine native, config đụng `configVersion`/Cloudflare, kiến trúc, tiền duyệt PR (vai Luna), mọi thứ có secret/keystore/ROM/file người dùng. Tokenize là bên thứ ba. Quyết định chi phí, key, số dư: báo thẳng sếp, không gửi PM.
- Không bao giờ in, ghi, commit key. Chỉ kiểm biến `TOKENIZE_API_KEY` có hay không. Thiếu key thì dừng báo sếp, đừng tự tìm key.

## 2. Chạy nhanh (đừng dựng tay)
```bash
export DS_WORK=<thư mục scratchpad của phiên>      # mọi tệp tạm (system, đầu vào, đầu ra) để ở đây, KHÔNG trong repo
S=.claude/skills/dieu-khien-deepseek
$S/ds-chay.sh hard high $DS_WORK/he-thong.txt $DS_WORK/ra.md "chỉ thị ngắn" -- $DS_WORK/dau-vao-1.txt $DS_WORK/dau-vao-2.txt
```
`ds-chay.sh` tự: lấy `tools/ds/ds.py` từ `origin/main` (hoặc nhánh PR #381 `ccr-37c8378a-bgfkia` nếu chưa gộp) vào `$DS_WORK/ds/`, áp `ds-stream-effort.patch` (streaming + cờ `--effort`), bật streaming, đặt `DS_EXTRA_ROOTS`, thử lại khi bị cắt kết nối (không thử lại lỗi chặn tệp/thiếu key), in một dòng tóm tắt (model, token mới, cache %). Cờ `CẮT-CỤT` hoặc file ra rỗng nghĩa là DS nghĩ hết `max_tokens` (xem mục 5).
- **Không commit `tools/` hay `.mcp.json` vào PR việc** (luật PM-003). Giữ bản ds.py ở `$DS_WORK`, repo sạch (hook dừng phiên sẽ kêu nếu có file untracked).
- `ds.py` từ chối tệp nhạy cảm (`*.pem`, `.env`, `local.properties`, `google-services.json`...) và tệp ngoài repo/`DS_EXTRA_ROOTS`. Đừng lách. Chỉ đưa tệp đã `git show` từ nhánh, kiểm không có secret.
- Mức dùng: `python3 $DS_WORK/ds/tools/ds/ds.py usage`. Sổ đếm theo từng máy; không chạy lô chồng giờ với phiên khác.
- Mẫu prompt đã chạy tốt ở `mau/` (đổi phần NHIỆM VỤ theo việc): `ra-soat-chuoi`, `viet-test`, `kiem-thu`, `dong-bo-tai-lieu`. Bảng sự thật từ mã: `trich-su-that.sh origin/main > $DS_WORK/su-that.md`.

## 3. Chọn model và effort
| | `simple` = `deepseek-v4.1-flash` | `hard` = `deepseek-v4-pro` |
|---|---|---|
| Viết test cho mã có sẵn | 12/12 đúng (ChtFormat); 6/7 (1 test sai offset) | 11/11 đúng; 7/7 |
| Rà văn phong có ngữ cảnh đầy đủ | 5 mục: 4 đúng + 1 hedged | 5 mục: 3 dùng được, 2 sở thích |
| Người kiểm thử bắt lỗi biên dịch | bắt 1/1 | bắt 1/2 (lượt sau bỏ sót) |
| Người kiểm thử bắt lỗi sai offset (khi yêu cầu kiểm dữ liệu dựng) | bắt | bắt |
- **Tham số:** `reasoning_effort` ∈ `none|low|high|max`, chạy cả `/v1/messages` (ds.py) lẫn `/chat/completions` (OpenCode). `none` = không suy nghĩ. **Dùng `high`**; `max` làm server hay cắt kết nối và chưa thấy tốt hơn. Bỏ tham số = mặc định của Tokenize (cũng có suy nghĩ).
- Hai model **ngang nhau** ở các việc đo được và bổ sung nhau (mỗi bên bắt vài mục bên kia sót). Việc quan trọng: chạy cả hai rồi hợp nhất. Flash rẻ hơn nên làm bản đầu.

## 4. Giao việc cho đạt (làm đúng ngay lần đầu)
1. **Kiểm tiền đề trước khi giao** (cách rẻ: `grep`, `git show`). Hai việc bị lệch tiền đề: DS-01 ("dịch" chuỗi nhưng `values/` của j2me vốn đã là tiếng Việt), DS-06 ("tiếng Anh sót" nhưng chỉ còn mã, URL, tên nút). Việc cơ học có dữ liệu sẵn (DS-04 gom crash) thì **dùng script, không dùng DS**.
2. **Phần cố định vào `system`** (`-s`): vai, nguyên tắc, khung đầu ra, mẫu giọng, danh sách ca đã phủ. Phần đổi để cuối. Chạy 1 request làm nóng cache rồi mới song song (≤3 cùng lúc). Cache dùng chung flash/pro. Mỗi request mất ~2.000 token mới không cache được; cần tiền tố ≥ ~18k token để cache ≥ 90%. Chạy 8 song song từ nguội thường cho cache 0%.
3. **Ngữ cảnh đầy đủ**: định hướng dự án (`CLAUDE.md`), mẫu giọng (README/CHANGELOG), toàn bộ chuỗi, ±3 dòng mã quanh mỗi chỗ (`git grep -n -C3`). Thiếu ngữ cảnh là nguyên nhân chính của các mục sai (DS không biết "Khóa web API" là nhãn của RetroAchievements, "cho bạn yêu 💖" là giọng thương hiệu).
4. **Quy trình bắt DS làm trong prompt**: lập ứng viên → với mỗi ứng viên tự phản biện (cố ý? đổi nghĩa? người dùng có bị cản?) → trích nguyên văn + `file:dòng` → có mục "Đã cân nhắc và bỏ" → cột "Độ chắc". "Thà bỏ sót còn hơn báo sai." Cấm đề xuất trùng chuỗi hiện tại hay ghi "giữ nguyên".
5. **Viết test**: đưa mã chính, các test hiện có (để khỏi trùng) và danh sách bẫy Kotlin (có trong `mau/viet-test.system.txt`): mẫu chuỗi `"$a_b"` ăn cả `_b` (dùng `"${a}_b"`), gán Int vào phần tử `ByteArray` phải là hằng số hoặc `.toByte()`, JUnit 4.13.2 `assertThrows(IOException::class.java) { ... }`. Bảo DS lần theo mã từng dòng rồi mới chốt kỳ vọng, "không chắc 100% thì bỏ ca".
6. **Đồng bộ tài liệu**: đưa bảng sự thật trích bằng lệnh (có file:dòng); dữ kiện ngoài bảng (trạng thái PR, cổng của sếp) thì trỏ nguồn, không để DS đoán. Tài liệu ghi "ảnh chụp ngày X" là lịch sử, chỉ thêm con trỏ.

## 5. Bẫy vận hành đã vấp
| Hiện tượng | Nguyên nhân | Cách xử |
|---|---|---|
| `Remote end closed connection` / `Connection reset by peer` | request không-streaming dài (effort max, đầu vào lớn) hoặc nhiều request cùng lúc | dùng streaming (wrapper đã bật), `high`, ≤3 song song |
| Tệp ra rỗng, `ra` = đúng `max_tokens` | DS nghĩ hết trần, chưa kịp viết (đầu vào lớn, effort cao) | `hard` đầu vào lớn dùng `DS_MAX_TOKENS=30000` (theo cấu hình OpenCode của sếp, model cho tới 32.768); khối suy nghĩ vẫn bị tính tiền |
| cache 0% | chạy song song từ nguội | làm nóng bằng 1 request tuần tự |
| `nằm ngoài thư mục cho phép` | tệp ngoài repo | để trong `$DS_WORK`; wrapper đã đặt `DS_EXTRA_ROOTS` |
| Gradle không chạy được | cloud không có Android SDK, Maven trả 429, tải compiler ngoài bị chặn | **CI là phép biên dịch duy nhất**; ghi rõ "chưa chạy Gradle" trong PR |

## 6. Rà đầu ra (bắt buộc, trước mọi commit)
- Mỗi mục: `git show origin/main:<file> | sed -n '<dòng>p'` so với trích dẫn của DS. Sai dòng hay sai nguyên văn là bỏ. Đếm **tỉ lệ phải bỏ/sửa**.
- **Ngưỡng dừng và báo sếp:** hơn 30% đầu ra phải sửa tay, hoặc cache dưới 80% dù tiền tố ≥ 20k token.
- Test DS viết: tự lần từng offset/regex/`check(...)` của mã chính, tìm lỗi biên dịch (đọc như trình biên dịch). Đây là chỗ hai lỗi thật đã lộ: `"cheat$i_code"` (không biên dịch) và test ghi sai offset (`bytes[1]` thay vì `16*2048+1`, nên `assertThrows` không bao giờ ném).
- **DS làm người kiểm thử chỉ là lớp phụ**: không ổn định (Pro bắt 1/2 lần). Yêu cầu nó kiểm rõ "dữ liệu dựng có chạm đúng điều kiện/offset mà tên test nói không". Lớp chốt là bạn đọc và CI.
- Đừng đẩy nhận định của DS về mã chính thành sự thật; ghi vào mô tả PR dưới nhãn "phát hiện, không sửa" (ví dụ `toInt()` tràn với chỉ số quá lớn).

## 7. Việc hợp và không hợp (đã đo)
| Hợp | Không hợp (hoặc cần rà nặng) |
|---|---|
| Viết test biên cho mã thuần (có CI kiểm) | Phán xét văn phong **thiếu ngữ cảnh** (lần đầu bỏ ~50% mục); có ngữ cảnh đầy đủ thì dùng được |
| Rà chuỗi UI với ngữ cảnh đầy đủ, hợp nhất hai model | Quyết định kiến trúc, phát hành, config có `configVersion` |
| Người kiểm thử đọc mã (lớp phụ) | Việc có dữ liệu sẵn và cơ học (DS-04): dùng script |
| Đề xuất đồng bộ tài liệu từ bảng sự thật | Dữ kiện ngoài bảng (trạng thái PR, ai làm gì): để DS đoán là bịa |

## 8. Checklist mỗi phiên
1. `TOKENIZE_API_KEY` có không (chỉ có/không). 2. `git fetch origin main ccr-37c8378a-bgfkia`. 3. Đọc `KE-HOACH.md` mục DS-worker và `BANG-TIN.md` (thư PM mới). 4. Kiểm tiền đề việc (mục 4.1). 5. Làm nóng cache, chạy flash + pro ở `high`. 6. Rà từng mục, tính tỉ lệ. 7. Một PR nhỏ/việc, header `Deepseek API`, ghi rõ chưa chạy Gradle. 8. Báo PM ≤5 dòng; chi phí/key/số dư báo sếp.
