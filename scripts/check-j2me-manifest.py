#!/usr/bin/env python3
"""V53: assert the actual merged app manifest, print component metadata only."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
CLOSED = {"ru.playsoftware.j2meloader.MainActivity", "ru.playsoftware.j2meloader.config.ConfigActivity"}
PREFIXES = ("ru.playsoftware.j2meloader.", "ru.woesss.j2me.", "javax.microedition.", "com.nokia.mid.")


def inspect(path, hardened):
    root = ET.parse(path).getroot()
    app = root.find("application")
    assert app is not None, "missing merged application"
    components = []
    for node in app:
        name = node.get(ANDROID + "name", "")
        if node.tag in {"activity", "receiver", "provider"} and name.startswith(PREFIXES):
            assert node.get(ANDROID + "exported") in {"true", "false"}, f"ambiguous exported: {name}"
            components.append({"kind": node.tag, "name": name, "exported": node.get(ANDROID + "exported") == "true"})
    by_name = {x["name"]: x for x in components}
    for name in CLOSED:
        assert name in by_name, f"missing component: {name}"
        assert by_name[name]["exported"] == (not hardened), f"unexpected exported: {name}"
    if hardened:
        assert not any(x["exported"] for x in components), "unexpected public J2ME component"
    assert "ru.woesss.j2me.installer.MonikaLaunchActivity" in by_name
    assert "javax.microedition.shell.MicroActivity" in by_name
    entry = next((x for x in app.findall("activity") if x.get(ANDROID + "name", "").endswith("vn.aow.monika.ui.MainActivity")), None)
    assert entry is not None and entry.get(ANDROID + "exported") == "true", "external Monika entry must stay public"
    filters = [x for x in entry.findall("intent-filter") if any(a.get(ANDROID + "name") == "android.intent.action.VIEW" for a in x.findall("action"))]
    mimes = {d.get(ANDROID + "mimeType") for f in filters for d in f.findall("data")}
    assert {"application/java-archive", "text/vnd.sun.j2me.app-descriptor"} <= mimes, "JAR/JAD VIEW contract lost"
    return sorted(components, key=lambda x: x["name"])


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("manifest", type=Path)
    p.add_argument("--phase", choices=["before", "after"], required=True)
    args = p.parse_args()
    print(json.dumps({"phase": args.phase, "components": inspect(args.manifest, args.phase == "after")}, ensure_ascii=False, indent=2))
