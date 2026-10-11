/*
 * Aow Monika (V85c): màu bàn phím ảo J2ME theo token bộ nút AowVN.
 * Giá trị trùng `Monika.colors.aow` (app/ui/theme/Theme.kt, `AowColors`); `AowKeyPaletteTest` ở module app giữ hai bên khớp
 * và kiểm tương phản chữ/nền ≥ 4.5:1. Chỉ là số, không phụ thuộc Android → kiểm bằng JUnit thường.
 */
package ru.playsoftware.j2meloader;

public final class AowKeyPalette {
	private AowKeyPalette() {
	}

	public static final int ORANGE = 0xFFFD4C0F;
	public static final int ORANGE_PRESSED = 0xFFD92501;
	public static final int DARK = 0xFF171919;
	public static final int DARK_PRESSED = 0xFF8E0F01;
	public static final int PAD_SURFACE = 0xFF282C2F;
	public static final int OUTLINE = 0xFF000000;
	public static final int OUTER_RIM = 0xFFFEFEFA;
	public static final int INK = 0xFF000000;
	public static final int ON_DARK = 0xFFFFFFFF;
	/**
	 * Đỏ nhấn trộn 50 % với nền tối (`lerp(DARK_PRESSED, DARK, .5f)` của Compose, nội suy Oklab) như `controllerColors` của tay cầm libretro,
	 * để chữ trắng giữ ≥ 4.5:1 trên bàn phím trong suốt. `AowKeyPaletteTest` so khớp với Compose.
	 */
	public static final int DARK_PRESSED_MIXED = 0xFF521E16;

	/** Mặt phím: phím thường = than đá `PAD_SURFACE`; phím chính (OK) = cam. Nhấn: đỏ sẫm / cam sẫm. */
	public static int face(boolean primary, boolean pressed) {
		if (primary) return pressed ? ORANGE_PRESSED : ORANGE;
		return pressed ? DARK_PRESSED_MIXED : PAD_SURFACE;
	}

	/** Nhãn: trắng trên nền tối và khi nhấn; mực đen trên cam thường (đạt 4.5:1). */
	public static int label(boolean primary, boolean pressed) {
		return primary && !pressed ? INK : ON_DARK;
	}

	/** Tỉ lệ tương phản WCAG giữa hai màu đục. */
	public static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int c) {
		return 0.2126 * channel((c >> 16) & 0xFF) + 0.7152 * channel((c >> 8) & 0xFF) + 0.0722 * channel(c & 0xFF);
	}

	private static double channel(int v) {
		double s = v / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}
}
