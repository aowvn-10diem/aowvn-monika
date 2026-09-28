/*
 * Aow Monika: khởi tạo J2ME Loader khi được nhúng làm thư viện.
 * Thay cho EmulatorApplication (bản gốc là 1 app riêng nên có Application class riêng).
 * Gọi từ Application.attachBaseContext của app chính — ở CẢ tiến trình chính lẫn ":midlet".
 * Không bật ACRA (bản gốc gửi báo lỗi về máy chủ của J2ME Loader).
 */
package ru.playsoftware.j2meloader;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.util.Constants;

public final class J2meRuntime {
	private static final SharedPreferences.OnSharedPreferenceChangeListener THEME_LISTENER = (sp, key) -> {
		if (Constants.PREF_THEME.equals(key)) applyNightMode(sp.getString(Constants.PREF_THEME, null));
	};

	private J2meRuntime() {
	}

	public static void init(Application app) {
		ContextHolder.setApplication(app);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(app);
		sp.registerOnSharedPreferenceChangeListener(THEME_LISTENER);
		applyNightMode(sp.getString(Constants.PREF_THEME, null));
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	/**
	 * Intent mở 1 file .jar/.jad trong J2ME Loader: màn hình cài (chuyển .jar → .dex lần đầu) rồi chạy.
	 * @param uri content:// (FileProvider) của file game, đã cấp quyền đọc.
	 */
	public static Intent openGameIntent(android.content.Context context, Uri uri) {
		return new Intent(Intent.ACTION_VIEW)
				.setClass(context, MainActivity.class)
				.setDataAndType(uri, "application/java-archive")
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	}

	/** Mở danh sách game Java đã cài trong J2ME Loader. */
	public static Intent libraryIntent(android.content.Context context) {
		return new Intent(context, MainActivity.class);
	}

	static void applyNightMode(String theme) {
		if (theme == null) theme = "system";
		switch (theme) {
			case "light":
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
				break;
			case "dark":
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
				break;
			case "auto-battery":
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
				break;
			default:
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
				break;
		}
	}
}
