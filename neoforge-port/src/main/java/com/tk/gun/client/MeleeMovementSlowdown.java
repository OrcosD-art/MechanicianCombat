package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.util.math.MathHelper;

/**
 * Better Combat-style movement input curve for Veritas close combat.
 *
 * <p>This scales the movement input, not the movement-speed attribute, so
 * vanilla acceleration and inertia remain unchanged.</p>
 */
public final class MeleeMovementSlowdown {
	/** Lowest movement input during a melee action: a 70% reduction. */
	public static final float MIN_MULTIPLIER = 0.3F;
	private static final float EASING_END = 0.98F;

	public static float inputMultiplier(int playerId) {
		GunClientState.Entry entry = GunClientState.get(playerId);
		if (entry == null || entry.state != TkGun.State.MELEE
				|| entry.meleeReturning || entry.total <= 0
				|| entry.elapsed >= entry.total) {
			return 1.0F;
		}
		float progress = MathHelper.clamp(
				entry.elapsed / (float) entry.total, 0.0F, 1.0F);
		if (progress >= EASING_END) {
			return 1.0F;
		}
		double curveInput = progress <= 0.5F
				? progress * 2.0D
				: 1.0D - (progress - 0.5D) * 2.0D;
		double eased = easeOutCubic(curveInput);
		return (float) (1.0D - (1.0D - MIN_MULTIPLIER) * eased);
	}

	private static double easeOutCubic(double value) {
		double inverse = 1.0D - MathHelper.clamp(value, 0.0D, 1.0D);
		return 1.0D - inverse * inverse * inverse;
	}

	private MeleeMovementSlowdown() {
	}
}
