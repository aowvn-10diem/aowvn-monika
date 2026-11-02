/*
 * Aow Monika (V161): test bố cục bàn phím ảo kiểu Monika khi máy XOAY NGANG.
 * Chạy bằng JUnit thường (không cần Android/Robolectric): AowLandscapePad chỉ là số và hàm thuần.
 */
package ru.playsoftware.j2meloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AowLandscapePadTest {
	/** Màn ngang điển hình (pixel, density = 1): 800 × 360. */
	private static final float W = 800;
	private static final float H = 360;
	private static final float DENSITY = 1;

	/** Phím ngang phải nhỏ hơn hẳn 1/6 bề rộng như bản dọc, nếu không 2 cụm phím phủ kín màn (lỗi V161). */
	@Test
	public void landscapeKeyIsSmallerThanPortraitColumns() {
		float size = AowLandscapePad.keySize(W, H, DENSITY);
		assertTrue("cỡ phím phải > 0", size > 0);
		assertTrue("cỡ phím ngang (" + size + ") phải nhỏ hơn 1/6 bề rộng (" + (W / 6) + ")",
				size < W / 6);
	}

	/** Khung game ở giữa: cách đều 2 mép trái/phải và trên/dưới. */
	@Test
	public void gameBandIsCentered() {
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		assertEquals("cách mép trái = cách mép phải", band[0], W - band[2], 0.01f);
		assertEquals("cách mép trên = cách mép dưới", band[1], H - band[3], 0.01f);
		assertTrue("khung game phải nằm trong màn", band[0] > 0 && band[2] < W);
	}

	/** Khung game còn đủ rộng để chơi: ≥ 45 % bề rộng và ≥ 75 % chiều cao màn. */
	@Test
	public void gameBandKeepsEnoughRoomForTheGame() {
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		float bandW = band[2] - band[0];
		float bandH = band[3] - band[1];
		assertTrue("khung game rộng " + bandW + " phải ≥ 45 % màn", bandW >= W * 0.45f);
		assertTrue("khung game cao " + bandH + " phải ≥ 75 % màn", bandH >= H * 0.75f);
	}

	/** Bàn số dạt mép trái, cụm mũi tên dạt mép phải; cả hai nằm ngoài khung game. */
	@Test
	public void keyClustersSitAtTheTwoEdgesOutsideTheGameBand() {
		float size = AowLandscapePad.keySize(W, H, DENSITY);
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		float edge = AowLandscapePad.EDGE_DP * DENSITY;
		float cluster = 3 * size;

		float leftStart = edge;
		float leftEnd = leftStart + cluster;
		float rightEnd = W - edge;
		float rightStart = rightEnd - cluster;

		assertTrue("cụm trái (" + leftStart + ".." + leftEnd + ") phải nằm trong dải trái, trước khung game "
				+ band[0], leftEnd <= band[0]);
		assertTrue("cụm phải (" + rightStart + ".." + rightEnd + ") phải nằm trong dải phải, sau khung game "
				+ band[2], rightStart >= band[2]);
		assertTrue("hai cụm không được chồng lên nhau", leftEnd < rightStart);
	}

	/** 4 hàng phím phải vừa chiều cao khung (không tràn ra ngoài màn). */
	@Test
	public void keyRowsFitTheBandHeight() {
		float size = AowLandscapePad.keySize(W, H, DENSITY);
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		float rows = AowLandscapePad.KEY_ROWS * size * AowLandscapePad.ROW_SCALE_Y;
		assertTrue("4 hàng phím cao " + rows + " phải vừa khung " + (band[3] - band[1]),
				rows <= band[3] - band[1]);
	}

	/** Ảnh game giữ đúng tỉ lệ khung hình (không kéo giãn méo) và nằm gọn trong khung. */
	@Test
	public void fitKeepsAspectRatio() {
		// Game 240×320 (dọc) trong khung ngang 400×300 → phải thu theo chiều cao, không kéo rộng.
		int[] fit = AowLandscapePad.fit(240, 320, 400, 300);
		assertEquals("cao = cả khung", 300, fit[1]);
		assertEquals("tỉ lệ giữ nguyên 240/320", 0.75f, (float) fit[0] / fit[1], 0.01f);
		assertTrue("không tràn khung", fit[0] <= 400 && fit[1] <= 300);

		// Khung đúng tỉ lệ thì lấp đầy.
		int[] exact = AowLandscapePad.fit(240, 320, 240, 320);
		assertEquals(240, exact[0]);
		assertEquals(320, exact[1]);

		// Game rộng hơn khung → thu theo chiều rộng.
		int[] wide = AowLandscapePad.fit(480, 320, 300, 300);
		assertEquals(300, wide[0]);
		assertEquals(1.5f, (float) wide[0] / wide[1], 0.01f);
	}

	/**
	 * Lỗi Luna 3 ở PR #424: khung trong phải là `band` trừ ĐỀU 2 lề mỗi chiều, nếu không tâm ảnh lệch.
	 */
	@Test
	public void innerFrameIsBandMinusPaddingOnBothAxes() {
		int padding = 12;
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		int[] frame = AowLandscapePad.innerFrame(W, H, DENSITY, padding);

		assertEquals("bề rộng khung trong = band - 2 * padding",
				(int) (band[2] - band[0]) - 2 * padding, frame[0]);
		assertEquals("chiều cao khung trong = band - 2 * padding",
				(int) (band[3] - band[1]) - 2 * padding, frame[1]);
		// Khung trong phải ĐỒNG TÂM với band, vì Canvas cộng padding vào onX/onY sau khi căn giữa.
		assertEquals("tâm ngang khung trong trùng tâm band",
				(band[0] + band[2]) / 2f, band[0] + padding + frame[0] / 2f, 0.01f);
		assertEquals("tâm dọc khung trong trùng tâm band",
				(band[1] + band[3]) / 2f, band[1] + padding + frame[1] / 2f, 0.01f);
		// Padding 0 giữ nguyên như trước (không đổi hành vi mặc định của J2ME).
		int[] noPadding = AowLandscapePad.innerFrame(W, H, DENSITY, 0);
		assertEquals((int) (band[2] - band[0]), noPadding[0]);
		assertEquals((int) (band[3] - band[1]), noPadding[1]);
	}

	/**
	 * Test tích hợp (dựng lại đúng phép tính của `Canvas.updateSize` khi máy ngang): với padding DƯƠNG,
	 * tâm ảnh game phải trùng tâm khung dành cho game — không lệch phải/xuống như lỗi Luna 3 bắt ở #424.
	 */
	@Test
	public void gameImageIsCenteredInsideTheFrameWithPositivePadding() {
		int padding = 12;
		int gameW = 240;
		int gameH = 320;
		float[] band = AowLandscapePad.band(W, H, DENSITY);
		float bandCenterX = (band[0] + band[2]) / 2f;
		float bandCenterY = (band[1] + band[3]) / 2f;

		int[] image = imageRectOnScreen(W, H, DENSITY, gameW, gameH, padding);
		int centerX = image[0] + image[2] / 2;
		int centerY = image[1] + image[3] / 2;
		// Sai số 1 px là do cắt phần nguyên khi căn giữa (onWidth/onHeight lẻ).
		assertTrue("tâm ảnh ngang " + centerX + " phải trùng tâm khung " + bandCenterX
						+ " (padding " + padding + ")", Math.abs(centerX - bandCenterX) <= 1);
		assertTrue("tâm ảnh dọc " + centerY + " phải trùng tâm khung " + bandCenterY,
				Math.abs(centerY - bandCenterY) <= 1);
		assertTrue("ảnh phải nằm trong khung trong, không đè lên khay phím",
				image[0] >= band[0] + padding && image[0] + image[2] <= band[2] - padding);

		// Chống tái phát: nếu lấy bề rộng CẢ band (cách tính cũ) thì tâm ảnh lệch phải đúng bằng padding.
		int[] old = imageRectWithFrameWidth(W, H, DENSITY, gameW, gameH, padding,
				(int) (band[2] - band[0]));
		int oldCenterX = old[0] + old[2] / 2;
		assertTrue("cách tính cũ phải lộ đúng lỗi lệch " + padding + " px (tâm " + oldCenterX + ")",
				oldCenterX - bandCenterX >= padding - 1);
	}

	/** Các cỡ màn/padding thường gặp: tâm ảnh luôn trùng tâm khung (sai số ≤ 1 px). */
	@Test
	public void imageStaysCenteredAcrossScreensAndPaddings() {
		float[][] screens = {{800, 360, 1f}, {2400, 1080, 3f}, {1280, 720, 2f}, {1920, 1080, 2.75f}};
		int[] paddings = {1, 4, 8, 12, 24};
		for (float[] screen : screens) {
			float[] band = AowLandscapePad.band(screen[0], screen[1], screen[2]);
			for (int padding : paddings) {
				int[] image = imageRectOnScreen(screen[0], screen[1], screen[2], 240, 320, padding);
				assertTrue("màn " + screen[0] + "x" + screen[1] + " padding " + padding
								+ ": tâm ngang lệch quá 1 px",
						Math.abs(image[0] + image[2] / 2 - (band[0] + band[2]) / 2f) <= 1);
				assertTrue("màn " + screen[0] + "x" + screen[1] + " padding " + padding
								+ ": tâm dọc lệch quá 1 px",
						Math.abs(image[1] + image[3] / 2 - (band[1] + band[3]) / 2f) <= 1);
			}
		}
	}

	/** `innerWidth` (bố cục máy DỌC): trừ đều lề hai bên + lề người dùng, padding 0 giữ nguyên như trước. */
	@Test
	public void innerWidthSubtractsSideMarginAndPadding() {
		int margin = 12;
		int padding = 10;
		assertEquals("rộng = màn - 2*margin - 2*padding", 360 - 2 * margin - 2 * padding,
				AowLandscapePad.innerWidth(360, margin, padding));
		assertEquals("padding 0 giữ nguyên cách tính cũ", 360 - 2 * margin,
				AowLandscapePad.innerWidth(360, margin, 0));
		assertEquals("lề âm coi như 0", 360 - 2 * margin, AowLandscapePad.innerWidth(360, margin, -5));
		assertEquals("không trả về số âm", 0, AowLandscapePad.innerWidth(20, margin, padding));
	}

	/**
	 * Cùng lỗi Luna 3 ở nhánh NGANG, nay sửa cho nhánh Monika máy DỌC: với padding DƯƠNG, tâm ảnh phải trùng
	 * tâm khung (trước khi sửa, tâm ảnh lệch phải đúng bằng padding).
	 *
	 * <p>Khung dùng ở đây là `AowLandscapePad.portraitFrame` — chính hàm `Canvas.updateSize` gọi, có chừa chỗ
	 * bàn phím Monika ở đáy (`AowLandscapePad.monikaKeyboardHeight`, cũng là hàm `VirtualKeyboard` gọi).
	 */
	@Test
	public void portraitImageIsCenteredWithPositivePadding() {
		float sw = 360, sh = 800, density = 1;
		int padding = 12;
		float vkHeight = AowLandscapePad.monikaKeyboardHeight(sw, sh, density);
		int[] frame = AowLandscapePad.portraitFrame(sw, sh, density, padding, vkHeight);
		int edge = frame[0];
		// Khung dọc: cách đều 2 mép trái/phải `edge`, phía trên chừa lề trên, đáy chừa bàn phím.
		float frameCenterX = sw / 2f;
		float frameCenterY = frame[1] + padding + frame[3] / 2f;

		// screenScaleType 2 (kéo giãn): ảnh phủ đúng khung.
		int[] stretch = portraitImageRect(sw, sh, density, 240, 320, padding, false);
		assertTrue("padding " + padding + ": tâm ngang ảnh stretch " + (stretch[0] + stretch[2] / 2f)
				+ " phải trùng tâm khung " + frameCenterX,
				Math.abs(stretch[0] + stretch[2] / 2f - frameCenterX) <= 1);
		assertTrue("tâm dọc ảnh stretch không bị padding làm lệch",
				Math.abs(stretch[1] + stretch[3] / 2f - frameCenterY) <= 1);

		// screenScaleType 1 (fit giữ tỉ lệ): ảnh nhỏ hơn khung nhưng vẫn phải ở giữa.
		int[] fit = portraitImageRect(sw, sh, density, 240, 320, padding, true);
		assertTrue("padding " + padding + ": tâm ngang ảnh fit tỉ lệ " + (fit[0] + fit[2] / 2f)
				+ " phải trùng tâm khung " + frameCenterX,
				Math.abs(fit[0] + fit[2] / 2f - frameCenterX) <= 1);
		assertTrue("tâm dọc ảnh fit tỉ lệ không bị padding làm lệch",
				Math.abs(fit[1] + fit[3] / 2f - frameCenterY) <= 1);
		assertTrue("ảnh phải nằm trong khung, không đè mép", fit[0] >= edge + padding);
		assertTrue("ảnh không được đè lên bàn phím",
				fit[1] + fit[3] <= frame[1] + padding + frame[3]);

		// Chống tái phát: cách tính CŨ (không trừ lề khỏi bề rộng) lệch phải đúng bằng padding.
		int innerW = AowLandscapePad.innerWidth(sw, edge, padding);
		int oldX = padding + edge + ((int) sw - 2 * edge - innerW) / 2;
		assertTrue("cách tính cũ phải lộ đúng lỗi lệch " + padding + " px",
				oldX + innerW / 2 - frameCenterX >= padding - 1);
	}

	/** Khung dọc phải chừa đúng chỗ bàn phím Monika (máy ngang: 0) — đây là phần mô hình test từng thiếu. */
	@Test
	public void portraitFrameReservesSpaceForTheMonikaKeyboard() {
		float sw = 360, sh = 800, density = 1;
		int padding = 12;
		float vkHeight = AowLandscapePad.monikaKeyboardHeight(sw, sh, density);
		assertTrue("bàn phím Monika máy dọc phải chiếm chỗ (cao > 0), được " + vkHeight, vkHeight > 0);
		assertEquals("máy ngang: phím nằm hai bên, không chiếm chiều cao", 0f,
				AowLandscapePad.monikaKeyboardHeight(800, 360, density), 0f);

		int[] frame = AowLandscapePad.portraitFrame(sw, sh, density, padding, vkHeight);
		assertEquals("cao khung = màn - bàn phím - 1 - lề dưới - lề trên",
				(int) (sh - vkHeight - 1) - padding - (int) (AowLandscapePad.PORTRAIT_TOP_DP * density),
				frame[3]);
		assertTrue("khung phải thấp hơn màn đúng phần bàn phím + lề trên + lề dưới",
				frame[3] < sh - 2 * padding - AowLandscapePad.PORTRAIT_TOP_DP * density);
		assertTrue("bề rộng khung = màn - 2 lề hai bên - 2 padding",
				frame[2] == (int) sw - 2 * frame[0] - 2 * padding);
	}

	/** Các cỡ màn dọc thường gặp × nhiều mức padding: tâm ảnh luôn trùng tâm khung. */
	@Test
	public void portraitImageStaysCenteredAcrossScreensAndPaddings() {
		float[][] screens = {{360, 800, 1f}, {1080, 2400, 3f}, {720, 1600, 2f}, {1440, 3120, 3.5f}};
		int[] paddings = {1, 4, 8, 12, 24};
		for (float[] screen : screens) {
			float vkHeight = AowLandscapePad.monikaKeyboardHeight(screen[0], screen[1], screen[2]);
			for (int padding : paddings) {
				int[] frame = AowLandscapePad.portraitFrame(screen[0], screen[1], screen[2], padding,
						vkHeight);
				int[] image = portraitImageRect(screen[0], screen[1], screen[2], 240, 320, padding, true);
				assertTrue("màn dọc " + screen[0] + "x" + screen[1] + " padding " + padding
								+ ": tâm ngang lệch quá 1 px",
						Math.abs(image[0] + image[2] / 2 - screen[0] / 2f) <= 1);
				assertTrue("màn dọc " + screen[0] + "x" + screen[1] + " padding " + padding
								+ ": tâm dọc lệch quá 1 px",
						Math.abs(image[1] + image[3] / 2 - (frame[1] + padding + frame[3] / 2f)) <= 1);
			}
		}
	}

	/**
	 * Dựng lại bước CUỐI của `Canvas.updateSize` cho nhánh Monika máy DỌC: khung lấy từ
	 * `AowLandscapePad.portraitFrame` (đã chừa chỗ bàn phím Monika ở đáy) → (tuỳ chọn) fit giữ tỉ lệ →
	 * căn giữa → cộng padding → cộng lề trên/trái. Trả về {x, y, rộng, cao} của ảnh trên màn.
	 */
	private static int[] portraitImageRect(float sw, float sh, float density, int gameW, int gameH,
			int padding, boolean keepAspect) {
		float vkHeight = AowLandscapePad.monikaKeyboardHeight(sw, sh, density);
		int[] frame = AowLandscapePad.portraitFrame(sw, sh, density, padding, vkHeight);
		int innerW = frame[2];
		int innerH = frame[3];
		int onW = innerW;
		int onH = innerH;
		if (keepAspect) {
			int[] fit = AowLandscapePad.fit(gameW, gameH, innerW, innerH);
			onW = fit[0];
			onH = fit[1];
		}
		int onX = (innerW - onW) / 2 + padding + frame[0];
		int onY = (innerH - onH) / 2 + padding + frame[1];
		return new int[]{onX, onY, onW, onH};
	}

	/**
	 * Dựng lại phép tính của `Canvas.updateSize` cho nhánh Monika máy ngang: fit vào khung trong → căn giữa →
	 * cộng padding → cộng gốc band. Trả về {x, y, rộng, cao} của ảnh trên màn.
	 */
	private static int[] imageRectOnScreen(float sw, float sh, float density, int gameW, int gameH,
			int padding) {
		int[] frame = AowLandscapePad.innerFrame(sw, sh, density, padding);
		return place(sw, sh, density, gameW, gameH, padding, frame[0], frame[1]);
	}

	/** Như trên nhưng cho phép chọn bề rộng khung — dùng để chứng minh cách tính CŨ (cả band) bị lệch. */
	private static int[] imageRectWithFrameWidth(float sw, float sh, float density, int gameW, int gameH,
			int padding, int frameWidth) {
		int[] frame = AowLandscapePad.innerFrame(sw, sh, density, padding);
		return place(sw, sh, density, gameW, gameH, padding, frameWidth, frame[1]);
	}

	private static int[] place(float sw, float sh, float density, int gameW, int gameH, int padding,
			int frameW, int frameH) {
		float[] band = AowLandscapePad.band(sw, sh, density);
		int[] fit = AowLandscapePad.fit(gameW, gameH, frameW, frameH);
		int onX = (frameW - fit[0]) / 2 + padding + (int) band[0];
		int onY = (frameH - fit[1]) / 2 + padding + (int) band[1];
		return new int[]{onX, onY, fit[0], fit[1]};
	}

	/** Phím ngang bán trong suốt, phím dọc giữ nguyên độ đục như trước V161. */
	@Test
	public void landscapeKeysAreSemiTransparent() {
		int landscape = AowLandscapePad.keyAlpha(true);
		assertEquals(AowLandscapePad.KEY_ALPHA, landscape);
		assertTrue("phải còn trong suốt", landscape < 0xFF);
		assertTrue("nhưng vẫn đủ rõ để bấm", landscape >= 0x80);
		assertEquals("máy dọc giữ nguyên", 0xFF, AowLandscapePad.keyAlpha(false));
	}

	/** Máy dọc (hoặc tham số hỏng) không bị đổi bố cục: không có dải phím 2 bên, khung = cả màn. */
	@Test
	public void portraitAndInvalidSizesAreUntouched() {
		assertFalse(AowLandscapePad.isLandscape(360, 800));
		assertEquals(0f, AowLandscapePad.keySize(360, 800, DENSITY), 0f);
		assertEquals(0f, AowLandscapePad.sideWidth(360, 800, DENSITY), 0f);
		float[] band = AowLandscapePad.band(360, 800, DENSITY);
		assertEquals(0f, band[0], 0f);
		assertEquals(360f, band[2], 0f);
		assertEquals(0f, AowLandscapePad.keySize(W, H, 0), 0f);
	}

	/** Mã thật phải dùng đúng lớp này (tránh sửa lớp mà quên nối vào bàn phím/khung game). */
	@Test
	public void productionCodeUsesLandscapeLayout() throws IOException {
		String keyboard = read("src/main/java/javax/microedition/lcdui/keyboard/VirtualKeyboard.java",
				"../j2me/src/main/java/javax/microedition/lcdui/keyboard/VirtualKeyboard.java");
		assertTrue("VirtualKeyboard phải tính cỡ phím ngang bằng AowLandscapePad.keySize",
				keyboard.contains("AowLandscapePad.keySize("));
		assertTrue("VirtualKeyboard phải tính bề rộng cụm phím bằng AowLandscapePad.sideWidth",
				keyboard.contains("AowLandscapePad.sideWidth("));
		assertTrue("phím ngang phải dùng độ mờ của AowLandscapePad",
				keyboard.contains("AowLandscapePad.keyAlpha("));

		String canvas = read("src/main/java/javax/microedition/lcdui/Canvas.java",
				"../j2me/src/main/java/javax/microedition/lcdui/Canvas.java");
		assertTrue("Canvas phải lấy khung game giữa bằng AowLandscapePad.band",
				canvas.contains("AowLandscapePad.band("));
		assertTrue("Canvas phải giữ tỉ lệ khung hình bằng AowLandscapePad.fit",
				canvas.contains("AowLandscapePad.fit("));
		assertTrue("Canvas phải trừ lề trước khi fit/căn giữa bằng AowLandscapePad.innerFrame",
				canvas.contains("AowLandscapePad.innerFrame("));
		assertTrue("Canvas nhánh máy dọc phải dựng khung bằng AowLandscapePad.portraitFrame",
				canvas.contains("AowLandscapePad.portraitFrame("));
		assertTrue("VirtualKeyboard phải lấy chiều cao bàn phím Monika từ AowLandscapePad.monikaKeyboardHeight",
				keyboard.contains("AowLandscapePad.monikaKeyboardHeight("));
	}

	private static String read(String local, String fromRoot) throws IOException {
		File file = new File(local);
		if (!file.isFile()) file = new File(fromRoot);
		assertTrue("không tìm thấy tệp nguồn: " + local, file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}
}
