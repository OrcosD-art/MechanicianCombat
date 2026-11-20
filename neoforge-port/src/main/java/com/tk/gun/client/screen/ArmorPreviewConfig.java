package com.tk.gun.client.screen;

/** Fixed default transform for the armor preview. */
public final class ArmorPreviewConfig {
	private static final float X = 0.5F;
	private static final float Y = -57.92306F;
	private static final float SCALE = 31.0F;

	public static float x() {
		return X;
	}

	public static float y() {
		return Y;
	}

	public static float scale() {
		return SCALE;
	}

	private ArmorPreviewConfig() {
	}
}
