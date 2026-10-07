#!/usr/bin/env python3
"""SOL013 V50: reject old artifact/setup-gradle majors in tracked workflows."""
from pathlib import Path
import re

required = {"actions/upload-artifact": 7, "gradle/actions/setup-gradle": 6}
for path in sorted((Path(__file__).resolve().parent.parent / ".github/workflows").glob("*.yml")):
    for action, minimum in required.items():
        for m in re.finditer(r"^\s*-?\s*uses:\s*['\"]?" + re.escape(action) + r"@v(\d+)\b", path.read_text(), re.MULTILINE):
            assert int(m.group(1)) >= minimum, f"{path.name}: {action}@v{m.group(1)} < v{minimum}"
print("All upload-artifact >=7 and setup-gradle >=6")
