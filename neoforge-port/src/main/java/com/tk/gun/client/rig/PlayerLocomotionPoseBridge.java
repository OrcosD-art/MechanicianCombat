package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.SoulAnchorSkillClientState;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Supplies the hidden GeckoLib locomotion pose to PlayerAnimator using the
 * same body/chest/head/arm pivot composition as the gun and sword bridge.
 */
public final class PlayerLocomotionPoseBridge implements IAnimation {
	private static final int LAYER_PRIORITY = 2;
	private static final Set<String> CHANNELS = Set.of(
			"body.rotation", "body.position",
			"chest.rotation", "chest.position",
			"head.position",
			"arm_left.rotation", "arm_left.position",
			"arm_right.rotation", "arm_right.position",
			"leg_left.rotation", "leg_left.position",
			"leg_right.rotation", "leg_right.position");
	private static final Map<UUID, PoseSnapshot> POSES = new HashMap<>();

	private final AbstractClientPlayerEntity player;

	private PlayerLocomotionPoseBridge(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register(
				(player, stack) -> stack.addAnimLayer(LAYER_PRIORITY,
						new PlayerLocomotionPoseBridge(player)));
	}

	public static void capture(int playerId,
			Map<String, BowgunPlayerAnimatorPose.BonePose> bones,
			float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		AbstractClientPlayerEntity player = client.world.getPlayers().stream()
				.filter(candidate -> candidate.getId() == playerId)
				.findFirst().orElse(null);
		if (player == null) {
			return;
		}
		Map<String, BowgunPlayerAnimatorPose.BonePose> smoothed =
				PlayerLocomotionSmoother.smooth(player, bones, tickDelta);
		POSES.put(player.getUuid(), new PoseSnapshot(
				Map.copyOf(smoothed), client.world.getTime()));
	}

	public static boolean hasFreshPose(UUID uuid) {
		MinecraftClient client = MinecraftClient.getInstance();
		PoseSnapshot snapshot = POSES.get(uuid);
		return client.world != null && snapshot != null
				&& client.world.getTime() - snapshot.gameTime() <= 2L;
	}

	public static boolean hasFreshPose(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return false;
		}
		AbstractClientPlayerEntity player = client.world.getPlayers().stream()
				.filter(candidate -> candidate.getId() == playerId)
				.findFirst().orElse(null);
		return player != null && hasFreshPose(player.getUuid());
	}

	@Override
	public boolean isActive() {
		boolean active = player.isAlive()
				&& PlayerLocomotionRigClient.shouldEvaluate(player)
				&& hasFreshPose(player.getUuid());
		if (!active) {
			PlayerLocomotionSmoother.clearNormal(player.getUuid());
		}
		return active;
	}

	@Override
	public Vec3f get3DTransform(String part, TransformType type,
			float tickDelta, Vec3f fallback) {
		if (!isActive()) {
			return fallback;
		}
		if ("head".equals(part) && type == TransformType.ROTATION) {
			// The head must keep the exact vanilla look direction. Composing
			// body/chest first makes the head horizontally follow the parent
			// chest in PlayerAnimator's flattened player hierarchy.
			return fallback;
		}
		if ((player.handSwinging || player.isUsingItem())
				&& ("torso".equals(part) || "leftArm".equals(part)
						|| "rightArm".equals(part))) {
			return fallback;
		}
		String[] path = switch (part) {
			case "torso" -> new String[]{"body", "chest"};
			case "head" -> new String[]{"body", "chest", "head"};
			case "leftArm" -> new String[]{"body", "chest", "arm_left"};
			case "rightArm" -> new String[]{"body", "chest", "arm_right"};
			case "leftLeg" -> new String[]{"body", "leg_left"};
			case "rightLeg" -> new String[]{"body", "leg_right"};
			default -> null;
		};
		if (path == null) {
			return fallback;
		}
		PoseSnapshot snapshot = POSES.get(player.getUuid());
		if (snapshot == null) {
			return fallback;
		}
		return BowgunPlayerAnimatorPose.transform(
				path, snapshot.bones(), CHANNELS, type, fallback);
	}

	@Override
	public void setupAnim(float tickDelta) {
		// The GeckoLib rig evaluates against the current render partial tick.
	}

	public static void clear() {
		POSES.clear();
		PlayerLocomotionSmoother.clear();
	}

	private record PoseSnapshot(
			Map<String, BowgunPlayerAnimatorPose.BonePose> bones,
			long gameTime) {
	}
}
