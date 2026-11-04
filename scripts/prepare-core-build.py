#!/usr/bin/env python3
"""V198: dựng thử từ nguồn ghim; chỉ tạo artifact, không đăng hay sửa config."""
import argparse
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
RECIPE = "fceumm-ndk-v1"
REPO = "https://github.com/libretro/libretro-fceumm.git"
NEEDED = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so"}
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
    if not lock["cores"] or set(lock["cores"]) - set(inventory["cores"]):
        raise ValueError("Không có recipe hoặc recipe ngoài kiểm kê")
    for name, core in lock["cores"].items():
        if name != "fceumm" or core["recipe"] != RECIPE or core["repository"] != REPO:
            raise ValueError("Recipe mới cần triển khai và tiền duyệt")
        if not SHA.fullmatch(core["commit"]):
            raise ValueError("Nguồn phải ghim SHA đầy đủ")
        if not core["abis"] or len(set(core["abis"])) != len(core["abis"]) or set(core["abis"]) - ABIS.keys():
            raise ValueError("ABI chưa kiểm hoặc trùng")
        if core["submodules"] != [] or core["patches"] != [] or core["dependencies"] != []:
            raise ValueError("Recipe đầu chưa hỗ trợ phụ thuộc/bản vá ngoài cây nguồn")
        if core["license"] != "GPL-2.0-or-later" or core["licenseFiles"] != ["Copying", "src/ntsc/license.txt"]:
            raise ValueError("Giấy phép chưa khớp recipe")
    return lock


def plan(lock):
    return {"include": [{"core": name, "abi": abi}
                        for name, core in sorted(lock["cores"].items())
                        for abi in core["abis"]]}


def checkout(core, directory):
    run(["git", "init", "-q", directory])
    run(["git", "fetch", "-q", "--depth=1", core["repository"], core["commit"]], directory)
    run(["git", "checkout", "-q", "--detach", "FETCH_HEAD"], directory)
    if run(["git", "rev-parse", "HEAD"], directory) != core["commit"]:
        raise ValueError("Commit checkout không khớp lock")
    if (directory / ".gitmodules").exists():
        raise ValueError("Có submodule chưa ghim; không tự tải")
    for name in core["licenseFiles"]:
        if not (directory / name).is_file():
            raise ValueError("Thiếu giấy phép trong nguồn")


def elf_metadata(path, abi, readelf, nm):
    data = path.read_bytes()
    cls, machine = ABIS[abi]
    if (len(data) < 20 or data[:4] != b"\x7fELF" or data[4] != cls or data[5] != 1
            or int.from_bytes(data[18:20], "little") != machine
            or int.from_bytes(data[16:18], "little") != 3):
        raise ValueError("ELF sai ABI hoặc không phải thư viện động")
    dynamic = run([readelf, "-d", path])
    needed = sorted(set(re.findall(r"Shared library: \[([^\]]+)\]", dynamic)))
    if set(needed) - NEEDED:
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


def prepare(lock, core_id, abi, ndk, output, lock_path):
    core = lock["cores"].get(core_id)
    if core is None or abi not in core["abis"]:
        raise ValueError("Core/ABI chưa có recipe đã kiểm; không tải binary thay thế")
    ndk = ndk.resolve()
    properties = (ndk / "source.properties").read_text()
    revision = re.search(r"^Pkg.Revision\s*=\s*(\S+)", properties, re.M)
    if not revision or revision.group(1) != lock["toolchain"]["ndk"]:
        raise ValueError("NDK không khớp lock")
    if output.exists():
        raise ValueError("Thư mục kết quả đã có; không ghi đè")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="v198-") as tmp:
        temp = Path(tmp)
        source = temp / "source"
        checkout(core, source)
        # Chỉ recipe cố định trong mã, không chạy chuỗi lệnh do JSON/input cung cấp.
        run([ndk / "ndk-build", "-j2", "NDK_PROJECT_PATH=" + str(source),
             "APP_BUILD_SCRIPT=" + str(source / "jni/Android.mk"), "APP_ABI=" + abi,
             "APP_PLATFORM=android-26", "APP_STL=c++_static", "NDK_DEBUG=0"], source)
        tools = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
        library = source / "libs" / abi / "libretro.so"
        metadata = elf_metadata(library, abi, tools / "llvm-readelf", tools / "llvm-nm")
        if run(["git", "status", "--porcelain", "--untracked-files=no"], source):
            raise ValueError("Build đã đổi nguồn tracked; chưa ghi nhận bản vá")
        stage = temp / "result"
        stage.mkdir()
        pack = temp / "pack"
        pack.mkdir()
        target = pack / (core_id + "_libretro_android.so")
        shutil.copyfile(library, target)
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
                      "submodules": [], "patches": [], "dependencies": [], **metadata}
        (pack / "SOURCE.txt").write_text(
            f"{core['repository']}\nCommit: {core['commit']}\nLicense: {core['license']}\n"
            f"Builder: https://github.com/aowvn-10diem/aowvn-monika/commit/{recipe_sha}\n"
            "Dựng từ nguồn bằng ndk-build; không lấy .so từ buildbot/AAR.\n")
        (pack / "needed.txt").write_text("\n".join(metadata["needed"]) + "\n")
        manifest = {**provenance, "loadOrder": [target.name],
                    "files": {target.name: {"size": target.stat().st_size, "sha256": digest(target)}}}
        (pack / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        archive = stage / f"{core_id}-{abi}.zip"
        write_zip(pack, archive)
        sources = stage / f"{core_id}-{core['commit']}-source.tar.gz"
        run(["git", "archive", "--format=tar.gz", "--prefix=" + core_id + "/",
             "--output=" + str(sources), "HEAD"], source)
        shutil.copyfile(__file__, stage / "BUILD-RECIPE.py")
        shutil.copyfile(lock_path, stage / "sources.lock.json")
        symbols = stage / f"symbols-{core_id}-{abi}.zip"
        symbol_source = temp / "symbols"
        symbol_source.mkdir()
        shutil.copyfile(source / "obj/local" / abi / "libretro.so", symbol_source / target.name)
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
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    lock = load_lock(args.lock, args.config)
    if args.plan:
        print(json.dumps(plan(lock), separators=(",", ":")))
        return
    if not all((args.core, args.abi, args.ndk, args.out)):
        parser.error("Cần --plan hoặc đủ --core/--abi/--ndk/--out")
    prepare(lock, args.core, args.abi, args.ndk, args.out.resolve(), args.lock)


if __name__ == "__main__":
    main()
