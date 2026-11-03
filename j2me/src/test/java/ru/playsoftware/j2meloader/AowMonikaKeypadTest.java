/*
 * Aow Monika (V185): test phần hình học/trạng thái chạm của bàn phím Monika — phần tách ra khỏi
 * `VirtualKeyboard` để chạy được bằng JUnit thường (không cần Android/Robolectric).
 *
 * Hình học trong test dựng theo bố cục THẬT của `VirtualKeyboard.resetLayout(TYPE_MONIKA)`:
 * máy dọc 3x4 bàn số bám mép dưới-trái, cụm mũi tên 3 cột bên phải; máy ngang bàn số bám mép trái.
 */
package ru.playsoftware.j2meloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AowMonikaKeypadTest {
	/** Cỡ phím thật khi máy dọc 1080x2400 (density 3): (1080 - 2*30) / 6 = 170, cao 170*0.78. */
	private static final float KEY_W = 170f;
	private static final float KEY_H = 132.6f;
	private static final float EDGE = 30f;

	// ---------- VIỆC 1: ánh xạ phím số ----------

	/** 4 phím góc 1/3/7/9 phải ra đúng mã MIDP (49/51/55/57), không lệch sang phím bên cạnh. */
	@Test
	public void cornerDigitsMapToTheRightMidpKeyCodes() {
		assertEquals("phím 1", 49, AowMonikaKeypad.digitKeyCode(1));
		assertEquals("phím 3", 51, AowMonikaKeypad.digitKeyCode(3));
		assertEquals("phím 7", 55, AowMonikaKeypad.digitKeyCode(7));
		assertEquals("phím 9", 57, AowMonikaKeypad.digitKeyCode(9));
	}

	/** Cả 9 phím số: mã đúng thứ tự, không trùng, không đè lên mã phím khác của bàn phím. */
	@Test
	public void everyDigitMapsToItsOwnKeyCode() {
		boolean[] seen = new boolean[64];
		for (int digit = 1; digit <= 9; digit++) {
			int code = AowMonikaKeypad.digitKeyCode(digit);
			assertEquals("số " + digit, 48 + digit, code);
			assertFalse("mã " + code + " bị dùng hai lần", seen[code - 32]);
			seen[code - 32] = true;
		}
	}

	/** Ngoài 1..9 là lỗi lập trình, không im lặng trả mã sai. */
	@Test(expected = IllegalArgumentException.class)
	public void digitZeroIsRejected() {
		AowMonikaKeypad.digitKeyCode(0);
	}

	// ---------- VIỆC 1: vùng chạm ----------

	/** Bàn số 3x4 bám mép dưới-trái: bấm giữa mỗi phím 1/3/7/9 phải trúng đúng phím đó. */
	@Test
	public void cornerKeysAreHitAtTheirCenters() {
		AowMonikaKeypad.Box[] boxes = numberPad();
		boolean[] visible = new boolean[boxes.length];
		for (int i = 0; i < visible.length; i++) visible[i] = true;

		int[] corners = {0, 2, 6, 8}; // 1, 3, 7, 9
		for (int index : corners) {
			AowMonikaKeypad.Box box = boxes[index];
			assertEquals("bấm giữa phím " + (index + 1), index,
					AowMonikaKeypad.hitTest(boxes, visible, box.centerX(), box.centerY()));
		}
		// Bấm sát 4 góc trong của phím vẫn phải trúng (biên trên/trái tính, biên dưới/phải không).
		AowMonikaKeypad.Box one = boxes[0];
		assertEquals(0, AowMonikaKeypad.hitTest(boxes, visible, one.left + 1, one.top + 1));
		assertEquals(0, AowMonikaKeypad.hitTest(boxes, visible, one.right - 1, one.bottom - 1));
	}

	/** Bấm ra ngoài mọi phím thì không trúng phím nào (không "ăn" nhầm). */
	@Test
	public void tapOutsideAnyKeyHitsNothing() {
		AowMonikaKeypad.Box[] boxes = numberPad();
		boolean[] visible = new boolean[boxes.length];
		for (int i = 0; i < visible.length; i++) visible[i] = true;
		assertEquals(-1, AowMonikaKeypad.hitTest(boxes, visible, 5, 5));
		assertEquals(-1, AowMonikaKeypad.hitTest(boxes, visible, KEY_W * 3 + 200, KEY_H * 4));
	}

	/** Phím đang ẩn (double-key chéo) không được nhận chạm. */
	@Test
	public void hiddenKeyNeverTakesTheTouch() {
		AowMonikaKeypad.Box[] boxes = {new AowMonikaKeypad.Box(0, 0, 100, 100),
				new AowMonikaKeypad.Box(0, 0, 100, 100)};
		boolean[] visible = {false, true};
		assertEquals("phím ẩn bị bỏ qua", 1, AowMonikaKeypad.hitTest(boxes, visible, 50, 50));
	}

	/**
	 * Hai phím chồng lấn (bố cục bị chỉnh cỡ phím): chọn phím có TÂM gần điểm chạm nhất, không phụ thuộc
	 * thứ tự mảng. Trước V185 lấy phím đầu tiên nên phím đứng trước "nuốt" cú chạm của phím kia.
	 */
	@Test
	public void overlappingKeysPickTheNearestCenter() {
		// Phím 0 to (0..200), phím 1 nhỏ nằm trong nó (100..150). Điểm 145 → phím 1 gần tâm hơn.
		AowMonikaKeypad.Box[] boxes = {new AowMonikaKeypad.Box(0, 0, 200, 200),
				new AowMonikaKeypad.Box(100, 100, 150, 150)};
		boolean[] visible = {true, true};
		assertEquals("phải chọn phím nhỏ có tâm gần hơn", 1,
				AowMonikaKeypad.hitTest(boxes, visible, 145, 145));
		// Điểm 10 nằm ngoài phím nhỏ → phím to.
		assertEquals(0, AowMonikaKeypad.hitTest(boxes, visible, 10, 10));
	}

	// ---------- VIỆC 2: ô đặt nút menu ----------

	/**
	 * Nút menu phải ở ô NGAY BÊN TRÁI phím mũi tên xuống, cho cả bố cục dọc (↓ ở cột giữa cụm mũi tên)
	 * lẫn ngang (↓ ở cột giữa, hàng dưới) — và không đè lên phím số nào.
	 */
	@Test
	public void menuSlotSitsLeftOfTheDownKey() {
		for (boolean landscape : new boolean[]{false, true}) {
			AowMonikaKeypad.Box[] numbers = landscape ? landscapeNumberPad() : numberPad();
			AowMonikaKeypad.Box down = landscape
					? new AowMonikaKeypad.Box(2014, 911.2f, 2192, 1050)
					: new AowMonikaKeypad.Box(710, 2104.8f, 880, 2237.4f);
			AowMonikaKeypad.Box screen = landscape
					? new AowMonikaKeypad.Box(0, 0, 2400, 1080)
					: new AowMonikaKeypad.Box(0, 0, 1080, 2400);

			AowMonikaKeypad.Box[] obstacles = concat(numbers, new AowMonikaKeypad.Box[]{down});
			AowMonikaKeypad.Box slot = AowMonikaKeypad.leftOfDown(down, screen, obstacles);

			assertNotNull("bố cục " + (landscape ? "ngang" : "dọc") + " phải có ô trái phím ↓", slot);
			assertEquals("cạnh phải ô = cạnh trái phím ↓", down.left, slot.right, 0.01f);
			assertEquals("cùng hàng với phím ↓", down.top, slot.top, 0.01f);
			assertEquals("cùng cỡ phím ↓", down.width(), slot.width(), 0.01f);
			for (AowMonikaKeypad.Box number : numbers) {
				assertFalse("nút menu không được đè phím số", slot.overlaps(number));
			}
			assertTrue("ô phải nằm trong màn hình", slot.inside(screen));
		}
	}

	/** Máy quá hẹp / ô đã bị phím khác chiếm: không trả ô nào để Canvas dùng vị trí dự phòng. */
	@Test
	public void menuSlotIsNullWhenItWouldOverlapOrLeaveTheScreen() {
		AowMonikaKeypad.Box down = new AowMonikaKeypad.Box(0, 500, 100, 600);
		AowMonikaKeypad.Box screen = new AowMonikaKeypad.Box(0, 0, 1080, 2400);
		assertNull("↓ sát mép trái: ô bên trái ra ngoài màn", AowMonikaKeypad.leftOfDown(down, screen, null));

		AowMonikaKeypad.Box down2 = new AowMonikaKeypad.Box(200, 500, 300, 600);
		AowMonikaKeypad.Box blocker = new AowMonikaKeypad.Box(90, 500, 200, 600);
		assertNull("ô đã bị phím khác chiếm",
				AowMonikaKeypad.leftOfDown(down2, screen, new AowMonikaKeypad.Box[]{blocker}));
	}

	// ---------- Trạng thái giữ phím (chống phím kẹt) ----------

	/** Huỷ cử chỉ phải nhả HẾT phím đang giữ, để MIDlet nhận keyReleased và bấm lại được. */
	@Test
	public void cancelReleasesEveryHeldKey() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses(10);
		assertEquals(-1, presses.press(0, 0));   // phím 1
		assertEquals(-1, presses.press(1, 8));   // phím 9
		assertEquals(0, presses.held(0));
		assertEquals(8, presses.held(1));

		int[] released = presses.cancelAll();
		assertEquals("phải nhả đúng 2 phím", 2, released.length);
		assertEquals(-1, presses.held(0));
		assertEquals(-1, presses.held(1));
		assertEquals("huỷ lần hai không còn phím nào", 0, presses.cancelAll().length);
	}

	/** Bấm phím mới bằng cùng một ngón phải nhả phím cũ trước (không để phím cũ kẹt "đang giữ"). */
	@Test
	public void pressingAnotherKeyWithSameFingerReleasesTheOldOne() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses(10);
		assertEquals(-1, presses.press(3, 6));
		assertEquals("phím cũ cần onUp", 6, presses.press(3, 2));
		assertEquals(2, presses.held(3));
		assertEquals("nhả ngón", 2, presses.release(3));
		assertEquals("nhả lần hai không còn gì", -1, presses.release(3));
	}

	/** Ngón ngoài miền ID của Android (>= 32) không làm tràn mảng, cũng không giữ phím. */
	@Test
	public void pointerOutsideAndroidIdRangeIsIgnoredInsteadOfCrashing() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses();
		for (int pointer : new int[]{32, 33, 99}) {
			assertFalse("id " + pointer + " ngoài miền phải bị loại", presses.accepts(pointer));
			assertEquals(-1, presses.press(pointer, 4));
			assertEquals(-1, presses.held(pointer));
			assertEquals(-1, presses.release(pointer));
		}
		assertEquals(-1, presses.press(-1, 4));
	}

	/**
	 * Luna 3 (#449): `Canvas` truyền **Android pointer ID** (0..31, có thể thưa), nên sức chứa phải đủ 32 ô.
	 * Guard và sức chứa là MỘT nguồn số; id hợp lệ >=10 phải bấm được (trước đây bị bỏ → "bấm không nhận").
	 */
	@Test
	public void everyAndroidPointerIdIsAcceptedAndTracked() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses();
		assertEquals("sức chứa mặc định", AowMonikaKeypad.Presses.MAX_POINTERS, presses.capacity());
		assertEquals("miền ID của Android là 0..31", 32, presses.capacity());

		for (int pointer : new int[]{0, 9, 10, 15, 27, 31}) {
			assertTrue("id " + pointer + " là ID hợp lệ, phải nhận", presses.accepts(pointer));
			assertEquals("id " + pointer + " nhận phím", -1, presses.press(pointer, 4));
			assertEquals("id " + pointer + " đang giữ phím 4", 4, presses.held(pointer));
			assertEquals("id " + pointer + " nhả phím 4", 4, presses.release(pointer));
			assertEquals("id " + pointer + " đã nhả sạch", -1, presses.held(pointer));
		}
		for (int pointer : new int[]{-1, 32}) {
			assertFalse("id " + pointer + " ngoài miền phải bị loại", presses.accepts(pointer));
		}
	}

	/** Nhiều ngón ID thưa (3 và 17) cùng lúc: mỗi ngón giữ/nhả phím riêng, không giẫm lên nhau. */
	@Test
	public void sparseSimultaneousPointersStayIndependent() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses();
		assertEquals(-1, presses.press(3, 7));    // ngón id 3 giữ phím 8
		assertEquals(-1, presses.press(17, 2));   // ngón id 17 giữ phím 3
		assertEquals(7, presses.held(3));
		assertEquals(2, presses.held(17));

		assertEquals("nhả ngón 3", 7, presses.release(3));
		assertEquals("ngón 17 vẫn giữ nguyên", 2, presses.held(17));
		assertEquals("nhả ngón 17", 2, presses.release(17));

		// Bấm lại bằng id lớn không nhả nhầm phím của id nhỏ.
		assertEquals(-1, presses.press(31, 5));
		assertEquals(-1, presses.press(3, 6));
		assertEquals(5, presses.release(31));
		assertEquals(6, presses.release(3));
	}

	/**
	 * Ngón bị loại không được để lại trạng thái kẹt: ngón hợp lệ vẫn nhấn/nhả bình thường và `cancelAll()`
	 * không phải nhả phím "ma" nào.
	 */
	@Test
	public void rejectedPointerLeavesNoStuckState() {
		AowMonikaKeypad.Presses presses = new AowMonikaKeypad.Presses();
		int key = 6; // phím 7
		assertEquals(-1, presses.press(32, key));
		assertEquals("không có phím ma", 0, presses.cancelAll().length);

		// Ngón hợp lệ vẫn chạy đúng sau đó.
		assertEquals(-1, presses.press(0, key));
		assertEquals(key, presses.held(0));
		assertEquals(key, presses.release(0));
		assertEquals(-1, presses.held(0));
		// Cả miền ID hợp lệ đều theo dõi được.
		for (int pointer = 0; pointer < presses.capacity(); pointer++) {
			assertEquals(-1, presses.press(pointer, key));
			assertEquals(key, presses.release(pointer));
		}
	}

	/** Mã thật phải dùng đúng lớp này (tránh sửa lớp thuần mà quên nối vào bàn phím/nút menu). */
	@Test
	public void productionKeyboardUsesTheseHelpers() throws IOException {
		String source = read("src/main/java/javax/microedition/lcdui/keyboard/VirtualKeyboard.java",
				"../j2me/src/main/java/javax/microedition/lcdui/keyboard/VirtualKeyboard.java");
		assertTrue("VirtualKeyboard phải lấy mã phím số từ AowMonikaKeypad.digitKeyCode",
				source.contains("AowMonikaKeypad.digitKeyCode("));
		assertTrue("VirtualKeyboard phải tìm phím bằng AowMonikaKeypad.hitTest",
				source.contains("AowMonikaKeypad.hitTest("));
		assertTrue("Nút menu phải đặt bằng AowMonikaKeypad.leftOfDown",
				source.contains("AowMonikaKeypad.leftOfDown("));
		assertTrue("Phím đang giữ phải theo dõi bằng AowMonikaKeypad.Presses",
				source.contains("AowMonikaKeypad.Presses"));
		assertTrue("Guard ngón phải đọc từ chính bộ theo dõi (Presses.accepts), không dùng hằng khác",
				source.contains("presses.accepts("));
		assertFalse("Guard cũ so với hitVisible.length (28) đã bỏ — nó lệch sức chứa Presses",
				source.contains("pointer >= hitVisible.length"));
		assertFalse("Không còn mảng associatedKeys cũ (không nhả phím khi huỷ cử chỉ)",
				source.contains("associatedKeys"));
	}

	private static String read(String local, String fromRoot) throws IOException {
		File file = new File(local);
		if (!file.isFile()) file = new File(fromRoot);
		assertTrue("không tìm thấy tệp nguồn: " + local, file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/** Bàn số 3x4 bám mép dưới-trái (máy dọc 1080x2400): [1 2 3 / 4 5 6 / 7 8 9 / * 0 #] tính từ trên. */
	private static AowMonikaKeypad.Box[] numberPad() {
		AowMonikaKeypad.Box[] boxes = new AowMonikaKeypad.Box[9];
		float top0 = 2400 - EDGE - 4 * KEY_H;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				float left = EDGE + col * KEY_W;
				float top = top0 + row * KEY_H;
				boxes[row * 3 + col] = new AowMonikaKeypad.Box(left, top, left + KEY_W, top + KEY_H);
			}
		}
		return boxes;
	}

	/** Bàn số 3x4 bám mép dưới-trái khi máy ngang 2400x1080 (density 3): phím 178 x 138.8, mép 30. */
	private static AowMonikaKeypad.Box[] landscapeNumberPad() {
		float w = 178f, h = 138.8f, edge = 30f;
		AowMonikaKeypad.Box[] boxes = new AowMonikaKeypad.Box[9];
		float top0 = 1080 - edge - 4 * h;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				float left = edge + col * w;
				float top = top0 + row * h;
				boxes[row * 3 + col] = new AowMonikaKeypad.Box(left, top, left + w, top + h);
			}
		}
		return boxes;
	}

	private static AowMonikaKeypad.Box[] concat(AowMonikaKeypad.Box[] a, AowMonikaKeypad.Box[] b) {
		AowMonikaKeypad.Box[] out = new AowMonikaKeypad.Box[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
