"""V44: đọc đúng một Scripts từ archive RGSSAD v1 vào scratch CI, không giải cả game.

Format tham khảo lõi đang dùng: BookerRues9/mkxp-z-android-reworked@b668e08,
app/jni/mkxp-z/src/crypto/rgssad.cpp (RGSS_openArchive/RGSS_ioRead).
Chỉ đọc dữ liệu, không eval/Marshal.load và không ghi lại archive/game.
"""
from pathlib import Path, PurePosixPath
import struct

MAX_ARCHIVE = 3 * 1024**3
MAX_SCRIPT = 64 * 1024**2
MAX_ENTRIES = 10_000
MAX_NAME = 512

def relative_name(name):
    name = name.replace('\\', '/')
    path = PurePosixPath(name)
    if not name or '\0' in name or ':' in name or path.is_absolute() or '..' in path.parts:
        raise ValueError('Tên resource không phải đường dẫn tương đối an toàn')
    return path.as_posix().casefold()

def extract_script(archive, wanted):
    """Trả bytes resource đã giải mã; giới hạn trước mọi cấp phát/seek."""
    wanted = relative_name(wanted)
    size = Path(archive).stat().st_size
    if size > MAX_ARCHIVE:
        raise ValueError('Archive vượt ngân sách')
    key = 0xDEADCAFE
    result = None
    count = 0
    with Path(archive).open('rb') as stream:
        def read_exact(length):
            data = stream.read(length)
            if len(data) != length:
                raise ValueError('Archive bị cắt ngắn')
            return data
        def advance():
            nonlocal key
            old = key
            key = (key * 7 + 3) & 0xffffffff
            return old
        if read_exact(8) != b'RGSSAD\0\x01':
            raise ValueError('Chỉ hỗ trợ RGSSAD v1 (XP/VX), không đoán format khác')
        while stream.tell() < size:
            count += 1
            if count > MAX_ENTRIES:
                raise ValueError('Archive quá nhiều resource')
            length = struct.unpack('<I', read_exact(4))[0] ^ advance()
            if not 0 < length <= MAX_NAME:
                raise ValueError('Tên resource vượt ngân sách')
            name = bytes(value ^ (advance() & 255) for value in read_exact(length))
            normalized = relative_name(name.decode('utf-8', errors='replace'))
            length = struct.unpack('<I', read_exact(4))[0] ^ advance()
            if length > size - stream.tell():
                raise ValueError('Resource vượt cuối archive')
            if normalized == wanted:
                if result is not None:
                    raise ValueError('Scripts trùng trong archive')
                if length > MAX_SCRIPT:
                    raise ValueError('Scripts vượt ngân sách')
                encoded = read_exact(length)
                output = bytearray()
                data_key = key  # Chuỗi cipher của data không đổi key đọc header tiếp theo.
                for offset in range(0, length, 4):
                    part = encoded[offset:offset + 4]
                    word = int.from_bytes(part, 'little') ^ data_key
                    output.extend(word.to_bytes(4, 'little')[:len(part)])
                    data_key = (data_key * 7 + 3) & 0xffffffff
                result = bytes(output)
            else:
                stream.seek(length, 1)
    if result is None:
        raise ValueError('Không tìm được Scripts cấu hình trong archive')
    return result
