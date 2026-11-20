package com.tk.gun;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

/** Shared definition of ordinary free flight for gun gameplay. */
public final class GunAirborneRules {
	public static final float IDLE_AFTER_DISTANCE = 1.0F;

	public static boolean isOrdinaryAirborne(PlayerEntity player) {
		return player.isAlive()
				&& !player.isOnGround()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isSpectator()
				&& !player.hasVehicle()
				&& !player.noClip
				&& !player.isTouchingWater()
				&& !player.isInLava()
				&& !player.isClimbing()
				&& !player.isUsingRiptide()
				&& !player.isSleeping()
				&& !player.hasStatusEffect(StatusEffects.LEVITATION);
	}

	private GunAirborneRules() {
	}
}
