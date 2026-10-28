package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

/**
 * Built-in shoulder camera used only when ShoulderSurfing is absent.
 *
 * <p>The camera owns a separate yaw/pitch pair. Mouse deltas update those
 * angles instead of the player's rotation, while {@link GunCamera} decides
 * when the body should follow the camera or track the crosshair.
 */
public final class FallbackShoulderCamera {
	private static final boolean SHOULDER_SURFING_INSTALLED =
			FabricLoader.getInstance().isModLoaded("shouldersurfing");
	private static final double DEFAULT_DISTANCE = 3.2D;
	private static final double DEFAULT_SIDE = 0.68D;
	private static final double DEFAULT_HEIGHT = 0.18D;
	private static final double AIM_DISTANCE = 2.35D;
	private static final double AIM_SIDE = 0.52D;
	private static final double AIM_HEIGHT = 0.12D;
	private static final double ACTION_DISTANCE = 3.55D;
	private static final double ACTION_SIDE = 0.58D;
	private static final double ACTION_HEIGHT = 0.32D;
	private static final double TRANSITION_SECONDS = 0.10D;
	private static final double COLLISION_MARGIN = 0.15D;

	private static boolean active;
	private static boolean initialized;
	private static float cameraYaw;
	private static float cameraPitch;
	private static double distance = DEFAULT_DISTANCE;
	private static double side = DEFAULT_SIDE;
	private static double height = DEFAULT_HEIGHT;

	public record Pose(Vec3d position, float yaw, float pitch) {
	}

	public static boolean isFallbackActive(MinecraftClient client) {
		return active && isEligible(client);
	}

	public static boolean usesFallback(MinecraftClient client) {
		return !SHOULDER_SURFING_INSTALLED && isEligible(client);
	}

	private static boolean isEligible(MinecraftClient client) {
		return client != null
				&& client.player != null
				&& !client.player.isSpectator()
				&& TkGun.holdingGun(client.player)
				&& client.options.getPerspective() == Perspective.THIRD_PERSON_BACK;
	}

	public static void tick(MinecraftClient client) {
		if (!usesFallback(client)) {
			if (active) {
				leave(client);
			}
			return;
		}
		if (!initialized) {
			cameraYaw = client.player.getYaw();
			cameraPitch = client.player.getPitch();
			distance = DEFAULT_DISTANCE;
			side = DEFAULT_SIDE;
			height = DEFAULT_HEIGHT;
			initialized = true;
		}
		active = true;

		double targetDistance = DEFAULT_DISTANCE;
		double targetSide = DEFAULT_SIDE;
		double targetHeight = DEFAULT_HEIGHT;
		boolean aiming = GunCamera.aiming(client);
		boolean focusing = GunClientState.focusish(client.player.getId());
		GunClientState.Entry state = GunClientState.get(client.player.getId());
		boolean action = state != null && (state.state == TkGun.State.ROLL
				|| state.state == TkGun.State.ROLL_TO_IDLE
				|| state.state == TkGun.State.SLIDE
				|| state.state == TkGun.State.SLIDE_READY);
		if (action) {
			targetDistance = ACTION_DISTANCE;
			targetSide = ACTION_SIDE;
			targetHeight = ACTION_HEIGHT;
		} else if (aiming || focusing) {
			targetDistance = AIM_DISTANCE;
			targetSide = AIM_SIDE;
			targetHeight = AIM_HEIGHT;
		}

		double frameDelta = Math.min(0.25D,
				Math.max(0.0D, client.getRenderTickCounter().getLastFrameDuration() / 20.0D));
		double blend = 1.0D - Math.exp(-frameDelta / TRANSITION_SECONDS);
		distance += (targetDistance - distance) * blend;
		side += (targetSide - side) * blend;
		height += (targetHeight - height) * blend;
	}

	/** Applies mouse deltas to the fallback camera and prevents vanilla body rotation. */
	public static boolean turn(ClientPlayerEntity player, double deltaX, double deltaY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active || !initialized || client.player != player || !isEligible(client)) {
			return false;
		}
		cameraYaw = MathHelper.wrapDegrees(
				cameraYaw + (float) deltaX * 0.15F);
		cameraPitch = MathHelper.clamp(
				cameraPitch + (float) deltaY * 0.15F, -90.0F, 90.0F);
		player.setHeadYaw(cameraYaw);
		player.setPitch(cameraPitch);
		if (ShoulderCouplingState.shouldCoupleCamera(client)) {
			player.setYaw(cameraYaw);
			player.setBodyYaw(cameraYaw);
		}
		return true;
	}

	public static Pose computePose(BlockView view, Entity focusedEntity, float tickDelta) {
		if (!active || !initialized || !(focusedEntity instanceof ClientPlayerEntity player)) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != player || !isEligible(client)) {
			return null;
		}

		Vec3d look = Vec3d.fromPolar(cameraPitch, cameraYaw);
		double yawRadians = Math.toRadians(cameraYaw);
		Vec3d right = new Vec3d(-Math.cos(yawRadians), 0.0D, -Math.sin(yawRadians));
		Vec3d interpolated = player.getLerpedPos(tickDelta);
		Vec3d eye = interpolated.add(0.0D, player.getEyeHeight(player.getPose()), 0.0D);
		Vec3d desired = eye
				.subtract(look.multiply(distance))
				.add(right.multiply(side))
				.add(0.0D, height, 0.0D);

		Vec3d delta = desired.subtract(eye);
		double desiredDistance = delta.length();
		if (desiredDistance > 1.0E-5D) {
			BlockHitResult hit = view.raycast(new RaycastContext(
					eye, desired, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() != HitResult.Type.MISS) {
				double safeDistance = Math.max(0.0D,
						eye.distanceTo(hit.getPos()) - COLLISION_MARGIN);
				desired = eye.add(delta.normalize().multiply(safeDistance));
			}
		}

		return new Pose(desired, cameraYaw, cameraPitch);
	}

	public static void leave(MinecraftClient client) {
		if (active && initialized && client.player != null) {
			// Preserve the visible camera direction when returning to vanilla controls.
			client.player.setYaw(cameraYaw);
			client.player.setPitch(cameraPitch);
			client.player.setHeadYaw(cameraYaw);
			client.player.setBodyYaw(cameraYaw);
			client.player.prevYaw = cameraYaw;
			client.player.prevPitch = cameraPitch;
		}
		active = false;
		initialized = false;
	}

	public static float cameraYaw() {
		return cameraYaw;
	}

	public static float cameraPitch() {
		return cameraPitch;
	}

	private FallbackShoulderCamera() {
	}
}
