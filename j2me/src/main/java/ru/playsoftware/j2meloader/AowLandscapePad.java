/*
 * Aow Monika (V161): bố cục bàn phím ảo kiểu Monika khi máy XOAY NGANG — bàn số dạt mép trái, cụm mũi tên dạt mép
 * phải, khung game nằm GIỮA và giữ đúng tỉ lệ khung hình; phím nhỏ hơn + bán trong suốt để không che game.
 *
 * Chỉ là số và hàm thuần, không phụ thuộc Android lúc chạy (density truyền vào) → `AowLandscapePadTest` ở
 * `j2me/src/test` kiểm bằng JUnit thường.
 *
 * Config-first: các giá trị dưới đây là MẶC ĐỊNH TRONG MÃ của đợt này; khóa trong `config/monika-config.json`
 * (tỉ lệ bề rộng mỗi cụm phím, độ trong suốt phím) sẽ thêm sau khi PR #409 gộp, rồi đọc qua `MonikaConfig`.
 */
package ru.playsoftware.j2meloader;

public final class AowLandscapePad {
	private AowLandscapePad() {
	}

	/** Bề rộng mỗi cụm phím (trái/phải) tính theo bề rộng màn: 0,26 → khung game giữa rộng ≥ 48 % màn. */
	public static final float SIDE_WIDTH_RATIO = 0.26f;
	/** Cách mép màn hình (dp) — khớp `VirtualKeyboard.MONIKA_EDGE`. */
	public static final float EDGE_DP = 10;
	/** Khay kính mờ rộng hơn cụm phím (dp) — khớp `VirtualKeyboard.MONIKA_PANEL_PAD`. */
	public static final float PANEL_PAD_DP = 8;
	/** Khoảng giữa khung game và khay phím (dp) — khớp `VirtualKeyboard.MONIKA_GAP`. */
	public static final float GAP_DP = 12;
	/** Số hàng phím của bàn số (1-2-3 / 4-5-6 / 7-8-9 / *-0-#). */
	public static final int KEY_ROWS = 4;
	/** Tỉ lệ cao/rộng của phím — khớp `VirtualKeyboard.MONIKA_KEY_SCALE_Y`. */
	public static final float ROW_SCALE_Y = 0.78f;
	/** Độ đục của mặt phím khi máy ngang (0xB4 ≈ 70 %): bán trong suốt để thấy game phía sau. */
	public static final int KEY_ALPHA = 0xB4;

	/** Máy đang ở thế ngang? (bố cục V161 chỉ áp cho máy ngang). */
	public static boolean isLandscape(float w, float h) {
		return w > h;
	}

	/**
	 * Cạnh mỗi phím (pixel) khi máy ngang: vừa 3 cột trong bề rộng mỗi cụm, vừa 4 hàng trong chiều cao màn.
	 * 0 = không áp dụng (máy dọc hoặc tham số không hợp lệ).
	 */
	public static float keySize(float w, float h, float density) {
		if (!isLandscape(w, h) || density <= 0 || w <= 0 || h <= 0) return 0;
		float edge = EDGE_DP * density;
		float pad = (EDGE_DP + PANEL_PAD_DP + GAP_DP) * density;
		float byWidth = (w * SIDE_WIDTH_RATIO - pad) / 3f;
		float byHeight = (h - 2 * edge) / (KEY_ROWS * ROW_SCALE_Y);
		return Math.max(0, Math.min(byWidth, byHeight));
	}

	/** Bề rộng một cụm phím + khay (pixel), tính từ mép màn vào. 0 = không áp dụng. */
	public static float sideWidth(float w, float h, float density) {
		float size = keySize(w, h, density);
		if (size <= 0) return 0;
		return 3 * size + (EDGE_DP + PANEL_PAD_DP + GAP_DP) * density;
	}

	/**
	 * Khung dành cho game khi máy ngang: {trái, trên, phải, dưới} (pixel), đối xứng hai bên và trên/dưới,
	 * nằm giữa 2 cụm phím. Máy dọc/không hợp lệ → cả màn hình.
	 */
	public static float[] band(float w, float h, float density) {
		if (!isLandscape(w, h) || density <= 0) return new float[]{0, 0, w, h};
		float edge = EDGE_DP * density;
		float side = sideWidth(w, h, density);
		return new float[]{side, edge, w - side, h - edge};
	}

	/**
	 * Khung TRONG của khung game máy ngang sau khi trừ lề hai bên: {rộng, cao} (pixel).
	 *
	 * <p>`Canvas` cộng `settings.screenPadding` vào `onX/onY` SAU khi căn giữa ảnh, nên ảnh phải được fit vào
	 * khung đã trừ lề (khung này vẫn đồng tâm với `band`). Nếu lấy nguyên bề rộng `band` thì tâm ảnh lệch phải
	 * đúng bằng padding (lỗi Luna 3 bắt ở PR #424).
	 */
	public static int[] innerFrame(float w, float h, float density, int padding) {
		float[] band = band(w, h, density);
		return new int[]{
				shrink((int) (band[2] - band[0]), padding),
				shrink((int) (band[3] - band[1]), padding)
		};
	}

	/**
	 * Bề rộng khung TRONG khi khung cách đều hai mép màn `margin` (pixel) — bố cục Monika máy DỌC.
	 *
	 * <p>Cùng lý do với {@link #innerFrame}: `Canvas` cộng `settings.screenPadding` vào `onX` SAU khi căn giữa,
	 * nên phải trừ lề ở đây TRƯỚC bước fit/căn giữa, nếu không tâm ảnh lệch phải đúng bằng padding.
	 */
	public static int innerWidth(float screenWidth, int margin, int padding) {
		return shrink((int) screenWidth - 2 * margin, padding);
	}

	/** Trừ lề mỗi bên `padding` khỏi một cạnh khung; không trả về số âm. */
	private static int shrink(int size, int padding) {
		return Math.max(0, size - 2 * Math.max(0, padding));
	}

	/**
	 * Cỡ ảnh game (pixel) vừa khung mà GIỮ ĐÚNG tỉ lệ khung hình: {rộng, cao}. Dùng thay cho kiểu "kéo giãn"
	 * khi máy ngang để game không bị méo.
	 */
	public static int[] fit(int canvasW, int canvasH, int bandW, int bandH) {
		if (bandW <= 0 || bandH <= 0) return new int[]{Math.max(0, bandW), Math.max(0, bandH)};
		if (canvasW <= 0 || canvasH <= 0) return new int[]{bandW, bandH};
		long heightAtFullWidth = (long) canvasH * bandW / canvasW;
		if (heightAtFullWidth <= bandH) {
			return new int[]{bandW, (int) Math.max(1, heightAtFullWidth)};
		}
		int widthAtFullHeight = (int) ((long) canvasW * bandH / canvasH);
		return new int[]{Math.max(1, widthAtFullHeight), bandH};
	}

	/** Độ đục mặt phím: máy ngang bán trong suốt, máy dọc giữ nguyên (đục) như trước V161. */
	public static int keyAlpha(boolean landscape) {
		return landscape ? KEY_ALPHA : 0xFF;
	}
}
