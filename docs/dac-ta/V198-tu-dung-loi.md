## V198 — Tự dựng lõi, ghim nguồn và kiểm toàn vẹn

- Người làm: Sol 2. Trạng thái: **Nháp**, sau V177; PR này chỉ kiểm kê/đặc tả, chưa triển khai mã hay đổi config.
- Giao việc: [PM/A81, 04/11/2026](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6622615233); mẫu A79, phối hợp [V193 G3](V193-cong-bao-ve.md).

### 1. Mục tiêu
Mỗi lõi người chơi tải có nguồn cố định, gói do Monika tự dựng và SHA-256 đã kiểm; lỗi cập nhật vẫn giữ lõi cũ. Quyền dùng hạ tầng riêng do cổng máy chủ V193 kiểm, việc tự dựng/hash không tự ngăn được fork.

### 2. Ngoài phạm vi
Chưa dựng/đăng gói trong PR đặc tả; không sửa game, APK, cổng Worker của V193, quyền repo hay khóa/token. Các app giả lập ngoài do người dùng cài không thuộc kho binary Monika.

### Kiểm kê tại main [fd8ec169](https://github.com/aowvn-10diem/aowvn-monika/commit/fd8ec169157b389fe43ece059fc03df56095a5af), configVersion 58
Đã đối chiếu mã cấu hình, workflow, metadata release/run và SOURCE/manifest của ba gói engine. “Có trong packs” tách riêng với “tự biên dịch”; chưa dựng lại hoặc thử máy thật trong lượt này.
| Lõi / nhóm (ghi đủ ID) | Nguồn tải đang chọn | Đã tự dựng từ nguồn? / bằng chứng |
|---|---|---|
| `mgba`, `gambatte`, `fceumm` | packs `cores-gb-gba-nes-1`, `artifacts` đủ 4 ABI, có hash | **Chưa**: [V32][S] chép ZIP buildbot, giải commit từ binary; [manifest/release][R] có 12 ZIP, không phải build nguồn |
| `desmume`, `melonds`, `melondsds`, `citra` | buildbot `nightly/android/latest/{abi}` [C] | Chưa thấy workflow tự dựng các core này; hai ID cuối chỉ khai báo arm64 |
| `pcsx_rearmed`, `ppsspp`, `mupen64plus_next_gles3`, `parallel_n64`, `flycast` | buildbot như trên [C] | Chưa; riêng PPSSPP còn tải `assets/system/PPSSPP.zip` từ buildbot, cũng phải ghim nguồn/hash |
| `snes9x`, `nestopia`, `genesis_plus_gx`, `easyrpg` | buildbot như trên [C] | Chưa; chưa có `artifacts`/hash theo ABI |
| `mednafen_pce_fast`, `mednafen_wswan`, `mednafen_ngp`, `handy`, `stella2014`, `prosystem` | buildbot như trên [C] | Chưa; chưa có `artifacts`/hash theo ABI |
| Module `azahar` | packs `engine-azahar-1`, arm64, hash [C] | **Có**: [run 36535181394](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/36535181394) job azahar SUCCESS; SOURCE ghim [662d412](https://github.com/AzaharPlus/AzaharPlus/commit/662d412123305a9f4be94dd3dc73ddf91a18c55e); khác core libretro `citra` |
| Module `kirikiri` | packs `engines-kirikiri-16`, arm64, hash [C] | **Có**: [run 38338600563](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/38338600563) SUCCESS; SOURCE ghim [6e61ce3](https://github.com/YuriSizuku/Kirikiroid2Yuri/commit/6e61ce3b81416ceb2be3427a5f0471edefab7151), kèm bản vá Monika |
| Module `onsyuri` | packs `engines-onsyuri-2`, WASM, hash [C] | **Có**: [run 36992329375](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/36992329375) job onsyuri SUCCESS; SOURCE ghim [08f744b](https://github.com/YuriSizuku/OnscripterYuri/commit/08f744b31cc1907b66a15f0402e62321a131ed81); lỗi cây ZIP hiện hữu theo skill, không coi run xanh là cài được |
| Module `rgss` | release `engines-rgss-6` của **repo app**, arm64, hash [C] | **Có**, chưa ở packs: [run 37126512701](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37126512701) SUCCESS; [release nguồn b668e08](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/engines-rgss-6); cổng giấy phép G8 còn áp dụng |
| Module `sevenzip` (phụ trợ) | packs `pack-sevenzip-1`, 2 ABI, hash [C] | **Chưa**: [publish-pack][P] chỉ chép ZIP có sẵn, mô tả lấy .so nguyên bản từ AAR 7-Zip-JBinding-4Android |
| `renpy8` (có bộ cài, chưa có trong `modules`) | Chưa có URL được cấu hình tại snapshot [C] | [Run 37073944824](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37073944824) đóng gói SDK/RAPT 8.5.3, **không** dựng `librenpython.so` từ nguồn; trạng thái kho riêng [CHƯA KIỂM] |
| `ruffle` (web Flash) | jsDelivr `@ruffle-rs/ruffle/ruffle.js` không ghim phiên bản [C] | Chưa; cần tự dựng cả JS/WASM từ commit ghim, không chỉ sao chép CDN |
| `j2me`, `libretrodroid` (vỏ chạy lõi) | Dựng cùng APK từ module nguồn trong repo [B] | Không phải lõi tải packs; giữ luồng APK hiện tại. MV/MZ/Tyrano dùng engine của game; app ngoài do người dùng cài, không có binary Monika để chuyển kho |

Kết quả: **22 core libretro = 3 snapshot packs + 19 buildbot**; chưa có core libretro nào được workflow Monika tự biên dịch. `cores.*.url` cả 22 vẫn là buildbot, nhưng `CoreManager` ưu tiên `artifacts[abi]` của 3 snapshot; không tính URL dự phòng đó thành 3 lượt tải buildbot hiện tại [M]. Kho `aowvn-monika-packs` hiện **công khai**, cây main [7c4132b](https://github.com/aowvn-10diem/aowvn-monika-packs/tree/7c4132ba968e49dcc5b46799c7e201cc53e6b496) chỉ README, nơi này lưu asset chứ chưa chứa workflow tự dựng.

### 3. Tiêu chí đạt / thiết kế workflow đề xuất
- [ ] `engines/sources.lock.json` liệt kê đủ 22 core, module/native phụ trợ, Ruffle và dữ liệu PPSSPP: repo nguồn, SHA **40 ký tự**, submodule, bản vá/hash, giấy phép, ABI thực có, NDK/SDK/Emscripten/Rust/phụ thuộc ghim. Không HEAD/master/latest; SHA/toolchain mới từng lõi **[CHƯA KIỂM]**, phải xác minh trong PR recipe, không đoán từ tag/HEAD.
- [ ] Workflow `build-cores.yml` đọc lock, matrix từng core/ABI, cache khóa theo lock/toolchain/ABI, giới hạn song song và thời gian. PR chỉ dựng/kiểm, quyền read, không secret; chế độ chuẩn bị mặc định không đăng. Job đăng riêng chỉ từ main của repo chủ đã duyệt, sau kiểm, PM kích hoạt; không nhận lệnh shell/ref tự do, không fallback sang repo công khai khi thiếu quyền kho đích.
- [ ] Mỗi gói kèm SOURCE/LICENSE, nguồn tương ứng + bản vá/script dựng, manifest hash/kích thước, symbols/BuildId; kiểm ZIP traversal, tên .so, ELF/ABI/NEEDED/JNI, WASM + cây file web. Test âm: thiếu lock/hash, sai ABI/hash, thiếu phụ thuộc, ZIP lỗi; smoke game tự sinh/không ROM, test máy thật còn thiếu ghi [CHƯA KIỂM]. Ren'Py/7-Zip phải dựng native, không nhận lại binary RAPT/AAR làm bằng chứng tự dựng.
- [ ] Sau khi PM cho đăng vào kho packs và cổng G3 sẵn sàng: xác minh hash tải lại từng asset, mỗi phiên bản mới bất biến (không ghi đè tag/asset cũ), sinh bản vá config để PR duyệt. Test thiếu ABI phải chặn trước mạng, tải ngắt/sai hash giữ lõi cũ; không quay lại buildbot/CDN nếu gói riêng lỗi. Kiểm tùy chọn core hiện có với binary mới và đọc save mẫu trước chuyển phiên bản.

### 4. Bản Play
`điều chỉnh`: đặc tả bao phủ nguồn lõi dùng chung, không thêm tải game/APK/link ngoài/ủng hộ vào Play. Trước PR mã phải đo lớp/config lõi thực có trong từng flavor; giữ stub/chặn dịch vụ hiện tại, chạy test hai flavor + quét APK Play khi sửa mã/config.

### 5. Config
**PR này không đổi.** Sau duyệt: tái dùng `cores.<id>.artifacts[abi]={url,sha256,version}` và `modules.<id>` hash/size/ABI, không ghi đè tùy chọn/bố cục; không mặc định ABI chưa dựng được. Dữ liệu PPSSPP và bộ JS/WASM Ruffle cần đề xuất trường/mặc định an toàn trong PR mã. Mỗi PR đổi JSON tăng `configVersion` so main lúc đó; không tự nâng `minVersionCode` hay đồng bộ/phát hành.

### 6. Chia PR (mỗi PR mã dẫn đặc tả này, chỉ bắt đầu sau V177 và PM duyệt đặc tả)
| # | Phạm vi | Phụ thuộc |
|---|---|---|
| 1 | Kiểm kê + đặc tả này (docs-only) | PM/A81 |
| 2 | Lock/provenance, workflow chuẩn bị và test offline, chưa đăng | 1 duyệt |
| 3a… | Recipe core nhẹ → NDS → 3D/PSP, chia PR nhỏ, báo rõ thiếu ABI | 2 |
| 4a… | Chuẩn hóa module tự dựng; dựng Ren'Py/7-Zip/Ruffle + dữ liệu PPSSPP | 2; giấy phép/G8 |
| 5… | Chuyển config từng nhóm đã kiểm, test hai flavor/rollback | 3/4 tương ứng; V193 G3 và PM xác nhận đường tải |

### 7. Rủi ro / cổng chuyển kho
Giữ nguyên save, phím, bố cục và lõi đang dùng; nâng core có thể đổi định dạng save state nên sao lưu trước và kiểm rollback. Công bố nguồn tương ứng theo giấy phép từng lõi, không dùng repo riêng để hạn chế quyền GPL; chưa đạt G8 không đăng RGSS. Tự dựng chỉ kiểm soát nguồn/phân phối, không phải bằng chứng chống giả mạo.
Theo V193: đường tải có kiểm quyền phía máy chủ → config/app dùng được → PM/sếp mới đổi visibility packs; giữ giai đoạn chuyển cho app cũ. Private GitHub asset không phải URL 302 công khai dùng được: G3 phải cung cấp đường CDN/R2 tải thật, không nhét token vào APK. V198 không làm lại cổng V193, không đổi quyền repo, không phát hành. Không proxy byte qua Worker; chỉ cấp link khi thiếu/nâng gói, dùng cache cục bộ, không gọi khi mở game đã cài; phép đo tải đồng thời 1000 người/chi phí **[CHƯA KIỂM]**.

[C]: https://github.com/aowvn-10diem/aowvn-monika/blob/fd8ec169157b389fe43ece059fc03df56095a5af/config/monika-config.json#L819
[S]: https://github.com/aowvn-10diem/aowvn-monika/blob/fd8ec169157b389fe43ece059fc03df56095a5af/scripts/core-snapshot.py#L42
[R]: https://github.com/aowvn-10diem/aowvn-monika-packs/releases/tag/cores-gb-gba-nes-1
[P]: https://github.com/aowvn-10diem/aowvn-monika/blob/fd8ec169157b389fe43ece059fc03df56095a5af/.github/workflows/publish-pack.yml#L30
[B]: https://github.com/aowvn-10diem/aowvn-monika/blob/fd8ec169157b389fe43ece059fc03df56095a5af/settings.gradle.kts#L24
[M]: https://github.com/aowvn-10diem/aowvn-monika/blob/fd8ec169157b389fe43ece059fc03df56095a5af/app/src/main/java/vn/aow/monika/runner/CoreManager.kt#L109
