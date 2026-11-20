package com.tk.gun;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Forced skill knockback with one path hit per entity. */
public final class FortressSkillKnockbackService {
	private static final int TRAVEL_TICKS = 15;
	private static final double STEP_DISTANCE =
			TkGun.FORTRESS_SKILL_KNOCKBACK_DISTANCE / TRAVEL_TICKS;
	private static final double DAMAGE_HALF_EXTENT = 1.5D;
	private static final List<Push> PUSHES = new ArrayList<>();

	public static void start(ServerPlayerEntity attacker, Entity target,
			Vec3d direction, float delayedDamage) {
		if (!(target.getWorld() instanceof ServerWorld) || direction.lengthSquared() < 1.0E-6D) {
			return;
		}
		Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z).normalize();
		PUSHES.removeIf(push -> push.target() == target);
		PUSHES.add(new Push(attacker, target, horizontal, delayedDamage,
				new HashSet<>(), 0.0D));
	}

	public static void tick(MinecraftServer server) {
		if (PUSHES.isEmpty()) {
			return;
		}
		Iterator<Push> iterator = PUSHES.iterator();
		while (iterator.hasNext()) {
			Push push = iterator.next();
			Entity target = push.target();
			if (target.isRemoved() || !target.isAlive()
					|| !(target.getWorld() instanceof ServerWorld world)) {
				iterator.remove();
				continue;
			}
			Vec3d from = target.getPos();
			Vec3d step = push.direction().multiply(STEP_DISTANCE);
			Vec3d next = from.add(step);
			Box shiftedBounds = target.getBoundingBox().offset(step);
			if (!world.isSpaceEmpty(target, shiftedBounds)) {
				applyDelayedDamage(world, push, target);
				iterator.remove();
				continue;
			}
			damagePath(world, push, from, next, target.getHeight() * 0.5D);
			target.refreshPositionAfterTeleport(next);
			Vec3d velocity = target.getVelocity();
			target.setVelocity(0.0D, velocity.y, 0.0D);
			target.velocityModified = true;
			spawnTrail(world, from, next, target.getHeight() * 0.5D);
			double travelled = push.travelled() + STEP_DISTANCE;
			if (travelled + 1.0E-4D >= TkGun.FORTRESS_SKILL_KNOCKBACK_DISTANCE) {
				applyDelayedDamage(world, push, target);
				iterator.remove();
			} else {
				push.setTravelled(travelled);
			}
		}
	}

	private static void applyDelayedDamage(ServerWorld world, Push push, Entity target) {
		if (!target.isAlive() || target.isRemoved()
				|| target.getWorld() != world) {
			return;
		}
		if (target instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
		}
		target.damage(push.attacker().getDamageSources().playerAttack(
				push.attacker()), push.delayedDamage());
	}

	private static void damagePath(ServerWorld world, Push push,
			Vec3d from, Vec3d to, double yOffset) {
		Vec3d lineStart = from.add(0.0D, yOffset, 0.0D);
		Vec3d lineEnd = to.add(0.0D, yOffset, 0.0D);
		Box startBox = new Box(
				lineStart.x - DAMAGE_HALF_EXTENT,
				lineStart.y - DAMAGE_HALF_EXTENT,
				lineStart.z - DAMAGE_HALF_EXTENT,
				lineStart.x + DAMAGE_HALF_EXTENT,
				lineStart.y + DAMAGE_HALF_EXTENT,
				lineStart.z + DAMAGE_HALF_EXTENT);
		Box endBox = new Box(
				lineEnd.x - DAMAGE_HALF_EXTENT,
				lineEnd.y - DAMAGE_HALF_EXTENT,
				lineEnd.z - DAMAGE_HALF_EXTENT,
				lineEnd.x + DAMAGE_HALF_EXTENT,
				lineEnd.y + DAMAGE_HALF_EXTENT,
				lineEnd.z + DAMAGE_HALF_EXTENT);
		Box sweptHitBox = startBox.union(endBox);
		float damage = TkGun.fortressSkillDamage(3)
				* (float) TkGun.FORTRESS_SKILL_PATH_DAMAGE_MULTIPLIER;
		for (Entity entity : world.getOtherEntities(push.target(), sweptHitBox,
				candidate -> candidate != push.attacker()
						&& candidate.isAlive() && candidate.canHit()
						&& !candidate.isSpectator()
						&& CombatTargeting.isTargetable(candidate))) {
			if (!push.hitIds().add(entity.getId())
					|| !sweptHitBox.intersects(entity.getBoundingBox())) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				living.timeUntilRegen = 0;
			}
			entity.damage(push.attacker().getDamageSources().playerAttack(
					push.attacker()), damage);
		}
	}

	private static void spawnTrail(ServerWorld world, Vec3d from, Vec3d to, double yOffset) {
		for (int index = 0; index < 3; index++) {
			double t = (index + 1.0D) / 4.0D;
			world.spawnParticles(ParticleTypes.FLAME,
					from.x + (to.x - from.x) * t,
					from.y + yOffset + (to.y - from.y) * t,
					from.z + (to.z - from.z) * t,
					2, 0.12D, 0.08D, 0.12D, 0.01D);
		}
	}

	private static final class Push {
		private final ServerPlayerEntity attacker;
		private final Entity target;
		private final Vec3d direction;
		private final float delayedDamage;
		private final Set<Integer> hitIds;
		private double travelled;

		private Push(ServerPlayerEntity attacker, Entity target, Vec3d direction,
				float delayedDamage, Set<Integer> hitIds, double travelled) {
			this.attacker = attacker;
			this.target = target;
			this.direction = direction;
			this.delayedDamage = delayedDamage;
			this.hitIds = hitIds;
			this.travelled = travelled;
		}

		ServerPlayerEntity attacker() {
			return attacker;
		}

		Entity target() {
			return target;
		}

		Vec3d direction() {
			return direction;
		}

		float delayedDamage() {
			return delayedDamage;
		}

		Set<Integer> hitIds() {
			return hitIds;
		}

		double travelled() {
			return travelled;
		}

		void setTravelled(double travelled) {
			this.travelled = travelled;
		}
	}

	private FortressSkillKnockbackService() {
	}
}
