# Gỡ chặn chẩn đoán native: ký hiệu lõi + `build_id` (18/11/2026)

Phạm vi: **chỉ thêm công cụ kiểm và tài liệu**. Không sửa `.github/workflows/**`, không sửa `config/monika-config.json`, không sửa mã app.
Nền: `origin/main` `5f3a67b6`. Mọi số dòng tính theo mốc này.
Quy ước: suy luận chưa có lệnh chạy kèm thì ghi **[CHƯA KIỂM]**.

## 0. Kết luận trước

| Việc | Trạng thái | Bằng chứng |
|---|---|---|
| Lõi tải từ buildbot **không có ký hiệu** | Đã chứng minh bằng công cụ mới, trên ZIP thật | mục 1.1 |
| App **không biết** máy đang chạy bản lõi nào | Đã chứng minh bằng `tệp:dòng` | mục 1.2 |
| Công cụ kiểm ký hiệu dùng được, không cần `readelf` | Đạt: 15/15 ca test + 4 tệp thật | mục 2 |
| Đóng gói `symbols-<lõi>-<build_id>.zip` + `manifest.json` | **Đề xuất YAML**, chưa áp dụng (đang có PR mở chạm `build-cores.yml`) | mục 3 |
| Ghim lõi + `sha256` + `version` thật trong config | **Đề xuất**, tệp khoá → PM/Thi công quyết | mục 4 |

## 1. Bằng chứng chặn

### 1.1. Lõi buildbot bị strip: không `.symtab`, không `.debug_info`

Tải ZIP công khai (không token), chạy `python scripts/kiem-ky-hieu.py …`:

| Tệp (trong ZIP) | Kiến trúc | `build_id` | `.symtab` | `.debug_info` | `.dynsym` | Kích thước | Kết luận |
|---|---|---|---|---:|---:|---:|---:|---|
| `citra_libretro_android.so` | `arm64` | `6adb3a8b75ce50703db4ad089e9a9b3cc5868d45` | không | không | có | 31.502.656 B | `THIEU_KY_HIEU` |
| `desmume_libretro_android.so` | `arm64` | `c8970571ec1419bd235be3de00fde2496550fcdc` | không | không | có | 3.363.168 B | `THIEU_KY_HIEU` |

Lệnh đã chạy (mã thoát `1`, đúng như thiết kế):

```
python scripts/kiem-ky-hieu.py citra_libretro_android.so.zip desmume_libretro_android.so.zip
```

`build_id` của citra khớp giá trị phiên Lỗi 3 đã đọc bằng `readelf` (`6adb3a8b…`), nên bộ đọc ELF thuần Python cho cùng kết quả với `readelf`/`llvm-readelf`.

ZIP dùng để kiểm:

| ZIP | sha256 |
|---|---|
| `citra_libretro_android.so.zip` | `fb802c291733b4bb409863f15f3e4b50ba173fd2ffa3cbc7117c78219feec1ee` |
| `desmume_libretro_android.so.zip` | `577ffb3a6546105c28bb7751cb436e09eafc1391bbbca1fa3b372adb50bb54bd` |

Đối chứng chiều ngược lại — lõi **đã ghim và có gói ký hiệu** (`cores-ppsspp-1`) cho `DAT`:

```
python scripts/kiem-ky-hieu.py symbols-ppsspp-arm64-v8a.zip
→ libs/dd1e9133155a869785d2e1ffd35789a93f6eaaa7/ppsspp_libretro_android.so
  arm64 · dd1e9133155a869785d2e1ffd35789a93f6eaaa7 · .symtab có · .debug_info có · .dynsym có · 206.975.768 B · DAT
```

`build_id` này trùng `manifest.json` của release (`artifacts.arm64-v8a.buildId = dd1e9133…`) và sha256 của ZIP (`c380cd0dc8c085e338245556e443fa7bedf0f548f5687f51fb17e86ff66292c6`) trùng `symbols.sha256` trong manifest — tức đường ống của lõi đã ghim là **đúng chuẩn**, chỉ 3 nhóm lõi trôi bản là chưa đi đường đó.

