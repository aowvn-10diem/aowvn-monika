#!/usr/bin/env python3
"""Tạo danh mục tên file cheat của libretro-database cho Aow Monika (app khớp tên game offline, tải file cheat khi cần).

Dùng:  git clone --depth 1 --filter=blob:none --sparse https://github.com/libretro/libretro-database
       (cd libretro-database && git sparse-checkout set cht)
       python3 scripts/cheats-index.py libretro-database/cht > app/src/main/assets/cheats/index.txt
Mỗi dòng: <thư mục hệ máy>\t<tên file không đuôi .cht>. Cập nhật vài tháng/lần là đủ.
"""
import os, sys

# Chỉ hệ máy Monika có lõi libretro hỗ trợ cheat (retro_cheat_set).
REPOS = [
    "Nintendo - Game Boy Advance", "Nintendo - Game Boy Color", "Nintendo - Game Boy",
    "Nintendo - Nintendo DS", "Nintendo - Super Nintendo Entertainment System", "Nintendo - Nintendo Entertainment System",
    "Nintendo - Nintendo 64", "Sega - Mega Drive - Genesis", "Sega - Master System - Mark III", "Sega - Game Gear",
    "Sega - Dreamcast", "NEC - PC Engine - TurboGrafx 16", "Sony - PlayStation", "Sony - PlayStation Portable",
    "Atari - Lynx", "Atari - 2600", "Atari - 7800", "Bandai - WonderSwan", "Bandai - WonderSwan Color",
    "SNK - Neo Geo Pocket", "SNK - Neo Geo Pocket Color",
]
root = sys.argv[1]
for r in REPOS:
    d = os.path.join(root, r)
    if not os.path.isdir(d):
        continue
    for f in sorted(os.listdir(d)):
        if f.endswith(".cht"):
            print(f"{r}\t{f[:-4]}")
