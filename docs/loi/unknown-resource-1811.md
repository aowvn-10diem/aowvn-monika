# `unknown/resource` (#302) và `unknown/op-fail`: vì sao mất lõi, đã sửa gì

Ngày 18/11/2026 · nhánh `loi/unknown-op-fail` · nền `origin/main` `4c4757b1`
Nguồn: `docs/loi/unknown-native-1311.md`, `docs/loi/op-fail-1311.md`, `docs/loi/op-fail-con-lai-1311.md`,
`docs/loi/TONG-HOP-1311.md`; mã tại `4c4757b1`; PR #638 (đã gộp), #655 (đang mở), #618 (đã gộp).

## 1. Kết luận ngắn

- **`unknown/resource` #302 (261 lượt, 50 máy)** không phải lỗi bot gán nhãn. Tiến trình `:midlet` (game Java)
  trước đây **không có phiên** nên báo cáo không có lõi. #638 (đã gộp) đã gắn phiên cho `:midlet`, nhưng
  **vẫn còn hai mắt xích** khiến lõi bị mất lại: báo cáo bị ghi **hai lần cùng `id`** (lần sau không có phiên
  ghi đè lần trước), và `collect()` xếp `REASON_EXCESSIVE_RESOURCE_USAGE` vào nhóm `killed` thay vì `resource`.
  Mã sửa hai mắt xích này nằm trong `Diagnostics.kt` — tệp đang thuộc **PR #655**, nên lượt này **không sửa**
  mà ghi đặc tả + mã đề xuất ở §3 để PM xếp sau #655.
- **`unknown/op-fail` (13 issue, 33 lượt)** đúng bản chất là "thao tác ngoài phiên chơi thì không có lõi".
  Phần sửa được nằm ở chỗ khác: báo cáo phải gắn **`Thành phần` + `bước`** rõ và không được sinh ra cho lỗi
  của máy người chơi. Lượt này sửa 3 tệp (§4): bịt ca **mở engine im lặng**, bớt báo cáo oan khi máy không có
  app nhận liên kết, và gộp hai báo cáo trùng của `cai-goi-engine` về cùng một `bước`.
- **Không đóng issue nào** trong lượt này: 13 issue op-fail không có `Thành phần`, phiên này không có
  `CRASH_ADMIN_TOKEN` nên **không đọc được `detail`/`component`** để xác nhận chữ ký — [CHƯA KIỂM]. Xem §5.

## 2. `#302` — ba mắt xích, bằng chứng `tệp:dòng`

`#302` là `kind=resource` của tiến trình `app:midlet`. `resource` chỉ sinh từ nhánh
`ApplicationExitInfo` (`Diagnostics.kt:870` — `REASON_EXCESSIVE_RESOURCE_USAGE -> "resource"`), tức báo cáo do
`collectProcessDeaths` tạo, **không** do `collect` tạo. Ba mắt xích:

