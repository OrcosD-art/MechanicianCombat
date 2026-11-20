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
import java.util.ArrayList;
import java.util.List;

/** C3 soul-chain damage propagation and visual event dispatch. */
public final class SoulChainAbility {
	private static final double LINK_DIAMETER = 10.0D;
	private static final double LINK_RADIUS = LINK_DIAMETER * 0.5D;
	private static final double LINK_CHANCE = 0.40D;
	private static final float SECONDARY_DAMAGE = 0.70F;
	private static final float TERTIARY_DAMAGE = 0.50F;
	private static final float MINIMUM_DAMAGE_FACTOR = 0.50F;
	private static final int NORMAL_MAX_TARGETS = 3;
	private static final int MARKED_MAX_TARGETS = 6;
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
		boolean marked = SoulAnchorMarks.isMarked(target);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3)
				|| !marked
						&& player.getRandom().nextDouble() >= LINK_CHANCE) {
			return;
		}

		ServerWorld world = (ServerWorld) target.getWorld();
		double radius = marked ? LINK_RADIUS * 2.0D : LINK_RADIUS;
		int maxTargets = marked
				? MARKED_MAX_TARGETS : NORMAL_MAX_TARGETS;
		List<LivingEntity> targets = new ArrayList<>(maxTargets);
		List<java.util.UUID> excluded = new ArrayList<>(maxTargets + 1);
		targets.add(target);
		excluded.add(target.getUuid());
		excluded.add(player.getUuid());
		LivingEntity current = target;
		while (targets.size() < maxTargets) {
			LivingEntity next = nearest(world, current, player,
					excluded, radius);
			if (next == null) {
				break;
			}
			targets.add(next);
			excluded.add(next.getUuid());
			current = next;
		}
		if (targets.size() < 2) {
			return;
		}

		resolving = true;
		try {
			for (int index = 1; index < targets.size(); index++) {
				float factor = marked
						? Math.max(MINIMUM_DAMAGE_FACTOR,
								0.70F - 0.10F * (index - 1))
						: index == 1 ? SECONDARY_DAMAGE : TERTIARY_DAMAGE;
				targets.get(index).damage(
						player.getDamageSources().playerAttack(player),
						damageTaken * factor);
			}
		} finally {
			resolving = false;
		}

		List<com.tk.gun.net.SoulChainPayload.ChainNode> points =
				new ArrayList<>(targets.size());
		for (LivingEntity linked : targets) {
			Vec3d point = effectPoint(linked);
			points.add(new com.tk.gun.net.SoulChainPayload.ChainNode(
					linked.getId(), point.x, point.y, point.z));
		}
		com.tk.gun.net.SoulChainPayload payload =
				new com.tk.gun.net.SoulChainPayload(
						List.copyOf(points), EFFECT_TICKS);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer.squaredDistanceTo(target.getPos())
					<= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static LivingEntity nearest(ServerWorld world, LivingEntity center,
										ServerPlayerEntity owner,
										List<java.util.UUID> excluded,
										double radius) {
		Vec3d position = center.getPos();
		Box bounds = new Box(
				position.x - radius,
				position.y - radius,
				position.z - radius,
				position.x + radius,
				position.y + radius,
				position.z + radius);
		return world.getEntitiesByClass(LivingEntity.class, bounds, entity ->
						entity != owner
								&& entity.isAlive()
								&& entity.canHit()
								&& entity.squaredDistanceTo(position)
										<= radius * radius
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
