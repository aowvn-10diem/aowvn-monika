#!/usr/bin/env python3
"""R2: chép 9 file Java của SDL 2.26.3 (zlib) sang module :rgss, đổi gói org.libsdl.app → vn.aow.monika.rgss.sdl.

Dùng:  python3 engines/rgss/rename-sdl-java.py <thư-mục-SDL-release-2.26.3> [thư-mục-đích]
Quy tắc (docs/opus/hop-thu/tra-loi-001.md bước 3): đổi dòng `package`, chuỗi "org.libsdl.app.USB_PERMISSION", không sửa gì khác.
Phần native tương ứng được đổi trong build-rgss.yml (macro SDL_JAVA_PREFIX + chuỗi FindClass).
"""
import sys, pathlib, re

src = pathlib.Path(sys.argv[1]) / "android-project/app/src/main/java/org/libsdl/app"
dst = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "rgss/src/main/java/vn/aow/monika/rgss/sdl")
dst.mkdir(parents=True, exist_ok=True)
OLD, NEW = "org.libsdl.app", "vn.aow.monika.rgss.sdl"
files = sorted(src.glob("*.java"))
assert len(files) == 9, f"mong 9 file Java, thấy {len(files)}"
for f in files:
    t = f.read_text(encoding="utf-8")
    assert t.startswith(f"package {OLD};"), f"{f.name}: dòng package lạ"
    t = t.replace(f"package {OLD};", f"package {NEW};", 1).replace(f'"{OLD}.USB_PERMISSION"', f'"{NEW}.USB_PERMISSION"')
    left = [m.group(0) for m in re.finditer(r"org[./]libsdl[./]app", t)]
    assert not left, f"{f.name}: còn tham chiếu gói cũ: {left[:3]}"
    (dst / f.name).write_text(t, encoding="utf-8")
    print("ok", f.name)
