package com.tk.gun.api;

import com.tk.gun.TkGun;

/** Definition of the Abyssal Ancient Cannon, using the gun-only source rig. */
public final class AbyssalAncientCannonGun {
	public static final GunDefinition DEFINITION = GunDefinition.builder(
					TkGun.id("abyssal_ancient_cannon"))
			.magazines(6, 10)
			.shotAnimationMax(10)
			.timing(TkGun.FIRE_TICKS, TkGun.SHOT_CYCLE_TICKS,
					TkGun.FOCUS_CYCLE_STEP, TkGun.FOCUS_CYCLE_MIN)
			.baseDamage(TkGun.BULLET_DAMAGE)
			.assets(
					TkGun.id("abyssal_ancient_cannon_rig"),
					TkGun.id("abyssal_ancient_cannon_rig"),
					TkGun.id("textures/entity/abyssal_ancient_cannon_gun.png"))
			.textureSize(64, 64)
			.translations(
					"item.tkgun.abyssal_ancient_cannon",
					"tooltip.tkgun.abyssal_ancient_cannon.description")
			.build();

	private AbyssalAncientCannonGun() {
	}
}
