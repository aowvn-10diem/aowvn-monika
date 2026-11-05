#!/usr/bin/env python3
"""V198: dựng thử từ nguồn ghim; chỉ tạo artifact, không đăng hay sửa config."""
import argparse
import configparser
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = re.compile(r"[0-9a-f]{40}")
ABIS = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}
PSP_SUBMODULE_REPOS = {
    "ext/OpenXR-SDK": "https://github.com/KhronosGroup/OpenXR-SDK.git",
    "ext/SPIRV-Cross": "https://github.com/KhronosGroup/SPIRV-Cross.git",
    "ext/aemu_postoffice": "https://github.com/Kethen/aemu_postoffice.git",
    "ext/armips": "https://github.com/Kingcom/armips.git",
    "ext/armips/ext/filesystem": "https://github.com/Kingcom/filesystem.git",
    "ext/cpu_features": "https://github.com/google/cpu_features.git",
    "ext/glslang": "https://github.com/hrydgard/glslang.git",
    "ext/libadrenotools": "https://github.com/bylaws/libadrenotools.git",
    "ext/libadrenotools/lib/linkernsbypass": "https://github.com/bylaws/liblinkernsbypass/",
    "ext/libchdr": "https://github.com/rtissera/libchdr.git",
    "ext/lua": "https://github.com/hrydgard/ppsspp-lua.git",
    "ext/miniupnp": "https://github.com/miniupnp/miniupnp.git",
    "ext/rapidjson": "https://github.com/Tencent/rapidjson.git",
    "ext/rapidjson/thirdparty/gtest": "https://github.com/google/googletest.git",
    "ext/rcheevos": "https://github.com/RetroAchievements/rcheevos.git",
    "ext/zstd": "https://github.com/facebook/zstd.git",
    "libretro/libretro-common": "https://github.com/libretro/libretro-common.git"
}
PSP_UNUSED_SUBMODULES = [
    "SDL/macOS",
    "assets/debugger",
    "ext/discord-rpc",
    "ext/freetype",
    "ext/nanosvg",
    "ext/oboe",
    "frametests",
    "ios/MoltenVK",
    "pspautotests"
]
PSP_LICENSE_FILES = [
    "LICENSE.TXT",
    "ext/OpenXR-SDK/COPYING.adoc",
    "ext/OpenXR-SDK/LICENSE",
    "ext/OpenXR-SDK/LICENSES/Apache-2.0.txt",
    "ext/OpenXR-SDK/LICENSES/BSD-3-Clause.txt",
    "ext/OpenXR-SDK/LICENSES/BSL-1.0.txt",
    "ext/OpenXR-SDK/LICENSES/CC-BY-4.0.txt",
    "ext/OpenXR-SDK/LICENSES/CC0-1.0.txt",
    "ext/OpenXR-SDK/LICENSES/ISC.txt",
    "ext/OpenXR-SDK/LICENSES/LicenseRef-Khronos-Free-Use-License-for-Software-and-Documentation.txt",
    "ext/OpenXR-SDK/LICENSES/LicenseRef-KhronosSpecCopyright-WithNormativeWording-v10.txt",
    "ext/OpenXR-SDK/LICENSES/LicenseRef-jsoncpp-public-domain.txt",
    "ext/OpenXR-SDK/LICENSES/MIT.txt",
    "ext/OpenXR-SDK/LICENSES/OFL-1.1-RFN.txt",
    "ext/OpenXR-SDK/LICENSES/Unlicense.txt",
    "ext/OpenXR-SDK/LICENSES/Zlib.txt",
    "ext/OpenXR-SDK/src/external/jnipp/LICENSE",
    "ext/OpenXR-SDK/src/external/jsoncpp/LICENSE",
    "ext/SPIRV-Cross/LICENSE",
    "ext/SPIRV-Cross/LICENSES/Apache-2.0.txt",
    "ext/SPIRV-Cross/LICENSES/CC-BY-4.0.txt",
    "ext/SPIRV-Cross/LICENSES/LicenseRef-KhronosFreeUse.txt",
    "ext/SPIRV-Cross/LICENSES/MIT.txt",
    "ext/aemu_postoffice/LICENSE",
    "ext/aemu_postoffice/ext/tinyalloc-master/LICENSE",
    "ext/armips/LICENSE.txt",
    "ext/armips/ext/filesystem/LICENSE",
    "ext/cityhash/COPYING",
    "ext/cpu_features/LICENSE",
    "ext/ffmpeg/COPYING.LGPLv2.1",
    "ext/ffmpeg/LICENSE.md",
    "ext/gason/LICENSE",
    "ext/glslang/LICENSE.txt",
    "ext/glslang/license-checker.cfg",
    "ext/libadrenotools/LICENSE",
    "ext/libadrenotools/lib/linkernsbypass/LICENSE",
    "ext/libchdr/LICENSE.txt",
    "ext/libchdr/deps/lzma-24.05/LICENSE",
    "ext/libchdr/deps/zlib-1.3.1/LICENSE",
    "ext/libchdr/deps/zlib-1.3.1/contrib/dotzlib/LICENSE_1_0.txt",
    "ext/libchdr/deps/zstd-1.5.6/COPYING",
    "ext/libchdr/deps/zstd-1.5.6/LICENSE",
    "ext/libchdr/deps/zstd-1.5.6/build/LICENSE",
    "ext/libdeflate/COPYING",
    "ext/miniupnp/LICENSE",
    "ext/naett-lib/LICENSE",
    "ext/portable-file-dialogs/COPYING",
    "ext/rapidjson/bin/jsonschema/LICENSE",
    "ext/rapidjson/contrib/natvis/LICENSE",
    "ext/rapidjson/license.txt",
    "ext/rapidjson/thirdparty/gtest/googlemock/LICENSE",
    "ext/rapidjson/thirdparty/gtest/googlemock/scripts/generator/LICENSE",
    "ext/rapidjson/thirdparty/gtest/googletest/LICENSE",
    "ext/rcheevos/LICENSE",
    "ext/snappy/COPYING",
    "ext/udis86/LICENSE",
    "ext/zlib/LICENSE",
    "ext/zstd/COPYING",
    "ext/zstd/LICENSE",
    "ext/zstd/build/LICENSE",
    "libretro/libretro-common/formats/h264/COPYING.MIT",
    "libretro/libretro-common/formats/h264/COPYING.OpenH264"
]
RECIPES = {
    "fceumm": {
        "recipe": "fceumm-ndk-v1",
        "repository": "https://github.com/libretro/libretro-fceumm.git",
        "androidMk": "jni/Android.mk",
        "license": "GPL-2.0-or-later",
        "licenseFiles": ["Copying", "src/ntsc/license.txt"],
    },
    "gambatte": {
        "recipe": "gambatte-ndk-v1",
        "repository": "https://github.com/libretro/gambatte-libretro.git",
        "androidMk": "libgambatte/libretro/jni/Android.mk",
        "license": "GPL-2.0-only",
        "licenseFiles": ["COPYING"],
    },
    "mgba": {
        "recipe": "mgba-cmake-v1",
        "repository": "https://github.com/libretro/mgba.git",
        "license": "MPL-2.0",
        "licenseFiles": ["LICENSE", "src/third-party/inih/LICENSE.txt"],
    },
    "ppsspp": {
        "recipe": "ppsspp-cmake-v1",
        "repository": "https://github.com/hrydgard/ppsspp.git",
        "license": "GPL-2.0-or-later",
        "licenseFiles": PSP_LICENSE_FILES,
    },
}
NEEDED = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so"}
PSP_NEEDED = NEEDED | {"libGLESv2.so", "libEGL.so", "libOpenSLES.so", "libz.so", "libvulkan.so"}
EXPORTS = {
    "retro_api_version", "retro_init", "retro_deinit", "retro_get_system_info",
    "retro_get_system_av_info", "retro_set_environment", "retro_set_video_refresh",
    "retro_set_audio_sample", "retro_set_audio_sample_batch", "retro_set_input_poll",
    "retro_set_input_state", "retro_set_controller_port_device", "retro_reset",
    "retro_run", "retro_serialize_size", "retro_serialize", "retro_unserialize",
    "retro_cheat_reset", "retro_cheat_set", "retro_load_game",
    "retro_load_game_special", "retro_unload_game", "retro_get_region",
    "retro_get_memory_data", "retro_get_memory_size",
}


