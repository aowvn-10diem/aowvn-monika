#!/usr/bin/env python3
"""V206: ký `config/monika-config.json` thành `config.signed.json` (Ed25519, app đọc 1 lượt GET).

Định dạng: dòng đầu `v1:<128 hex chữ ký>`, sau ký tự xuống dòng là **nguyên văn bytes JSON**.

- Khoá bí mật: biến môi trường `CONFIG_ED25519_PRIVKEY` (32 byte seed, hex 64 ký tự).
- Script **không đọc/in khoá**; thiếu secret thì bỏ qua kèm cảnh báo (app vẫn đọc bản không ký).
- `--print-pubkey`: in khoá công khai (64 hex) để điền
  `ConfigSignature.OFFICIAL_CONFIG_ED25519_PUBKEY_HEX`.
"""
import argparse
import json
import os
import pathlib
import sys

ENV_KEY = "CONFIG_ED25519_PRIVKEY"
SEED_BYTES = 32
SIG_HEX_LEN = 128
HEADER_PREFIX = "v1:"


def warn(message):
    print(f"::warning::{message}", file=sys.stderr)


def load_seed():
    """Đọc seed từ môi trường; trả None khi chưa đặt. Không bao giờ in giá trị."""
    raw = os.environ.get(ENV_KEY, "").strip()
    if not raw:
        return None
    try:
        seed = bytes.fromhex(raw)
    except ValueError:
        raise SystemExit(f"{ENV_KEY} không phải chuỗi hex hợp lệ")
    if len(seed) != SEED_BYTES:
        raise SystemExit(f"{ENV_KEY} phải là {SEED_BYTES} byte ({SEED_BYTES * 2} ký tự hex)")
    return seed


def load_crypto():
    try:
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    except ImportError:
        raise SystemExit("Thiếu thư viện cryptography: python3 -m pip install cryptography")
    return Ed25519PrivateKey, serialization


def public_hex(private_key, serialization):
    return private_key.public_key().public_bytes(
        serialization.Encoding.Raw, serialization.PublicFormat.Raw
    ).hex()


def sign(config_path, out_path, seed):
    payload = config_path.read_bytes()
    # Chặn ký nhầm tệp không phải JSON hợp lệ (bytes ký giữ nguyên, không chuẩn hoá lại).
    json.loads(payload.decode("utf-8"))
    private_key_cls, serialization = load_crypto()
    key = private_key_cls.from_private_bytes(seed)
    signature = key.sign(payload).hex()
    if len(signature) != SIG_HEX_LEN:
        raise SystemExit("chữ ký Ed25519 không đúng độ dài")
    out_path.write_bytes(HEADER_PREFIX.encode() + signature.encode() + b"\n" + payload)
    return signature


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="config/monika-config.json")
    parser.add_argument("--out", default="config.signed.json")
    parser.add_argument(
        "--print-pubkey",
        action="store_true",
        help="in khoá công khai (hex) suy ra từ secret rồi thoát",
    )
    args = parser.parse_args()

    seed = load_seed()
    if args.print_pubkey:
        if seed is None:
            raise SystemExit(f"Thiếu {ENV_KEY} để suy ra khoá công khai")
        private_key_cls, serialization = load_crypto()
        print(public_hex(private_key_cls.from_private_bytes(seed), serialization))
        return 0

    if seed is None:
        warn(
            f"Thiếu {ENV_KEY} — bỏ qua ký config.signed.json; "
            "app vẫn đọc config.json chưa ký."
        )
        return 0

    config_path = pathlib.Path(args.config)
    if not config_path.is_file():
        raise SystemExit(f"Không thấy tệp cấu hình: {config_path}")
    signature = sign(config_path, pathlib.Path(args.out), seed)
    print(f"Đã ký {args.config} → {args.out} (Ed25519, {len(signature)} hex)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
