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
	}

	private static String read(String local, String fromRoot) throws IOException {
		File file = new File(local);
		if (!file.isFile()) file = new File(fromRoot);
		assertTrue("không tìm thấy tệp nguồn: " + local, file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}
}