def run(args, cwd=None):
    return subprocess.run([str(a) for a in args], cwd=cwd, check=True,
                          text=True, stdout=subprocess.PIPE).stdout.strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_lock(path, config_path):
    lock = json.loads(path.read_text())
    config = json.loads(config_path.read_text())
    if lock["schemaVersion"] != 1:
        raise ValueError("Phiên bản lock chưa hỗ trợ")
    inventory = lock["inventory"]
    for key in ("cores", "modules"):
        names = inventory[key]
        if len(set(names)) != len(names) or set(names) != set(config[key]):
            raise ValueError("Kiểm kê không khớp config: " + key)
    if (len(inventory["additional"]) != 3 or
            set(inventory["additional"]) != {"renpy8", "ruffle", "ppsspp-system"}):
        raise ValueError("Thiếu engine web/native phụ trợ")
    tools = lock["toolchain"]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", tools["ndk"]):
        raise ValueError("NDK phải là phiên bản cố định")
    if tools["api"] != 26:
        raise ValueError("Chưa kiểm API này")
    if (tools.get("cmake") != "3.22.1" or tools.get("cmakeBuild") != "3.22.1-g37088a8"
            or tools.get("ninja") != "1.10.2"):
        raise ValueError("CMake/Ninja phải khớp phiên bản recipe đã kiểm")
    if not lock["cores"] or set(lock["cores"]) - set(inventory["cores"]):
        raise ValueError("Không có recipe hoặc recipe ngoài kiểm kê")
    for name, core in lock["cores"].items():
        recipe = RECIPES.get(name)
        if recipe is None:
            raise ValueError("Recipe mới cần triển khai và tiền duyệt")
        if any(core.get(key) != recipe[key] for key in
               ("recipe", "repository", "license", "licenseFiles")):
            raise ValueError("Nguồn/giấy phép chưa khớp recipe")
        if not SHA.fullmatch(core["commit"]):
            raise ValueError("Nguồn phải ghim SHA đầy đủ")
        if not core["abis"] or len(set(core["abis"])) != len(core["abis"]) or set(core["abis"]) - ABIS.keys():
            raise ValueError("ABI chưa kiểm hoặc trùng")
        if name == "ppsspp":
            modules = core["submodules"]
            if (not isinstance(modules, list) or len(modules) != len(PSP_SUBMODULE_REPOS)
                    or any(not isinstance(m, dict) or set(m) != {"path", "repository", "commit"}
                           or any(not isinstance(v, str) for v in m.values()) for m in modules)
                    or {m["path"]: m["repository"] for m in modules} != PSP_SUBMODULE_REPOS
                    or any(not SHA.fullmatch(m["commit"]) for m in modules)):
                raise ValueError("Submodule PSP phải ghim đủ đường/nguồn/SHA đã duyệt")
        elif core["submodules"] != []:
            raise ValueError("Recipe chưa hỗ trợ submodule")
        if core["patches"] != [] or core["dependencies"] != []:
            raise ValueError("Recipe đầu chưa hỗ trợ phụ thuộc/bản vá ngoài cây nguồn")
    return lock


