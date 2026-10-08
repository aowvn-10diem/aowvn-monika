#!/usr/bin/env python3
"""V55: bounded public reads; report only versions/status, never config bodies."""
import argparse
import json
from pathlib import Path
import re
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
MAIN_URL = "https://raw.githubusercontent.com/aowvn-10diem/aowvn-monika/main/config/monika-config.json"
LIMIT = 1024 * 1024


def read_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Monika-PeriodicCheck/1.0", "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        body = response.read(LIMIT + 1)
        if len(body) > LIMIT:
            raise ValueError("response_too_large")
        return json.loads(body)


def version(data):
    value = data.get("configVersion") if isinstance(data, dict) else None
    if type(value) is not int or value < 0:
        raise ValueError("invalid_config_version")
    return value


def compare(fetch, remote_url, main_url=MAIN_URL):
    result = {"status": "unknown", "remote_version": None, "main_version": None}
    try:
        result["main_version"] = version(fetch(main_url))
        result["remote_version"] = version(fetch(remote_url))
        result["status"] = "stale" if result["remote_version"] < result["main_version"] else "current"
    except urllib.error.HTTPError as error:
        result["error"] = "http_" + str(error.code)
    except (urllib.error.URLError, TimeoutError, OSError):
        result["error"] = "network_error"
    except (ValueError, TypeError):
        result["error"] = "invalid_response"
    return result


def remote_url():
    urls = re.findall(r'https://[^\s"\\]+/config\.json', (ROOT / "app/build.gradle.kts").read_text())
    assert len(urls) == 1, "remote config endpoint ambiguous"
    return urls[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    result = compare(read_json, remote_url())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    if args.github_output:
        with args.github_output.open("a") as output:
            output.write("report=" + json.dumps(result, separators=(",", ":")) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["status"] == "current" else 1


if __name__ == "__main__":
    raise SystemExit(main())