| # | Mắt xích | Bằng chứng | Hệ quả |
|---|---|---|---|
| 1 | Trước #638, `:midlet` không có phiên | không có `Diagnostics.begin` trong `j2me/`; phiên chỉ được ghi từ các activity game | `collect()` đọc `active-session.json` ra `null` → trả `null` (`Diagnostics.kt:220`) ⇒ chỉ còn `collectProcessDeaths` tạo báo cáo, `session = null` ⇒ `core` rỗng ⇒ `unknown` |
| 2 | #638 (đã gộp `eabd8047`) gắn phiên | `MidletDiagnostics.kt:131-133` gọi `Diagnostics.begin(activity, "midlet", "midlet", …, "J2ME")` khi `MicroActivity` mở | **đã có phiên** cho `:midlet` |
| 3 | **Báo cáo bị ghi hai lần cùng `id`** | `MainActivity` github `:86` / play `:76-77` gọi `collect()` **rồi** `collectProcessDeaths()`; `collect` đặt `id = whenDied = exit.timestamp` (`Diagnostics.kt:237-241`); `collectProcessDeaths` đặt `id = e.timestamp` (`:886-889`); `record` → `save` ghi `<id>.json` (`Diagnostics.kt:726-735`) | lần ghi thứ hai **ghi đè** lần đầu. Chốt chống ghi đè hiện chỉ áp cho `:game` (`:874`: `endsWith(":game")`), **không** áp cho `:midlet` ⇒ báo cáo cuối là bản **không có phiên** (trước #655) ⇒ lõi vừa ghi được bị xoá |
| 4 | Nhãn lệch nhóm | `collect()` `when (exit?.reason)` (`Diagnostics.kt:232-240`) **không có** nhánh `EXCESSIVE_RESOURCE_USAGE`/`INITIALIZATION_FAILURE` ⇒ rơi vào `else -> "killed"` | báo cáo của `collect` mang `killed`, của `collectProcessDeaths` mang `resource` — hai nhóm khác nhau cho **cùng một** lần chết, tuỳ lần nào ghi sau |

Vì sao vẫn `unknown` sau #638: mắt xích 3. Vì sao #655 có thể che được: #655 cho `collectProcessDeaths` đọc
phiên chết cho `resource` (`Diagnostics.kt:877` → `readsDeathSession(kind)`), nên bản ghi sau **cũng** có phiên.
Nhưng mắt xích 3 **vẫn còn**: bản ghi sau ghi đè bản ghi trước, reset `count`, và `pending`/thông báo đã trỏ
tới bản ghi trước — nếu `processStateSummary` không khớp (máy/ROM thật chưa kiểm), lõi lại mất.

## 3. Bản vá đề xuất cho `Diagnostics.kt` — **chờ #655 gộp**

Không nằm trong PR này (tệp đang thuộc #655). Hai hàm thuần, không đổi định dạng `Report`/`Session`:

```kotlin
/** Lý do chết Android → `kind`; `null` = người dùng tự thoát, không lập báo cáo. */
internal fun collectKind(reason: Int?): String? = when (reason) {
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
    ApplicationExitInfo.REASON_CRASH -> "java"
    ApplicationExitInfo.REASON_ANR -> "anr"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource"   // thêm: #302
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initfail"     // thêm
    ApplicationExitInfo.REASON_USER_REQUESTED,
    ApplicationExitInfo.REASON_USER_STOPPED,
    ApplicationExitInfo.REASON_EXIT_SELF -> null
    null -> "unknown"
    else -> "killed"
}
// dùng ở Diagnostics.kt:232 → val kind = collectKind(exit?.reason) ?: return null

/** Cùng một lần chết bị ghi hai lần với cùng `id`: lần sau thiếu phiên thì giữ phiên của lần trước. */
internal fun carrySession(next: Report, prev: Report?): Report {
    if (next.session != null || prev?.session == null) return next
    if (next.kind !in deathKinds || prev.kind !in deathKinds) return next
    val s = prev.session
    return next.copy(
        session = s,
        core = next.core.ifBlank { s.core },
        system = next.system.ifBlank { s.system },
        stage = next.stage.ifBlank { s.stage },
        component = if (next.component.isBlank() || next.component.startsWith("app:")) prev.component else next.component,
        fromGame = prev.fromGame || next.fromGame,
    )
}
// dùng trong record() (Diagnostics.kt:726):
//   val existing = list(c)
//   val safe = sanitized(c, enrichDeathReport(carrySession(r0, existing.firstOrNull { it.id == r0.id })))
//   val prev = existing.firstOrNull { it.fingerprint == fp }
```

`deathKinds = setOf("native","java","anr","lowmem","killed","unknown","resource","initfail")` — chặn việc báo cáo
`op-fail` (id là mili-giây "bây giờ") vô tình mượn phiên của một lần chết trùng mili-giây.

Test đề xuất (thuần, không cần máy thật; đã viết và **gỡ khỏi nhánh** theo yêu cầu — [CHƯA KIỂM] khi áp lại):

1. `collectKind(REASON_EXCESSIVE_RESOURCE_USAGE) == "resource"`, `INITIALIZATION_FAILURE == "initfail"`,
   `USER_REQUESTED/STOPPED/EXIT_SELF == null`, `null == "unknown"`, `OTHER == "killed"`.
2. `carrySession(bản ghi sau không phiên, bản ghi trước có phiên midlet)` → giữ `session`, `core = "midlet"`,
   `system = "J2ME"`, `component` của lần trước, `fromGame = true`.
3. Bản ghi sau **có** phiên riêng → **không** bị thay bằng phiên cũ.
4. `op-fail` không mượn phiên của `anr`/`resource` dù trùng `id`.

Cách đo lại sau khi gộp #655 + bản vá này: `scripts/crash-insight.py` hoặc bảng `byCoreKindStage` của Worker,
so số `unknown/resource` của hai bản phát hành liên tiếp; kỳ vọng `#302` hết cộng lượt, không phải "giảm ngay".

## 4. `unknown/op-fail` (13 issue, 33 lượt) — lượt này sửa gì

### 4.1 Đã sửa trong PR này (3 tệp, không đụng `Diagnostics.kt`)

| Tệp | Trước | Sau | Vì sao |
|---|---|---|---|
| `app/src/{github,play}/java/vn/aow/monika/runner/EnginePrepActivity.kt` (`open()`) | `route?.open?.invoke(...)` gọi trần ngoài mọi `Diagnostics.op` — `ActivityNotFoundException`/`SecurityException`/lỗi quyền **biến mất không dấu vết** | bọc `Diagnostics.op(Op.OPEN_GAME, target = title) { step("mo-engine"); … }`, lỗi → báo cáo `op:mo-game` + `bước: mo-engine`, kèm toast | đây là "báo **thiếu**" số 4 trong `op-fail-con-lai-1311.md` §6.4: nhóm `op-fail` chưa từng có dữ liệu ca mở engine hỏng |
| `app/src/play/java/vn/aow/monika/play/PlayLinks.kt:19,28` | `op("mo-lien-ket")` / `op("chia-se-hoi-nhom")` báo mọi lỗi, kể cả `ActivityNotFoundException` (máy không có trình duyệt/app nhận) | thêm `expected = { it is ActivityNotFoundException }` → chỉ ghi vệt | "báo **sai**" số 7 (§5.7): lỗi môi trường của máy người chơi thành báo cáo, mỗi lần thành một nhóm `unknown` đơn lẻ |
| `app/src/main/java/vn/aow/monika/pack/PackManager.kt:173` | `bước = "pack:<id> (sau N lần thử)"` → khoá chống spam khác `EnginePrepActivity` (`bước = "pack:<id>"`) ⇒ **2 báo cáo** cho 1 sự cố | `bước = "pack:<id>"`; số lần thử đã nằm ở vệt `pack <id> lần N lỗi` ngay trên | "báo trùng" số 4 (§5.4) |

### 4.2 Kiểm rồi, **không** sửa

- `app/src/main/java/vn/aow/monika/patch/PatchFlow.kt:20,44,53,62,65` — **đã đúng từ #618** (đã gộp):
  `inputFail(...)` truyền `expected = { it is PatchException }` cho 4 chỗ chọn nhầm ROM/bản vá; `mkdirs` /
  `renameTo` / đọc-ghi / hết bộ nhớ vẫn báo (`:44,:53,:62,:65`) — đúng, đó là lỗi môi trường thật. Không sửa
  thêm để khỏi trùng #618.
- Tên op của flavor `play` (`"mo-lien-ket"`, `"chia-se-hoi-nhom"`) vẫn **ngoài** `Diagnostics.Op` vì `Op` nằm
  trong `Diagnostics.kt`. Muốn gom về một nguồn thì phải thêm `OPEN_LINK`/`SHARE_POST`/`READ_CRASH` vào `Op`
  (`Diagnostics.kt:403-412`) — **chờ #655**, xem §6.

### 4.3 Còn lại, cần lượt khác

1. `tai-xuong` 4xx: `BrowserDownloads.kt:145` (flavor github) ném `IOException` cho **mọi** mã ≠ 2xx rồi
   `opFail` ở `:184` — link chết 404 vẫn thành báo cáo. Cần `HttpStatusException(code)` rồi `expected = code < 500`.
   Chưa làm: phải sửa cách lớp tải báo lỗi (nhiều chỗ gọi), không phải một dòng.
2. `tai-cau-hinh` 4xx: `ConfigRepository.kt:107` dùng `check(response.isSuccessful)` — cần exception mang mã HTTP
   tương tự. Chưa làm cùng lý do trên.
3. `Importer.kt:140` bỏ qua kết quả `renameTo` rồi `:141` xoá `.dang-giai-nen` → **mất file** mà không có vệt.
   Đây là rủi ro mất dữ liệu người chơi, nên làm thành PR riêng có test, không gộp vào PR báo cáo.

## 5. Issue đóng được

| Issue | Kết luận lượt này |
|---|---|
| [#302](https://github.com/aowvn-10diem/aowvn-monika/issues/302) (261 lượt) | **Chưa đóng.** Cần #655 gộp **và** bản vá §3. PR này chỉ có chẩn đoán + đặc tả. |
| #614 `op:va-rom` | Đã sửa bởi #618 (đã gộp). Đóng được **sau một bản phát hành** không cộng lượt nữa. Lượt này không đóng (không có token để xác nhận báo cáo gốc). |
| #608 `op:tai-xuong` | **Không đóng**: 4xx vẫn báo (§4.3 mục 1). |
| #393, #401, #564, #531, #525, #407, #395, #376, #371, #366, #319 (11 issue) | **Không đóng**: thân issue không có `Thành phần`, không có `detail`; mọi phép gán op là suy đoán. Cần `CRASH_ADMIN_TOKEN` đọc `scripts/crash-reports.sh <id>` rồi mới quyết. |

## 6. Việc cần PM / người giữ `Diagnostics.kt` quyết

1. **Thứ tự gộp**: #655 trước → rồi PR bản vá §3 (hoặc người giữ #655 tự thêm `collectKind`/`carrySession` vào
   cùng PR). Không sửa song song cùng tệp.
2. **Có làm phiên cho `:midlet` ở API < 28 không**: `MidletDiagnostics.install` bỏ qua `SDK < 28`
   (`MidletDiagnostics.kt:68`) vì `Application.getProcessName()` chỉ có từ 28. Máy 9-10 vẫn `unknown` — chấp nhận
   hay dùng cách khác (đọc `/proc/self/cmdline`)? Cần quyết, không tự làm.
3. **`Op` cho flavor play**: thêm 3 hằng vào `Diagnostics.Op` + `DiagnosticsOpTest` (đang chờ #655).
4. **`CRASH_ADMIN_TOKEN` cho một lượt triage**: đọc 14 báo cáo của #393 và 11 issue đơn lẻ để đóng đúng, thay vì
   suy từ mã.

## 7. Giới hạn

- Không có `CRASH_ADMIN_TOKEN`: không đọc được `component`/`detail`/`frames` của bất kỳ báo cáo nào ⇒ mọi câu về
  "op nào" ở §5 là **chưa kiểm**.
- Bản vá §3 **chưa chạy test trên nhánh này** (đã gỡ khỏi `Diagnostics.kt` theo yêu cầu phạm vi); test đã viết
  nhưng chưa áp lại — ghi [CHƯA KIỂM] thay vì trình bày như đã xanh.
- Số lượt là số bot cộng dồn, không phải số người; ảnh chụp 13/11 (`TONG-HOP-1311.md`) nên không so trực tiếp.

## 8. Cập nhật 20/11/2026 — trạng thái sau #655/#680 và PR #692

Ba mắt xích ở §2 đã bịt trên `main`: #638 (gắn phiên cho `:midlet`), #655 (`resource`/`native`/
`initfail` đọc phiên chết), #680 (`collectKind` trả `resource`, `carrySession` giữ phiên khi ghi lại).
PR #692 vá ba điểm còn lại: `component` của phiên `midlet` → `engine:j2me`; giữ ảnh chụp phiên khi
`exitInfo == null`; và `record` xoá bản tạm `unknown` khi bản đầy đủ của **đúng phiên**
(`pid` + `startedAt`) đã tới — một lần chết chỉ còn một báo cáo.

Còn lại **một** đường sinh được chữ ký cũ `unknown / resource / 2ba6b80864be…`:
`collectProcessDeaths` khi **không khớp được ảnh chụp phiên** (`processStateSummary` lệch, ảnh chụp đã
bị dọn, PID được cấp lại) ⇒ `session = null`, `core = ""`; bot tính `token("") = "unknown"` và
`frames` rỗng (báo cáo `resource` không có stack). Không bịt bằng cách suy `core` từ tên tiến trình
(quy ước: `unknown` = thiếu bằng chứng, không giả một `Session`) và không nới `matchingDeathSession`
(mở lại lỗi gắn nhầm lõi khi Android cấp lại PID).

Theo dõi sau phát hành: nhóm `midlet / resource` phải xuất hiện và tăng, còn nhóm
`unknown / resource` phải **ngừng** tăng. Nhóm đó vẫn tăng ⇒ `processStateSummary` không khớp trên
máy thật ⇒ mở việc riêng, đọc `/report?id=` để lấy bằng chứng. **#302 vẫn mở** cho tới khi có bản phát
hành và số liệu theo dõi.
