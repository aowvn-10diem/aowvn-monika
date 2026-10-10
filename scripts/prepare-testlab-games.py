#!/usr/bin/env python3
"""V56: chỉ gọi trong CI; pack lấy đúng URL/hash trong config, game tự sinh trong test APK."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import urllib.request
import wave

HEAD = 'var out = System.exePath;\nfunction mark(name, text) { var a = []; a.add(text + " t=" + System.getTickCount()); a.save(out + name); }\n'
S0 = HEAD + 'mark("monika-ready.txt", "ready");\n'
S1 = HEAD + """class CiWindow extends Window {
  var base;
  function CiWindow() {
    super.Window();
    setInnerSize(640, 360);
    base = new Layer(this, null);
    base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28);
    base.visible = true;
    mark("monika-ready.txt", "ready");
  }
}
var win = new CiWindow(); win.visible = true;
"""
S2 = S1.replace('    mark("monika-ready.txt", "ready");', '    var snd = new WaveSoundBuffer(this); snd.open("beep.wav"); snd.looping = true; snd.play();\n    mark("monika-ready.txt", "ready");')
S3 = HEAD + """class CiWindow extends Window {
  var base, snd, t, pos1;
  function CiWindow() {
    super.Window();
    setInnerSize(640, 360);
    base = new Layer(this, null);
    base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28);
    base.visible = true;
    snd = new WaveSoundBuffer(this);
    snd.open("beep.wav"); snd.looping = true; snd.play();
    t = new Timer(onTimer, ""); t.interval = 1500; t.enabled = true;
    mark("monika-ready.txt", "ready");
  }
  function onTimer() {
    if (pos1 === void) { pos1 = snd.position; return; }
    mark("monika-audio.txt", "status=" + snd.status + " pos1=" + pos1 + " pos2=" + snd.position);
    t.enabled = false;
  }
  function onMouseDown(x, y, button, shift) { mark("monika-touch.txt", "x=" + x + " y=" + y + " b=" + button); }
}
var saveFile = System.exePath + "monika-save.txt";
if (Storages.isExistentStorage(saveFile)) {
  var d = Scripts.evalStorage(saveFile);
  mark("monika-load.txt", "n=" + (d.n + 1));
} else {
  var d = %["n" => 1];
  (Dictionary.saveStruct incontextof d)(saveFile);
}
var win = new CiWindow(); win.visible = true;
"""

def prepare(output, config, head):
    output.mkdir(parents=True, exist_ok=True)
    meta = json.loads(config.read_text())["modules"]["kirikiri"]
    assert meta["abis"] == ["arm64-v8a"]
    # Không nhận URL/tag tùy ý: catalog trong config là nguồn duy nhất.
    request = urllib.request.Request(meta["url"], headers={"User-Agent": "AowMonika-V56-CI", "Accept": "application/octet-stream"})
    digest = hashlib.sha256()
    count = 0
    with urllib.request.urlopen(request, timeout=90) as response, (output / "kirikiri.zip").open("wb") as dest:
        while data := response.read(65536):
            count += len(data)
            if count > 32 * 1024 * 1024:
                raise ValueError("engine vượt giới hạn 32 MiB")
            digest.update(data)
            dest.write(data)
    if digest.hexdigest() != meta["sha256"] or count != meta["size"]:
        raise ValueError("engine hash/size không khớp config")
    # Giữ TJS K1–K8 đã dùng trong ci-emulator-games.sh; không đưa game/ROM bên ngoài vào.
    for tier, src in (("s0", S0), ("s3", S3)):
        game = output / "games" / tier
        game.mkdir(parents=True, exist_ok=True)
        (game / "startup.tjs").write_text(src, encoding="utf-8")
        with wave.open(str(game / "beep.wav"), "wb") as sound:
            sound.setnchannels(1)
            sound.setsampwidth(2)
            sound.setframerate(22050)
            sound.writeframes(b"".join(struct.pack("<h", int(12000 * math.sin(2 * math.pi * 440 * n / 22050))) for n in range(8 * 22050)))
    (output / "metadata.json").write_text(json.dumps({"head": head, "module": "kirikiri", "sha256": meta["sha256"],
        "size": count, "version": meta["version"], "synthetic": True}, indent=2) + "\n")
    print("V56 engine SHA-256/size verified; synthetic K1–K8 test assets generated (no artifact upload)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=Path("config/monika-config.json"))
    parser.add_argument("--head", required=True)
    args = parser.parse_args()
    prepare(args.output, args.config, args.head)
