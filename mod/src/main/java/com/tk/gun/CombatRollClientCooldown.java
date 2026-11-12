package com.tk.gun;

import net.combat_roll.internals.RollingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Clears Combat Roll's client-authoritative cooldown when the reset item is used. */
public final class CombatRollClientCooldown {
	public static void clear(PlayerEntity player) {
		if (!CombatRollHook.isInstalled()
				|| !(player instanceof RollingEntity rolling)) {
			return;
		}
		if (rolling.getRollManager() instanceof CombatRollCooldownAccess access) {
			access.mechaniciancombat$setTimeSinceLastRoll(0);
			access.mechaniciancombat$setCooldownProgress(0.0F);
			access.mechaniciancombat$setAvailableRolls(access.mechaniciancombat$maxRolls());
		}
	}

	private CombatRollClientCooldown() {
	}
}
