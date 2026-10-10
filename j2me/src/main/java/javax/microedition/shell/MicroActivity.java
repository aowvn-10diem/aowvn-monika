/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2021 Nikita Shakarun
 * Copyright 2019-2026 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.shell;

import static android.content.pm.ActivityInfo.*;
import static ru.playsoftware.j2meloader.util.Constants.*;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.lcdui.skin.SkinLayer;
import javax.microedition.util.ContextHolder;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.J2meRuntime;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import ru.playsoftware.j2meloader.databinding.DialogInputBinding;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;

	private Displayable current;
	private boolean statusBarEnabled;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private int menuKey;
	private String appPath;
	private ActivityMicroBinding binding;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		lockNightMode();
		super.onCreate(savedInstanceState);
		ContextHolder.setCurrentActivity(this);
		binding = ActivityMicroBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		setSupportActionBar(binding.toolbar);
		setupMonikaMenuButton();
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		statusBarEnabled = sp.getBoolean(PREF_STATUSBAR, false);
		if (sp.getBoolean(PREF_KEEP_SCREEN, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		ContextHolder.setVibration(sp.getBoolean(PREF_VIBRATION, true));
		Canvas.setScreenshotRawMode(sp.getBoolean(PREF_SCREENSHOT_SWITCH, false));
		Intent intent = getIntent();
		if (BuildConfig.FULL_EMULATOR) {
			appName = intent.getStringExtra(KEY_MIDLET_NAME);
			Uri data = intent.getData();
			if (data == null) {
				showErrorDialog("Invalid intent: app path is null");
				return;
			}
			appPath = data.toString();
		} else {
			appName = getTitle().toString();
			appPath = getApplicationInfo().dataDir + "/files/converted/midlet";
			File dir = new File(appPath);
			if (!dir.exists() && !dir.mkdirs()) {
				throw new RuntimeException("Can't access file system");
			}
		}
		microLoader = new MicroLoader(appPath);
		if (!microLoader.init()) {
			Config.openSettings(this, appName, appPath);
			finish();
			return;
		}
		microLoader.applyConfiguration();
		SkinLayer skinLayer = SkinLayer.getInstance();
		if (skinLayer != null) {
			binding.overlay.addLayer(skinLayer);
			if (!statusBarEnabled) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					WindowManager.LayoutParams attributes = getWindow().getAttributes();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
						attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
					} else {
						attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
					}
					getWindow().setAttributes(attributes);
				}
			}
		}
		VirtualKeyboard vk = ContextHolder.getVk();
		int orientation = microLoader.getOrientation();
		if (vk != null) {
			vk.setView(binding.overlay);
			binding.overlay.addLayer(vk);
			// Aow Monika: bàn phím Monika xoay ngang được (phím tách 2 bên), kiểu điện thoại khác vẫn khóa dọc.
			if (vk.isPhone() && !vk.isMonika()) {
				orientation = ORIENTATION_PORTRAIT;
			}
		}
		setOrientation(orientation);
		menuKey = microLoader.getMenuKeyCode();
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				// Intentionally overridden by empty due to support for back-key remapping.
			}
		});
		loadMIDlet();
	}

	public void lockNightMode() {
		int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (current == Configuration.UI_MODE_NIGHT_YES) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
	}

	@Override
	public void onPause() {
		hideSoftInput();
		super.onPause();
	}

	private void hideSoftInput() {
		if (inputMethodManager != null) {
			IBinder windowToken = binding.displayableContainer.getWindowToken();
			inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				current instanceof Canvas) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		setRequestedOrientation(switch (orientation) {
			case ORIENTATION_DEFAULT -> SCREEN_ORIENTATION_UNSPECIFIED;
			case ORIENTATION_AUTO -> SCREEN_ORIENTATION_FULL_SENSOR;
			case ORIENTATION_PORTRAIT -> SCREEN_ORIENTATION_SENSOR_PORTRAIT;
			case ORIENTATION_LANDSCAPE -> SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
			default -> SCREEN_ORIENTATION_UNSPECIFIED;
		});
	}

	private void loadMIDlet() {
		Map<String, String> midlets;
		try {
			midlets = microLoader.loadMIDletList();
		} catch (IOException e) {
			showErrorDialog(e.toString());
			return;
		}
		int size = midlets.size();
		String[] midletsNameArray = midlets.values().toArray(new String[0]);
		String[] midletsClassArray = midlets.keySet().toArray(new String[0]);
		if (size == 0) {
			showErrorDialog("No MIDlets found");
		} else if (size == 1) {
			microLoader.loadMidlet(midletsClassArray[0], appName);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] names, final String[] classes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(names, (d, n) -> {
					String clazz = classes[n];
					ErrorReporter errorReporter = ACRA.getErrorReporter();
					String report = errorReporter.getCustomData(Constants.KEY_APPCENTER_ATTACHMENT);
					StringBuilder sb = new StringBuilder();
					if (report != null) {
						sb.append(report).append("\n");
					}
					sb.append("Begin app: ").append(names[n]).append(", ").append(clazz);
					errorReporter.putCustomData(Constants.KEY_APPCENTER_ATTACHMENT, sb.toString());
					microLoader.loadMidlet(clazz, appName);
				})
				.setOnCancelListener(d -> {
					d.dismiss();
					MidletThread.notifyDestroyed();
				});
		builder.show();
	}

	void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> MidletThread.notifyDestroyed());
		builder.setOnCancelListener(dialogInterface -> MidletThread.notifyDestroyed());
		builder.show();
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			if (!statusBarEnabled) {
				flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
			}
			getWindow().getDecorView().setSystemUiVisibility(flags);
		} else if (!statusBarEnabled) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	private void showSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public void setCurrent(Displayable displayable) {
		ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
		current = displayable;
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
	}

	private static final int MONIKA_EXIT = -1;
	private static final int MONIKA_CONTINUE = -2;
	private static final int MONIKA_SETTINGS = -3;
	private static final int MONIKA_ASK = -4;
	/** Menu popup mỗi lần tạo mới → tự nhớ trạng thái "khóa xoay màn hình". */
	private boolean orientationLocked;

	private void exitToMonika() {
		hideSoftInput();
		MidletThread.destroyApp();
	}

	/** Aow Monika: menu popup kiểu Monika — lấy đúng các mục của menu gốc (ẩn/hiện theo màn hình hiện tại). */
	private void showMonikaMenu(J2meRuntime.MenuPresenter presenter) {
		android.widget.PopupMenu popup = new android.widget.PopupMenu(this, binding.getRoot());
		Menu menu = popup.getMenu();
		onCreateOptionsMenu(menu);
		onPrepareOptionsMenu(menu);
		MenuItem lock = menu.findItem(R.id.action_lock_orientation);
		if (lock != null) lock.setChecked(orientationLocked);
		java.util.List<J2meRuntime.MenuEntry> entries = new java.util.ArrayList<>();
		entries.add(new J2meRuntime.MenuEntry(MONIKA_CONTINUE, getString(R.string.monika_keep_playing), "monika_continue", false));
		entries.add(new J2meRuntime.MenuEntry(MONIKA_ASK, getString(R.string.monika_ask_group), "monika_ask", false));
		collectMenu(menu, entries);
		entries.add(new J2meRuntime.MenuEntry(MONIKA_SETTINGS, getString(R.string.monika_game_settings), "monika_settings", false));
		presenter.show(this, appName, getString(R.string.monika_java_game), entries, id -> {
			if (id == MONIKA_CONTINUE) return;
			if (id == MONIKA_ASK) {
				// Game Java tự lưu theo cách của game → chỉ chụp màn hình rồi mở nhóm.
				if (current instanceof Canvas) {
					//noinspection ResultOfMethodCallIgnored
					((Canvas) current).getScreenshot()
							.subscribeOn(io.reactivex.schedulers.Schedulers.computation())
							.observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
							.subscribe(bmp -> presenter.askCommunity(this, bmp, appName),
									e -> presenter.askCommunity(this, null, appName));
				} else {
					presenter.askCommunity(this, null, appName);
				}
				return;
			}
			if (id == MONIKA_SETTINGS) {
				hideSoftInput();
				Config.openSettings(this, appName, appPath);
				MidletThread.destroyApp();
				return;
			}
			// Đã chọn "Thoát" trong menu → thoát luôn, không hỏi lần 2.
			if (id == R.id.action_exit_midlet) {
				exitToMonika();
				return;
			}
			MenuItem item = menu.findItem(id);
			if (item == null) return;
			onOptionsItemSelected(item);
			if (id == R.id.action_lock_orientation) orientationLocked = item.isChecked();
		});
	}

	private void collectMenu(Menu menu, java.util.List<J2meRuntime.MenuEntry> out) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if (!item.isVisible()) continue;
			if (item.hasSubMenu()) {
				collectMenu(item.getSubMenu(), out);
				continue;
			}
			String key;
			try {
				key = getResources().getResourceEntryName(item.getItemId());
			} catch (Exception e) {
				key = "";
			}
			out.add(new J2meRuntime.MenuEntry(item.getItemId(), String.valueOf(item.getTitle()), key, item.isChecked()));
		}
	}

	/** Aow Monika: nút menu nổi duy nhất trên màn chơi — bấm = mở menu Monika (đẩy khỏi tai thỏ / thanh trạng thái). */
	private void setupMonikaMenuButton() {
		final android.widget.ImageButton button = binding.monikaMenuButton;
		button.setOnClickListener(v -> openOptionsMenu());
		final int base = (int) (10 * getResources().getDisplayMetrics().density);
		ViewCompat.setOnApplyWindowInsetsListener(button, (v, insets) -> {
			androidx.core.graphics.Insets bars = insets.getInsets(
					WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
			lp.topMargin = base + bars.top;
			lp.setMarginEnd(base + bars.right);
			v.setLayoutParams(lp);
			return insets;
		});
	}

	/** Dự phòng khi app chính chưa gắn {@link J2meRuntime#menuPresenter}: menu gốc, neo vào nút menu nổi. */
	private void showFallbackMenu() {
		android.widget.PopupMenu popup = new android.widget.PopupMenu(this, binding.monikaMenuButton);
		Menu menu = popup.getMenu();
		onCreateOptionsMenu(menu);
		onPrepareOptionsMenu(menu);
		popup.setOnMenuItemClickListener(this::onOptionsItemSelected);
		popup.show();
	}

	public void showExitConfirmation() {
		J2meRuntime.MenuPresenter presenter = J2meRuntime.menuPresenter;
		if (presenter != null) {
			java.util.List<J2meRuntime.MenuEntry> entries = new java.util.ArrayList<>();
			entries.add(new J2meRuntime.MenuEntry(MONIKA_CONTINUE, getString(R.string.monika_keep_playing), "monika_continue", false));
			entries.add(new J2meRuntime.MenuEntry(MONIKA_SETTINGS, getString(R.string.monika_game_settings), "monika_settings", false));
			entries.add(new J2meRuntime.MenuEntry(MONIKA_EXIT, getString(R.string.monika_exit_ok), "action_exit_midlet", false));
			presenter.show(this, getString(R.string.monika_exit_title), getString(R.string.monika_exit_message), entries, id -> {
				if (id == MONIKA_EXIT) exitToMonika();
				else if (id == MONIKA_SETTINGS) {
					hideSoftInput();
					Config.openSettings(this, appName, appPath);
					MidletThread.destroyApp();
				}
			});
			return;
		}
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		// Aow Monika: hỏi nhẹ nhàng, thoát là về thẳng Monika.
		alertBuilder.setTitle(R.string.monika_exit_title)
				.setMessage(R.string.monika_exit_message)
				.setPositiveButton(R.string.monika_exit_ok, (d, w) -> {
					hideSoftInput();
					MidletThread.destroyApp();
				})
				.setNeutralButton(R.string.monika_game_settings, (d, w) -> {
					hideSoftInput();
					Config.openSettings(this, appName, appPath);
					MidletThread.destroyApp();
				})
				.setNegativeButton(R.string.monika_keep_playing, null);
		alertBuilder.create().show();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU)
			if (current instanceof Canvas && binding.displayableContainer.dispatchKeyEvent(event)) {
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.getRepeatCount() == 0) {
					event.startTracking();
					return true;
				} else if (event.isLongPress()) {
					return onKeyLongPress(event.getKeyCode(), event);
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				return onKeyUp(event.getKeyCode(), event);
			}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void openOptionsMenu() {
		J2meRuntime.MenuPresenter presenter = J2meRuntime.menuPresenter;
		if (presenter != null) {
			showMonikaMenu(presenter);
			return;
		}
		showFallbackMenu();
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
			showExitConfirmation();
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
				&& (event.getFlags() & (KeyEvent.FLAG_LONG_PRESS | KeyEvent.FLAG_CANCELED)) == 0) {
			openOptionsMenu();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.midlet_displayable, menu);
		if (inputMethodManager == null) {
			menu.findItem(R.id.action_ime_keyboard).setVisible(false);
		}
		if (ContextHolder.getVk() == null) {
			menu.findItem(R.id.action_submenu_vk).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (current instanceof Canvas) {
			menu.setGroupVisible(R.id.action_group_canvas, true);
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null) {
				boolean visible = vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF;
				menu.findItem(R.id.action_layout_edit_finish).setVisible(visible);
			}
		} else {
			menu.setGroupVisible(R.id.action_group_canvas, false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_exit_midlet) {
			showExitConfirmation();
		} else if (id == R.id.action_save_log) {
			saveLog();
		} else if (id == R.id.action_lock_orientation) {
			if (item.isChecked()) {
				VirtualKeyboard vk = ContextHolder.getVk();
				int orientation = vk != null && vk.isPhone() && !vk.isMonika() ? ORIENTATION_PORTRAIT : microLoader.getOrientation();
				setOrientation(orientation);
				item.setChecked(false);
			} else {
				lockOrientation();
				item.setChecked(true);
			}
		} else if (id == R.id.action_ime_keyboard) {
			inputMethodManager.toggleSoftInputFromWindow(binding.displayableContainer.getWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
		} else if (id == R.id.action_take_screenshot) {
			takeScreenshot();
		} else if (id == R.id.action_limit_fps) {
			showLimitFpsDialog();
		} else if (ContextHolder.getVk() != null) {
			// Handled only when virtual keyboard is enabled
			handleVkOptions(id);
		}
		return true;
	}

	private void lockOrientation() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			setRequestedOrientation(SCREEN_ORIENTATION_LOCKED);
			return;
		}
		Configuration configuration = getResources().getConfiguration();
		int rotation = getWindowManager().getDefaultDisplay().getRotation();

		// Search for the natural position of the device
		if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
				(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) ||
				configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
						(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)) {
			// Natural position is Landscape
			setRequestedOrientation(switch (rotation) {
				case Surface.ROTATION_0 -> SCREEN_ORIENTATION_LANDSCAPE;
				case Surface.ROTATION_90 -> SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				case Surface.ROTATION_180 -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				case Surface.ROTATION_270 -> SCREEN_ORIENTATION_PORTRAIT;
				default -> SCREEN_ORIENTATION_UNSPECIFIED;
			});
		} else {
			// Natural position is Portrait
			setRequestedOrientation(switch (rotation) {
				case Surface.ROTATION_0 -> SCREEN_ORIENTATION_PORTRAIT;
				case Surface.ROTATION_90 -> SCREEN_ORIENTATION_LANDSCAPE;
				case Surface.ROTATION_180 -> SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				case Surface.ROTATION_270 -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				default -> SCREEN_ORIENTATION_UNSPECIFIED;
			});
		}
	}

	private void handleVkOptions(int id) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (id == R.id.action_layout_edit_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_KEYS);
			Toast.makeText(this, R.string.layout_edit_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_scale_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_SCALES);
			Toast.makeText(this, R.string.layout_scale_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_edit_finish) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
			Toast.makeText(this, R.string.layout_edit_finished, Toast.LENGTH_SHORT).show();
			showSaveVkAlert(false);
		} else if (id == R.id.action_layout_switch) {
			showSetLayoutDialog();
		} else if (id == R.id.action_hide_buttons) {
			showHideButtonDialog();
		}
	}

	@SuppressLint("CheckResult")
	private void takeScreenshot() {
		microLoader.takeScreenshot(current, new SingleObserver<>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				Toast.makeText(MicroActivity.this, getString(R.string.screenshot_saved)
						+ " " + s, Toast.LENGTH_LONG).show();
				MediaScannerConnection.scanFile(MicroActivity.this, new String[]{s}, null, null);
			}

			@Override
			public void onError(@NonNull Throwable e) {
				e.printStackTrace();
				Toast.makeText(MicroActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void saveLog() {
		try {
			LogUtils.writeLog();
			Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		boolean[] states = vk.getKeysVisibility();
		boolean[] changed = states.clone();
		new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(vk.getKeyNames(), changed, (dialog, which, isChecked) -> {})
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					if (!Arrays.equals(states, changed)) {
						vk.setKeysVisibility(changed);
						showSaveVkAlert(true);
					}
				}).show();
	}

	private void showSaveVkAlert(boolean keepScreenPreferred) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.CONFIRMATION_REQUIRED);
		builder.setMessage(R.string.pref_vk_save_alert);
		builder.setNegativeButton(android.R.string.no, null);
		AlertDialog dialog = builder.create();

		final VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.isPhone()) {
			AppCompatCheckBox cb = new AppCompatCheckBox(this);
			cb.setText(R.string.opt_save_screen_params);
			cb.setChecked(keepScreenPreferred);

			TypedValue out = new TypedValue();
			getTheme().resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding, out, true);
			int paddingH = getResources().getDimensionPixelOffset(out.resourceId);
			int paddingT = getResources().getDimensionPixelOffset(androidx.appcompat.R.dimen.abc_dialog_padding_top_material);
			dialog.setView(cb, paddingH, paddingT, paddingH, 0);

			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) -> {
				if (cb.isChecked()) {
					vk.saveScreenParams();
				}
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
			});
		} else {
			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) ->
					ContextHolder.getVk().onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM));
		}
		dialog.show();
	}

	private void showSetLayoutDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.layout_switch)
				.setSingleChoiceItems(R.array.PREF_VK_TYPE_ENTRIES, vk.getLayout(), null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					vk.setLayout(((AlertDialog) d).getListView().getCheckedItemPosition());
					if (vk.isPhone() && !vk.isMonika()) {
						setOrientation(ORIENTATION_PORTRAIT);
					} else {
						setOrientation(microLoader.getOrientation());
					}
				});
		builder.show();
	}

	private void showLimitFpsDialog() {
		TextInputLayout inputLayout = DialogInputBinding.inflate(getLayoutInflater()).getRoot();
		EditText editText = Objects.requireNonNull(inputLayout.getEditText());
		editText.setHint(R.string.unlimited);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
		editText.setMaxLines(1);
		editText.setSingleLine(true);
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_LIMIT_FPS)
				.setView(inputLayout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					Editable text = editText.getText();
					int fps = 0;
					try {
						fps = TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text.toString().trim());
					} catch (NumberFormatException ignored) {
					}
					Canvas.setLimitFps(fps);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.reset, ((d, which) -> Canvas.setLimitFps(-1)))
				.show();
	}

	@Override
	public boolean onContextItemSelected(@NonNull MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		}

		return super.onContextItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		ContextHolder.notifyOnActivityResult(requestCode, resultCode, data);
	}

	public String getAppName() {
		return appName;
	}

	public void toast(@StringRes int message) {
		runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
	}

	private class SetCurrentEvent extends SimpleEvent {
		private final Displayable current;
		private final Displayable next;

		private SetCurrentEvent(Displayable current, Displayable next) {
			this.current = current;
			this.next = next;
		}

		@Override
		public void process() {
			closeOptionsMenu();
			if (current != null) {
				current.clearDisplayableView();
			}
			binding.displayableContainer.removeAllViews();
			ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) binding.toolbar.getLayoutParams();
			int toolbarHeight = 0;
			if (next instanceof Canvas) {
				hideSystemUI();
			} else {
				showSystemUI();
			}
			// Aow Monika: không còn thanh công cụ trên đầu (cả màn chơi lẫn Form/List); mọi chức năng nằm trong
			// menu Monika, mở bằng nút menu nổi (monikaMenuButton) hoặc phím Back/Menu.
			actionBar.hide();
			layoutParams.height = 0;
			binding.overlay.setLocation(0, toolbarHeight);
			binding.toolbar.setLayoutParams(layoutParams);
			if (next != null) {
				binding.displayableContainer.addView(next.getDisplayableView());
			}
		}
	}
}
