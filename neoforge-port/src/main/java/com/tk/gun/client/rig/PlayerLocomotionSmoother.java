package com.tk.gun.client.rig;

import com.tk.gun.client.TkGunClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stateful per-player smoothing for the ordinary GeckoLib locomotion pose.
 *
 * <p>The authored clips provide the base motion. This layer adds frame-rate
 * independent pose filtering, smooth mode intensities and small secondary
 * motions without requiring an external model-expression mod.</p>
 */
public final class PlayerLocomotionSmoother {
	private static final double TWO_PI = Math.PI * 2.0D;
	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, State> WEAPON_STATES = new HashMap<>();
	private static ClientWorld cachedWorld;

	public static Map<String, BowgunPlayerAnimatorPose.BonePose> smooth(
			AbstractClientPlayerEntity player,
			Map<String, BowgunPlayerAnimatorPose.BonePose> raw,
			float tickDelta) {
		return smooth(STATES, player, raw, tickDelta, true);
	}

	public static Map<String, BowgunPlayerAnimatorPose.BonePose> smoothWeapon(
			AbstractClientPlayerEntity player,
			Map<String, BowgunPlayerAnimatorPose.BonePose> raw,
			float tickDelta, boolean secondaryMotion) {
		if (!com.tk.gun.client.LocomotionIsolationDebug
				.poseSmoothingEnabled()) {
			WEAPON_STATES.remove(player.getUuid());
			return raw;
		}
		return smooth(WEAPON_STATES, player, raw, tickDelta,
				secondaryMotion);
	}

	private static Map<String, BowgunPlayerAnimatorPose.BonePose> smooth(
			Map<UUID, State> states,
			AbstractClientPlayerEntity player,
			Map<String, BowgunPlayerAnimatorPose.BonePose> raw,
			float tickDelta, boolean secondaryMotion) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (cachedWorld != client.world) {
			clear();
			cachedWorld = client.world;
		}
		if (client.world == null || raw.isEmpty()) {
			return raw;
		}
		double sampleTime = client.world.getTime()
				+ MathHelper.clamp(tickDelta, 0.0F, 1.0F);
		State state = states.computeIfAbsent(player.getUuid(),
				ignored -> new State());
		if (Double.isNaN(state.lastSampleTime)) {
			state.pose.putAll(raw);
			state.output = Map.copyOf(state.pose);
			state.lastSampleTime = sampleTime;
			state.lastY = player.getY();
			state.lastVerticalSpeed = player.getVelocity().y;
			state.wasOnGround = player.isOnGround();
			return state.output;
		}
		double rawDelta = sampleTime - state.lastSampleTime;
		if (rawDelta <= 1.0E-5D) {
			return state.output;
		}
		double dt = MathHelper.clamp(rawDelta,
				1.0D / 240.0D, 0.05D);
		boolean reset = rawDelta > 0.25D
				|| Math.abs(player.getY() - state.lastY) > 3.0D;
		state.lastSampleTime = sampleTime;

		double velocityX = player.getVelocity().x;
		double velocityY = player.getVelocity().y;
		double velocityZ = player.getVelocity().z;
		double horizontalSpeed = Math.hypot(velocityX, velocityZ);
		boolean onGround = player.isOnGround();
		boolean moving = TkGunClient.isMoving(player.getId())
				|| horizontalSpeed > 0.025D;
		boolean sprinting = onGround && moving
				&& player.isSprinting() && !player.isSneaking();

		state.walkIntensity = damp(state.walkIntensity,
				onGround && moving && !sprinting ? 1.0D : 0.0D,
				0.16D, dt);
		state.runIntensity = damp(state.runIntensity,
				sprinting ? 1.0D : 0.0D, 0.18D, dt);
		state.airIntensity = damp(state.airIntensity,
				onGround ? 0.0D : 1.0D, 0.12D, dt);
		state.sprintIntensity = damp(state.sprintIntensity,
				sprinting ? 1.0D : 0.0D, 0.14D, dt);

		if (!state.wasOnGround && onGround) {
			state.landingVelocity -= Math.min(2.4D,
					0.45D + Math.abs(state.lastVerticalSpeed) * 2.2D);
		}
		state.wasOnGround = onGround;
		state.lastVerticalSpeed = velocityY;
		state.lastY = player.getY();

