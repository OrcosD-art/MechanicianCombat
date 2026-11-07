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
	private static final Identifier RESOURCE =
			TkGun.id("animations/entity/war_banner_held.animation.json");
	private static Map<String, BoneChannels> flagBones;

	static void clearCache() {
		flagBones = null;
	}

	static void apply(GeoBone bone, double seconds) {
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
		BoneChannels channels = channels(bone.getName());
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

	private static BoneChannels channels(String boneName) {
		load();
		return flagBones.get(boneName);
	}

	private static void load() {
		if (flagBones != null) {
			return;
		}
		Map<String, BoneChannels> loaded = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		try (var stream = client.getResourceManager().getResource(RESOURCE)
				.orElseThrow().getInputStream();
			 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			var animation = com.google.gson.JsonParser.parseReader(reader)
					.getAsJsonObject().getAsJsonObject("animations")
					.getAsJsonObject("skill_flag").getAsJsonObject("bones");
			for (String boneName : animation.keySet()) {
				var channels = animation.getAsJsonObject(boneName);
				loaded.put(boneName, new BoneChannels(
						channel(channels.get("rotation")),
						channel(channels.get("position")),
						channel(channels.get("scale"))));
			}
		} catch (Exception exception) {
			TkGun.LOGGER.error("[tkgun] Failed to load held War Banner pose {}",
					RESOURCE, exception);
		}
		flagBones = Map.copyOf(loaded);
	}

	private static TreeMap<Double, Vec3> channel(
			com.google.gson.JsonElement source) {
		if (source == null || !source.isJsonObject()) {
			return null;
		}
		TreeMap<Double, Vec3> frames = new TreeMap<>();
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