def plan(lock):
    return {"include": [{"core": name, "abi": abi}
                        for name, core in sorted(lock["cores"].items())
                        for abi in core["abis"]]}


def fetch_pinned(repository, commit, directory):
    directory.mkdir(parents=True, exist_ok=True)
    run(["git", "init", "-q", directory])
    run(["git", "fetch", "-q", "--depth=1", repository, commit], directory)
    run(["git", "checkout", "-q", "--detach", "FETCH_HEAD"], directory)
    if run(["git", "rev-parse", "HEAD"], directory) != commit:
        raise ValueError("Commit checkout không khớp lock")


def verify_psp_gitlinks(directory, prefix, modules):
    """Đối chiếu gitlink và .gitmodules, không chạy git submodule update/--remote."""
    entries = {}
    for line in run(["git", "ls-tree", "-r", "HEAD"], directory).splitlines():
        meta, name = line.split("\t", 1)
        mode, kind, commit = meta.split()
        if kind == "commit":
            entries[name] = commit
    urls = {}
    module_file = directory / ".gitmodules"
    if module_file.exists():
        parser = configparser.RawConfigParser()
        parser.read_string(module_file.read_text())
        for section in parser.sections():
            path = parser[section]["path"]
            if path in urls:
                raise ValueError("Đường submodule PSP bị trùng")
            urls[path] = parser[section]["url"]
    found = set()
    for path, commit in entries.items():
        full = prefix + "/" + path if prefix else path
        if not prefix and full in PSP_UNUSED_SUBMODULES:
            continue  # frontend/test/Apple/VR không được dựng trong recipe LIBRETRO này
        expected = modules.get(full)
        if expected is None or expected["commit"] != commit or expected["repository"] != urls.get(path):
            raise ValueError("Gitlink/URL/SHA submodule PSP không khớp lock: " + full)
        found.add(full)
    return found