		state.landingVelocity += (-state.landingOffset * 105.0D
				- state.landingVelocity * 13.0D) * dt;
		state.landingOffset += state.landingVelocity * dt;
		state.landingOffset = MathHelper.clamp(
				state.landingOffset, -0.32D, 0.22D);

		double targetVertical = onGround && !moving ? 0.0D
				: MathHelper.clamp(velocityY * 0.75D, -0.16D, 0.16D);
		state.verticalMotion = damp(state.verticalMotion,
				targetVertical, 0.11D, dt);

		double yaw = Math.toRadians(player.getYaw());
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		double sideSpeed = velocityX * rightX + velocityZ * rightZ;
		if (Math.abs(sideSpeed) < 0.01D) {
			sideSpeed = 0.0D;
		}
		state.strafeMotion = damp(state.strafeMotion,
				MathHelper.clamp(sideSpeed * 0.8D, -0.24D, 0.24D),
				0.12D, dt);

		float animationPhase = TkGunClient.walkPhase(player.getId());
		animationPhase += tickDelta * 0.05F
				* TkGunClient.walkAnimationSpeed(player.getId());
		double gaitPhase = wrapPhase(animationPhase * TWO_PI);
		double locomotion = MathHelper.clamp(
				state.walkIntensity + state.runIntensity, 0.0D, 1.0D);
		double gaitPrimary = Math.sin(gaitPhase) * locomotion;
		double gaitSecondary = Math.sin(gaitPhase * 2.0D) * locomotion;
		double gaitSide = Math.cos(gaitPhase) * locomotion;
		double airMotion = state.airIntensity
				* MathHelper.clamp(velocityY * 0.5D, -0.18D, 0.18D);
		boolean settledIdle = onGround && !moving
				&& locomotion < 0.005D && state.airIntensity < 0.005D
				&& Math.abs(state.verticalMotion) < 0.005D
				&& Math.abs(state.strafeMotion) < 0.005D
				&& Math.abs(state.landingOffset) < 0.005D;
		state.idleSettleSeconds = settledIdle
				? state.idleSettleSeconds + dt : 0.0D;

		double poseTau = onGround
				? (moving ? 0.065D : 0.095D) : 0.12D;
		float poseAlpha = (float) (1.0D - Math.exp(-dt / poseTau));
		if (state.idleSettleSeconds > 0.25D) {
			poseAlpha = 1.0F;
		}
		if (reset) {
			poseAlpha = 1.0F;
			state.landingVelocity = 0.0D;
			state.landingOffset = 0.0D;
			state.verticalMotion = 0.0D;
			state.strafeMotion = 0.0D;
		}

		Map<String, BowgunPlayerAnimatorPose.BonePose> next =
				new HashMap<>(raw.size());
		for (Map.Entry<String, BowgunPlayerAnimatorPose.BonePose> entry
				: raw.entrySet()) {
			BowgunPlayerAnimatorPose.BonePose previous =
					state.pose.get(entry.getKey());
			next.put(entry.getKey(), previous == null
					? entry.getValue()
					: blend(previous, entry.getValue(), poseAlpha));
		}
		state.pose.clear();
		state.pose.putAll(next);
		Map<String, BowgunPlayerAnimatorPose.BonePose> output =
				new HashMap<>(next);

		if (secondaryMotion && !settledIdle) {
			float chestBob = (float) (gaitSecondary
					* (0.055D * state.walkIntensity
							+ 0.085D * state.runIntensity)
					+ state.verticalMotion * 0.55D
					+ state.landingOffset * 0.75D);
			float chestPitch = (float) (gaitSecondary * 0.007D
					+ state.sprintIntensity * 0.018D
					+ airMotion * 0.06D);
			float chestYaw = (float) (gaitPrimary * 0.012D);
			float chestRoll = (float) (gaitSide * 0.011D
					+ state.strafeMotion * 0.055D);
			if (com.tk.gun.client.LocomotionIsolationDebug
					.parentSecondaryEnabled()) {
				offset(output, "body", 0.0F, 0.0F, 0.0F,
						0.0F, 0.0F,
						(float) (state.strafeMotion * 0.018D));
				offset(output, "chest", 0.0F, chestBob, 0.0F,
						chestPitch, chestYaw, chestRoll);
				offset(output, "head",
						(float) (state.strafeMotion * 0.025D),
						0.0F, 0.0F,
						0.0F, 0.0F, 0.0F);
			}
			if (com.tk.gun.client.LocomotionIsolationDebug
					.directArmSecondaryEnabled()) {
				offset(output, "arm_left", 0.0F, 0.0F, 0.0F,
						(float) (-gaitPrimary * 0.016D), 0.0F,
						(float) (gaitSecondary * 0.008D));
				offset(output, "arm_right", 0.0F, 0.0F, 0.0F,
						(float) (gaitPrimary * 0.016D), 0.0F,
						(float) (-gaitSecondary * 0.008D));
			}
		}