Kết luận 1.1: không phải app "không biết giải ký hiệu", mà **không có gì để giải** — lõi buildbot chỉ còn `.dynsym` (bảng động, không có tên hàm nội bộ C++ và không có dòng nguồn).

### 1.2. App chỉ ghi `version="1"`: báo cáo không mang `build_id`/`sha256`

| Chỗ | Nội dung | Ý nghĩa |
|---|---|---|
| `config/monika-config.json:1120-1127` | `"citra": { "version": "1", "url": ".../nightly/android/latest/{abi}/citra_libretro_android.so.zip" }` | không `sha256`, URL `latest` đổi bản mỗi đêm |
| `config/monika-config.json:821-823` | `"desmume": { "version": "1", "url": ".../latest/{abi}/desmume_libretro_android.so.zip" }` | như trên |
| `config/monika-config.json:1129-1131` | `"melondsds": { "version": "1", ... latest ... }` | như trên |
| `config/monika-config.json:958-960` | `"ppsspp": { "version": "cores-ppsspp-1", "url": ".../releases/download/cores-ppsspp-1/ppsspp-{abi}.zip" }` | mẫu đúng: đã ghim |
| `app/src/main/java/vn/aow/monika/runner/CoreManager.kt:135-140` | `versionFile(id).writeText(def.version)` · `info.txt` = `"cfg v${def.version} · $abi · …KB · tải <ngày> · bản dựng <Last-Modified>"` | chuỗi máy nhận được chỉ là `cfg v1 · arm64-v8a · …` |
| `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:326` | `Diagnostics.coreInfo(this@RetroActivity, AppGraph.cores.info(coreId))` (dòng 323 là `Diagnostics.stage(..., "core-download-failed")`, không phải lệnh này) | đính nguyên chuỗi trên vào phiên chạy |
| `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:115` | `appendLine("Lõi tải: ${it.coreInfo}")` | chuỗi đó vào thẳng báo cáo gửi lên |
| `app/src/main/java/vn/aow/monika/config/MonikaConfig.kt:311` | `data class CoreArtifact(val url: String, val sha256: String, val version: String)` | chỗ chứa `sha256` đã có sẵn, chỉ chưa dùng cho 3 lõi này |
| `app/src/main/java/vn/aow/monika/runner/CoreManager.kt:145-146` | `throw IOException("Ảnh chụp lõi thiếu hash/version")` | app **đã bắt buộc** hash khi đi đường `artifacts` — nên chỉ cần khai báo là dùng được |

Hệ quả: báo cáo `native` chỉ có `cfg v1` + ngày tải. Không `build_id`, không `sha256` → kể cả khi có gói symbols, **không biết mở gói nào**; ngược lại khi đã biết `build_id` của lõi (việc app làm được) thì cũng không có gói symbols để mở.

### 1.3. Quy mô đang bị chặn (đếm 18/11, `label:crash-moi`, `state:open`)

Nguồn: metadata `aow-crash-v100` trong thân issue (trường `count` = lượt bot đã thông báo, `len(seen)` = số báo cáo khác nhau). 74 issue mở, tổng **1.300 lượt** — khớp bảng tồn 18/11.

| Nhóm | Số issue | Lượt | Máy |
|---|---:|---:|---:|
| `ppsspp/native` | 4 | **206** | 65 |
| `desmume/native` | 5 | **97** | 39 |
| `citra/core-error` | 2 | **57** | 14 |
| `citra/native` | 2 | 40 | 12 |
| **Tổng 3 nhóm** (ppsspp + desmume + citra `core-error`, cách gộp trong bản giao việc) | 11 | **360** | 118 |
| Nếu gộp thêm `citra/native` | 13 | 400 | 130 |

Tất cả đều là khung C++ trong `.so` — chỉ giải được khi có `.symtab`/`.debug_info` cùng `build_id`. Đây là lý do 360–400 lượt hiện **không thể chẩn đoán**, không phải thiếu dữ liệu người dùng.

## 2. Công cụ kiểm: `scripts/kiem-ky-hieu.py`

