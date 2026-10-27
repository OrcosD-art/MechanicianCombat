package com.tk.gun;

/** Shared roll timings and the optional Combat Roll integration entrypoint. */
public final class CombatRollHook {
	private static final int ROLL_FALLBACK_TICKS = 13;
	private static final int ANIMATION_END_HOLD_TICKS = 1;

	public static final double ROLL_FAST_FOCUS_SECONDS = 0.38D;
	public static final int ROLL_FAST_FOCUS_TICKS =
			(int) Math.ceil(ROLL_FAST_FOCUS_SECONDS * 20.0D);
	public static final int ROLL_DURATION =
			animationTicks("roll", ROLL_FALLBACK_TICKS) + ANIMATION_END_HOLD_TICKS;
	public static final int ROLL_TO_IDLE_TICKS =
			animationTicks("roll_to_idle", 3) + ANIMATION_END_HOLD_TICKS;

	private static int animationTicks(String animation, int fallback) {
		String path = "assets/tkgun/animations/entity/bowgun_rig.animation.json";
		try (java.io.InputStream in = CombatRollHook.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return fallback;
			}
			String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			com.google.gson.JsonObject animations = com.google.gson.JsonParser.parseString(text)
					.getAsJsonObject().getAsJsonObject("animations");
			double length = animations.getAsJsonObject(animation)
					.get("animation_length").getAsDouble();
			return Math.max(1, (int) Math.ceil(length * 20.0D));
		} catch (Throwable throwable) {
			TkGun.LOGGER.warn("[tkgun] 读取 `{}` 动画长度失败，状态退回 {} tick：{}",
					animation, fallback, throwable.toString());
			return fallback;
		}
	}

	public static boolean isInstalled() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.isModLoaded("combat_roll");
	}

	public static void register() {
		CombatRollBridge.register();
	}

	private CombatRollHook() {
	}
}
