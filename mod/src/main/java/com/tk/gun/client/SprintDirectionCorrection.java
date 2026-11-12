package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Aligns the body with the movement key during decoupled sprint/dash movement.
 *
 * <p>The camera keeps its own yaw. W/A/S/D are converted into a camera-relative
 * world direction, the body continuously steers toward that direction, and the
 * input is then expressed as forward movement.
 */
public final class SprintDirectionCorrection {
	private static final float INPUT_EPSILON = 0.05F;
	/** Normal sprint steering: four times the original dash steering rate. */
	private static final float NORMAL_SPRINT_STEER_DEGREES_PER_TICK = 16.0F;
	/** Post-roll acceleration steering: twice the original dash steering rate. */
	private static final float DASH_STEER_DEGREES_PER_TICK = 8.0F;

	public static boolean correct(MinecraftClient client, KeyboardInput input) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null
				|| !GunCamera.active(client)
				|| WarBannerSkillClientState.isActive(client.player.getId())
				|| !ShoulderCouplingState.usesCameraRelativeSteering(client)
				|| GunCamera.aiming(client)
				|| !TkGun.holdingGun(client.player)) {
			return false;
		}

		ClientPlayerEntity player = client.player;
		int id = player.getId();
		if (GunClientState.focusish(id)) {
			return false;
		}
		GunClientState.Entry state = GunClientState.get(id);
		if (state != null && switch (state.state) {
			case MELEE, SLIDE, SLIDE_READY, OVERHEAT, OVER, INSPECT,
					SKILL_JUMP, FALL_ATTACK, FALL_ATTACK_RECOVERY -> true;
			default -> false;
		}) {
			return false;
		}

		float forward = input.movementForward;
		float sideways = input.movementSideways;
		if (Math.abs(forward) < INPUT_EPSILON && Math.abs(sideways) < INPUT_EPSILON) {
			return false;
		}

		float cameraYaw = movementCameraYaw(client);
		float targetYaw = MathHelper.wrapDegrees(
				(float) (cameraYaw - Math.toDegrees(Math.atan2(sideways, forward))));
		boolean rolling = GunClientState.rolling(id);
		if (!rolling) {
			float currentYaw = player.getYaw();
			float delta = MathHelper.wrapDegrees(targetYaw - currentYaw);
			float steerRate = GunClientState.dashing(id)
					? DASH_STEER_DEGREES_PER_TICK
					: NORMAL_SPRINT_STEER_DEGREES_PER_TICK;
			float step = MathHelper.clamp(delta,
					-steerRate, steerRate);
			float steeredYaw = MathHelper.wrapDegrees(currentYaw + step);
			player.setYaw(steeredYaw);
			player.setBodyYaw(steeredYaw);
		}
		player.setHeadYaw(cameraYaw);

		// Re-express the pressed direction as forward movement in the new body
		// frame. Vanilla normalises diagonal input later, so one unit is enough.
		input.movementForward = 1.0F;
		input.movementSideways = 0.0F;
		return true;
	}

	/**
	 * Turns the body toward the physical roll direction on the key-press frame.
	 * The camera remains untouched.
	 */
	public static void alignForRoll(MinecraftClient client, float directionX, float directionZ) {
		if (client == null || client.player == null || client.world == null
				|| !GunCamera.active(client)) {
			return;
		}
		float cameraYaw = movementCameraYaw(client);
		double horizontalSq = directionX * directionX + directionZ * directionZ;
		float targetYaw = horizontalSq < 1.0E-6D
				? cameraYaw
				: MathHelper.wrapDegrees(
						(float) Math.toDegrees(Math.atan2(-directionX, directionZ)));
		ClientPlayerEntity player = client.player;
		player.setYaw(targetYaw);
		player.setBodyYaw(targetYaw);
		player.setHeadYaw(cameraYaw);
		player.prevYaw = targetYaw;
	}

	private static float movementCameraYaw(MinecraftClient client) {
		if (FallbackShoulderCamera.isFallbackActive(client)) {
			return FallbackShoulderCamera.cameraYaw();
		}
		Float shoulderYaw = ShoulderSurfBridge.shoulderCameraYaw();
		if (shoulderYaw != null) {
			return MathHelper.wrapDegrees(shoulderYaw);
		}
		return MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw());
	}

	private SprintDirectionCorrection() {
	}
}
