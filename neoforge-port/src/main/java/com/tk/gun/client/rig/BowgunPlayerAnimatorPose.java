package com.tk.gun.client.rig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.TkGun;
import com.tk.gun.client.SoulAnchorSkillClientState;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bridges evaluated GeckoLib body poses into PlayerAnimator's vanilla player model. */
public final class BowgunPlayerAnimatorPose implements IAnimation {
	/**
	 * Better Combat owns its attack layer at priority 2000. Holding a gun must
	 * keep the GeckoLib-evaluated pose authoritative over stale attack layers.
	 * Jump-backpack clips are explicitly excluded below and retain their own
	 * higher-level visuals.
	 */
	private static final int LAYER_PRIORITY = 2100;
	private static final boolean TRACE =
			System.getProperty("mechaniciancombat.posebridge") != null;
	private static final Set<String> TRACED_PARTS = ConcurrentHashMap.newKeySet();
	private static final Map<Integer, PoseSnapshot> POSES = new ConcurrentHashMap<>();
	private static final Set<String> REQUIRED_UPPER_CHANNELS = Set.of(
			"body.rotation", "body.position",
			"chest.rotation", "chest.position",
			"head.rotation", "head.position",
			"arm_left.rotation", "arm_left.position",
			"arm_right.rotation", "arm_right.position");
	private static final Set<String> REQUIRED_POSE_BONES = Set.of(
			"body", "chest", "head", "arm_left", "arm_right",
			"leg_left", "leg_right");
	private static ClientWorld cachedWorld;
	private static final Map<Identifier, Map<String, Set<String>>> ANIMATION_CHANNELS =
			new ConcurrentHashMap<>();

	private final AbstractClientPlayerEntity player;

