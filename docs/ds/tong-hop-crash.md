# Tổng hợp crash đang mở (DS-04)

Nguồn: 38 issue mở nhãn `crash-moi` (bot `github-actions` tạo) tại 30/10/2026, đọc thân issue (`count`, `Phụ trách`). Bảng sinh bằng script từ dữ liệu đó, không dùng mô hình; không đoán nguyên nhân.

**Cách đọc:** `Tần suất` = số lần bot đã thông báo (`Số lần quan sát đã thông báo`), **không phải số người**. Phạm vi mỗi lần bot đọc là tối đa 100 bản ghi mới nhất của Worker, không suy ra toàn bộ lịch sử (theo thân issue). Chữ ký đầy đủ nằm trong thân issue; bảng chỉ ghi 12 ký tự đầu như tiêu đề. `Hệ máy` suy từ tên lõi. `Phụ trách` lấy từ dòng "Phụ trách" trong thân issue (cả 38 đều ghi Sol); tài khoản được gán trên GitHub là `aowvn-10diem`. Đây là đầu vào cho Sol, không tự sửa.

Tổng: 38 chữ ký, 2.932 lượt quan sát đã thông báo.

## Theo lõi

| Lõi | Hệ máy | Số chữ ký | Tổng lượt |
|---|---|---:|---:|
| `unknown` | chưa rõ lõi | 7 | 2.686 |
| `ppsspp` | PSP | 5 | 101 |
| `citra` | 3DS | 6 | 82 |
| `melondsds` | NDS | 8 | 32 |
| `desmume` | NDS | 7 | 18 |
| `pcsx_rearmed` | PS1 | 2 | 8 |
| `mgba` | GBA | 3 | 5 |

## Theo loại

| Loại | Số chữ ký | Tổng lượt |
|---|---:|---:|
| `lowmem` | 3 | 2.050 |
| `op-fail` | 6 | 495 |
| `resource` | 1 | 144 |
| `native` | 5 | 105 |
| `core-error` | 2 | 63 |
| `killed` | 7 | 45 |
| `anr` | 7 | 16 |
| `user` | 4 | 9 |
| `java` | 1 | 3 |
| `unknown` | 2 | 2 |

## Chi tiết (xếp theo tần suất giảm dần)