Chỉ thư viện chuẩn (`struct`, `zipfile`, `json`, `argparse`) — tự đọc ELF 32/64-bit, cả little/big endian, đọc `build_id` từ khối `SHT_NOTE`/`PT_NOTE` (nên đọc được cả lõi **đã strip mất bảng section**, chỉ còn `PT_NOTE`), có dự phòng quét mẫu note GNU.

```
python scripts/kiem-ky-hieu.py out/core/*.so          # bảng
python scripts/kiem-ky-hieu.py core.zip --json        # JSON cho CI
```

| Cột | Ý nghĩa |
|---|---|
| `tệp` | `tên.so` hoặc `zip:tên/trong/zip.so` (ZIP nhiều `.so` thì mỗi `.so` một dòng) |
| `kiến trúc` | `arm64`, `arm`, `x86_64`… từ `e_machine` |
| `build_id` | hex GNU build-id, `(thiếu)` nếu không có |
| `.symtab` / `.debug_info` / `.dynsym` | có/không (`.zdebug_info` cũng tính là `debug_info`) |
| `kích thước` | byte của `.so` (không phải của ZIP) |
| `kết luận` | `DAT` khi có `build_id` **và** `.symtab`; còn lại `THIEU_KY_HIEU` |

| Mã thoát | Khi nào |
|---:|---|
| 0 | mọi `.so` đều `DAT` |
| 1 | có ít nhất một `.so` `THIEU_KY_HIEU` (thiếu `build_id` hoặc thiếu `.symtab`) |
| 2 | sai cách dùng, tệp không đọc được, không phải ELF, ZIP không có `.so` |
| 3 | **lỗi nội bộ của công cụ** — không phải kết luận về ký hiệu |

Console Windows mặc định (cp437/cp1252) từng làm `print("thiếu")` ném `UnicodeEncodeError` và tiến trình thoát **1**, trùng mã `THIEU_KY_HIEU` — tức một lỗi môi trường bị đọc thành kết luận nghiệp vụ. Từ bản này `main()` gọi `chuan_hoa_console()` (`sys.stdout.reconfigure(encoding="utf-8", errors="replace")`, tương tự cho `stderr`) và mọi lỗi ngoài dự kiến trả **3**. Đã kiểm trên console **cp437, không đặt `chcp 65001`**: bản cũ thoát 1 với stdout 0 byte + `UnicodeEncodeError`; bản mới in đủ bảng, `citra` → `THIEU_KY_HIEU` (1), `easyrpg` → `DAT` (0), thiếu tệp → 2.

Vì sao không dùng `readelf`: `scripts/package-native-symbols.py:12-24` gọi `readelf` ngoài tiến trình — máy Windows của đội không có, nên không kiểm được gói tải về trước khi mở issue. Công cụ mới chạy được ở cả Windows và runner Ubuntu, không thêm phụ thuộc.

### 2.1. Test: `scripts/test-kiem-ky-hieu.py` — 15/15 đạt

ELF giả lập dựng bằng byte (không tải mạng), mỗi ca chạy đúng dòng lệnh để kiểm cả bảng in ra lẫn mã thoát:

