package com.tk.gun.client;

import com.tk.gun.net.CameraShakePayload;
import com.tk.gun.net.MeleeImpactPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;

/** Client feedback for melee impacts: slash effects and camera shake only. */
public final class MeleeImpactClientState {
	private static ShakeState cameraShake;

	private MeleeImpactClientState() {
	}

	public static void apply(MeleeImpactPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		Entity attacker = client.world.getEntityById(payload.attackerId());
		for (int index = 0; index < payload.targetIds().length; index++) {
			Entity target = client.world.getEntityById(
					payload.targetIds()[index]);
			if (target == null || !target.isAlive()) {
				continue;
			}
			int effectId = index < payload.effectIds().length
					? payload.effectIds()[index] : 0;
			SlashHitClientState.spawn(attacker, target, effectId);
		}
		if (payload.cameraShake() && client.player != null
				&& client.player.getId() == payload.attackerId()
				&& payload.ticks() > 0) {
			startCameraShake(payload.ticks(), payload.critical());
		}
	}

	public static void apply(CameraShakePayload payload) {
		if (payload.ticks() > 0) {
			startCameraShake(payload.ticks(), payload.critical(),
					payload.intensityScale());
		}
	}

	private static void startCameraShake(int ticks, boolean critical) {
		startCameraShake(ticks, critical, 1.0F);
	}

	private static void startCameraShake(int ticks, boolean critical,
			float intensityScale) {
		float intensity = ticks <= 1 ? 0.3F : ticks <= 3 ? 0.6F : 1.0F;
		if (critical) {
			intensity *= 1.2F;
		}
		intensity *= Math.max(0.0F, intensityScale);
		cameraShake = new ShakeState(ticks + 1, ticks + 1, intensity);
	}

	public static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			clear();
			return;
		}
		SlashHitClientState.tick();
		if (cameraShake != null && --cameraShake.remainingTicks <= 0) {
			cameraShake = null;
		}
	}

	public static void applyCameraShake(MatrixStack matrices, float tickDelta) {
		if (cameraShake == null || cameraShake.remainingTicks <= 0
				|| cameraShake.totalTicks <= 0) {
			return;
		}
		float progress = Math.max(0.0F,
				(cameraShake.remainingTicks - tickDelta)
						/ cameraShake.totalTicks);
		float magnitude = cameraShake.intensity * progress;
		float elapsed = cameraShake.totalTicks
				- cameraShake.remainingTicks + tickDelta;
		float shakeX = (float) (Math.sin(elapsed * 7.0F)
				* magnitude * 0.5F);
		float shakeZ = (float) (Math.sin(elapsed * 9.0F) * magnitude);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(shakeX));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shakeZ));
	}

	public static void clear() {
		SlashHitClientState.clear();
		cameraShake = null;
	}

	private static final class ShakeState {
		private int remainingTicks;
		private final int totalTicks;
		private final float intensity;

		private ShakeState(int remainingTicks, int totalTicks,
				float intensity) {
			this.remainingTicks = remainingTicks;
			this.totalTicks = totalTicks;
			this.intensity = intensity;
		}
	}
}
