package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared fade-in/out treatment for every custom Mechanician screen. */
public final class ModGuiFadeOverlay {
	private static final long FADE_NANOS = 180_000_000L;
	private static final Map<Object, Long> OPENED = new WeakHashMap<>();
	private static final Map<Object, Long> CLOSING = new WeakHashMap<>();
	private static final Map<Object, Boolean> ALLOW_CLOSE = new WeakHashMap<>();
	private static final Map<Object, Boolean> CLOSE_SCHEDULED =
			new WeakHashMap<>();

	public static void render(Screen screen, DrawContext context) {
		if (!isModGui(screen)) {
			return;
		}
		long now = System.nanoTime();
		long openedAt = OPENED.computeIfAbsent(screen, ignored -> now);
		long closeAt = CLOSING.getOrDefault(screen, Long.MAX_VALUE);
		if (closeAt <= now) {
			if (CLOSE_SCHEDULED.put(screen, Boolean.TRUE) == null) {
				OPENED.remove(screen);
				CLOSING.remove(screen);
				ALLOW_CLOSE.put(screen, Boolean.TRUE);
				MinecraftClient.getInstance().execute(screen::close);
			}
			return;
		}
		double fadeIn = Math.min(1.0D,
				(now - openedAt) / (double) FADE_NANOS);
		double fadeOut = closeAt == Long.MAX_VALUE ? 1.0D
				: Math.max(0.0D,
						(closeAt - now) / (double) FADE_NANOS);
		float alpha = (float) ((1.0D - Math.min(fadeIn, fadeOut))
				* 0.65D);
		if (alpha <= 0.0F) {
			return;
		}
		context.fill(0, 0,
				MinecraftClient.getInstance().getWindow().getScaledWidth(),
				MinecraftClient.getInstance().getWindow().getScaledHeight(),
				((int) (alpha * 255.0F) << 24));
	}

	public static boolean shouldDelayClose(Screen screen) {
		if (ALLOW_CLOSE.remove(screen) != null || !isModGui(screen)) {
			return false;
		}
		CLOSING.computeIfAbsent(screen,
				ignored -> System.nanoTime() + FADE_NANOS);
		CLOSE_SCHEDULED.remove(screen);
		return true;
	}

	private static boolean isModGui(Screen screen) {
		return screen != null && screen.getClass().getName()
				.startsWith("com.tk.gun.client.screen.");
	}

	private ModGuiFadeOverlay() {
	}
}
