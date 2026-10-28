package com.tk.gun.api;

import com.tk.gun.TkGun;

/** Definition of Veritas_et_Justitia, using its gun-only source rig. */
public final class VeritasEtJustitiaGun {
	public static final GunDefinition DEFINITION = GunDefinition.builder(
					TkGun.id("veritas_et_justitia"))
			.magazines(TkGun.MAG_CAPACITY_HIP, TkGun.MAG_CAPACITY_FOCUS)
			.shotAnimationMax(TkGun.SHOT_ANIM_MAX)
			.timing(TkGun.FIRE_TICKS, TkGun.SHOT_CYCLE_TICKS,
					TkGun.FOCUS_CYCLE_STEP, TkGun.FOCUS_CYCLE_MIN)
			.baseDamage(TkGun.BULLET_DAMAGE)
			.assets(
					TkGun.id("veritas_et_justitia_rig"),
					TkGun.id("veritas_et_justitia_rig"),
					TkGun.id("textures/entity/veritas_et_justitia_gun.png"))
			.textureSize(64, 64)
			.translations(
					"item.tkgun.veritas_et_justitia",
					"tooltip.tkgun.veritas_et_justitia.description")
			.build();

	private VeritasEtJustitiaGun() {
	}
}
