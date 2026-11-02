/*
 * Aow Monika (V185): phần HÌNH HỌC/TRẠNG THÁI CHẠM thuần của bàn phím ảo Monika — tách khỏi
 * `VirtualKeyboard` (lớp `javax.*` không chạy được trong unit test) để kiểm bằng JUnit thường.
 *
 * Không import Android: mọi thứ là số thực và chỉ số, nhờ vậy test dựng đúng hình học thật của bàn phím.
 */
package ru.playsoftware.j2meloader;

public final class AowMonikaKeypad {
	private AowMonikaKeypad() {
	}

	/** Mã phím MIDP của phím số '1' (`Canvas.KEY_NUM1`); '9' là 57. */
	public static final int KEY_NUM1 = 49;

	/** Mã phím gửi cho MIDlet khi bấm phím số `digit` (1..9). */
	public static int digitKeyCode(int digit) {
		if (digit < 1 || digit > 9) throw new IllegalArgumentException("digit=" + digit);
		return KEY_NUM1 + digit - 1;
	}

	/** Hộp chạm (pixel) — thay cho `RectF` để test được không cần Android. */
	public static final class Box {
		public float left;
		public float top;
		public float right;
		public float bottom;

		public Box(float left, float top, float right, float bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}

		public float width() {
			return right - left;
		}

		public float height() {
			return bottom - top;
		}

		public float centerX() {
			return (left + right) / 2f;
		}

		public float centerY() {
			return (top + bottom) / 2f;
		}

		public boolean isEmpty() {
			return right <= left || bottom <= top;
		}

		public boolean contains(float x, float y) {
			return x >= left && x < right && y >= top && y < bottom;
		}

		/** Chồng lấn THẬT (chạm mép không tính). */
		public boolean overlaps(Box other) {
			return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
		}

		public boolean inside(Box area) {
			return left >= area.left && right <= area.right && top >= area.top && bottom <= area.bottom;
		}
	}

	/**
	 * Chỉ số hộp chạm trúng điểm (x, y); -1 = không trúng.
	 *
	 * <p>Nhiều hộp cùng chứa điểm (bố cục bị người dùng chỉnh cỡ phím nên chồng lấn) thì chọn hộp có TÂM
	 * GẦN ĐIỂM NHẤT — không phụ thuộc thứ tự trong mảng keypad. Trước V185 lấy hộp đầu tiên, nên khi hai phím
	 * chồng nhau thì phím đứng trước "nuốt" cú chạm của phím kia (bấm không ăn).
	 */
	public static int hitTest(Box[] boxes, boolean[] visible, float x, float y) {
		int best = -1;
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i < boxes.length; i++) {
			if (visible != null && i < visible.length && !visible[i]) continue;
			Box box = boxes[i];
			if (box == null || box.isEmpty() || !box.contains(x, y)) continue;
			float dx = box.centerX() - x;
			float dy = box.centerY() - y;
			float distance = dx * dx + dy * dy;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Ô trống ngay BÊN TRÁI phím mũi tên xuống để đặt nút menu Monika (yêu cầu của chủ máy: "nút bên trái nút
	 * mũi tên xuống"), cùng cỡ với phím ↓.
	 *
	 * @return hộp mới, hoặc null nếu ô đó ra ngoài màn hình / đè lên phím khác (ví dụ cụm phím số) — khi đó
	 *         `Canvas` để nút menu về góc trên phải như cũ.
	 */
	public static Box leftOfDown(Box down, Box screen, Box[] obstacles) {
		if (down == null || down.isEmpty() || screen == null) return null;
		Box slot = new Box(down.left - down.width(), down.top, down.left, down.bottom);
		if (!slot.inside(screen)) return null;
		if (obstacles != null) {
			for (Box obstacle : obstacles) {
				if (obstacle == null || obstacle.isEmpty()) continue;
				if (slot.overlaps(obstacle)) return null;
			}
		}
		return slot;
	}

	/**
	 * Theo dõi ngón tay nào đang giữ phím nào (thay cho mảng `associatedKeys`).
	 *
	 * <p>V185: giữ đúng một phím cho mỗi ngón và LUÔN nhả phím cũ trước khi gán phím mới / khi huỷ cử chỉ —
	 * trước đây `cancel()` chỉ bỏ tô sáng mà không gửi `keyReleased`, phím kẹt "đang giữ" nên bấm lại không ăn.
	 */
	public static final class Presses {
		private final int[] held;

		public Presses(int maxPointers) {
			held = new int[Math.max(1, maxPointers)];
			for (int i = 0; i < held.length; i++) held[i] = -1;
		}

		/** Gán phím cho ngón; trả về chỉ số phím CŨ của ngón đó (cần `onUp()`) hoặc -1. */
		public int press(int pointer, int key) {
			if (pointer < 0 || pointer >= held.length) return -1;
			int previous = held[pointer];
			held[pointer] = key;
			return previous == key ? -1 : previous;
		}

		/** Phím ngón đang giữ; -1 = không giữ phím nào. */
		public int held(int pointer) {
			if (pointer < 0 || pointer >= held.length) return -1;
			return held[pointer];
		}

		/** Nhả ngón; trả về phím cần `onUp()` hoặc -1. */
		public int release(int pointer) {
			if (pointer < 0 || pointer >= held.length) return -1;
			int key = held[pointer];
			held[pointer] = -1;
			return key;
		}

		/** Nhả tất cả (huỷ cử chỉ); trả về danh sách phím cần `onUp()`. */
		public int[] cancelAll() {
			int count = 0;
			for (int key : held) if (key >= 0) count++;
			int[] released = new int[count];
			int i = 0;
			for (int p = 0; p < held.length; p++) {
				if (held[p] >= 0) {
					released[i++] = held[p];
					held[p] = -1;
				}
			}
			return released;
		}
	}
}