	private BowgunPlayerAnimatorPose(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("playeranimator")) {
			TkGun.LOGGER.error("[mechaniciancombat] PlayerAnimator 未加载，无法启用原版玩家枪械动画");
			return;
		}
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register(
				(player, stack) -> stack.addAnimLayer(
						LAYER_PRIORITY, new BowgunPlayerAnimatorPose(player)));
		TkGun.LOGGER.info("[mechaniciancombat] 已注册 PlayerAnimator 原版玩家姿势桥接");
	}

	public static void capture(int playerId, Identifier animationResource,
			Map<String, BonePose> pose, Set<String> activeAnimations) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (cachedWorld != client.world) {
			POSES.clear();
			cachedWorld = client.world;
		}
		long gameTime = client.world == null ? 0L : client.world.getTime();
		Set<String> channels = new HashSet<>();
		Map<String, Set<String>> knownChannels = channels(animationResource);
		for (String animation : activeAnimations) {
			channels.addAll(knownChannels.getOrDefault(animation, Set.of()));
		}
		// The GeckoLib rig has already evaluated these bones for this frame.
		// Do not let a one-tick animation-name/cache mismatch drop the entire
		// upper body back to the vanilla pose.
		channels.addAll(REQUIRED_UPPER_CHANNELS);
		SoulAnchorSkillClientState.Entry anchor =
				SoulAnchorSkillClientState.get(playerId);
		if (SoulAnchorSkillClientState.ready(playerId)
				|| anchor != null && !anchor.gunHeld()) {
			// Ready and every non-gun anchor phase leave direct head rotation to
			// vanilla/PlayerAnimator while the chest still carries the body pose.
			channels.remove("head.rotation");
		}
		BonePose chest = pose.get("chest");
		// The walk_* chest bob is added by the renderer, not authored in JSON.
		if (chest != null && chest.y() != 0.0F) {
			channels.add("chest.position");
		}
		Map<String, BonePose> stablePose = pose;
		Set<String> stableChannels = channels;
		PoseSnapshot previous = POSES.get(playerId);
		if (previous != null && gameTime - previous.gameTime() <= 2L
				&& !pose.keySet().containsAll(REQUIRED_POSE_BONES)) {
			// A single incomplete GeckoLib pass must not collapse the vanilla
			// player model back to bind pose for upper body or arms.
			Map<String, BonePose> mergedPose = new HashMap<>(previous.bones());
			mergedPose.putAll(pose);
			Set<String> mergedChannels = new HashSet<>(previous.channels());
			mergedChannels.addAll(channels);
			stablePose = mergedPose;
			stableChannels = mergedChannels;
		}
		POSES.put(playerId, new PoseSnapshot(
				Map.copyOf(stablePose), Set.copyOf(stableChannels), gameTime));
	}

	public static void clear() {
		POSES.clear();
		cachedWorld = null;
	}

	@Override
	public boolean isActive() {
		boolean active = player.isAlive() && (TkGun.holdingGun(player)
				|| SoulAnchorSkillClientState.active(player.getId()));
		if (!active) {
			POSES.remove(player.getId());
		}
		return active;
	}

	@Override
	public Vec3f get3DTransform(String part, TransformType type,
			float tickDelta, Vec3f fallback) {
		if (!isActive()) {
			return fallback;
		}
		if (usesPlainAnchorAirLegs(player.getId())
				&& ("leftLeg".equals(part) || "rightLeg".equals(part))) {
			// Non-gun chain clips only own the upper body while airborne.
			// Returning the lower-layer transform preserves jump/fall legs.
			return fallback;
		}
		PoseSnapshot snapshot = POSES.get(player.getId());
		if (snapshot == null || player.getWorld() == null
				|| cachedWorld != player.getWorld()) {
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
		trace(snapshot, path, part, type);
		return transform(path, snapshot.bones(), snapshot.channels(), type, fallback);
	}

	/**
	 * Kinetic Sword already selects its authored jump/fall clips while airborne,
	 * so its leg channels must remain active. Plain non-gun chain poses instead
	 * yield their legs to the normal airborne animation layer.
	 */
	private static boolean usesPlainAnchorAirLegs(int playerId) {
		SoulAnchorSkillClientState.Entry anchor =
				SoulAnchorSkillClientState.get(playerId);
		if (anchor == null || anchor.gunHeld() || anchor.swordSkill()) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null
				&& client.world.getEntityById(playerId)
						instanceof net.minecraft.entity.player.PlayerEntity player
				&& !player.isOnGround();
	}

	static Vec3f transform(String[] path, Map<String, BonePose> bones,
			Set<String> channels, TransformType type, Vec3f fallback) {
		if (type == TransformType.ROTATION) {
			Quaternionf combined = new Quaternionf();
			boolean hasRotation = false;
			for (int i = 0; i < path.length - 1; i++) {
				if (channels.contains(path[i] + ".rotation")) {
					combined.mul(playerRotation(
							bones.getOrDefault(path[i], BonePose.ZERO)));
					hasRotation = true;
				}
			}
			String directName = path[path.length - 1];
			if (channels.contains(directName + ".rotation")) {
				combined.mul(playerRotation(
						bones.getOrDefault(directName, BonePose.ZERO)));
				hasRotation = true;
			} else {
				combined.mul(rotation(fallback));
			}
			return hasRotation ? eulerZYX(combined.normalize()) : fallback;
		}
		if (type != TransformType.POSITION) {
			return fallback;
		}

		boolean hasPositionEffect = false;
		Matrix4f transform = new Matrix4f();
		for (String name : path) {
			BonePose pose = bones.getOrDefault(name, BonePose.ZERO);
			boolean hasPosition = channels.contains(name + ".position");
			boolean hasRotation = channels.contains(name + ".rotation");
			if (hasPosition) {
				transform.translate(localTranslation(pose));
				hasPositionEffect = true;
			}
			Vector3f pivot = pivot(pose);
			transform.translate(pivot);
			if (hasRotation) {
				transform.rotate(rotation(pose));
				hasPositionEffect = true;
			}
			transform.translate(new Vector3f(pivot).negate());
		}
		if (!hasPositionEffect) {
			return fallback;
		}

		// Convert the vanilla model-part pivot into GeckoLib's model basis.
		Vector3f targetPivot = new Vector3f(
				-fallback.getX(), 24.0F - fallback.getY(), fallback.getZ());
		Vector3f movedPivot = transform.transformPosition(
				new Vector3f(targetPivot));
		Vector3f delta = movedPivot.sub(targetPivot);
		return new Vec3f(fallback.getX() - delta.x(),
				fallback.getY() - delta.y(), fallback.getZ() + delta.z());
	}

	private static Vector3f localTranslation(BonePose pose) {
		return new Vector3f(-pose.x(), pose.y(), pose.z());
	}

	private static Vector3f pivot(BonePose pose) {
		return new Vector3f(pose.pivotX(), pose.pivotY(), pose.pivotZ());
	}

	private static Quaternionf rotation(BonePose pose) {
		return new Quaternionf().rotationZYX(
				pose.rotZ(), pose.rotY(), pose.rotX());
	}

	private static Quaternionf playerRotation(BonePose pose) {
		return new Quaternionf().rotationZYX(
				pose.rotZ(), -pose.rotY(), -pose.rotX());
	}

	private static Quaternionf rotation(Vec3f pose) {
		return new Quaternionf().rotationZYX(
				pose.getZ(), pose.getY(), pose.getX());
	}

	private static Vec3f eulerZYX(Quaternionf q) {
		float x = (float) Math.atan2(
				2.0F * (q.w * q.x + q.y * q.z),
				1.0F - 2.0F * (q.x * q.x + q.y * q.y));
		float y = (float) Math.asin(Math.max(-1.0F,
				Math.min(1.0F, 2.0F * (q.w * q.y - q.z * q.x))));
		float z = (float) Math.atan2(
				2.0F * (q.w * q.z + q.x * q.y),
				1.0F - 2.0F * (q.y * q.y + q.z * q.z));
		return new Vec3f(x, y, z);
	}

	private void trace(PoseSnapshot snapshot, String[] path,
			String part, TransformType type) {
		if (!TRACE) {
			return;
		}
		String key = player.getId() + ":" + part + ":" + type;
		if (!TRACED_PARTS.add(key)) {
			return;
		}
		Set<String> active = new HashSet<>();
		for (String bone : path) {
			for (String channel : Set.of("position", "rotation")) {
				String name = bone + "." + channel;
				if (snapshot.channels().contains(name)) {
					active.add(name);
				}
			}
		}
		TkGun.LOGGER.info("[mechaniciancombat] PlayerAnimator 姿势回调：player={} part={} type={} channels={}",
				player.getId(), part, type, active);
	}

	private static Map<String, Set<String>> channels(Identifier id) {
		return ANIMATION_CHANNELS.computeIfAbsent(id, BowgunPlayerAnimatorPose::parseChannels);
	}

	private static Map<String, Set<String>> parseChannels(Identifier id) {
		Map<String, Set<String>> parsed = new HashMap<>();
		try (var resource = MinecraftClient.getInstance().getResourceManager()
				.getResource(id).orElseThrow().getInputStream();
			 var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
			JsonObject animations = JsonParser.parseReader(reader)
					.getAsJsonObject().getAsJsonObject("animations");
			for (Map.Entry<String, JsonElement> animation : animations.entrySet()) {
				Set<String> keys = new HashSet<>();
				JsonObject bones = animation.getValue().getAsJsonObject()
						.getAsJsonObject("bones");
				if (bones != null) {
					for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
						JsonObject boneChannels = bone.getValue().getAsJsonObject();
						for (String channel : Set.of("position", "rotation")) {
							if (boneChannels.has(channel)) {
								keys.add(bone.getKey() + "." + channel);
							}
						}
					}
				}
				parsed.put(animation.getKey(), Set.copyOf(keys));
			}
		} catch (Exception exception) {
			TkGun.LOGGER.error("[mechaniciancombat] 读取 PlayerAnimator 姿势通道失败：{}",
					id, exception);
		}
		return Map.copyOf(parsed);
	}

	@Override
	public void setupAnim(float tickDelta) {
		// The GeckoLib rig and PlayerAnimator both use current render partial ticks.
	}

	public record BonePose(float x, float y, float z,
			float rotX, float rotY, float rotZ,
			float pivotX, float pivotY, float pivotZ) {
		private static final BonePose ZERO =
				new BonePose(0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	private record PoseSnapshot(Map<String, BonePose> bones,
			Set<String> channels, long gameTime) {
	}
}
