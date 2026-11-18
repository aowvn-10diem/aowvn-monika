# Toàn bộ 22 lõi: có ký hiệu giải lỗi hay không (18/11/2026)

Tài liệu quyết định cho PM về ghim lõi + đóng gói symbols (V210). Bổ sung cho `docs/loi/ky-hieu-loi-1811.md` (bằng chứng chặn + đề xuất YAML + đề xuất ghim config).
Nền: `origin/main` `5f3a67b6`, `configVersion = 65`. Lượt kiểm: 18/11/2026.

## 0. Cách kiểm (để PM tin được, không phải đoán)

- Lấy **toàn bộ 22 lõi** khai trong `config/monika-config.json:cores`, không lọc theo nhóm lỗi.
- Mỗi lõi: xác định nguồn thật mà app dùng — có `artifacts` thì nguồn là release (`aowvn-monika-packs`), không có thì nguồn là `buildbot.../nightly/latest`. Tải ZIP công khai **không token, không đụng Cloudflare**, chạy:

```
python scripts/kiem-ky-hieu.py --json <zip-cua-loi>
```

- ABI kiểm: `arm64-v8a` cho cả 22 lõi (ABI duy nhất của `citra`, `melondsds`). Kiểm thêm `armeabi-v7a` cho 3 lõi có lượng lỗi cao và có ABI đó (bảng 1.1) để chắc rằng bị strip là **đồng nhất giữa các ABI**.
- Kết quả: **22/22 lõi kiểm được, 0 lõi `[CHƯA KIỂM]`** — không lõi nào bị mạng chặn trong lượt này. ZIP tải về bị xoá ngay sau khi đọc để không đội ngân sách ổ đĩa.
- Cột `lượt (số issue)` lấy từ **dữ liệu issue thật**: `gh issue list --state open --json number,body` → 82 issue mở, trong đó **73 issue** mang khối `aow-crash-v100` (trường `count` = lượt bot đã thông báo, cộng theo `Lõi:`); 9 issue còn lại là việc theo dõi, không có lõi. Tổng **1.300 lượt**, trong đó **965 lượt** gắn được với lõi trong config; 335 lượt còn lại là `unknown` 321, `kirikiroid2-yuri` 13, `mkxp-z` 1 — không phải lõi libretro trong config.

## 1. Bảng quyết định — 22 lõi

Sắp theo số lượt giảm dần. `build_id` và kích thước là của bản tải ngày 18/11; với 18 lõi `buildbot` các giá trị này **đổi mỗi đêm** (xem mục 2, nhận xét 3).

