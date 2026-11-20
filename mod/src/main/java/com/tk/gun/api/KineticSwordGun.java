package com.tk.gun.api;

import com.tk.gun.TkGun;

/** Definition of the Kinetic Sword, registered through the shared weapon state machine. */
public final class KineticSwordGun {
	public static final GunDefinition DEFINITION = GunDefinition.builder(
					TkGun.id("kinetic_sword"))
			.magazines(1, 1)
			.shotAnimationMax(6)
			.timing(1, 1, 0, 1)
			.baseDamage(TkGun.KINETIC_SWORD_DAMAGE)
			.assets(
					TkGun.id("kinetic_sword_rig"),
					TkGun.id("kinetic_sword_rig"),
					TkGun.id("textures/entity/kinetic_sword_sword.png"))
			.textureSize(64, 64)
			.meleeOnly()
			.translations(
					"item.mechaniciancombat.kinetic_sword",
					"tooltip.mechaniciancombat.kinetic_sword.description")
			.build();

	private KineticSwordGun() {
	}
}
