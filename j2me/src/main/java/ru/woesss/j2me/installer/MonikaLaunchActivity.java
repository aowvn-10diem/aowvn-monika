/*
 * Aow Monika: màn "Đang chuẩn bị game Java" thay cho danh sách app + hộp thoại cài của J2ME Loader.
 * Monika gửi file .jar/.jad tới đây → tự cài (lần đầu: chuyển .jar → .dex vài giây) → chạy game ngay.
 * Không hỏi gì (bản trùng: chạy bản đã cài, giữ dữ liệu lưu; bản mới hơn: tự cập nhật).
 * Thoát game → quay về Monika (cùng task, không đi qua màn hình nào của J2ME Loader).
 */
package ru.woesss.j2me.installer;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;

public class MonikaLaunchActivity extends AppCompatActivity {
	private final CompositeDisposable disposables = new CompositeDisposable();
	private AppInstaller installer;
	private AppListModel appListModel;
	private TextView title;
	private TextView status;
	private ProgressBar progress;
	private Button back;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		buildUi();
		Uri uri = getIntent().getData();
		if (uri == null) { finish(); return; }
		File workDir = new File(Config.getEmulatorDir());
		if (!FileUtils.initWorkDir(workDir)) {
			fail("Không tạo được thư mục dữ liệu game Java:\n" + workDir);
			return;
		}
		appListModel = new ViewModelProvider(this).get(AppListModel.class);
		appListModel.setEmulatorDirectory(workDir.getAbsolutePath());
		if (savedInstanceState == null) install(null, uri);
	}

	private void install(File jar, Uri uri) {
		installer = new AppInstaller(jar, uri, appListModel);
		disposables.add(Single.create(installer::loadInfo)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onStatus, this::onError));
	}

	private void convert() {
		status.setText("Lần đầu chơi: đang chuẩn bị game (vài giây)…");
		disposables.add(Single.create(installer::install)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onStatus, this::onError));
	}

	private void onStatus(Integer s) {
		if (isFinishing()) return;
		String name = installer.getNewDescriptor() != null ? installer.getNewDescriptor().getName() : null;
		if (name != null) title.setText(name);
		switch (s) {
			case AppInstaller.STATUS_SUCCESS:
				run(installer.getExistsApp());
				break;
			case AppInstaller.STATUS_NEW:
			case AppInstaller.STATUS_NEWER: // Bản mới hơn bản đã cài → cập nhật (dữ liệu lưu vẫn giữ).
				if (installer.getJar() != null) convert();
				else fail("File .jad cần kèm file .jar của game. Hãy tải bản .jar.");
				break;
			case AppInstaller.STATUS_EQUAL:
			case AppInstaller.STATUS_SAME:
			case AppInstaller.STATUS_OLDER: // Trùng / cũ hơn bản đã cài → chạy bản đã cài.
				installer.clearCache();
				installer.deleteTemp();
				run(installer.getExistsApp());
				break;
			case AppInstaller.STATUS_UNMATCHED:
				install(installer.getJar(), android.net.Uri.fromFile(installer.getJar())); // .jad không khớp .jar → cài theo .jar.
				break;
			default:
				fail("Không nhận ra file game Java này.");
		}
	}

	private void run(AppItem app) {
		if (app == null) { fail("Cài game xong nhưng không tìm thấy game."); return; }
		Config.startApp(this, app.getTitle(), app.getPathExt());
		finish();
		overridePendingTransition(0, 0);
	}

	private void onError(Throwable e) {
		e.printStackTrace();
		if (installer != null) { installer.clearCache(); installer.deleteTemp(); }
		fail("Không mở được game Java: " + e.getMessage());
	}

	private void fail(String message) {
		progress.setVisibility(android.view.View.GONE);
		status.setText(message);
		back.setVisibility(android.view.View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		disposables.clear();
		super.onDestroy();
	}

	// ---------- Giao diện (View thuần, màu Monika) ----------

	private int dp(float v) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
	}

	private void buildUi() {
		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(0xFF141315);
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setGravity(Gravity.CENTER_HORIZONTAL);
		card.setPadding(dp(24), dp(28), dp(24), dp(24));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xFF201F21);
		bg.setCornerRadius(dp(28));
		bg.setStroke(dp(1), 0x24FFFFFF);
		card.setBackground(bg);

		TextView badge = new TextView(this);
		badge.setText("☕  GAME JAVA");
		badge.setTextColor(0xFFFFB285);
		badge.setTextSize(12);
		badge.setLetterSpacing(0.12f);
		card.addView(badge);

		title = new TextView(this);
		title.setText("Đang mở game…");
		title.setTextColor(Color.WHITE);
		title.setTextSize(20);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setGravity(Gravity.CENTER);
		title.setPadding(0, dp(10), 0, dp(14));
		card.addView(title);

		progress = new ProgressBar(this);
		progress.setIndeterminateTintList(ColorStateList.valueOf(0xFFFF7A32));
		card.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40)));

		status = new TextView(this);
		status.setText("Đang kiểm tra game…");
		status.setTextColor(0xFFC8C5CB);
		status.setTextSize(14);
		status.setGravity(Gravity.CENTER);
		status.setPadding(0, dp(14), 0, 0);
		card.addView(status);

		back = new Button(this);
		back.setText("Về Aow Monika");
		back.setAllCaps(false);
		back.setTextColor(Color.WHITE);
		GradientDrawable btn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFB052, 0xFFFF7F78, 0xFFE95CC8});
		btn.setCornerRadius(dp(999));
		back.setBackground(btn);
		back.setVisibility(android.view.View.GONE);
		back.setOnClickListener(v -> finish());
		LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
		bp.topMargin = dp(20);
		card.addView(back, bp);

		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
		lp.leftMargin = lp.rightMargin = dp(24);
		root.addView(card, lp);
		setContentView(root);
	}
}
