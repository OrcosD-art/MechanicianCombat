package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Samples the authored flag subtree for the player-held skill prop. */
final class WarBannerHeldPose {
	private static final Identifier HELD_RESOURCE =
			TkGun.id("animations/entity/war_banner_held.animation.json");
	private static final Identifier FPA_RESOURCE =
			TkGun.id("animations/entity/war_banner_fpa.animation.json");
	private static final Identifier ANCHOR_FPA_RESOURCE =
			TkGun.id("animations/entity/soul_anchor_fpa.animation.json");
	private static final Map<Identifier,
			Map<String, Map<String, BoneChannels>>> ANIMATION_BONES =
			new HashMap<>();

	static void clearCache() {
		ANIMATION_BONES.clear();
	}

	static void apply(GeoBone bone, double seconds, boolean gunHeld,
			boolean kineticSword) {
		String animation = kineticSword ? "GS_skill_flag"
				: gunHeld ? "skill_flag_gun" : "skill_flag";
		apply(bone, seconds, HELD_RESOURCE, animation);
	}

	static void applyFirstPerson(GeoBone bone, double seconds) {
		apply(bone, seconds, FPA_RESOURCE, "skill_flag_fpa");
	}

	static void applyAnchorFirstPerson(GeoBone bone, double seconds,
			boolean throwing) {
		apply(bone, seconds, ANCHOR_FPA_RESOURCE,
				throwing ? "throw" : "ready");
	}

	private static void apply(GeoBone bone, double seconds,
			Identifier resource, String animationName) {
		BoneSnapshot initial = bone.getInitialSnapshot();
		if (initial != null) {
			bone.setPosX(initial.getOffsetX());
			bone.setPosY(initial.getOffsetY());
			bone.setPosZ(initial.getOffsetZ());
			bone.setRotX(initial.getRotX());
			bone.setRotY(initial.getRotY());
			bone.setRotZ(initial.getRotZ());
			bone.setScaleX(initial.getScaleX());
			bone.setScaleY(initial.getScaleY());
			bone.setScaleZ(initial.getScaleZ());
		}
		BoneChannels channels =
				channels(resource, animationName, bone.getName());
		if (channels == null) {
			return;
		}
		Vec3 rotation = sample(channels.rotation(), seconds);
		Vec3 position = sample(channels.position(), seconds);
		Vec3 scale = sample(channels.scale(), seconds);
		if (rotation != null) {
			float baseX = initial == null ? 0.0F : initial.getRotX();
			float baseY = initial == null ? 0.0F : initial.getRotY();
			float baseZ = initial == null ? 0.0F : initial.getRotZ();
			bone.setRotX(baseX + (float) Math.toRadians(rotation.x()));
			bone.setRotY(baseY + (float) Math.toRadians(rotation.y()));
			bone.setRotZ(baseZ + (float) Math.toRadians(rotation.z()));
		}
		if (position != null) {
			float baseX = initial == null ? 0.0F : initial.getOffsetX();
			float baseY = initial == null ? 0.0F : initial.getOffsetY();
			float baseZ = initial == null ? 0.0F : initial.getOffsetZ();
			// Animation position channels are offsets from the baked parent-relative
			// rest position. Replacing the snapshot moved flag above arm_right's pivot.
			bone.setPosX(baseX + position.x());
			bone.setPosY(baseY + position.y());
			bone.setPosZ(baseZ + position.z());
		}
		if (scale != null) {
			float baseX = initial == null ? 1.0F : initial.getScaleX();
			float baseY = initial == null ? 1.0F : initial.getScaleY();
			float baseZ = initial == null ? 1.0F : initial.getScaleZ();
			bone.setScaleX(baseX * scale.x());
			bone.setScaleY(baseY * scale.y());
			bone.setScaleZ(baseZ * scale.z());
		}
	}

	private static BoneChannels channels(Identifier resource,
			String animationName, String boneName) {
		Map<String, BoneChannels> bones =
				load(resource).get(animationName);
		return bones == null ? null : bones.get(boneName);
	}

