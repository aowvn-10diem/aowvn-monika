#!/usr/bin/env python3
"""Kiểm `scripts/kiem-ky-hieu.py` bằng ELF giả lập tự dựng (không cần mạng/readelf).

Mỗi ca chạy đúng tiến trình dòng lệnh để kiểm cả bảng in ra lẫn mã thoát.
"""
from __future__ import annotations

import importlib.util
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "kiem-ky-hieu.py"
ELF_MAGIC = b"\x7fELF"

spec = importlib.util.spec_from_file_location("kiem_ky_hieu", SCRIPT)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


def _ghi_muc(buf, bits, e, base, name, loai, dau, dai):
    if bits == 64:
        struct.pack_into(e + "IIQQQQ", buf, base, name, loai, 0, 0, dau, dai)
    else:
        struct.pack_into(e + "IIIIII", buf, base, name, loai, 0, 0, dau, dai)


def make_elf(*, bits=64, little=True, machine=183, muc=(".dynsym", ".symtab"),
             build_id=b"\x2a" * 20, co_bang_muc=True, co_note=True) -> bytes:
    """Dựng ELF tối thiểu đủ để bộ đọc thật nhận đúng cấu trúc."""
    e = "<" if little else ">"
    ehsize = 64 if bits == 64 else 52
    phentsize = 56 if bits == 64 else 32
    shentsize = 64 if bits == 64 else 40

    khoi = []
    if co_note and build_id is not None:
        note = struct.pack(e + "III", 4, len(build_id), 3) + b"GNU\x00" + build_id
        note += b"\x00" * ((-len(build_id)) % 4)
        khoi.append((".note.gnu.build-id", 7, note))
    for ten in muc:
        loai = 2 if ten == ".symtab" else (11 if ten == ".dynsym" else 1)
        khoi.append((ten, loai, b"\x00" * 24))

    ten_muc = bytearray(b"\x00")
    vi_tri_ten = {}
    for ten, _loai, _than in khoi:
        vi_tri_ten[ten] = len(ten_muc)
        ten_muc += ten.encode() + b"\x00"
    if co_bang_muc:
        vi_tri_ten[".shstrtab"] = len(ten_muc)
        ten_muc += b".shstrtab\x00"
        khoi.append((".shstrtab", 3, bytes(ten_muc)))

    dau = ehsize + (0 if co_bang_muc else phentsize)
    da_dat = []
    for ten, loai, than in khoi:
        dau += (-dau) % 4
        da_dat.append((ten, loai, dau, than))
        dau += len(than)

    shoff = 0
    shnum = shstrndx = 0
    if co_bang_muc:
        shoff = dau + (-dau) % 8
        shnum = len(da_dat) + 1
        shstrndx = len(da_dat)
        tong = shoff + shnum * shentsize
    else:
        tong = dau

    buf = bytearray(tong)
    buf[0:4] = ELF_MAGIC
    buf[4] = 1 if bits == 32 else 2
    buf[5] = 1 if little else 2
    buf[6] = 1
    struct.pack_into(e + "H", buf, 16, 3)          # ET_DYN
    struct.pack_into(e + "H", buf, 18, machine)
    struct.pack_into(e + "I", buf, 20, 1)          # e_version
    if bits == 64:
        struct.pack_into(e + "Q", buf, 32, 0 if co_bang_muc else ehsize)
        struct.pack_into(e + "Q", buf, 40, shoff)
        struct.pack_into(e + "H", buf, 52, ehsize)
        struct.pack_into(e + "H", buf, 54, phentsize)
        struct.pack_into(e + "H", buf, 56, 0 if co_bang_muc else 1)
        struct.pack_into(e + "H", buf, 58, shentsize)
        struct.pack_into(e + "H", buf, 60, shnum)
        struct.pack_into(e + "H", buf, 62, shstrndx)
    else:
        struct.pack_into(e + "I", buf, 28, 0 if co_bang_muc else ehsize)
        struct.pack_into(e + "I", buf, 32, shoff)
        struct.pack_into(e + "H", buf, 40, ehsize)
        struct.pack_into(e + "H", buf, 42, phentsize)
        struct.pack_into(e + "H", buf, 44, 0 if co_bang_muc else 1)
        struct.pack_into(e + "H", buf, 46, shentsize)
        struct.pack_into(e + "H", buf, 48, shnum)
        struct.pack_into(e + "H", buf, 50, shstrndx)

    for ten, _loai, vi_tri, than in da_dat:
        buf[vi_tri:vi_tri + len(than)] = than

    if not co_bang_muc:
        _ten, _loai, vi_tri, than = da_dat[0]
        if bits == 64:
            struct.pack_into(e + "IIQQQQ", buf, ehsize, 4, 0, vi_tri, 0, 0, len(than))
        else:
            struct.pack_into(e + "IIIIII", buf, ehsize, 4, vi_tri, 0, 0, len(than), 0)
    else:
        for index, (ten, loai, vi_tri, than) in enumerate(da_dat, start=1):
            _ghi_muc(buf, bits, e, shoff + index * shentsize,
                     vi_tri_ten[ten], loai, vi_tri, len(than))
    return bytes(buf)


class KiemKyHieuTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory(prefix="monika-kyhieu-")
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def tep(self, ten, data: bytes) -> Path:
        path = self.root / ten
        path.write_bytes(data)
        return path

    def zip(self, ten, **noi_dung: bytes) -> Path:
        path = self.root / ten
        with zipfile.ZipFile(path, "w") as archive:
            for ten_trong, data in noi_dung.items():
                archive.writestr(ten_trong, data)
        return path

    def chay(self, *args, bang_json=False):
        lenh = [sys.executable, "-W", "ignore", str(SCRIPT)]
        if bang_json:
            lenh.append("--json")
        lenh += [str(a) for a in args]
        moi_truong = {**os.environ, "PYTHONIOENCODING": "utf-8"}
        ket_qua = subprocess.run(lenh, capture_output=True, text=True, encoding="utf-8",
                                 env=moi_truong)
        if bang_json:
            return ket_qua, json.loads(ket_qua.stdout)
        return ket_qua

    def test_elf64_arm64_du_ky_hieu(self):
        kq = self.chay(self.tep("liba.so", make_elf()))
        self.assertEqual(kq.returncode, 0, kq.stdout + kq.stderr)
        self.assertIn("DAT", kq.stdout)
        self.assertIn("arm64", kq.stdout)
        self.assertIn("2a" * 20, kq.stdout)
        self.assertIn("đủ ký hiệu 1 · thiếu 0", kq.stdout)

    def test_elf64_chi_con_dynsym(self):
        kq = self.chay(self.tep("libb.so", make_elf(muc=(".dynsym",))))
        self.assertEqual(kq.returncode, 1, kq.stdout + kq.stderr)
        self.assertIn("THIEU_KY_HIEU", kq.stdout)
        self.assertIn("không", kq.stdout)

    def test_elf32_arm_du_ky_hieu(self):
        kq = self.chay(self.tep("libc.so", make_elf(bits=32, machine=40)))
        self.assertEqual(kq.returncode, 0, kq.stdout + kq.stderr)
        self.assertIn("arm", kq.stdout)

    def test_elf32_big_endian_mips(self):
        kq = self.chay(self.tep("libd.so", make_elf(bits=32, little=False, machine=8)))
        self.assertEqual(kq.returncode, 0, kq.stdout + kq.stderr)
        self.assertIn("mips", kq.stdout)

    def test_thieu_build_id_van_bao_thieu(self):
        kq = self.chay(self.tep("libe.so", make_elf(co_note=False)))
        self.assertEqual(kq.returncode, 1, kq.stdout + kq.stderr)
        self.assertIn("(thiếu)", kq.stdout)
        self.assertIn("THIEU_KY_HIEU", kq.stdout)

    def test_bi_strip_mat_bang_muc_van_doc_duoc_build_id(self):
        """Giống lõi buildbot: chỉ còn PT_NOTE, không .symtab."""
        kq = self.chay(self.tep("libf.so", make_elf(co_bang_muc=False)))
        self.assertEqual(kq.returncode, 1, kq.stdout + kq.stderr)
        self.assertIn("2a" * 20, kq.stdout)
        self.assertIn("THIEU_KY_HIEU", kq.stdout)

    def test_zip_tron_tot_va_xau(self):
        path = self.zip("core-a.zip", **{
            "arm64-v8a/libtot.so": make_elf(),
            "arm64-v8a/libcat.so": make_elf(muc=(".dynsym",), build_id=b"\x99" * 20),
        })
        kq = self.chay(path)
        self.assertEqual(kq.returncode, 1, kq.stdout + kq.stderr)
        self.assertIn("core-a.zip:arm64-v8a/libtot.so", kq.stdout)
        self.assertIn("core-a.zip:arm64-v8a/libcat.so", kq.stdout)
        self.assertIn("đủ ký hiệu 1 · thiếu 1", kq.stdout)

    def test_zip_sach(self):
        path = self.zip("core-b.zip", **{"libx.so": make_elf(), "liby.so": make_elf(bits=32)})
        kq = self.chay(path)
        self.assertEqual(kq.returncode, 0, kq.stdout + kq.stderr)
        self.assertIn("Tổng: 2 tệp .so", kq.stdout)
        self.assertIn("đủ ký hiệu 2", kq.stdout)

    def test_zip_khong_co_so(self):
        path = self.zip("core-c.zip", **{"README.txt": b"khong co so"})
        kq = self.chay(path)
        self.assertEqual(kq.returncode, 2)
        self.assertIn("không có tệp .so", kq.stderr)

    def test_tep_khong_phai_elf(self):
        kq = self.chay(self.tep("gia.so", b"day khong phai elf"))
        self.assertEqual(kq.returncode, 2)
        self.assertIn("không phải ELF", kq.stderr)

    def test_nhieu_tep_cung_luc(self):
        kq = self.chay(self.tep("lib1.so", make_elf()), self.tep("lib2.so", make_elf(bits=32)))
        self.assertEqual(kq.returncode, 0, kq.stdout + kq.stderr)
        self.assertIn("Tổng: 2 tệp .so", kq.stdout)

    def test_bang_du_cot_va_json(self):
        path = self.tep("libz.so", make_elf())
        bang = self.chay(path)
        for cot in ["tệp", "kiến trúc", "build_id", ".symtab", ".debug_info", ".dynsym",
                    "kích thước", "kết luận"]:
            self.assertIn(cot, bang.stdout)
        kq = self.chay(path, bang_json=True)
        du_lieu = kq[1]
        self.assertEqual(du_lieu["tep"][0]["ket_luan"], "DAT")
        self.assertEqual(du_lieu["tep"][0]["build_id"], "2a" * 20)
        self.assertTrue(du_lieu["tep"][0]["symtab"])
        self.assertEqual(du_lieu["loi"], [])

    def test_bo_doc_nhan_dien_zdebug_info(self):
        thong_tin = module.doc_elf(make_elf(muc=(".dynsym", ".symtab", ".zdebug_info")))
        self.assertTrue(thong_tin["debug_info"])
        self.assertTrue(thong_tin["symtab"])
        self.assertEqual(module.ket_luan(thong_tin), "DAT")


if __name__ == "__main__":
    unittest.main(verbosity=2)
