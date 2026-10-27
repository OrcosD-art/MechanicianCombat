package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Centered red warning: two 250 ms flashes over one second. */
public final class GunWarningHud {
	private static final long FLASH_MS = 250L;
	private static final long TOTAL_MS = FLASH_MS * 4L;
	private static long startedAt;

	public static void showOverweight() {
		startedAt = System.nanoTime();
	}

	public static void render(DrawContext context) {
		if (startedAt == 0L) {
			return;
		}
		long elapsed = System.nanoTime() - startedAt;
		if (elapsed >= TOTAL_MS * 1_000_000L) {
			startedAt = 0L;
			return;
		}
		if ((elapsed / (FLASH_MS * 1_000_000L)) % 2L != 0L) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		String text = "\u8fc7\u91cd\uff01";
		int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(text)) / 2;
		int y = context.getScaledWindowHeight() / 2 + 10;
		context.drawText(client.textRenderer, text, x, y, 0xFFFF3B30, true);
	}

	private GunWarningHud() {
	}
}
