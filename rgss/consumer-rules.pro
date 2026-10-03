# libSDL2.so / libmkxp-z.so gọi lại Java theo TÊN lớp + hàm (JNI, RegisterNatives) → không được đổi tên hay cắt.
-keep class vn.aow.monika.rgss.sdl.** { *; }
-dontwarn vn.aow.monika.rgss.sdl.**