def checkout(core, directory):
    fetch_pinned(core["repository"], core["commit"], directory)
    psp = core["recipe"] == "ppsspp-cmake-v1"
    if (directory / ".gitmodules").exists() and not psp:
        raise ValueError("Có submodule chưa ghim; không tự tải")
    if psp:
        modules = {m["path"]: m for m in core["submodules"]}
        found = verify_psp_gitlinks(directory, "", modules)
        for path in sorted(modules, key=lambda p: (p.count("/"), p)):
            if path not in found:
                raise ValueError("Submodule PSP thiếu gitlink trước khi tải: " + path)
            module = modules[path]
            target = directory / path  # đường cố định trong PSP_SUBMODULE_REPOS, không nhận path tự do
            fetch_pinned(module["repository"], module["commit"], target)
            found |= verify_psp_gitlinks(target, path, modules)
        if found != set(modules):
            raise ValueError("Thiếu gitlink submodule PSP trong nguồn")
    for name in core["licenseFiles"]:
        file = directory / name
        if not file.is_file() or file.is_symlink() or not file.resolve().is_relative_to(directory.resolve()):
            raise ValueError("Thiếu giấy phép trong nguồn")


def elf_metadata(path, abi, readelf, nm, allowed_needed=NEEDED):
    data = path.read_bytes()
    cls, machine = ABIS[abi]
    if (len(data) < 20 or data[:4] != b"\x7fELF" or data[4] != cls or data[5] != 1
            or int.from_bytes(data[18:20], "little") != machine
            or int.from_bytes(data[16:18], "little") != 3):
        raise ValueError("ELF sai ABI hoặc không phải thư viện động")
    dynamic = run([readelf, "-d", path])
    needed = sorted(set(re.findall(r"Shared library: \[([^\]]+)\]", dynamic)))
    if set(needed) - allowed_needed:
        raise ValueError("Phụ thuộc không có trong gói/NDK: " + ",".join(needed))
    symbols = run([nm, "-D", "--defined-only", path])
    exported = {line.split()[-1] for line in symbols.splitlines() if line.split()}
    if EXPORTS - exported:
        raise ValueError("Thiếu hợp đồng libretro: " + ",".join(sorted(EXPORTS - exported)))
    notes = run([readelf, "-n", path])
    build_id = re.search(r"Build ID: ([0-9a-f]+)", notes)
    if not build_id:
        raise ValueError("Thiếu BuildId để giải ký hiệu")
    return {"needed": needed, "buildId": build_id.group(1)}


def require_debug_symbols(path, readelf):
    sections = run([readelf, "-S", path])
    if ".debug_info" not in sections or ".symtab" not in sections:
        raise ValueError("PSP thiếu debug info/symbol table; không nhận symbols dynamic-only")


