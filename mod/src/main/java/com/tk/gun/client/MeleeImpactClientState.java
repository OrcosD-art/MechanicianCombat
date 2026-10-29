package com.tk.gun.client;

import com.tk.gun.net.MeleeImpactPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client state for close-combat hitstop, camera shake and target freeze. */
public final class MeleeImpactClientState {
	private static final double TARGET_SHAKE_DISTANCE = 0.075D;
	private static final Map<Integer, HitstopState> HITSTOPS = new HashMap<>();
	private static final Map<Integer, FreezeState> FROZEN_TARGETS = new HashMap<>();
	private static ShakeState cameraShake;

	private MeleeImpactClientState() {
	}

	public static void apply(MeleeImpactPayload payload) {
		if (payload.ticks() <= 0) {
			return;
		}
		HitstopState previous = HITSTOPS.get(payload.attackerId());
		int remaining = previous == null
				? payload.ticks() : Math.max(previous.remaining, payload.ticks());
		boolean lockAttacker = payload.lockAttacker()
				|| previous != null && previous.lockAttacker;
		HITSTOPS.put(payload.attackerId(), new HitstopState(
				remaining, Math.max(payload.ticks(),
				previous == null ? 0 : previous.total), lockAttacker));

		if (payload.lockAttacker()) {
			GunClientState.extendMelee(payload.attackerId(), payload.ticks());
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		Entity attacker = client.world.getEntityById(payload.attackerId());
		for (int targetId : payload.targetIds()) {
			Entity target = client.world.getEntityById(targetId);
			if (target == null) {
				continue;
			}
			FreezeState state = createFreezeState(target, attacker,
					payload.ticks(), payload.ticks());
			FreezeState old = FROZEN_TARGETS.get(targetId);
			if (old == null || state.remainingTicks >= old.remainingTicks) {
				FROZEN_TARGETS.put(targetId, state);
			}
		}

		if (payload.cameraShake() && client.player != null
				&& client.player.getId() == payload.attackerId()) {
			startCameraShake(payload.ticks(), payload.critical());
		}
	}

	private static FreezeState createFreezeState(Entity target, Entity attacker,
			int ticks, int totalTicks) {
		float bodyYaw = target.getYaw();
		float headYaw = target.getYaw();
		if (target instanceof LivingEntity living) {
			bodyYaw = living.getBodyYaw();
			headYaw = living.getHeadYaw();
		}
		double angle = attacker != null && attacker != target
				? Math.atan2(target.getZ() - attacker.getZ(),
						target.getX() - attacker.getX())
				: target.getRandom().nextDouble() * Math.PI * 2.0D;
		return new FreezeState(ticks, totalTicks, target.getX(), target.getY(),
				target.getZ(), bodyYaw, target.getYaw(), target.getPitch(),
				headYaw, Math.cos(angle) * TARGET_SHAKE_DISTANCE,
				Math.sin(angle) * TARGET_SHAKE_DISTANCE);
	}

	private static void startCameraShake(int ticks, boolean critical) {
		float intensity = ticks <= 1 ? 0.3F : ticks <= 3 ? 0.6F : 1.0F;
		if (critical) {
			intensity *= 1.2F;
		}
		cameraShake = new ShakeState(ticks + 1, ticks + 1, intensity);
	}

	public static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			clear();
			return;
		}
		tickMap(HITSTOPS);
		tickMap(FROZEN_TARGETS);
		if (cameraShake != null && --cameraShake.remainingTicks <= 0) {
			cameraShake = null;
		}
	}

	private static <T extends TimedState> void tickMap(Map<Integer, T> states) {
		Iterator<Map.Entry<Integer, T>> iterator = states.entrySet().iterator();
		while (iterator.hasNext()) {
			T state = iterator.next().getValue();
			if (state.tickDown() <= 0) {
				iterator.remove();
			}
		}
	}

	public static boolean freezesAnimation(int playerId) {
		HitstopState state = HITSTOPS.get(playerId);
		return state != null && state.remaining > 0 && state.lockAttacker;
	}

	public static boolean freezesLocalInput() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && freezesAnimation(client.player.getId());
	}

	public static FreezeState frozenTarget(int entityId) {
		FreezeState state = FROZEN_TARGETS.get(entityId);
		return state == null || state.remainingTicks <= 0 ? null : state;
	}

	public static void applyCameraShake(MatrixStack matrices, float tickDelta) {
		if (cameraShake == null || cameraShake.remainingTicks <= 0
				|| cameraShake.totalTicks <= 0) {
			return;
		}
		float progress = Math.max(0.0F,
				(cameraShake.remainingTicks - tickDelta) / cameraShake.totalTicks);
		float magnitude = cameraShake.intensity * progress;
		float elapsed = cameraShake.totalTicks - cameraShake.remainingTicks + tickDelta;
		float shakeX = (float) (Math.sin(elapsed * 7.0F) * magnitude * 0.5F);
		float shakeZ = (float) (Math.sin(elapsed * 9.0F) * magnitude);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(shakeX));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shakeZ));
	}

	public static void clear() {
		HITSTOPS.clear();
		FROZEN_TARGETS.clear();
		cameraShake = null;
	}

	private interface TimedState {
		int tickDown();
	}

	private static final class HitstopState implements TimedState {
		private int remaining;
		private final int total;
		private final boolean lockAttacker;

		private HitstopState(int remaining, int total, boolean lockAttacker) {
			this.remaining = remaining;
			this.total = total;
			this.lockAttacker = lockAttacker;
		}

		@Override
		public int tickDown() {
			remaining--;
			return remaining;
		}
	}

	public static final class FreezeState implements TimedState {
		private int remainingTicks;
		private final int totalTicks;
		private final double x;
		private final double y;
		private final double z;
		private final float bodyYaw;
		private final float yaw;
		private final float pitch;
		private final float headYaw;
		private final double shakeX;
		private final double shakeZ;

		private FreezeState(int remainingTicks, int totalTicks, double x, double y,
				double z, float bodyYaw, float yaw, float pitch, float headYaw,
				double shakeX, double shakeZ) {
			this.remainingTicks = remainingTicks;
			this.totalTicks = totalTicks;
			this.x = x;
			this.y = y;
			this.z = z;
			this.bodyYaw = bodyYaw;
			this.yaw = yaw;
			this.pitch = pitch;
			this.headYaw = headYaw;
			this.shakeX = shakeX;
			this.shakeZ = shakeZ;
		}

		@Override
		public int tickDown() {
			remainingTicks--;
			return remainingTicks;
		}

		public double getShake(float partialTick) {
			if (totalTicks <= 0) {
				return 0.0D;
			}
			float elapsed = totalTicks - remainingTicks + partialTick;
			float fade = Math.max(0.0F, (remainingTicks - partialTick) / totalTicks);
			return Math.sin(elapsed * 5.0F) * fade;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		public double z() {
			return z;
		}

		public float yaw() {
			return yaw;
		}

		public float pitch() {
			return pitch;
		}

		public float headYaw() {
			return headYaw;
		}

		public float bodyYaw() {
			return bodyYaw;
		}

		public double shakeX() {
			return shakeX;
		}

		public double shakeZ() {
			return shakeZ;
		}
	}

	private static final class ShakeState {
		private int remainingTicks;
		private final int totalTicks;
		private final float intensity;

		private ShakeState(int remainingTicks, int totalTicks, float intensity) {
			this.remainingTicks = remainingTicks;
			this.totalTicks = totalTicks;
			this.intensity = intensity;
		}
	}
}
