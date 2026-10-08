#!/usr/bin/env python3
"""Offline tests for verify-apk-cert.py; all signing bytes are synthetic."""
import contextlib
import hashlib
import importlib.util
import io
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("verify-apk-cert.py")
SPEC = importlib.util.spec_from_file_location("verify_apk_cert_under_test", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def lp(data):
    return struct.pack("<I", len(data)) + data


def synthetic_apk(certificate=b"synthetic certificate, not a real key"):
    signed_data = lp(b"") + lp(lp(certificate))
    signer = lp(signed_data)
    signers = lp(signer)
    scheme = lp(signers)
    pair = struct.pack("<Q", len(scheme) + 4) + struct.pack("<I", MODULE.APK_V2_ID) + scheme
    block_size = len(pair) + 24
    signing_block = struct.pack("<Q", block_size) + pair + struct.pack("<Q", block_size) + MODULE.APK_SIGNING_BLOCK_MAGIC
    comment = b"ok"
    eocd = struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, 0, 0, 0, len(signing_block), len(comment))
    return signing_block + eocd + comment, certificate


class VerifyApkCertOfflineTest(unittest.TestCase):
    def test_reads_synthetic_v2_certificate_fingerprint(self):
        apk_bytes, certificate = synthetic_apk()
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "synthetic.apk"
            apk.write_bytes(apk_bytes)
            expected = hashlib.sha256(certificate).hexdigest()
            self.assertEqual(expected, MODULE.signing_certificate_sha256(apk))

            output = io.StringIO()
            with mock.patch.object(MODULE, "EXPECTED_SHA256", expected), \
                    mock.patch.object(sys, "argv", [str(SCRIPT), str(apk)]), \
                    contextlib.redirect_stdout(output):
                self.assertEqual(0, MODULE.main())
            self.assertIn("Khớp chứng thư phát hành.", output.getvalue())

    def test_rejects_apk_without_signing_block(self):
        comment = b"ok"
        eocd = struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, 0, 0, 0, 0, len(comment)) + comment
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "unsigned.apk"
            apk.write_bytes(eocd)
            with self.assertRaisesRegex(ValueError, "Signing Block"):
                MODULE.signing_certificate_sha256(apk)


if __name__ == "__main__":
    unittest.main()
