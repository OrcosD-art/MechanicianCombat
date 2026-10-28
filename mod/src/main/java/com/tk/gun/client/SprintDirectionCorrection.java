package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;

/**
 * Aligns the body with the movement key during decoupled sprint/dash movement.
 *
 * <p>The camera keeps its own yaw. W/A/S/D are converted into a camera-relative
 * world direction, the body is snapped to that direction, and the input is then
 * expressed as forward movement. This avoids both backpedalling while facing
 * forward and the older gradual A/D steering behaviour.
 */
public final class SprintDirectionCorrection {
	private static final float INPUT_EPSILON = 0.05F;
	/** Max body yaw change per tick while steering in the post-roll dash window. */
	private static final float DASH_STEER_DEGREES_PER_TICK = 4.0F;
	private static boolean directionLatched;
	private static int lastForwardSign;
	private static int lastSidewaysSign;

	public static boolean correct(MinecraftClient client, KeyboardInput input) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null
				|| !GunCamera.active(client)
				|| !ShoulderCouplingState.isSprintDirectionActive(client)
				|| GunCamera.aiming(client)
				|| !TkGun.holdingGun(client.player)) {
			resetDirection();
			return false;
		}

		ClientPlayerEntity player = client.player;
		int id = player.getId();
		if (GunClientState.focusish(id)) {
			resetDirection();
			return false;
		}
		GunClientState.Entry state = GunClientState.get(id);
		if (state != null && switch (state.state) {
			case SLIDE, SLIDE_READY, OVERHEAT, OVER, INSPECT -> true;
			default -> false;
		}) {
			resetDirection();
			return false;
		}

		float forward = input.movementForward;
		float sideways = input.movementSideways;
		if (Math.abs(forward) < INPUT_EPSILON && Math.abs(sideways) < INPUT_EPSILON) {
			resetDirection();
			return false;
		}

		int forwardSign = Integer.signum(Math.round(forward));
		int sidewaysSign = Integer.signum(Math.round(sideways));
		boolean directionChanged = !directionLatched
				|| forwardSign != lastForwardSign
				|| sidewaysSign != lastSidewaysSign;
		float cameraYaw = movementCameraYaw(client);
		float targetYaw = MathHelper.wrapDegrees(
				(float) (cameraYaw - Math.toDegrees(Math.atan2(sideways, forward))));
		boolean dashSteering = GunClientState.dashing(id)
				&& !GunClientState.rolling(id);
		if (dashSteering) {
			float currentYaw = player.getYaw();
			float delta = MathHelper.wrapDegrees(targetYaw - currentYaw);
			float step = MathHelper.clamp(delta,
					-DASH_STEER_DEGREES_PER_TICK, DASH_STEER_DEGREES_PER_TICK);
			float steeredYaw = MathHelper.wrapDegrees(currentYaw + step);
			player.setYaw(steeredYaw);
			player.setBodyYaw(steeredYaw);
			directionLatched = true;
			lastForwardSign = forwardSign;
			lastSidewaysSign = sidewaysSign;
		} else if (directionChanged) {

			// Lock the movement direction only when the key combination changes.
			// While those keys stay held, turning the camera cannot rotate the body.
			player.setYaw(targetYaw);
			player.setBodyYaw(targetYaw);
			directionLatched = true;
			lastForwardSign = forwardSign;
			lastSidewaysSign = sidewaysSign;
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
	 * The camera remains untouched, while the held movement combination is
	 * latched so the following sprint tick keeps the same body orientation.
	 */
	public static void alignForRoll(MinecraftClient client, float directionX, float directionZ) {
		if (client == null || client.player == null || client.world == null
				|| !GunCamera.active(client)) {
			resetDirection();
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
		directionLatched = true;
		lastForwardSign = keyDirection(client.options.forwardKey,
				client.options.backKey);
		lastSidewaysSign = keyDirection(client.options.leftKey,
				client.options.rightKey);
	}

	private static void resetDirection() {
		directionLatched = false;
		lastForwardSign = 0;
		lastSidewaysSign = 0;
	}

	private static int keyDirection(KeyBinding positive, KeyBinding negative) {
		return (positive.isPressed() ? 1 : 0) - (negative.isPressed() ? 1 : 0);
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
