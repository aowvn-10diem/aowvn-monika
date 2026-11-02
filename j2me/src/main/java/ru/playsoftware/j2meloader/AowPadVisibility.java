/*
 * Aow Monika (V162): quy tắc ẩn/hiện phím ảo — dùng chung cho libretro, Azahar (3DS) và J2ME.
 * Đặt ở module :j2me vì đây là module duy nhất cả app lẫn J2ME đều thấy (app phụ thuộc :j2me).
 * Chỉ là trạng thái + quy tắc thuần, không phụ thuộc Android → `PadVisibilityTest` ở module app kiểm bằng JUnit.
 */
package ru.playsoftware.j2meloader;

public final class AowPadVisibility {
	/** Người chơi bấm nút tự ẩn — nhớ trong Prefs, tôn trọng qua mọi lần cắm/rút tay cầm. */
	private boolean manualHidden;
	/** Tay cầm thật đang cắm → tự ẩn, rút ra thì bỏ cờ này. */
	private boolean autoHidden;

	public AowPadVisibility() {
		this(false);
	}

	public AowPadVisibility(boolean manualHidden) {
		this.manualHidden = manualHidden;
	}

	public boolean isManualHidden() {
		return manualHidden;
	}

	/** Nạp lại lựa chọn đã lưu trong Prefs lúc mở game. */
	public void setManualHidden(boolean hidden) {
		manualHidden = hidden;
	}

	public boolean isVisible() {
		return !manualHidden && !autoHidden;
	}

	/** Sự kiện cắm/rút tay cầm (hoặc đồng bộ lúc mở game): cắm = tự ẩn, rút = tự hiện. */
	public void setPadConnected(boolean connected) {
		autoHidden = connected;
	}

	/**
	 * Nút ẩn/hiện: người chơi chủ động nên ghi đè cả trạng thái tự ẩn của tay cầm
	 * (đang cắm tay cầm mà bấm "Hiện phím" thì phím hiện ngay).
	 * @return trạng thái hiện sau khi bấm.
	 */
	public boolean toggle() {
		if (isVisible()) {
			manualHidden = true;
		} else {
			manualHidden = false;
			autoHidden = false;
		}
		return isVisible();
	}
}
