package com.tk.gun;

import com.tk.gun.net.CameraShakePayload;
import com.tk.gun.net.MeleeImpactPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Immediate close-combat impact feedback. This service never pauses entity,
 * camera, input or animation timelines and applies knockback immediately.
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
	public static final int MAX_FEEDBACK_TICKS = 8;
	public static final double KNOCKBACK_STRENGTH = 0.35D;

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

	public static void apply(ServerPlayerEntity attacker,
			List<Entity> targets, Vec3d forward, int baseTicks,
			boolean critical, boolean cameraShake) {
		if (attacker.getServer() == null || targets == null
				|| targets.isEmpty()) {
			return;
		}
		int feedbackTicks = Math.min(MAX_FEEDBACK_TICKS,
				Math.max(1, baseTicks + (critical ? CRIT_BONUS_TICKS : 0)));
		int[] targetIds = new int[targets.size()];
		int[] effectIds = new int[targets.size()];
		boolean impactSoundPlayed = false;
		for (int index = 0; index < targets.size(); index++) {
			Entity target = targets.get(index);
			targetIds[index] = target.getId();
			effectIds[index] = attacker.getRandom().nextInt(
					TkGunParticles.MELEE_HIT_EFFECTS.length);
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
			if (!impactSoundPlayed) {
				impactSoundPlayed = true;
				attacker.getServerWorld().playSound(null, impactPos.x,
						impactPos.y, impactPos.z,
						SoundEvent.of(TkGun.SND_MELEE_HIT),
						SoundCategory.MASTER, 2.0F, 2.0F);
			}
			if (target instanceof LivingEntity living) {
				living.takeKnockback(KNOCKBACK_STRENGTH,
						forward.x, forward.z);
			}
		}

		MeleeImpactPayload payload = new MeleeImpactPayload(
				attacker.getId(), targetIds, effectIds, feedbackTicks,
				critical, cameraShake);
		for (ServerPlayerEntity viewer :
				attacker.getServer().getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(viewer, payload);
		}
		TkGun.LOGGER.info(
				"[mechaniciancombat] melee impact: feedbackTicks={} critical={} targets={} player={}",
				feedbackTicks, critical, targets.size(),
				attacker.getName().getString());
	}
}