| # | Ca | Kỳ vọng |
|---:|---|---|
| 1 | ELF64 `arm64` đủ `.symtab` + `.debug_info` + build-id | `DAT`, thoát 0 |
| 2 | ELF64 chỉ `.dynsym` | `THIEU_KY_HIEU`, thoát 1 |
| 3 | ELF32 `arm` đủ ký hiệu | `DAT`, thoát 0 |
| 4 | ELF32 big-endian `mips` | đọc đúng kiến trúc, thoát 0 |
| 5 | ELF có `.symtab` nhưng **không** build-id | `(thiếu)` + `THIEU_KY_HIEU`, thoát 1 |
| 6 | ELF **mất bảng section** (chỉ `PT_NOTE`, giống lõi buildbot) | vẫn đọc ra build-id từ `PT_NOTE`, `THIEU_KY_HIEU`, thoát 1 |
| 7 | ZIP 2 `.so`: 1 đạt + 1 thiếu | thoát 1, in cả 2 dòng, "đủ ký hiệu 1 · thiếu 1" |
| 8 | ZIP 2 `.so` đều đạt | thoát 0, "Tổng: 2 tệp .so" |
| 9 | ZIP không có `.so` | thoát 2, "không có tệp .so" |
| 10 | tệp không phải ELF | thoát 2, "không phải ELF" |
| 11 | nhiều tệp `.so` cùng lúc | thoát 0, tổng 2 |
| 12 | đủ 8 cột bảng + `--json` đúng `build_id`/`ket_luan`/`loi` | khớp |
| 13 | `.zdebug_info` được tính là `debug_info` | `DAT` |
| 14 | console Windows **cp1252** (`PYTHONIOENCODING=cp1252`, không `chcp`): in tiếng Việt | `DAT`, thoát **0**, không `UnicodeEncodeError` |
| 15 | lỗi nội bộ (`doc_tep` ném `RuntimeError`) | thoát **3** + `LỖI NỘI BỘ`, **không** phải 1 |

```
python scripts/test-kiem-ky-hieu.py
→ Ran 15 tests … OK   (máy Windows in ResourceWarning của tempfile → chạy thêm `python -W ignore scripts/test-kiem-ky-hieu.py`)
```

**Chưa vào CI:** `.github/workflows/script-tests.yml:33-44` liệt kê tên test **thủ công**, nên `test-kiem-ky-hieu.py` chỉ chạy trên máy cho tới khi Thi công thêm một dòng vào danh sách đó — việc này chạm workflow nên để chung đợt với mục 3 (xem mục 3.4 về thứ tự PR).

## 3. Đề xuất đóng gói symbols (**chỉ đề xuất — KHÔNG sửa workflow**)

### 3.1. Hiện trạng `build-cores.yml` đã gần đúng, thiếu 3 thứ

| Dòng | Nội dung | Vấn đề |
|---|---|---|
| `.github/workflows/build-cores.yml:75-84` | chạy `prepare-core-build.py --core … --abi … --out out/core` | đã dựng từ nguồn, đã sinh ký hiệu — **nhưng chỉ cho lõi có mục trong `cores`** |
| `engines/sources.lock.json:48` | `cores` chỉ có `fceumm`, `gambatte`, `mgba`, `ppsspp` | `melondsds`, `desmume`, `pcsx_rearmed`, `snes9x`, `citra`, `melonds` chỉ nằm ở `inventory.cores` (`:10-34`, danh sách tên) → **phải viết recipe trước** khi dựng (xem `docs/loi/ky-hieu-toan-bo-1811.md` mục 2) |
| `scripts/prepare-core-build.py:441` | `symbols = stage / f"symbols-{core_id}-{abi}.zip"` | tên gói **theo ABI, không theo `build_id`** → hai bản dựng khác `build_id` trùng tên |
| `scripts/prepare-core-build.py:295-298` | đọc `Build ID:` bằng `llvm-readelf`, lưu `buildId` vào `provenance.json` | có `build_id` nhưng không đưa vào tên gói/artifact |
| `.github/workflows/build-cores.yml:85-89` | `upload-artifact` tên `source-build-<core>-<abi>`, `retention-days: 3` | gói symbols chết sau 3 ngày, không có job đăng release, không có `manifest.json` riêng để tra `build_id` ↔ `sha256` |

Nên đề xuất **không phải viết lại đường ống**, mà là: đặt tên theo `build_id`, chặn bằng công cụ kiểm, và đăng lên release thay vì artifact 3 ngày.

### 3.2. YAML đề xuất — thêm vào job `build`, ngay sau bước dựng và **thay** bước `upload-artifact` hiện có