		state.output = Map.copyOf(output);
		return state.output;
	}

	public static void clearNormal(UUID uuid) {
		STATES.remove(uuid);
	}

	public static void clearWeapon(UUID uuid) {
		WEAPON_STATES.remove(uuid);
	}

	public static void clear(UUID uuid) {
		clearNormal(uuid);
		clearWeapon(uuid);
	}

	public static void clear() {
		STATES.clear();
		WEAPON_STATES.clear();
		cachedWorld = null;
	}

	private static void offset(
			Map<String, BowgunPlayerAnimatorPose.BonePose> pose,
			String bone, float x, float y, float z,
			float rotX, float rotY, float rotZ) {
		BowgunPlayerAnimatorPose.BonePose value = pose.get(bone);
		if (value == null) {
			return;
		}
		pose.put(bone, new BowgunPlayerAnimatorPose.BonePose(
				value.x() + x, value.y() + y, value.z() + z,
				value.rotX() + rotX, value.rotY() + rotY,
				value.rotZ() + rotZ,
				value.pivotX(), value.pivotY(), value.pivotZ()));
	}

	private static BowgunPlayerAnimatorPose.BonePose blend(
			BowgunPlayerAnimatorPose.BonePose from,
			BowgunPlayerAnimatorPose.BonePose to, float amount) {
		Quaternionf rotation = rotation(from);
		rotation.slerp(rotation(to), amount).normalize();
		float rotX = (float) Math.atan2(
				2.0F * (rotation.w * rotation.x
						+ rotation.y * rotation.z),
				1.0F - 2.0F * (rotation.x * rotation.x
						+ rotation.y * rotation.y));
		float rotY = (float) Math.asin(MathHelper.clamp(
				2.0F * (rotation.w * rotation.y
						- rotation.z * rotation.x),
				-1.0F, 1.0F));
		float rotZ = (float) Math.atan2(
				2.0F * (rotation.w * rotation.z
						+ rotation.x * rotation.y),
				1.0F - 2.0F * (rotation.y * rotation.y
						+ rotation.z * rotation.z));
		return new BowgunPlayerAnimatorPose.BonePose(
				MathHelper.lerp(amount, from.x(), to.x()),
				MathHelper.lerp(amount, from.y(), to.y()),
				MathHelper.lerp(amount, from.z(), to.z()),
				rotX, rotY, rotZ,
				to.pivotX(), to.pivotY(), to.pivotZ());
	}

	private static Quaternionf rotation(
			BowgunPlayerAnimatorPose.BonePose pose) {
		return new Quaternionf().rotationZYX(
				pose.rotZ(), pose.rotY(), pose.rotX());
	}

	private static double damp(double current, double target,
			double responseSeconds, double dt) {
		double alpha = 1.0D - Math.exp(-dt / responseSeconds);
		return current + (target - current) * alpha;
	}

	private static double wrapPhase(double phase) {
		double wrapped = phase % TWO_PI;
		return wrapped < 0.0D ? wrapped + TWO_PI : wrapped;
	}

	private static final class State {
		private final Map<String, BowgunPlayerAnimatorPose.BonePose> pose =
				new HashMap<>();
		private Map<String, BowgunPlayerAnimatorPose.BonePose> output =
				Map.of();
		private double lastSampleTime = Double.NaN;
		private double lastY;
		private double lastVerticalSpeed;
		private boolean wasOnGround;
		private double walkIntensity;
		private double runIntensity;
		private double airIntensity;
		private double sprintIntensity;
		private double verticalMotion;
		private double strafeMotion;
		private double landingOffset;
		private double landingVelocity;
		private double idleSettleSeconds;
	}

	private PlayerLocomotionSmoother() {
	}
}