	private static Map<String, Map<String, BoneChannels>> load(
			Identifier resource) {
		Map<String, Map<String, BoneChannels>> loaded =
				ANIMATION_BONES.get(resource);
		if (loaded != null) {
			return loaded;
		}
		loaded = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		try (var stream = client.getResourceManager().getResource(resource)
				.orElseThrow().getInputStream();
			 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			var animations = com.google.gson.JsonParser.parseReader(reader)
					.getAsJsonObject().getAsJsonObject("animations");
			for (String animationName : animations.keySet()) {
				var animation = animations.getAsJsonObject(animationName);
				if (animation == null || !animation.has("bones")) {
					continue;
				}
				var bones = animation.getAsJsonObject("bones");
				Map<String, BoneChannels> parsed = new HashMap<>();
				for (String boneName : bones.keySet()) {
					var channels = bones.getAsJsonObject(boneName);
					parsed.put(boneName, new BoneChannels(
							channel(channels.get("rotation")),
							channel(channels.get("position")),
							channel(channels.get("scale"))));
				}
				loaded.put(animationName, Map.copyOf(parsed));
			}
		} catch (Exception exception) {
			TkGun.LOGGER.error("[mechaniciancombat] Failed to load held War Banner pose {}",
					resource, exception);
		}
		Map<String, Map<String, BoneChannels>> immutable =
				Map.copyOf(loaded);
		ANIMATION_BONES.put(resource, immutable);
		return immutable;
	}

	private static TreeMap<Double, Vec3> channel(
			com.google.gson.JsonElement source) {
		if (source == null) {
			return null;
		}
		TreeMap<Double, Vec3> frames = new TreeMap<>();
		if (source.isJsonArray()) {
			Vec3 constant = vector(source);
			if (constant != null) {
				frames.put(0.0D, constant);
				return frames;
			}
			return null;
		}
		if (!source.isJsonObject()) {
			return null;
		}
		for (var frame : source.getAsJsonObject().entrySet()) {
			var value = frame.getValue();
			if (value.isJsonObject()) {
				var object = value.getAsJsonObject();
				if (object.has("post")) {
					value = object.get("post");
				} else if (object.has("vector")) {
					value = object.get("vector");
				}
			}
			Vec3 vector = vector(value);
			if (vector != null) {
				frames.put(Double.parseDouble(frame.getKey()), vector);
			}
		}
		return frames.isEmpty() ? null : frames;
	}

	private static Vec3 vector(com.google.gson.JsonElement value) {
		if (value == null || !value.isJsonArray()
				|| value.getAsJsonArray().size() != 3) {
			return null;
		}
		return new Vec3(value.getAsJsonArray().get(0).getAsFloat(),
				value.getAsJsonArray().get(1).getAsFloat(),
				value.getAsJsonArray().get(2).getAsFloat());
	}

	private static Vec3 sample(TreeMap<Double, Vec3> frames, double seconds) {
		if (frames == null || frames.isEmpty()) {
			return null;
		}
		var first = frames.firstEntry();
		var last = frames.lastEntry();
		if (seconds <= first.getKey()) {
			return first.getValue();
		}
		if (seconds >= last.getKey()) {
			return last.getValue();
		}
		var before = frames.floorEntry(seconds);
		var after = frames.ceilingEntry(seconds);
		double span = after.getKey() - before.getKey();
		float t = span <= 1.0E-8D ? 1.0F
				: (float) ((seconds - before.getKey()) / span);
		Vec3 a = before.getValue();
		Vec3 b = after.getValue();
		return new Vec3(a.x() + (b.x() - a.x()) * t,
				a.y() + (b.y() - a.y()) * t,
				a.z() + (b.z() - a.z()) * t);
	}

	private record BoneChannels(TreeMap<Double, Vec3> rotation,
			TreeMap<Double, Vec3> position, TreeMap<Double, Vec3> scale) {
	}

	private record Vec3(float x, float y, float z) {
	}

	private WarBannerHeldPose() {
	}
}
