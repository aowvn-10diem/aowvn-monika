# Mã native của Kirikiroid2Yuri / cocos2d-x gọi lại Java theo TÊN lớp + hàm (JNI) → không được đổi tên/cắt.
-keep class org.cocos2dx.lib.** { *; }
-keep class org.tvp.kirikiri2.** { *; }
-keep class com.android.vending.expansion.zipfile.** { *; }
-dontwarn org.cocos2dx.lib.**
