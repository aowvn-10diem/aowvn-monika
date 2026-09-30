package vn.aow.monika.loader;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Chạy khi tiến trình game khởi động (trước Application.onCreate): gắn bộ bắt lỗi + báo "đã hiện màn hình" + nhịp sống về Monika.
 * Để Monika tự kiểm tra game đã chỉnh có chạy được không.
 */
public class MonikaLoaderProvider extends ContentProvider {
    private static boolean screenSent;

    /** Monika yêu cầu theo dõi lại (mở thử game lần nữa) → cho phép báo "đã hiện màn hình" thêm 1 lần. */
    static void resetScreen() {
        synchronized (MonikaLoaderProvider.class) { screenSent = false; }
    }

    @Override public boolean onCreate() {
        final Context ctx = getContext();
        if (ctx == null) return false;
        try {
            Watch.arm(ctx, 60000);
            installCrashHook(ctx);
            installScreenHook(ctx);
            Bus.send(ctx, "started", null, null);
        } catch (Throwable ignored) {
            // Không được làm game chết vì bộ nạp.
        }
        return true;
    }

    private static void installCrashHook(final Context ctx) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String s = sw.toString();
                    Bundle b = new Bundle();
                    b.putString("summary", s.length() > 1500 ? s.substring(0, 1500) : s);
                    Bus.send(ctx, "crash", null, b);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private static void installScreenHook(final Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!(app instanceof Application)) return;
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) {
                synchronized (MonikaLoaderProvider.class) {
                    if (screenSent) return;
                    screenSent = true;
                }
                Bus.send(ctx, "screen", a.getClass().getName(), null);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
