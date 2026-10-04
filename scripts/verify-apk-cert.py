#!/usr/bin/env python3
"""Print and verify the SHA-256 fingerprint of the first APK signing certificate."""

import argparse
import hashlib
from pathlib import Path
import struct
import sys


EXPECTED_SHA256 = "c46902e97029a96593f43c38abd9871170a7519d90992a0b5466c752d45ab20c"
EOCD_SIGNATURE = b"PK\x05\x06"
APK_SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
APK_V2_ID = 0x7109871A
APK_V3_ID = 0xF05368C0
MAX_EOCD_SIZE = 22 + 0xFFFF


def find_central_directory_offset(apk, file_size):
    tail_size = min(file_size, MAX_EOCD_SIZE)
    apk.seek(file_size - tail_size)
    tail = apk.read(tail_size)
    pos = tail.rfind(EOCD_SIGNATURE)

    while pos >= 0:
        if pos + 22 <= len(tail):
            comment_length = struct.unpack_from("<H", tail, pos + 20)[0]
            if pos + 22 + comment_length == len(tail):
                central_directory_offset = struct.unpack_from("<I", tail, pos + 16)[0]
                if central_directory_offset == 0xFFFFFFFF:
                    raise ValueError("APK ZIP64 chưa được hỗ trợ")
                if central_directory_offset > file_size - 24:
                    raise ValueError("Vị trí central directory nằm ngoài file APK")
                return central_directory_offset
        pos = tail.rfind(EOCD_SIGNATURE, 0, pos)

    raise ValueError("Không tìm thấy ZIP End of Central Directory; file không phải APK hợp lệ")


def read_signing_block(apk, central_directory_offset):
    footer_offset = central_directory_offset - 24
    if footer_offset < 0:
        raise ValueError("Không tìm thấy APK Signing Block v2/v3")

    apk.seek(footer_offset)
    footer = apk.read(24)
    if len(footer) != 24 or footer[8:] != APK_SIGNING_BLOCK_MAGIC:
        raise ValueError("Không tìm thấy APK Signing Block v2/v3")

    block_size = struct.unpack_from("<Q", footer, 0)[0]
    block_start = central_directory_offset - block_size - 8
    if block_size < 24 or block_start < 0:
        raise ValueError("Kích thước APK Signing Block không hợp lệ")

    apk.seek(block_start)
    block = apk.read(block_size + 8)
    if len(block) != block_size + 8 or struct.unpack_from("<Q", block, 0)[0] != block_size:
        raise ValueError("Kích thước đầu và cuối APK Signing Block không khớp")
    if block[-16:] != APK_SIGNING_BLOCK_MAGIC:
        raise ValueError("Magic của APK Signing Block không hợp lệ")

    pairs_end = len(block) - 24
    pos = 8
    pairs = {}
    while pos < pairs_end:
        if pos + 8 > pairs_end:
            raise ValueError("Cặp dữ liệu trong APK Signing Block bị cắt cụt")
        pair_size = struct.unpack_from("<Q", block, pos)[0]
        pair_end = pos + 8 + pair_size
        if pair_size < 4 or pair_end > pairs_end:
            raise ValueError("Độ dài cặp dữ liệu trong APK Signing Block không hợp lệ")
        pair_id = struct.unpack_from("<I", block, pos + 8)[0]
        pairs.setdefault(pair_id, block[pos + 12:pair_end])
        pos = pair_end
    if pos != pairs_end:
        raise ValueError("Dữ liệu APK Signing Block không khớp ranh giới cặp")
    return pairs


def read_length_prefixed(data, offset, label):
    if offset < 0 or offset + 4 > len(data):
        raise ValueError(f"Thiếu độ dài trường {label} trong chữ ký APK")
    length = struct.unpack_from("<I", data, offset)[0]
    start = offset + 4
    end = start + length
    if end > len(data):
        raise ValueError(f"Trường {label} vượt ngoài dữ liệu chữ ký APK")
    return data[start:end], end


def first_certificate(scheme_block, scheme_name):
    signers, end = read_length_prefixed(scheme_block, 0, f"{scheme_name} signers")
    if end != len(scheme_block):
        raise ValueError(f"Dữ liệu {scheme_name} có byte thừa")
    if not signers:
        raise ValueError(f"Chữ ký {scheme_name} không có signer")

    signer, _ = read_length_prefixed(signers, 0, f"{scheme_name} signer")
    signed_data, _ = read_length_prefixed(signer, 0, f"{scheme_name} signed data")
    _, offset = read_length_prefixed(signed_data, 0, f"{scheme_name} digests")
    certificates, _ = read_length_prefixed(signed_data, offset, f"{scheme_name} certificates")
    certificate, _ = read_length_prefixed(certificates, 0, "certificate")
    if not certificate:
        raise ValueError(f"Chữ ký {scheme_name} không có chứng thư")
    return certificate


def signing_certificate_sha256(apk_path):
    with apk_path.open("rb") as apk:
        apk.seek(0, 2)
        file_size = apk.tell()
        central_directory_offset = find_central_directory_offset(apk, file_size)
        pairs = read_signing_block(apk, central_directory_offset)

    for pair_id, scheme_name in ((APK_V3_ID, "v3"), (APK_V2_ID, "v2")):
        scheme_block = pairs.get(pair_id)
        if scheme_block is not None:
            certificate = first_certificate(scheme_block, scheme_name)
            return hashlib.sha256(certificate).hexdigest()

    raise ValueError("APK Signing Block không có chữ ký v2 (0x7109871a) hoặc v3 (0xf05368c0)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="đường dẫn APK cần kiểm tra")
    args = parser.parse_args()

    try:
        actual = signing_certificate_sha256(args.apk)
    except (OSError, ValueError) as error:
        print(f"Lỗi: {error}", file=sys.stderr)
        return 2

    print(f"SHA-256 chứng thư ký APK: {actual}")
    if actual.lower() != EXPECTED_SHA256:
        print(f"Không khớp chứng thư phát hành; mong đợi: {EXPECTED_SHA256}", file=sys.stderr)
        return 1
    print("Khớp chứng thư phát hành.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
