/*
 * Aow Monika V135: kích thước màn hình đoán cho game Java chỉ áp vào cấu hình MỚI.
 * Game đã có config.json (kể cả sếp đã chỉnh tay) thì giữ nguyên.
 */
package ru.playsoftware.j2meloader.config;

public final class MonikaScreenDefaults {
	public static final String EXTRA_SCREEN_WIDTH = "vn.aow.monika.extra.J2ME_SCREEN_WIDTH";
	public static final String EXTRA_SCREEN_HEIGHT = "vn.aow.monika.extra.J2ME_SCREEN_HEIGHT";

	private MonikaScreenDefaults() {
	}

	/**
	 * @return [rộng, cao] sẽ ghi khi tạo cấu hình mới; null = giữ nguyên cấu hình đã có
	 * (hoặc kích thước không hợp lệ).
	 */
	public static int[] screenForNewConfig(boolean configExists, int width, int height) {
		if (configExists || width <= 0 || height <= 0) return null;
		return new int[]{width, height};
	}

	/**
	 * Áp kích thước đoán cho cấu hình MỚI. Cấu hình mới sao từ profile mặc định đã có file sẵn
	 * nên phải ghi lại ngay, để lần mở sau vẫn giữ kích thước đoán được.
	 *
	 * @return true nếu đã ghi kích thước vào {@code params}
	 */
	public static boolean applyToNewConfig(boolean newConfig, ProfileModel params, int width, int height) {
		int[] screen = screenForNewConfig(!newConfig, width, height);
		if (screen == null) return false;
		params.screenWidth = screen[0];
		params.screenHeight = screen[1];
		if (!params.isNew) ProfilesManager.saveConfig(params);
		return true;
	}
}