| lõi | nguồn | version | ABI | build_id | có `.symtab` | có `.debug_info` | kết luận | lượt (số issue) | gói symbols trên release |
|---|---|---|---|---|---|---:|---:|---|---:|---|
| `melondsds` | `buildbot` | `1` | `arm64-v8a` | `509efe113955dd6eaf96cd6f83f51c833840fd6a` | không | không | `THIEU_KY_HIEU` | 456 (12) | — |
| `ppsspp` | `artifacts` cores-ppsspp-1 | `cores-ppsspp-1` | `arm64-v8a` | `dd1e9133155a869785d2e1ffd35789a93f6eaaa7` | không | không | `THIEU_KY_HIEU` | 214 (5) | có (`symbols-ppsspp-arm64-v8a.zip`, `DAT`) |
| `citra` | `buildbot` | `1` | `arm64-v8a` | `6adb3a8b75ce50703db4ad089e9a9b3cc5868d45` | không | không | `THIEU_KY_HIEU` | 111 (8) | — |
| `desmume` | `buildbot` | `1` | `arm64-v8a` | `c8970571ec1419bd235be3de00fde2496550fcdc` | không | không | `THIEU_KY_HIEU` | 105 (10) | — |
| `pcsx_rearmed` | `buildbot` | `1` | `arm64-v8a` | `3c6d8b2178e92440b9bdd8bad848b6b14ee7b82e` | không | không | `THIEU_KY_HIEU` | 55 (2) | — |
| `snes9x` | `buildbot` | `1` | `arm64-v8a` | `8c0cc61e9f0473b00cb083e416a2a8be6255b854` | không | không | `THIEU_KY_HIEU` | 8 (3) | — |
| `mgba` | `artifacts` cores-gb-gba-nes-1 | `cores-gb-gba-nes-1-359b4bc58a6b2667` | `arm64-v8a` | `e8b3e8ea932a89d99536fde20da9c859d99cbcf0` | không | không | `THIEU_KY_HIEU` | 7 (4) | **không có** |
| `gambatte` | `artifacts` cores-gb-gba-nes-1 | `cores-gb-gba-nes-1-e6e1c00c8e7920b7` | `arm64-v8a` | `71617d3c645e5b9793ab6f6439a30ff5210afd6d` | không | không | `THIEU_KY_HIEU` | 5 (3) | **không có** |
| `melonds` | `buildbot` | `1` | `arm64-v8a` | `5621bf92194a2123d497c312d2d5249a10257e52` | không | không | `THIEU_KY_HIEU` | 4 (2) | — |
| `easyrpg` | `buildbot` | `1` | `arm64-v8a` | `4aa343b3e448bb5195177c386b957a7ad8205275` | có | có | `DAT` | 0 (0) | — |
| `fceumm` | `artifacts` cores-gb-gba-nes-1 | `cores-gb-gba-nes-1-b1fb1f953f32a894` | `arm64-v8a` | `05f83a2bb48c8a2d9fec1e874788b4d13c1f5996` | không | không | `THIEU_KY_HIEU` | 0 (0) | **không có** |
| `flycast` | `buildbot` | `1` | `arm64-v8a` | `4ac6c071899e9d3e9bfa356de2523cfa30321cc8` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `genesis_plus_gx` | `buildbot` | `1` | `arm64-v8a` | `bdaaf680c8f87845df0275fa3a654c0a77a8f578` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `handy` | `buildbot` | `1` | `arm64-v8a` | `07d66da7a06f8f64124029413c806e879bc931bd` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `mednafen_ngp` | `buildbot` | `1` | `arm64-v8a` | `b7c10abb07b72bc217ee8f85400a0696a3cd77bf` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `mednafen_pce_fast` | `buildbot` | `1` | `arm64-v8a` | `f740753423359df865c519fbf9591a294919590d` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `mednafen_wswan` | `buildbot` | `1` | `arm64-v8a` | `bf2191f3e7eb2c35717a40d1e235486ffbee2108` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `mupen64plus_next_gles3` | `buildbot` | `1` | `arm64-v8a` | `daebe2522947b188bf7e26d078b32810e883616b` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `nestopia` | `buildbot` | `1` | `arm64-v8a` | `cf2985ee29afea4ba1dccbe55f9c7324518825b2` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `parallel_n64` | `buildbot` | `1` | `arm64-v8a` | `026729c3862cb1267828fbcfe5bd1cc1b2e09c0f` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `prosystem` | `buildbot` | `1` | `arm64-v8a` | `686835b4102b20bde272edf7173a6fc5351f2aa7` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |
| `stella2014` | `buildbot` | `1` | `arm64-v8a` | `8374ad46e60f7312373aaa3ce44ac4fc88901eb0` | không | không | `THIEU_KY_HIEU` | 0 (0) | — |

Tổng: **22 lõi · 1 `DAT` (easyrpg) · 1 có gói symbols riêng (ppsspp) · 20 runtime bị strip và chưa có gói symbols**.

### 1.1. Kiểm thêm ABI thứ hai (chứng minh không phải chuyện riêng của `arm64-v8a`)

| lõi | ABI | kiến trúc | build_id | có `.symtab` | kết luận |
|---|---|---|---|---|---|
| `desmume` | `armeabi-v7a` | `arm` | `91be21c09c879c7c3d6c4bbf07e67eb1cbaadf99` | không | `THIEU_KY_HIEU` |
| `pcsx_rearmed` | `armeabi-v7a` | `arm` | `e87d9fffa9569a1786d6ccae8af224178c7a9160` | không | `THIEU_KY_HIEU` |
| `ppsspp` | `armeabi-v7a` | `arm` | `5fa7608ea827b868f4dbb55098352e719a6598ef` | không | `THIEU_KY_HIEU` |

## 2. Kết luận ngắn cho PM

| Nhóm | Số lõi | Lượt gán được | Ghi chú |
|---|---:|---:|---|
| **Đã có đủ ký hiệu ngay trong bản chạy** | 1 | 0 | `easyrpg` — buildbot tình cờ còn `.symtab` + `.debug_info` (30,4 MB), không cần làm gì |
| **Bản chạy bị strip nhưng ĐÃ có gói symbols trên release** | 1 | 214 | `ppsspp` — `symbols-ppsspp-arm64-v8a.zip` đã kiểm `DAT`, `build_id` khớp `manifest.json`; chỉ thiếu một thứ: app không báo `build_id` |
| **Bản chạy bị strip và CHƯA có gói symbols** | 20 | 751 | 18 lõi `buildbot` (`version="1"`, URL `latest`) + `mgba`/`gambatte`/`fceumm` (đã ghim `cores-gb-gba-nes-1` nhưng release **không có** `symbols-*`) |
| **Tổng** | **22** | **965** | phần còn lại 335 lượt không gắn lõi libretro (`unknown` 321, `kirikiroid2-yuri` 13, `mkxp-z` 1) |

Ba nhận xét quyết định:

