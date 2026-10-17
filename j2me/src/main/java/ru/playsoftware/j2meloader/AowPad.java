/*
 * Aow Monika (V120): tay cầm thật trong game Java. Chỉ là số và hàm thuần, không phụ thuộc Android lúc chạy
 * (hằng KeyEvent/Canvas được trình biên dịch nhúng sẵn) → `J2meGamepadKeyMapTest` ở module app kiểm bằng JUnit thường.
 */
package ru.playsoftware.j2meloader;

import android.view.KeyEvent;

import javax.microedition.lcdui.Canvas;

public final class AowPad {
	private AowPad() {
	}

	/**
	 * Bảng mặc định {mã Android, mã MIDP}: A/Start/giữa D-pad = Fire, B = phím mềm phải (thoát/quay lại),
	 * X = phím mềm trái, Y = 0, Select = *, L1/R1 = 1/3 (đường chéo ở nhiều game). Hồ sơ tùy chỉnh ghi đè được.
	 */
	public static final int[][] KEYS = {
			{KeyEvent.KEYCODE_DPAD_CENTER, Canvas.KEY_FIRE},
			{KeyEvent.KEYCODE_BUTTON_A, Canvas.KEY_FIRE},
			{KeyEvent.KEYCODE_BUTTON_START, Canvas.KEY_FIRE},
			{KeyEvent.KEYCODE_BUTTON_B, Canvas.KEY_SOFT_RIGHT},
			{KeyEvent.KEYCODE_BUTTON_X, Canvas.KEY_SOFT_LEFT},
			{KeyEvent.KEYCODE_BUTTON_Y, Canvas.KEY_NUM0},
			{KeyEvent.KEYCODE_BUTTON_SELECT, Canvas.KEY_STAR},
			{KeyEvent.KEYCODE_BUTTON_L1, Canvas.KEY_NUM1},
			{KeyEvent.KEYCODE_BUTTON_R1, Canvas.KEY_NUM3},
	};

	/**
	 * Hướng D-pad (KEYCODE_DPAD_*) từ hat hoặc cần trái; 0 = đứng yên. Hat ưu tiên hơn cần;
	 * cần chỉ lấy trục lệch nhiều hơn khi vượt ngưỡng 0,5 (J2ME chỉ có 4 hướng).
	 */
	public static int direction(float hatX, float hatY, float stickX, float stickY) {
		if (hatX != 0f || hatY != 0f) {
			if (Math.abs(hatX) >= Math.abs(hatY)) {
				return hatX < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
			}
			return hatY < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
		}
		float ax = Math.abs(stickX), ay = Math.abs(stickY);
		if (Math.max(ax, ay) < 0.5f) {
			return 0;
		}
		if (ax >= ay) {
			return stickX < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
		}
		return stickY < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
	}
}
