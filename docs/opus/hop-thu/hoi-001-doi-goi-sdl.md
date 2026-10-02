# Hỏi 001 — Đổi gói lớp SDL cho mkxp-z
Bước plan: R1 (U5/X1)      Hạn cần: trước R2 (chưa gấp, spike R0 chưa dựng xong)
Bối cảnh: Ren'Py giữ `org.libsdl.app` (SDL 2.0.20, nhị phân dựng sẵn); mkxp-z dùng SDL 2.26.3 nên phải đổi gói lớp Java sang `vn.aow.monika.rgss.sdl` trong cùng APK.
Câu hỏi: danh sách chính xác chuỗi/macro cần vá trong `SDL_android.c` (SDL release-2.26.3) để mọi `FindClass`/`RegisterNatives`/tên hàm JNI `Java_org_libsdl_app_*` trỏ đúng gói mới? Có chỗ nào khác (SDL_audio, HIDAPI, `SDL_hidapi` JNI) cũng hardcode?
Đang cân nhắc: A) `sed` chuỗi `org/libsdl/app` + tiền tố `Java_org_libsdl_app_` khi dựng  B) hạ SDL mkxp-z về 2.0.20 để dùng chung lớp với Ren'Py  C) khác
Đã thử và hỏng: chưa thử (R0 mới dựng được thư viện, chưa tới ndk-build mkxp-z).
