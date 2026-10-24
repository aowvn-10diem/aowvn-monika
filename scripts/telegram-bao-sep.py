#!/usr/bin/env python3
"""V146: chuyển comment GitHub có tiền tố sang Telegram cho sếp.

An toàn: thân comment chỉ đi qua biến môi trường, đóng JSON bằng json.dumps,
không ghép vào lệnh shell và không in token.
"""
import json
import os
import sys
import urllib.request

PREFIXES = ("[Cố vấn → sếp]", "[PM → sếp]")
DEFAULT_ALLOWED = ("aowvn-10diem", "vudoanbinhminh")
MAX_CHARS = 600
API = "https://api.telegram.org/bot{token}/sendMessage"


def allowed_logins(raw):
    names = tuple(x.strip() for x in (raw or "").split(",") if x.strip())
    return names or DEFAULT_ALLOWED


def should_forward(author, body, names):
    if not author or author not in names:
        return False
    return body.lstrip().startswith(PREFIXES)


def telegram_text(body, url):
    text = body.strip()
    if len(text) > MAX_CHARS:
        text = text[:MAX_CHARS].rstrip()
    return f"{text}\n{url}" if url else text


def post_json(url, payload, opener=urllib.request.urlopen):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with opener(request, timeout=20) as response:
        return response.status


def run(env, opener=urllib.request.urlopen):
    token = (env.get("TELEGRAM_BOT_TOKEN") or "").strip()
    chat_id = (env.get("TELEGRAM_CHAT_ID") or "").strip()
    if not token or not chat_id:
        print("chua cau hinh TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID")
        return 0
    author = (env.get("COMMENT_AUTHOR") or "").strip()
    body = env.get("COMMENT_BODY") or ""
    url = (env.get("COMMENT_URL") or "").strip()
    if not should_forward(author, body, allowed_logins(env.get("ALLOWED_LOGINS"))):
        print("bo qua: tac gia hoac tien to khong khop")
        return 0
    try:
        status = post_json(
            API.format(token=token),
            {
                "chat_id": chat_id,
                "text": telegram_text(body, url),
                "disable_web_page_preview": True,
            },
            opener,
        )
    except Exception as error:
        print("gui that bai: " + type(error).__name__)
        return 1
    print("da gui: status=" + str(status))
    return 0


def main():
    return run(os.environ)


if __name__ == "__main__":
    sys.exit(main())
