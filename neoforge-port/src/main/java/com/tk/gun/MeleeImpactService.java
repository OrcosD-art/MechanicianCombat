package com.tk.gun;

import com.tk.gun.net.MeleeImpactPayload;
import com.tk.gun.net.CameraShakePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.SimpleParticleType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Server-side close-combat hitstop timing and delayed knockback.
 *
 * <p>Only an actual successful damage call reaches this service. The caller
 * therefore owns hit filtering and invulnerability-frame checks.</p>
 */
public final class MeleeImpactService {
	public static final int ATK_1_TICKS = 3;
	public static final int ATK_2_TICKS = 3;
	public static final int ATK_3_TICKS = 4;
	public static final int ATK_4_TICKS = 5;
	public static final int HOLD_OVER_TICKS = 4;
	public static final int DASH_TICKS = 4;
	public static final int RANGED_IMPACT_TICKS = ATK_3_TICKS;
	public static final float GUN_SHOT_SHAKE_SCALE = 0.7F;
	public static final int CRIT_BONUS_TICKS = 2;
	public static final int MAX_HITSTOP_TICKS = 8;
	public static final double KNOCKBACK_STRENGTH = 0.35D;

	private static final Map<Integer, PendingKnockback> PENDING_KNOCKBACK =
			new HashMap<>();

	private MeleeImpactService() {
	}

	public static int ticksForStep(int step) {
		return switch (step) {
			case 1 -> ATK_1_TICKS;
			case 2 -> ATK_2_TICKS;
			case 3 -> ATK_3_TICKS;
			default -> ATK_4_TICKS;
		};
	}

	public static void sendCameraShake(ServerPlayerEntity player, int ticks,
			boolean critical) {
		sendCameraShake(player, ticks, critical, 1.0F);
	}

	public static void sendCameraShake(ServerPlayerEntity player, int ticks,
			boolean critical, float intensityScale) {
		if (player == null || ticks <= 0) {
			return;
		}
		ServerPlayNetworking.send(player,
				new CameraShakePayload(ticks, critical, intensityScale));
	}

	public static void sendGunShotShake(ServerPlayerEntity player) {
		sendCameraShake(player, RANGED_IMPACT_TICKS, false,
				GUN_SHOT_SHAKE_SCALE);
	}

	public static void apply(ServerPlayerEntity attacker, TkGunServer.PState state,
			List<Entity> targets, Vec3d forward, int baseTicks, boolean critical,
			boolean lockAttacker, boolean cameraShake) {
		if (attacker.getServer() == null || targets == null || targets.isEmpty()) {
			return;
		}
		int ticks = Math.min(MAX_HITSTOP_TICKS,
				Math.max(1, baseTicks + (critical ? CRIT_BONUS_TICKS : 0)));
		int[] targetIds = new int[targets.size()];
		long now = attacker.getWorld().getTime();
		boolean impactSoundPlayed = false;
		for (int index = 0; index < targets.size(); index++) {
			Entity target = targets.get(index);
			targetIds[index] = target.getId();
			Vec3d center = target.getBoundingBox().getCenter();
			Vec3d outward = attacker.getEyePos().subtract(center);
			outward = new Vec3d(outward.x, 0.0D, outward.z);
			if (outward.lengthSquared() < 1.0E-6D) {
				double yaw = Math.toRadians(attacker.getYaw());
				outward = new Vec3d(Math.sin(yaw), 0.0D, -Math.cos(yaw));
			}
			outward = outward.normalize();
			double surfaceOffset = target.getWidth() * 0.5D + 0.2D;
			Vec3d impactPos = new Vec3d(
					center.x + outward.x * surfaceOffset,
					target.getY() + target.getHeight() * 0.62D,
					center.z + outward.z * surfaceOffset);
			SimpleParticleType impactEffect = TkGunParticles.MELEE_HIT_EFFECTS[
					attacker.getRandom().nextInt(
							TkGunParticles.MELEE_HIT_EFFECTS.length)];
			attacker.getServerWorld().spawnParticles(
					impactEffect,
					impactPos.x, impactPos.y, impactPos.z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (!impactSoundPlayed) {
				impactSoundPlayed = true;
				attacker.getServerWorld().playSound(null, impactPos.x,
						impactPos.y, impactPos.z,
						SoundEvent.of(TkGun.SND_MELEE_HIT),
						SoundCategory.MASTER, 2.0F, 2.0F);
			}
			if (target instanceof LivingEntity living) {
				PENDING_KNOCKBACK.compute(living.getId(), (id, previous) -> {
					long releaseAt = now + ticks;
					if (previous != null) {
						releaseAt = Math.max(releaseAt, previous.releaseAt());
					}
					return new PendingKnockback(living, forward, KNOCKBACK_STRENGTH,
							releaseAt);
				});
			}
		}

		if (lockAttacker) {
			pauseMeleeTimeline(state, ticks);
		}

		MeleeImpactPayload payload = new MeleeImpactPayload(
				attacker.getId(), targetIds, ticks, critical, lockAttacker, cameraShake);
		for (ServerPlayerEntity viewer :
				attacker.getServer().getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(viewer, payload);
		}
		TkGun.LOGGER.info("[mechaniciancombat] melee impact: stepTicks={} critical={} targets={} "
						+ "lockAttacker={} player={}",
				ticks, critical, targets.size(), lockAttacker,
				attacker.getName().getString());
	}

	private static void pauseMeleeTimeline(TkGunServer.PState state, int ticks) {
		state.stateStarted += ticks;
		if (state.stateUntil > 0L) {
			state.stateUntil += ticks;
		}
		if (state.meleeInputHeld && state.meleeInputPressedAt > 0L) {
			state.meleeInputPressedAt += ticks;
		}
	}

	/** Called once per server tick, after normal entity/player ticking. */
	public static void tick(MinecraftServer server) {
		if (PENDING_KNOCKBACK.isEmpty()) {
			return;
		}
		if (server.getPlayerManager().getPlayerList().isEmpty()) {
			PENDING_KNOCKBACK.clear();
			return;
		}
		Iterator<Map.Entry<Integer, PendingKnockback>> iterator =
				PENDING_KNOCKBACK.entrySet().iterator();
		while (iterator.hasNext()) {
			PendingKnockback pending = iterator.next().getValue();
			LivingEntity target = pending.target();
			if (target.isRemoved() || !target.isAlive()) {
				iterator.remove();
				continue;
			}
			long now = target.getWorld().getTime();
			if (now < pending.releaseAt()) {
				Vec3d velocity = target.getVelocity();
				target.setVelocity(0.0D, velocity.y, 0.0D);
				target.velocityModified = true;
				continue;
			}
			iterator.remove();
			Vec3d direction = pending.direction();
			target.takeKnockback(pending.strength(), direction.x, direction.z);
		}
	}

	public static void clear() {
		PENDING_KNOCKBACK.clear();
	}

	private record PendingKnockback(LivingEntity target, Vec3d direction,
									double strength, long releaseAt) {
	}
}
