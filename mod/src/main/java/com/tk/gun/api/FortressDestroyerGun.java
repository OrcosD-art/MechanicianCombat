package com.tk.gun.api;

import com.tk.gun.TkGun;

/** Built-in definition. Its values reproduce the r396 Fortress Destroyer Gun exactly. */
public final class FortressDestroyerGun {
	public static final GunDefinition DEFINITION = new GunDefinition(
			TkGun.id("bowgun"),
			6,
			10,
			10,
			TkGun.FIRE_TICKS,
			TkGun.SHOT_CYCLE_TICKS,
			TkGun.FOCUS_CYCLE_STEP,
			TkGun.FOCUS_CYCLE_MIN,
			TkGun.BULLET_DAMAGE,
			TkGun.id("bowgun_rig"),
			TkGun.id("bowgun_rig"),
			TkGun.id("textures/entity/bowgun_gun.png"),
			64,
			64,
			"item.tkgun.bowgun",
			"tooltip.tkgun.bowgun.description"
	);

	private FortressDestroyerGun() {
	}
}