1. **Vấn đề không phải "vài lõi lỗi" mà là mặc định của đường ống**: 20/22 lõi không có ký hiệu, phủ 751/965 lượt gắn được. Chỉ `easyrpg` là may mắn, và `ppsspp` là trường hợp đã làm đúng mẫu.
2. **`build_id` là mắt xích rẻ nhất và đang chặn cả 214 lượt của `ppsspp`**: gói symbols đã nằm sẵn trên release, nhưng báo cáo chỉ có `cfg v1` (`CoreManager.kt:137-140`) nên không biết mở gói nào. Không cần dựng thêm gì để cứu 214 lượt này.
3. **18 lõi buildbot đổi `build_id` mỗi đêm** → `sha256`/`build_id` trong bảng trên chỉ đúng cho bản 18/11. Muốn ghim thật thì phải dựng từ nguồn hoặc mirror ZIP, không thể ghim URL `latest` (chi tiết ở `ky-hieu-loi-1811.md` mục 4).

### Thứ tự 3 việc rẻ nhất để phủ hết 965 lượt

| # | Việc | Công sức | Phủ | Lõi |
|---:|---|---|---:|---|
| 1 | **Ghi `build_id` vào `info.txt`** (`CoreManager.kt:137-140`, ~1 dòng): đọc `manifest.json` trong gói lõi (đã có `buildId`) hoặc ghi sha256 của `.so` | 1 dòng + 1 test | **214** | `ppsspp` (gói symbols đã có sẵn) |
| 2 | **Dựng + đăng `symbols-*` cho 4 lõi đã có trong `engines/sources.lock.json`** (`melondsds`, `desmume`, `pcsx_rearmed`, `snes9x`) bằng recipe hiện có + bước YAML ở `ky-hieu-loi-1811.md` mục 3.2 | dùng lại recipe, 0 lõi mới | **624** | 4 lõi Đợt 1 của V210 |
| 3 | **`citra` (111) + `melonds` (4) + thêm `symbols-*` cho `cores-gb-gba-nes-1`** (`mgba` 7, `gambatte` 5, `fceumm` 0) | 2 lõi mới vào lock + dựng lại 1 release | **127** | 5 lõi còn lại |

Cộng 214 + 624 + 127 = **965 lượt** — tức phủ 100% số lượt gắn được với lõi libretro, không cần chạm 13 lõi 0 lượt (`fceumm` đã nằm trong việc 3 vì cùng release; 12 lõi 0 lượt còn lại để sau, không cấp bách).

Điều kiện để việc 2 và 3 có giá trị: **việc 1 phải xong trước**, vì không có `build_id` trong báo cáo thì gói symbols đóng gói ra cũng không tra được. Đây là lý do xếp việc 1 lên đầu dù nó chỉ là 1 dòng.

## 3. Điều còn hở (ghi rõ, không giấu)

| Hở | Trạng thái |
|---|---|
| Chỉ kiểm `arm64-v8a` cho 22 lõi + `armeabi-v7a` cho 3 lõi | `x86`/`x86_64` **chưa kiểm** — các ABI này chỉ dùng cho máy giả lập/emulator, không phải máy người dùng thật; nếu cần thì chạy lại cùng lệnh với `{abi}` tương ứng |
| `build_id`/kích thước của 18 lõi buildbot | Chỉ đúng cho bản 18/11, đổi mỗi đêm — dùng để chứng minh "bị strip", không dùng làm giá trị ghim |
| `easyrpg` còn `.debug_info` (bản chạy 30,4 MB) | Không phải lỗi, nhưng nếu muốn giảm dung lượng tải thì đó là ứng viên strip — ngoài phạm vi tài liệu này |
| Gói symbols của `cores-gb-gba-nes-1` | Không có trong release (đã kiểm danh sách asset: chỉ có 12 ZIP lõi + `manifest.json`, **không** `symbols-*`, **không** `sources-*`) |

## 4. Chạy lại

```bash
# từng lõi
python scripts/kiem-ky-hieu.py citra_libretro_android.so.zip --json

# cả 22 lõi: đọc đúng URL trong config (artifacts nếu có, ngược lại buildbot), tải ZIP rồi chạy công cụ
python - <<'PY'
import io, json, pathlib, subprocess, sys, tempfile, urllib.request
cfg = json.load(io.open("config/monika-config.json", encoding="utf-8"))["cores"]
for core, c in cfg.items():
    art = c.get("artifacts") or {}
    abi = "arm64-v8a"
    url = art[abi]["url"] if abi in art else c["url"].replace("{abi}", abi)
    with tempfile.TemporaryDirectory() as tmp:
        dest = pathlib.Path(tmp) / "core.zip"
        req = urllib.request.Request(url, headers={"User-Agent": "kiem-ky-hieu"})
        with urllib.request.urlopen(req, timeout=90) as r, open(dest, "wb") as f:
            f.write(r.read())
        print("==", core, flush=True)
        subprocess.run([sys.executable, "scripts/kiem-ky-hieu.py", "--json", str(dest)])
PY
```

Dữ liệu thô của lượt kiểm này: 22 dòng kết quả JSON + bảng lượt theo lõi (ZIP đã xoá ngay sau khi đọc, không lưu trong repo).
