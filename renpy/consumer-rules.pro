# librenpython.so / libSDL2.so gọi lại Java theo TÊN lớp + hàm (JNI, pyjnius) → không được đổi tên hay cắt.
-keep class org.renpy.android.** { *; }
-keep class org.jnius.** { *; }
-keep class org.libsdl.app.** { *; }
-dontwarn org.renpy.android.**
-dontwarn org.libsdl.app.**
