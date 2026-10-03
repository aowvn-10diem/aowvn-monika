# Giữ nguyên tên các lớp cấu hình (kotlinx.serialization) khi bật minify.
-keep class vn.aow.monika.config.** { *; }
-keep class vn.aow.monika.feed.** { *; }

# Thư viện native: mã C gọi ngược vào lớp Java theo tên → giữ nguyên cả gói.
-keep class com.swordfish.libretrodroid.** { *; }
# Siêu dữ liệu game (.monika.json, kotlinx.serialization)
-keep class vn.aow.monika.library.GameMeta* { *; }
# Trình chuyển .jar → .dex của J2ME Loader chạy lúc cài game
-keep class com.android.dx.** { *; }
-dontwarn com.android.dx.**
-keepattributes SourceFile,LineNumberTable
# Commons Compress: nhiều định dạng tùy chọn (brotli, zstd, asm...) không đóng gói → bỏ cảnh báo.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-keep class org.apache.commons.compress.archivers.sevenz.** { *; }
-keep class org.tukaani.xz.** { *; }
# 7-Zip-JBinding: mã C gọi ngược lớp Java theo tên
-keep class net.sf.sevenzipjbinding.** { *; }

# Báo lỗi game (kotlinx.serialization) + thông tin game tự nhận diện
-keep class vn.aow.monika.diag.** { *; }
-keep class vn.aow.monika.library.GameInfoResolver* { *; }

# Engine 3DS nhúng (Azahar): thư viện native gọi ngược lớp Kotlin theo tên/chữ ký
-keep class org.citra.citra_emu.** { *; }
-keep class vn.aow.monika.azahar.AzaharBridge { *; }

# Trình cài game Android: ARSCLib (sửa manifest nhị phân) và apksig (ký APK) dùng phản chiếu/nạp lớp theo tên → giữ nguyên.
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
-dontwarn javax.annotation.**
# Nhật ký Cách 1/2/3 lưu bằng kotlinx.serialization theo tên enum
-keepclassmembers enum vn.aow.monika.apkinstall.** { *; }

# Cách 3: gỡ lỗi không dây (libadb-android + BouncyCastle/spake2 dùng phản xạ/JCA)
-keep class io.github.muntashirakon.adb.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class io.github.muntashirakon.crypto.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn io.github.muntashirakon.**

# RPG Maker (mkxp-z): mã native tìm trường tĩnh GAME_PATH và các hàm tĩnh theo TÊN trên lớp của Activity (JNI) — không đổi tên/cắt.
-keepclassmembers class vn.aow.monika.runner.RgssGameActivity {
    public static java.lang.String GAME_PATH;
    public static ** getSystemLanguage();
    public static ** hasVibrator();
    public static ** vibrate(int);
    public static ** vibrateStop();
    public static ** inMultiWindow(android.app.Activity);
}
