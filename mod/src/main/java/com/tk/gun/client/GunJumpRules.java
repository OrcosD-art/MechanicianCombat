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
		GunClientState.Entry entry = GunClientState.get(client.player.getId());
		if (entry != null && (entry.state == TkGun.State.IDLE
				|| entry.state == TkGun.State.RUN)) {
			return !client.player.isSprinting() && !client.player.isUsingItem()
					&& !GunClientState.dashing(client.player.getId())
					&& !GunClientState.overheated(client.player.getId());
		}
		if (client.player.isSprinting() || client.player.isUsingItem()
				|| GunClientState.dashing(client.player.getId())
				|| GunClientState.focusish(client.player.getId())
				|| GunClientState.overheated(client.player.getId())) {
			return false;
		}
		return entry == null;
	}

	private GunJumpRules() {
	}
}