def write_zip(directory, target):
    """Chỉ đóng gói file thường trong cây do builder tạo."""
    files = sorted(p for p in directory.rglob("*") if p.is_file())
    if not files:
        raise ValueError("Gói rỗng")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in files:
            if path.is_symlink() or not path.resolve().is_relative_to(directory.resolve()):
                raise ValueError("Không đóng symlink hoặc đường dẫn ngoài gói")
            info = zipfile.ZipInfo(path.relative_to(directory).as_posix(), (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def cmake_tools(lock, cmake):
    if cmake is None:
        raise ValueError("Recipe CMake cần công cụ ghim qua --cmake; không tự tìm/tải công cụ")
    cmake = cmake.resolve()
    if run([cmake, "--version"]).splitlines()[0] != "cmake version " + lock["toolchain"]["cmakeBuild"]:
        raise ValueError("CMake không khớp lock")
    ninja = cmake.with_name("ninja")
    if run([ninja, "--version"]) != lock["toolchain"]["ninja"]:
        raise ValueError("Ninja không khớp lock")
    return cmake, ninja


def build(core_id, source, abi, ndk, cmake=None, ninja=None):
    """Trả ELF cài và ELF giữ ký hiệu; đường/lệnh cố định trong recipe."""
    if core_id not in ("mgba", "ppsspp"):
        run([ndk / "ndk-build", "-j2", "NDK_PROJECT_PATH=" + str(source),
             "APP_BUILD_SCRIPT=" + str(source / RECIPES[core_id]["androidMk"]), "APP_ABI=" + abi,
             "APP_PLATFORM=android-26", "APP_STL=c++_static", "NDK_DEBUG=0"], source)
        return source / "libs" / abi / "libretro.so", source / "obj/local" / abi / "libretro.so"
    directory = source.parent / "cmake-build"
    if core_id == "ppsspp":
        run([cmake, "-S", source, "-B", directory, "-G", "Ninja",
             "-DCMAKE_MAKE_PROGRAM=" + str(ninja),
             "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
             "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-26", "-DANDROID_STL=c++_static",
             "-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DCMAKE_CXX_STANDARD=17",
             "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=sha1", "-DLIBRETRO=ON",
             "-DHEADLESS=OFF", "-DUNITTEST=OFF", "-DATLAS_TOOL=OFF", "-DOPENXR=OFF",
             "-DUSE_DISCORD=OFF", "-DUSE_CCACHE=OFF", "-DUSE_SYSTEM_FFMPEG=OFF",
             "-DUSE_SYSTEM_LIBPNG=OFF", "-DUSE_SYSTEM_ZSTD=OFF", "-DUSE_SYSTEM_LIBCHDR=OFF",
             "-DMOBILE_DEVICE=ON"], source)
        run([cmake, "--build", directory, "--target", "ppsspp_libretro", "--parallel", "2"], source)
        library = directory / "ppsspp_libretro_android.so"
        return library, library
    run([cmake, "-S", source, "-B", directory, "-G", "Ninja",
         "-DCMAKE_MAKE_PROGRAM=" + str(ninja),
         "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
         "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-26", "-DANDROID_STL=c++_static",
         "-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=sha1",
         "-DBUILD_LIBRETRO=ON", "-DSKIP_LIBRARY=ON", "-DDISABLE_FRONTENDS=ON",
         "-DDISABLE_DEPS=ON", "-DENABLE_DEBUGGERS=OFF", "-DENABLE_SCRIPTING=OFF",
         "-DUSE_EDITLINE=OFF", "-DUSE_FFMPEG=OFF", "-DUSE_MINIZIP=OFF",
         "-DUSE_LIBZIP=OFF", "-DUSE_ELF=OFF", "-DUSE_LUA=OFF", "-DUSE_LZMA=OFF",
         "-DUSE_EPOXY=OFF", "-DBUILD_GL=OFF", "-DBUILD_GLES2=OFF", "-DBUILD_GLES3=OFF"], source)
    run([cmake, "--build", directory, "--target", "mgba_libretro", "--parallel", "2"], source)
    library = directory / "mgba_libretro.so"
    return library, library


def prepare(lock, core_id, abi, ndk, output, lock_path, cmake=None):
    core = lock["cores"].get(core_id)
    if core is None or abi not in core["abis"]:
        raise ValueError("Core/ABI chưa có recipe đã kiểm; không tải binary thay thế")
    ndk = ndk.resolve()
    properties = (ndk / "source.properties").read_text()
    revision = re.search(r"^Pkg.Revision\s*=\s*(\S+)", properties, re.M)
    if not revision or revision.group(1) != lock["toolchain"]["ndk"]:
        raise ValueError("NDK không khớp lock")
    ninja = None
    if core_id in ("mgba", "ppsspp"):
        cmake, ninja = cmake_tools(lock, cmake)
    if output.exists():
        raise ValueError("Thư mục kết quả đã có; không ghi đè")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="v198-") as tmp:
        temp = Path(tmp)
        source = temp / "source"
        checkout(core, source)
        # Chỉ recipe cố định trong mã, không chạy chuỗi lệnh do JSON/input cung cấp.
        library, symbol_library = build(core_id, source, abi, ndk, cmake, ninja)
        tools = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
        metadata = elf_metadata(library, abi, tools / "llvm-readelf", tools / "llvm-nm",
                                PSP_NEEDED if core_id == "ppsspp" else NEEDED)
        if core_id == "ppsspp":
            require_debug_symbols(symbol_library, tools / "llvm-readelf")
        if run(["git", "status", "--porcelain", "--untracked-files=no"], source):
            raise ValueError("Build đã đổi nguồn tracked; chưa ghi nhận bản vá")
        stage = temp / "result"
        stage.mkdir()
        pack = temp / "pack"
        pack.mkdir()
        target = pack / (core_id + "_libretro_android.so")
        shutil.copyfile(library, target)
        if core_id in ("mgba", "ppsspp"):
            run([tools / "llvm-strip", "--strip-unneeded", target])
        for name in core["licenseFiles"]:
            dest = pack / "LICENSES" / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source / name, dest)
        recipe_sha = run(["git", "rev-parse", "HEAD"], ROOT)
        if not SHA.fullmatch(recipe_sha):
            raise ValueError("Không xác định được commit builder")
        provenance = {"core": core_id, "abi": abi, "repository": core["repository"],
                      "commit": core["commit"], "recipe": core["recipe"],
                      "builderCommit": recipe_sha, "lockSha256": digest(lock_path),
                      "recipeSha256": digest(Path(__file__)),
                      "toolchain": lock["toolchain"], "license": core["license"],
                      "submodules": core["submodules"], "patches": [], "dependencies": [], **metadata}
        (pack / "SOURCE.txt").write_text(
            f"{core['repository']}\nCommit: {core['commit']}\nLicense: {core['license']}\n"
            f"Builder: https://github.com/aowvn-10diem/aowvn-monika/commit/{recipe_sha}\n"
            f"Dựng từ nguồn bằng {'CMake/Ninja' if core_id in ('mgba', 'ppsspp') else 'ndk-build'}; "
            "không lấy .so từ buildbot/AAR.\n")
        (pack / "needed.txt").write_text("\n".join(metadata["needed"]) + "\n")
        manifest = {**provenance, "loadOrder": [target.name],
                    "files": {target.name: {"size": target.stat().st_size, "sha256": digest(target)}}}
        (pack / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        archive = stage / f"{core_id}-{abi}.zip"
        write_zip(pack, archive)
        sources = stage / f"{core_id}-{core['commit']}-source.tar.gz"
        run(["git", "archive", "--format=tar.gz", "--prefix=" + core_id + "/",
             "--output=" + str(sources), "HEAD"], source)
        for module in core["submodules"]:
            name = module["path"].replace("/", "_")
            archive_path = stage / f"{core_id}-{name}-{module['commit']}-source.tar.gz"
            run(["git", "archive", "--format=tar.gz", "--prefix=" + core_id + "/" + module["path"] + "/",
                 "--output=" + str(archive_path), "HEAD"], source / module["path"])
        shutil.copyfile(__file__, stage / "BUILD-RECIPE.py")
        shutil.copyfile(lock_path, stage / "sources.lock.json")
        symbols = stage / f"symbols-{core_id}-{abi}.zip"
        symbol_source = temp / "symbols"
        symbol_source.mkdir()
        shutil.copyfile(symbol_library, symbol_source / target.name)
        run(["python3", ROOT / "scripts/package-native-symbols.py", "--pack", pack,
             "--source-root", symbol_source, "--readelf", tools / "llvm-readelf",
             "--abi", abi, "--output", symbols])
        provenance["artifacts"] = {p.name: {"size": p.stat().st_size, "sha256": digest(p)}
                                   for p in sorted(stage.iterdir())}
        (stage / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
        (stage / "SHA256SUMS").write_text("".join(
            f"{digest(p)}  {p.name}\n" for p in sorted(stage.iterdir()) if p.is_file()))
        # Kết quả chỉ xuất hiện sau khi mọi bước kiểm đã qua.
        shutil.copytree(stage, output)
    print("ĐẠT: " + core_id + "/" + abi + "; chỉ chuẩn bị artifact, chưa đăng.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=Path, default=ROOT / "engines/sources.lock.json")
    parser.add_argument("--config", type=Path, default=ROOT / "config/monika-config.json")
    parser.add_argument("--plan", action="store_true")
    parser.add_argument("--core")
    parser.add_argument("--abi")
    parser.add_argument("--ndk", type=Path)
    parser.add_argument("--cmake", type=Path, help="CMake SDK ghim; Ninja cùng thư mục (mGBA/PSP)")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    lock = load_lock(args.lock, args.config)
    if args.plan:
        print(json.dumps(plan(lock), separators=(",", ":")))
        return
    if not all((args.core, args.abi, args.ndk, args.out)):
        parser.error("Cần --plan hoặc đủ --core/--abi/--ndk/--out")
    prepare(lock, args.core, args.abi, args.ndk, args.out.resolve(), args.lock, args.cmake)


if __name__ == "__main__":
    main()