```yaml
      - name: Kiểm ký hiệu lõi (chặn nếu thiếu .symtab)
        env:
          CORE_ID: ${{ matrix.core }}
          CORE_ABI: ${{ matrix.abi }}
        run: |
          set -euo pipefail
          python3 scripts/kiem-ky-hieu.py out/core/"$CORE_ID"_libretro_android.so

      - name: Đóng gói symbols theo BuildId + manifest
        id: symbols
        env:
          CORE_ID: ${{ matrix.core }}
          CORE_ABI: ${{ matrix.abi }}
        run: |
          set -euo pipefail
          python3 - <<'PY'
          import hashlib, json, os, pathlib, shutil, zipfile
          core, abi = os.environ["CORE_ID"], os.environ["CORE_ABI"]
          out = pathlib.Path("out/core")
          prov = json.loads((out / "provenance.json").read_text())
          build_id = prov["buildId"]
          src = out / f"symbols-{core}-{abi}.zip"          # do prepare-core-build.py sinh
          name = f"symbols-{core}-{abi}-{build_id}.zip"
          shutil.move(src, out / name)
          sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
          core_zip = next(out.glob(f"{core}-{abi}.zip"))
          manifest = {
              "core": core, "abi": abi, "buildId": build_id,
              "coreFile": core_zip.name,
              "coreSha256": prov["files"][f"{core}_libretro_android.so"]["sha256"],
              "symbolsFile": name, "symbolsSha256": sha(out / name),
              "symbolsSize": (out / name).stat().st_size,
              "sourceCommit": prov["commit"], "builderCommit": prov["builderCommit"],
              "ndk": prov["toolchain"]["ndk"], "lockSha256": prov["lockSha256"],
          }
          (out / f"symbols-{core}-{abi}-{build_id}.json").write_text(
              json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
          with open(os.environ["GITHUB_OUTPUT"], "a") as f:
              print(f"build_id={build_id}", file=f)
              print(f"symbols_file={name}", file=f)
          PY

      - uses: actions/upload-artifact@v7
        with:
          name: symbols-${{ matrix.core }}-${{ matrix.abi }}-${{ steps.symbols.outputs.build_id }}
          path: |
            out/core/symbols-${{ matrix.core }}-${{ matrix.abi }}-*.zip
            out/core/symbols-${{ matrix.core }}-${{ matrix.abi }}-*.json
          retention-days: 90
          if-no-files-found: error
```

### 3.3. YAML đề xuất — job đăng gói (job mới, chỉ chạy khi bấm tay)

```yaml
# Thêm job này vào mục `jobs:` của .github/workflows/build-cores.yml (thụt vào 2 dấu cách so với `jobs:`).
publish:
    if: github.repository == 'aowvn-10diem/aowvn-monika' && github.event_name == 'workflow_dispatch'
    needs: [plan, build]
    runs-on: ubuntu-24.04
    permissions: {contents: write}
    steps:
      - uses: actions/download-artifact@v5
        with: {pattern: 'symbols-*', path: out/symbols, merge-multiple: true}
      - env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          REPO: aowvn-10diem/aowvn-monika-packs
        run: |
          set -euo pipefail
          tag="cores-${{ needs.plan.outputs.tag }}"
          gh release view "$tag" -R "$REPO" >/dev/null 2>&1 || gh release create "$tag" -R "$REPO" --prerelease -n "Lõi ghim $tag"
          gh release upload "$tag" out/symbols/*.zip out/symbols/*.json -R "$REPO" --clobber
```

Ghi chú thi công cho khối trên:

