package com.tk.gun;

import com.tk.gun.entity.BoltProjectileEntity;
import com.tk.gun.entity.BulletEntity;
import com.tk.gun.entity.MoltenGolemEntity;
import com.tk.gun.entity.MoltenTridentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/** Shared exclusions for MechanicianCombat attacks, projectiles, and locks. */
public final class CombatTargeting {
	public static final String BACKPACK_PROJECTILE_TAG = "tkgun_backpack_projectile";

	private static final RegistryKey<DamageType> GUN_DAMAGE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, TkGun.id("bolt"));
	private static final RegistryKey<DamageType> SONIC_DAMAGE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, TkGun.id("sonic"));

	public static boolean isGolem(Entity entity) {
		return entity instanceof MoltenGolemEntity;
	}

	public static boolean isTargetable(Entity entity) {
		return entity != null && !isGolem(entity);
	}

	public static boolean isTkGunDamage(DamageSource source) {
		if (source == null) {
			return false;
		}
		if (source.isOf(GUN_DAMAGE) || source.isOf(SONIC_DAMAGE)) {
			return true;
		}
		Entity direct = source.getSource();
		Entity attacker = source.getAttacker();
		if (attacker instanceof PlayerEntity player && TkGun.holdingGun(player)) {
			return true;
		}
		return direct instanceof BulletEntity
				|| direct instanceof BoltProjectileEntity
				|| direct instanceof MoltenTridentEntity
				|| (direct != null
						&& direct.getCommandTags().contains(BACKPACK_PROJECTILE_TAG));
	}

	private CombatTargeting() {
	}
}
