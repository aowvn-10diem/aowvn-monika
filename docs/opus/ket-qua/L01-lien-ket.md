# L01 — Kiểm tra liên kết trong cấu hình

Kiểm tra lúc 2026-10-04 01:11 UTC. Nguồn: `config/monika-config.json`; ABI lấy từ `app/build.gradle.kts`: arm64-v8a, armeabi-v7a. Với `cores.<id>.abis`, chỉ kiểm ABI được khai; ABI còn lại được ghi là bỏ qua.
Tổng 70 mục: 61 đã gửi yêu cầu, 0 phản hồi lỗi/kết nối, 9 mục không kiểm.

URL `aow.vn` được bỏ qua vì máy chạy ngoài Việt Nam; không tính là hỏng. Chỉ gửi HEAD; nếu máy chủ từ chối HEAD (405/501), gửi GET `Range: bytes=0-0` và đọc tối đa 1 byte.
Mẫu URL còn tham số (ví dụ `$1`) được ghi nhận nhưng không gửi đi.

| Nguồn cấu hình | URL | HTTP | Phương thức | Kích thước phản hồi | Kích thước config | So sánh | Ghi chú |
|---|---|---:|---|---:|---:|---|---|
| account.loginUrl | `https://www.aow.vn/p/device-login.html` | — | — | — | — | — | Không kiểm được từ máy ngoài VN; không coi là hỏng |
| account.voteUrl | `https://www.aow.vn/p/vote-game.html` | — | — | — | — | — | Không kiểm được từ máy ngoài VN; không coi là hỏng |
| cores.citra.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/citra_libretro_android.so.zip` | — | — | — | — | — | Bỏ qua: ABI armeabi-v7a không khai trong cores.citra.abis |
| cores.melondsds.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/melondsds_libretro_android.so.zip` | — | — | — | — | — | Bỏ qua: ABI armeabi-v7a không khai trong cores.melondsds.abis |
| downloadHosts[0].directUrl | `https://pixeldrain.com/api/file/$1?download` | — | — | — | — | — | Mẫu URL còn tham số; chưa gửi yêu cầu |
| downloadHosts[1].directUrl | `https://pixeldrain.com/api/filesystem/$1?attach` | — | — | — | — | — | Mẫu URL còn tham số; chưa gửi yêu cầu |
| externalApps[0].downloadUrl | `https://www.aow.vn/p/tai-gia-lap-kirikiroid.html` | — | — | — | — | — | Không kiểm được từ máy ngoài VN; không coi là hỏng |
| externalApps[1].downloadUrl | `https://www.aow.vn/p/tai-gia-lap-joiplay.html` | — | — | — | — | — | Không kiểm được từ máy ngoài VN; không coi là hỏng |
| feed.url | `https://www.aow.vn/feeds/posts/default` | — | — | — | — | — | Không kiểm được từ máy ngoài VN; không coi là hỏng |
| account.databaseUrl | `https://aowvn-xemtrang.firebaseio.com` | 200 | GET range 0-0 | — | — | — | Đã nhận phản hồi HTTP |
| adblock.lists[0] | `https://cdn.jsdelivr.net/gh/abpvn/abpvn@master/filter/abpvn_ublock.txt` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| adblock.lists[1] | `https://easylist.to/easylist/easylist.txt` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| adblock.lists[2] | `https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro-onlydomains.txt` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| adblock.lists[3] | `https://abpvn.com/android/abpvn.txt` | 200 | HEAD | 566,158 B | — | — | Đã nhận phản hồi HTTP |
| community.channels[0].url | `https://www.facebook.com/groups/aowvn` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| community.channels[1].url | `https://discord.gg/ynZAnpnXPk` | 200 | HEAD | 23,196 B | — | — | Đã nhận phản hồi HTTP |
| community.channels[2].url | `https://forum.aowvn.org` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| community.discord | `https://discord.gg/ynZAnpnXPk` | 200 | HEAD | 23,180 B | — | — | Đã nhận phản hồi HTTP |
| community.facebookGroup | `https://www.facebook.com/groups/aowvn` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| cores.citra.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/citra_libretro_android.so.zip` | 200 | HEAD | 9,730,003 B | — | — | Đã nhận phản hồi HTTP |
| cores.desmume.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/desmume_libretro_android.so.zip` | 200 | HEAD | 847,738 B | — | — | Đã nhận phản hồi HTTP |
| cores.desmume.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/desmume_libretro_android.so.zip` | 200 | HEAD | 739,425 B | — | — | Đã nhận phản hồi HTTP |
| cores.easyrpg.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/easyrpg_libretro_android.so.zip` | 200 | HEAD | 9,939,535 B | — | — | Đã nhận phản hồi HTTP |
| cores.easyrpg.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/easyrpg_libretro_android.so.zip` | 200 | HEAD | 9,439,969 B | — | — | Đã nhận phản hồi HTTP |
| cores.fceumm.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/fceumm_libretro_android.so.zip` | 200 | HEAD | 445,577 B | — | — | Đã nhận phản hồi HTTP |
| cores.fceumm.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/fceumm_libretro_android.so.zip` | 200 | HEAD | 348,340 B | — | — | Đã nhận phản hồi HTTP |
| cores.flycast.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/flycast_libretro_android.so.zip` | 200 | HEAD | 5,938,009 B | — | — | Đã nhận phản hồi HTTP |
| cores.flycast.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/flycast_libretro_android.so.zip` | 200 | HEAD | 5,848,440 B | — | — | Đã nhận phản hồi HTTP |
| cores.gambatte.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/gambatte_libretro_android.so.zip` | 200 | HEAD | 433,996 B | — | — | Đã nhận phản hồi HTTP |
| cores.gambatte.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/gambatte_libretro_android.so.zip` | 200 | HEAD | 369,326 B | — | — | Đã nhận phản hồi HTTP |
| cores.genesis_plus_gx.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/genesis_plus_gx_libretro_android.so.zip` | 200 | HEAD | 1,133,412 B | — | — | Đã nhận phản hồi HTTP |
| cores.genesis_plus_gx.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/genesis_plus_gx_libretro_android.so.zip` | 200 | HEAD | 1,063,628 B | — | — | Đã nhận phản hồi HTTP |
| cores.handy.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/handy_libretro_android.so.zip` | 200 | HEAD | 141,020 B | — | — | Đã nhận phản hồi HTTP |
| cores.handy.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/handy_libretro_android.so.zip` | 200 | HEAD | 113,799 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_ngp.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mednafen_ngp_libretro_android.so.zip` | 200 | HEAD | 152,520 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_ngp.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mednafen_ngp_libretro_android.so.zip` | 200 | HEAD | 124,934 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_pce_fast.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mednafen_pce_fast_libretro_android.so.zip` | 200 | HEAD | 374,130 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_pce_fast.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mednafen_pce_fast_libretro_android.so.zip` | 200 | HEAD | 301,133 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_wswan.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mednafen_wswan_libretro_android.so.zip` | 200 | HEAD | 97,945 B | — | — | Đã nhận phản hồi HTTP |
| cores.mednafen_wswan.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mednafen_wswan_libretro_android.so.zip` | 200 | HEAD | 97,335 B | — | — | Đã nhận phản hồi HTTP |
| cores.melonds.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/melonds_libretro_android.so.zip` | 200 | HEAD | 690,401 B | — | — | Đã nhận phản hồi HTTP |
| cores.melonds.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/melonds_libretro_android.so.zip` | 200 | HEAD | 520,302 B | — | — | Đã nhận phản hồi HTTP |
| cores.melondsds.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/melondsds_libretro_android.so.zip` | 200 | HEAD | 2,003,724 B | — | — | Đã nhận phản hồi HTTP |
| cores.mgba.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mgba_libretro_android.so.zip` | 200 | HEAD | 419,371 B | — | — | Đã nhận phản hồi HTTP |
| cores.mgba.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mgba_libretro_android.so.zip` | 200 | HEAD | 383,098 B | — | — | Đã nhận phản hồi HTTP |
| cores.mupen64plus_next_gles3.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mupen64plus_next_gles3_libretro_android.so.zip` | 200 | HEAD | 2,201,357 B | — | — | Đã nhận phản hồi HTTP |
| cores.mupen64plus_next_gles3.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mupen64plus_next_gles3_libretro_android.so.zip` | 200 | HEAD | 2,307,697 B | — | — | Đã nhận phản hồi HTTP |
| cores.nestopia.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip` | 200 | HEAD | 807,985 B | — | — | Đã nhận phản hồi HTTP |
| cores.nestopia.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/nestopia_libretro_android.so.zip` | 200 | HEAD | 631,120 B | — | — | Đã nhận phản hồi HTTP |
| cores.parallel_n64.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/parallel_n64_libretro_android.so.zip` | 200 | HEAD | 1,962,066 B | — | — | Đã nhận phản hồi HTTP |
| cores.parallel_n64.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/parallel_n64_libretro_android.so.zip` | 200 | HEAD | 2,096,629 B | — | — | Đã nhận phản hồi HTTP |
| cores.pcsx_rearmed.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/pcsx_rearmed_libretro_android.so.zip` | 200 | HEAD | 532,595 B | — | — | Đã nhận phản hồi HTTP |
| cores.pcsx_rearmed.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/pcsx_rearmed_libretro_android.so.zip` | 200 | HEAD | 517,745 B | — | — | Đã nhận phản hồi HTTP |
| cores.ppsspp.systemFiles | `https://buildbot.libretro.com/assets/system/PPSSPP.zip` | 200 | HEAD | 9,201,341 B | — | — | Đã nhận phản hồi HTTP |
| cores.ppsspp.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/ppsspp_libretro_android.so.zip` | 200 | HEAD | 7,718,092 B | — | — | Đã nhận phản hồi HTTP |
| cores.ppsspp.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/ppsspp_libretro_android.so.zip` | 200 | HEAD | 7,307,458 B | — | — | Đã nhận phản hồi HTTP |
| cores.prosystem.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/prosystem_libretro_android.so.zip` | 200 | HEAD | 47,540 B | — | — | Đã nhận phản hồi HTTP |
| cores.prosystem.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/prosystem_libretro_android.so.zip` | 200 | HEAD | 36,344 B | — | — | Đã nhận phản hồi HTTP |
| cores.snes9x.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/snes9x_libretro_android.so.zip` | 200 | HEAD | 890,362 B | — | — | Đã nhận phản hồi HTTP |
| cores.snes9x.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/snes9x_libretro_android.so.zip` | 200 | HEAD | 824,667 B | — | — | Đã nhận phản hồi HTTP |
| cores.stella2014.url [arm64-v8a] | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/stella2014_libretro_android.so.zip` | 200 | HEAD | 599,044 B | — | — | Đã nhận phản hồi HTTP |
| cores.stella2014.url [armeabi-v7a] | `https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/stella2014_libretro_android.so.zip` | 200 | HEAD | 478,423 B | — | — | Đã nhận phản hồi HTTP |
| crash.endpoint | `https://aowvn-monika-crash.aowvn-system.workers.dev/report` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| forum.baseUrl | `https://forum.aowvn.org` | 200 | HEAD | — | — | — | Đã nhận phản hồi HTTP |
| modules.azahar.url | `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/engine-azahar-1/azahar-android-arm64.zip` | 200 | HEAD | 13,116,908 B | 13,116,908 B | khớp | Đã nhận phản hồi HTTP |
| modules.kirikiri.url | `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/engines-kirikiri-14/kirikiri-arm64.zip` | 200 | HEAD | 17,166,751 B | 17,166,751 B | khớp | Đã nhận phản hồi HTTP |
| modules.onsyuri.url | `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/engines-onsyuri-2/onsyuri-web.zip` | 200 | HEAD | 1,789,894 B | 1,789,894 B | khớp | Đã nhận phản hồi HTTP |
| modules.sevenzip.url [arm64-v8a] | `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/pack-sevenzip-1/sevenzip-arm64-v8a.zip` | 200 | HEAD | 966,227 B | 966,227 B | khớp | Đã nhận phản hồi HTTP |
| modules.sevenzip.url [armeabi-v7a] | `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/pack-sevenzip-1/sevenzip-armeabi-v7a.zip` | 200 | HEAD | 879,046 B | 879,046 B | khớp | Đã nhận phản hồi HTTP |
| webPlayers.ruffle.script | `https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle/ruffle.js` | 200 | HEAD | 465,076 B | — | — | Đã nhận phản hồi HTTP |
