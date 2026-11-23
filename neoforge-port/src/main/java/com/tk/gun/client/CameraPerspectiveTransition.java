package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Smooths forced first/third-person changes around gun equip and unequip.
 *
 * <p>The vanilla perspective enum still changes immediately so entity and item
 * visibility stay authoritative. This controller only blends the resulting
 * camera pose from the last visible frame towards the new camera target.
 */
public final class CameraPerspectiveTransition {
	private static final double EQUIP_SECONDS = 0.28D;
	private static final double UNEQUIP_SECONDS = 0.24D;

	private static boolean active;
	private static long startedAtNanos;
	private static long durationNanos;
	private static Vec3d fromPosition = Vec3d.ZERO;
	private static float fromYaw;
	private static float fromPitch;
	private static double fromFov;
	private static double progress;

	public record Pose(Vec3d position, float yaw, float pitch) {
	}

	public static void beginEquip(MinecraftClient client) {
		begin(client, EQUIP_SECONDS, "equip");
	}

	public static void beginUnequip(MinecraftClient client) {
		begin(client, UNEQUIP_SECONDS, "unequip");
	}

	private static void begin(MinecraftClient client, double seconds,
			String reason) {
		if (client == null || client.gameRenderer == null) {
			return;
		}
		Camera camera = client.gameRenderer.getCamera();
		fromPosition = camera.getPos();
		fromYaw = camera.getYaw();
		fromPitch = camera.getPitch();
		fromFov = GunCamera.renderedFovDegrees();
		startedAtNanos = System.nanoTime();
		durationNanos = Math.max(1L,
				Math.round(seconds * 1_000_000_000.0D));
		progress = 0.0D;
		active = true;
		com.tk.gun.TkGun.LOGGER.info(
				"[mechaniciancombat] camera perspective transition {} started duration={}ms",
				reason, Math.round(seconds * 1000.0D));
	}

	/**
	 * Returns an interpolated pose, or {@code null} when the final target pose
	 * should be used unchanged.
	 */
	public static Pose apply(Vec3d targetPosition, float targetYaw,
			float targetPitch) {
		if (!active) {
			return null;
		}
		double raw = (System.nanoTime() - startedAtNanos)
				/ (double) durationNanos;
		if (!Double.isFinite(raw) || raw >= 1.0D) {
			active = false;
			progress = 1.0D;
			return null;
		}
		double t = MathHelper.clamp(raw, 0.0D, 1.0D);
		// Smoothstep removes the visible velocity pop at both endpoints while
		// retaining a compact 0.24-0.28 second travel time.
		progress = t * t * (3.0D - 2.0D * t);
		float yaw = fromYaw + MathHelper.wrapDegrees(targetYaw - fromYaw)
				* (float) progress;
		float pitch = fromPitch + (targetPitch - fromPitch)
				* (float) progress;
		Vec3d position = new Vec3d(
				MathHelper.lerp(progress, fromPosition.x, targetPosition.x),
				MathHelper.lerp(progress, fromPosition.y, targetPosition.y),
				MathHelper.lerp(progress, fromPosition.z, targetPosition.z));
		return new Pose(position, MathHelper.wrapDegrees(yaw), pitch);
	}

	public static double blendFov(double targetFov) {
		if (!active) {
			return targetFov;
		}
		return fromFov + (targetFov - fromFov) * progress;
	}

	public static void reset() {
		active = false;
		progress = 1.0D;
		durationNanos = 0L;
	}

	public static boolean active() {
		return active;
	}

	private CameraPerspectiveTransition() {
	}
}
