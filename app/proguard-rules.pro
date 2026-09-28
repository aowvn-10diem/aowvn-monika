# Giữ nguyên tên các lớp cấu hình (kotlinx.serialization) khi bật minify.
-keep class vn.aow.monika.config.** { *; }
-keep class vn.aow.monika.feed.** { *; }

# Thư viện native: mã C gọi ngược vào lớp Java theo tên → giữ nguyên cả gói.
-keep class com.swordfish.libretrodroid.** { *; }
-keep class me.zhanghai.android.libarchive.** { *; }
# Siêu dữ liệu game (.monika.json, kotlinx.serialization)
-keep class vn.aow.monika.library.GameMeta* { *; }
# Trình chuyển .jar → .dex của J2ME Loader chạy lúc cài game
-keep class com.android.dx.** { *; }
-dontwarn com.android.dx.**
-keepattributes SourceFile,LineNumberTable
