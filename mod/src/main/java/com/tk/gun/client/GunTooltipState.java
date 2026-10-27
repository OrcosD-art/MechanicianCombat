package com.tk.gun.client;

import net.minecraft.util.Identifier;

/** 标记当前正在绘制的 tooltip 是否属于破垒重炮。 */
public final class GunTooltipState {
	public static final Identifier TEXTURE =
			Identifier.of("tkgun", "gun_tooltip");

	private static boolean gunTooltip;

	public static void set(boolean value) {
		gunTooltip = value;
	}

	public static boolean isGunTooltip() {
		return gunTooltip;
	}

	private GunTooltipState() {
	}
}
