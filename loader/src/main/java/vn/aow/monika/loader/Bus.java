package vn.aow.monika.loader;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/** Gửi tin về Aow Monika (ứng dụng chủ) qua ContentProvider "<gói Monika>.loaderbus". Lỗi thì bỏ qua — không bao giờ làm hỏng game. */
final class Bus {
    static final String META_HOST = "vn.aow.monika.HOST";

    private Bus() {}

    static String host(Context c) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData == null ? null : ai.metaData.getString(META_HOST);
        } catch (Throwable t) {
            return null;
        }
    }

    static void send(Context c, String method, String arg, Bundle extras) {
        try {
            String host = host(c);
            if (host == null) return;
            Bundle b = extras == null ? new Bundle() : extras;
            b.putString("pkg", c.getPackageName());
            c.getContentResolver().call(Uri.parse("content://" + host + ".loaderbus"), method, arg, b);
        } catch (Throwable ignored) {
        }
    }
}
