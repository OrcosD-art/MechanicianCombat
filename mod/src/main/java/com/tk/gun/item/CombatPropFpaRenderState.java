package com.tk.gun.item;

/** Client mode shared by the hidden first-person combat-prop animatables. */
public final class CombatPropFpaRenderState {
	public enum Mode {
		NONE,
		HUNTER_ANCHOR,
		READY,
		AIM,
		CD
	}

	private static volatile Mode mode = Mode.NONE;

	public static Mode mode() {
		return mode;
	}

	public static void set(Mode value) {
		mode = value == null ? Mode.NONE : value;
	}

	private CombatPropFpaRenderState() {
	}
}
