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
}
