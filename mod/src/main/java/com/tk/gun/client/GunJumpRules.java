package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;

/** Shared client-side rule for jumping while carrying the gun. */
public final class GunJumpRules {
	public static boolean canJump(MinecraftClient client) {
		if (client.player == null || client.world == null || client.currentScreen != null
				|| !TkGun.holdingGun(client.player)) {
			return true;
		}
		return false;
	}

	private GunJumpRules() {
	}
}
