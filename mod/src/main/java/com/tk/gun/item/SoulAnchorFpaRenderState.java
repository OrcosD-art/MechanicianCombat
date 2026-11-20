package com.tk.gun.item;

/** Client render mode consumed by the hidden first-person anchor animatable. */
public final class SoulAnchorFpaRenderState {
	public enum Mode {
		NONE,
		READY,
		THROW
	}

	private static volatile Mode mode = Mode.NONE;

	public static Mode mode() {
		return mode;
	}

	public static void set(Mode value) {
		mode = value == null ? Mode.NONE : value;
	}

	private SoulAnchorFpaRenderState() {
	}
}