| Issue | Chữ ký (12 ký tự đầu) | Lõi | Hệ máy | Loại | Tần suất | Mở ngày | Phụ trách |
|---|---|---|---|---|---:|---|---|
| [#304](https://github.com/aowvn-10diem/aowvn-monika/issues/304) | `a264e8cb16d5` | `unknown` | chưa rõ lõi | `lowmem` | 2.048 | 20/10/2026 | Sol |
| [#305](https://github.com/aowvn-10diem/aowvn-monika/issues/305) | `ba1937dab151` | `unknown` | chưa rõ lõi | `op-fail` | 490 | 20/10/2026 | Sol |
| [#302](https://github.com/aowvn-10diem/aowvn-monika/issues/302) | `2ba6b80864be` | `unknown` | chưa rõ lõi | `resource` | 144 | 20/10/2026 | Sol |
| [#372](https://github.com/aowvn-10diem/aowvn-monika/issues/372) | `7af5e6657638` | `ppsspp` | PSP | `native` | 90 | 27/10/2026 | Sol |
| [#301](https://github.com/aowvn-10diem/aowvn-monika/issues/301) | `1d781229d457` | `citra` | 3DS | `core-error` | 56 | 20/10/2026 | Sol |
| [#306](https://github.com/aowvn-10diem/aowvn-monika/issues/306) | `bb7c57376b7b` | `melondsds` | NDS | `killed` | 18 | 20/10/2026 | Sol |
| [#308](https://github.com/aowvn-10diem/aowvn-monika/issues/308) | `e9dd48f45fcb` | `citra` | 3DS | `killed` | 16 | 20/10/2026 | Sol |
| [#322](https://github.com/aowvn-10diem/aowvn-monika/issues/322) | `01448aec65b4` | `pcsx_rearmed` | PS1 | `core-error` | 7 | 20/10/2026 | Sol |
| [#316](https://github.com/aowvn-10diem/aowvn-monika/issues/316) | `3f181942717a` | `ppsspp` | PSP | `native` | 6 | 20/10/2026 | Sol |
| [#314](https://github.com/aowvn-10diem/aowvn-monika/issues/314) | `03d00b1fe612` | `melondsds` | NDS | `anr` | 6 | 20/10/2026 | Sol |
| [#307](https://github.com/aowvn-10diem/aowvn-monika/issues/307) | `ce454cd1fcf8` | `citra` | 3DS | `user` | 6 | 20/10/2026 | Sol |
| [#315](https://github.com/aowvn-10diem/aowvn-monika/issues/315) | `303bd0bb2914` | `desmume` | NDS | `anr` | 4 | 20/10/2026 | Sol |
| [#377](https://github.com/aowvn-10diem/aowvn-monika/issues/377) | `ce10c8fe6467` | `desmume` | NDS | `native` | 3 | 27/10/2026 | Sol |
| [#369](https://github.com/aowvn-10diem/aowvn-monika/issues/369) | `a093f6541c51` | `desmume` | NDS | `native` | 3 | 26/10/2026 | Sol |
| [#334](https://github.com/aowvn-10diem/aowvn-monika/issues/334) | `62ac52dd9c08` | `desmume` | NDS | `killed` | 3 | 23/10/2026 | Sol |
| [#331](https://github.com/aowvn-10diem/aowvn-monika/issues/331) | `c8765c891f30` | `desmume` | NDS | `native` | 3 | 22/10/2026 | Sol |
| [#330](https://github.com/aowvn-10diem/aowvn-monika/issues/330) | `11abbf1e449c` | `ppsspp` | PSP | `java` | 3 | 22/10/2026 | Sol |
| [#303](https://github.com/aowvn-10diem/aowvn-monika/issues/303) | `58ee148e0480` | `mgba` | GBA | `killed` | 3 | 20/10/2026 | Sol |
| [#349](https://github.com/aowvn-10diem/aowvn-monika/issues/349) | `5de4a80000d8` | `melondsds` | NDS | `killed` | 2 | 24/10/2026 | Sol |
| [#328](https://github.com/aowvn-10diem/aowvn-monika/issues/328) | `94176173e6dd` | `citra` | 3DS | `anr` | 2 | 22/10/2026 | Sol |
| [#317](https://github.com/aowvn-10diem/aowvn-monika/issues/317) | `9654f6b49689` | `melondsds` | NDS | `killed` | 2 | 20/10/2026 | Sol |
| [#380](https://github.com/aowvn-10diem/aowvn-monika/issues/380) | `ebd797ae9971` | `ppsspp` | PSP | `user` | 1 | 30/10/2026 | Sol |
| [#378](https://github.com/aowvn-10diem/aowvn-monika/issues/378) | `f71fe56ab347` | `mgba` | GBA | `unknown` | 1 | 28/10/2026 | Sol |
| [#376](https://github.com/aowvn-10diem/aowvn-monika/issues/376) | `8c9affd02a9b` | `unknown` | chưa rõ lõi | `op-fail` | 1 | 27/10/2026 | Sol |
| [#374](https://github.com/aowvn-10diem/aowvn-monika/issues/374) | `62fc9646f253` | `ppsspp` | PSP | `killed` | 1 | 27/10/2026 | Sol |
| [#373](https://github.com/aowvn-10diem/aowvn-monika/issues/373) | `a924b4c43381` | `citra` | 3DS | `unknown` | 1 | 27/10/2026 | Sol |
| [#371](https://github.com/aowvn-10diem/aowvn-monika/issues/371) | `6c2b2d1ef18a` | `unknown` | chưa rõ lõi | `op-fail` | 1 | 27/10/2026 | Sol |
| [#368](https://github.com/aowvn-10diem/aowvn-monika/issues/368) | `751363ed54f2` | `desmume` | NDS | `op-fail` | 1 | 26/10/2026 | Sol |
| [#366](https://github.com/aowvn-10diem/aowvn-monika/issues/366) | `cbca1c044460` | `unknown` | chưa rõ lõi | `op-fail` | 1 | 26/10/2026 | Sol |
| [#350](https://github.com/aowvn-10diem/aowvn-monika/issues/350) | `d98da506cbec` | `melondsds` | NDS | `anr` | 1 | 24/10/2026 | Sol |
| [#336](https://github.com/aowvn-10diem/aowvn-monika/issues/336) | `991ea7b8a2ad` | `citra` | 3DS | `anr` | 1 | 23/10/2026 | Sol |
| [#335](https://github.com/aowvn-10diem/aowvn-monika/issues/335) | `9018fbc8276f` | `melondsds` | NDS | `lowmem` | 1 | 23/10/2026 | Sol |
| [#333](https://github.com/aowvn-10diem/aowvn-monika/issues/333) | `34dd8a2c0c98` | `desmume` | NDS | `anr` | 1 | 23/10/2026 | Sol |
| [#332](https://github.com/aowvn-10diem/aowvn-monika/issues/332) | `b6a48323d69f` | `melondsds` | NDS | `user` | 1 | 22/10/2026 | Sol |
| [#329](https://github.com/aowvn-10diem/aowvn-monika/issues/329) | `8cc7b9e565fe` | `pcsx_rearmed` | PS1 | `user` | 1 | 22/10/2026 | Sol |
| [#319](https://github.com/aowvn-10diem/aowvn-monika/issues/319) | `a5b0731e404e` | `unknown` | chưa rõ lõi | `op-fail` | 1 | 20/10/2026 | Sol |
| [#318](https://github.com/aowvn-10diem/aowvn-monika/issues/318) | `9a90aaa75e95` | `melondsds` | NDS | `anr` | 1 | 20/10/2026 | Sol |
| [#300](https://github.com/aowvn-10diem/aowvn-monika/issues/300) | `0cedbd254c09` | `mgba` | GBA | `lowmem` | 1 | 20/10/2026 | Sol |

## Ghi chú
- Chữ ký `a264e8cb16d5` (#304, `unknown`/`lowmem`) có tần suất đúng 2048 (= 2^11): có thể là trần đếm của bot hay số liệu thật thì **[CHƯA KIỂM]**.
- Theo danh sách issue, chỉ #332–#336 có 1 bình luận; đã đọc #336 (Sol: citra/`anr`, nguyên nhân gốc chưa kiểm). 33 issue còn lại chưa có bình luận.
- Lõi `unknown` gồm 7 chữ ký (`op-fail` x5, `lowmem` x1, `resource` x1): bot chưa gán được lõi, nên không quy được hệ máy.

