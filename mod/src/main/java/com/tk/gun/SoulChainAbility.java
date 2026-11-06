package com.tk.gun;

import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/** C3 soul-chain damage propagation and visual event dispatch. */
public final class SoulChainAbility {
	private static final double LINK_RADIUS = 5.0D;
	private static final double LINK_CHANCE = 0.35D;
	private static final float SECONDARY_DAMAGE = 0.70F;
	private static final float TERTIARY_DAMAGE = 0.50F;
	private static final int EFFECT_TICKS = 30;
	private static boolean resolving;

	private SoulChainAbility() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(
				SoulChainAbility::afterDamage);
	}

	private static void afterDamage(LivingEntity target, DamageSource source,
									float baseDamage, float damageTaken,
									boolean blocked) {
		if (resolving || blocked || damageTaken <= 0.0F
				|| !(source.getAttacker() instanceof ServerPlayerEntity player)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3)
				|| player.getRandom().nextDouble() >= LINK_CHANCE) {
			return;
		}

		ServerWorld world = (ServerWorld) target.getWorld();
		LivingEntity second = nearest(world, target, player,
				List.of(target.getUuid(), player.getUuid()));
		if (second == null) {
			return;
		}
		LivingEntity third = nearest(world, second, player,
				List.of(target.getUuid(), second.getUuid(), player.getUuid()));

		Vec3d a = effectPoint(target);
		Vec3d b = effectPoint(second);
		Vec3d c = third == null ? Vec3d.ZERO : effectPoint(third);
		resolving = true;
		try {
			second.damage(player.getDamageSources().playerAttack(player),
					damageTaken * SECONDARY_DAMAGE);
			if (third != null) {
				third.damage(player.getDamageSources().playerAttack(player),
						damageTaken * TERTIARY_DAMAGE);
			}
		} finally {
			resolving = false;
		}

		com.tk.gun.net.SoulChainPayload payload =
				new com.tk.gun.net.SoulChainPayload(
						target.getId(), second.getId(),
						third == null ? -1 : third.getId(),
						a.x, a.y, a.z,
						b.x, b.y, b.z,
						c.x, c.y, c.z,
						third != null, EFFECT_TICKS);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer.squaredDistanceTo(a) <= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static LivingEntity nearest(ServerWorld world, LivingEntity center,
										ServerPlayerEntity owner,
										List<java.util.UUID> excluded) {
		Vec3d position = center.getPos();
		Box bounds = new Box(
				position.x - LINK_RADIUS,
				position.y - LINK_RADIUS,
				position.z - LINK_RADIUS,
				position.x + LINK_RADIUS,
				position.y + LINK_RADIUS,
				position.z + LINK_RADIUS);
		return world.getEntitiesByClass(LivingEntity.class, bounds, entity ->
						entity != owner
								&& entity.isAlive()
								&& entity.canHit()
								&& entity.squaredDistanceTo(position)
										<= LINK_RADIUS * LINK_RADIUS
								&& !excluded.contains(entity.getUuid())
								&& CombatTargeting.isTargetable(entity))
				.stream()
				.min(Comparator.comparingDouble(entity ->
						entity.squaredDistanceTo(position)))
				.orElse(null);
	}

	private static Vec3d effectPoint(Entity entity) {
		return new Vec3d(entity.getX(),
				entity.getY() + entity.getHeight() * 0.55D,
				entity.getZ());
	}
}
