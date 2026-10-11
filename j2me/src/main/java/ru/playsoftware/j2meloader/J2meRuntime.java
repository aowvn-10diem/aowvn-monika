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

	/** Aow Monika: 1 mục trong menu popup. key = tên id trong R (vd. "action_exit_midlet") để app chọn icon. */
	public static final class MenuEntry {
		public final int id;
		public final String title;
		public final String key;
		public final boolean checked;

		public MenuEntry(int id, String title, String key, boolean checked) {
			this.id = id;
			this.title = title;
			this.key = key;
			this.checked = checked;
		}
	}

	/** Aow Monika: app chính vẽ menu popup dưới đáy (cùng thiết kế với giả lập khác) thay cho menu Android mặc định. */
	public interface MenuPresenter {
		void show(android.app.Activity activity, String title, String subtitle,
				  java.util.List<MenuEntry> entries, java.util.function.IntConsumer onPick);

		/** "Hỏi nhóm": ảnh chụp màn hình game (có thể null) + tên game → mở Group Facebook. */
		void askCommunity(android.app.Activity activity, android.graphics.Bitmap shot, String gameName);
	}

	/** Null = dùng menu gốc của J2ME Loader. */
	public static volatile MenuPresenter menuPresenter;

	public static void init(Application app) {
		ContextHolder.setApplication(app);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(app);
		sp.registerOnSharedPreferenceChangeListener(THEME_LISTENER);
		// Aow Monika: không còn thanh công cụ trên đầu màn chơi (nút menu nổi + menu Monika thay thế) nên bỏ mặc định PREF_TOOLBAR.
		applyNightMode(sp.getString(Constants.PREF_THEME, null));
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	/** Tên tiến trình hiện tại (chính / ":midlet"). Dùng cho báo lỗi. */
	public static String getProcessName() {
		return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
				? Application.getProcessName()
				: ru.playsoftware.j2meloader.util.FileUtils.getText("/proc/self/cmdline").trim();
	}

	/**
	 * Intent mở 1 file .jar/.jad trong J2ME Loader: màn hình cài (chuyển .jar → .dex lần đầu) rồi chạy.
	 * @param uri content:// (FileProvider) của file game, đã cấp quyền đọc.
	 */
	public static Intent openGameIntent(android.content.Context context, Uri uri) {
		// Aow Monika: đi thẳng màn chuẩn bị + chạy game (không qua danh sách app của J2ME Loader).
		return new Intent(Intent.ACTION_VIEW)
				.setClass(context, ru.woesss.j2me.installer.MonikaLaunchActivity.class)
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