- `needs.plan.outputs.tag` chưa tồn tại — `plan` hiện chỉ xuất `matrix`/`ndk`/`cmake` (`.github/workflows/build-cores.yml:26-29,44-47`); cần thêm một output `tag` (hoặc truyền qua `workflow_dispatch` input) trước khi dùng job `publish`.
- `provenance.json`: `prepare-core-build.py:295-298` trả `{"needed", "buildId"}`, trộn vào `provenance` ở `:414-419`, ghi ở `:450`; `shutil.copytree(stage, output)` ở `:454` đưa nguyên sang `--out out/core` (`build-cores.yml:82-84`). Vì vậy `out/core/provenance.json` có `buildId`, `commit`, `builderCommit`, `lockSha256`, `toolchain.ndk`, `files[…]` — khối YAML chỉ đọc lại, không tính lại.
- `actions/upload-artifact@v7` và `actions/checkout@v4` giữ nguyên như tệp hiện tại; nếu #215/#290 gộp trước thì đổi theo.
- Không đưa gói symbols lên R2/Worker: chỉ để ở release `aowvn-monika-packs` (0 lượt Worker), đúng bảng phân loại gói ở `docs/dac-ta/V210-ghim-loi-ky-hieu.md` (PR #664, đang mở) — tài liệu đó đề xuất `symbols-<core_id>-<abi>.zip`; bản này chỉ **thêm `-<build_id>`** vào tên để hai bản dựng khác nhau không đè nhau.
- Hai khối YAML trên đã được kiểm cú pháp bằng `PyYAML` (`safe_load` cả hai khối: đạt) và đoạn Python đóng gói đã **chạy thật trên dữ liệu giả** (`provenance.json` + 2 ZIP rỗng): sinh đúng `symbols-<lõi>-<abi>-<build_id>.zip`, `manifest.json` cùng tên, và ghi `GITHUB_OUTPUT` `build_id`/`symbols_file`. Chưa chạy trong Actions thật — **[CHƯA KIỂM]** cho tới khi PR YAML được mở.

### 3.4. Thứ tự gộp — **CẦN PM XẾP THỨ TỰ**

| PR | Chạm `build-cores.yml`? | Bằng chứng | Hệ quả |
|---|---|---|---|
| **#215** (dependabot, `actions/checkout` 4 → 7) | **có** | `gh api …/pulls/215/files` có `.github/workflows/build-cores.yml` (44 tệp) | gộp sau sẽ đổi `uses:` trong đúng tệp này → dễ xung đột với khối YAML trên |
| **#290** (dependabot, `actions/cache` 4 → 6) | **có** | `gh api …/pulls/290/files` | như trên |
| **#572** (siết bảo mật workflow, ghim SHA bên thứ ba) | **không**, theo danh sách tệp hiện tại | 26 tệp workflow, **không** có `build-cores.yml` | vẫn phải xếp hàng vì cùng nhóm tệp và cùng quy tắc `uses:` — nếu #572 mở rộng phạm vi sang `build-cores.yml` thì mọi khối `uses:` phải là SHA ghim |
| **#664** (đặc tả V210, chỉ 1 tệp `docs/`) | không | `docs/dac-ta/V210-ghim-loi-ky-hieu.md` | chỉ tài liệu, không xung đột cơ học, nhưng phải thống nhất tên gói |

Đề nghị: gộp **#215 + #290 trước** (cơ học, nhỏ), rồi mới mở PR YAML ở mục 3.2/3.3; #572 gộp sau cùng và chuyển các `uses:` thành SHA.

## 4. Đề xuất ghim lõi + `sha256` + `version` thật (**tệp khoá — PM/Thi công quyết**)

`config/monika-config.json` là tệp khoá (được ký qua `scripts/sign-config.py`, đổi thì phải tăng `configVersion`, hiện `65`). Tài liệu này **không sửa** tệp đó; đây là hình dạng đề xuất.

| Lõi | Hiện tại (dòng) | Đề xuất |
|---|---|---|
| `citra` | `version: "1"`, không `sha256`, `latest` (`:1120-1127`) | `version: "cores-citra-1"`, `artifacts.arm64-v8a = {url, sha256, version}` |
| `desmume` | `version: "1"`, không `sha256`, `latest` (`:821-823`) | `version: "cores-desmume-1"`, `artifacts` cho `arm64-v8a` + `armeabi-v7a` |
| `melondsds` | `version: "1"`, không `sha256`, `latest` (`:1129-1131`) | `version: "cores-melondsds-1"`, `artifacts.arm64-v8a` |
| `ppsspp` | đã đúng mẫu (`:958-960`, `artifacts` ở `:975-983`) | giữ nguyên, dùng làm chuẩn |

Hình dạng khai báo (theo `MonikaConfig.kt:311`, `CoreManager.kt:145-146` — app đã sẵn đường này):

```json
"citra": {
  "version": "cores-citra-1",
  "url": "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/cores-citra-1/citra-{abi}.zip",
  "artifacts": {
    "arm64-v8a": {
      "url": "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/cores-citra-1/citra-arm64-v8a.zip",
      "sha256": "<64 hex của ZIP lõi>",
      "version": "cores-citra-1"
    }
  },
  "aspectRatio": 0.8333,
  "abis": ["arm64-v8a"],
  "lowLatencyAudio": false
}
```

Ba điều kiện để khối trên có nghĩa:

1. **Không thể ghim URL `latest` bằng `sha256`**: ZIP buildbot đổi mỗi đêm (sha256 ở mục 1.1 chỉ đúng cho bản tải hôm nay). Phải dựng từ nguồn (như `cores-ppsspp-1`) hoặc mirror ZIP vào release `aowvn-monika-packs` rồi ghim.
2. `sha256` là của **ZIP lõi**, `build_id` là của **`.so` bên trong** — manifest ở mục 3.2 giữ cả hai để không lẫn.
3. Đổi `version` là app tự tải lại lõi (`CoreManager.kt:18`); đổi config phải tăng `configVersion` + đồng bộ Cloudflare, nên việc này thuộc PM/Thi công, không thuộc PR này.

Kèm một đề xuất nhỏ cho Thi công (1 dòng, không nằm trong PR này): thêm `build_id` vào `info.txt` ở `CoreManager.kt:137-140`. Khi đó báo cáo `native` tự mang `build_id` → gói symbols tra được ngay, không cần đọc lại máy. Hiện `coreInfo` đã đi thẳng vào báo cáo (`Diagnostics.kt:115`) nên chỉ cần đổi chuỗi.

## 5. Việc còn lại

| # | Việc | Ai | Chặn bởi |
|---:|---|---|---|
| 1 | Gộp #215, #290 | PM | — |
| 2 | PR YAML mục 3.2/3.3 (thêm output `tag` cho job `plan`) | Thi công | 1, và PM xếp #572 |
| 3 | Thêm **recipe** cho `melondsds`, `desmume`, `pcsx_rearmed`, `snes9x`, `citra`, `melonds` vào `engines/sources.lock.json` (hiện `cores` chỉ có 4 lõi) | Thi công | PM duyệt commit ghim |
| 4 | Dựng + đăng `cores-{citra,desmume,melondsds}-1` kèm `symbols-…-<build_id>.zip` + `manifest.json` | Thi công | 2, 3 |
| 5 | Ghim `version`/`sha256` trong config, tăng `configVersion`, ký lại, đồng bộ Cloudflare | PM | 4 |
| 6 | Chạy `scripts/kiem-ky-hieu.py` trên gói đã đăng trước khi đóng issue native | Trưởng nhóm Lỗi | 4 |
| 7 | Thêm `test-kiem-ky-hieu.py` vào danh sách `.github/workflows/script-tests.yml:33-44` (thêm 1 dòng) | Thi công | cùng đợt với mục 2 |

Tiêu chí xong: với một báo cáo `native` bất kỳ của 3 nhóm trên, tìm được đúng `symbols-<lõi>-<build_id>.zip`, `llvm-symbolizer` ra được `tệp.cpp:dòng`, và `scripts/kiem-ky-hieu.py` trên gói đó trả `DAT` (thoát 0).

## 6. Cách chạy lại bằng chứng

```bash
curl -sSLO https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/citra_libretro_android.so.zip
python scripts/kiem-ky-hieu.py citra_libretro_android.so.zip        # → THIEU_KY_HIEU, thoát 1
python scripts/kiem-ky-hieu.py --json citra_libretro_android.so.zip  # → build_id + cờ từng mục
python scripts/test-kiem-ky-hieu.py                                  # → 13 ca, OK
```

Bảng quyết định cho **toàn bộ 22 lõi** (kèm lượt issue phụ thuộc và thứ tự 3 việc rẻ nhất): `docs/loi/ky-hieu-toan-bo-1811.md`.
