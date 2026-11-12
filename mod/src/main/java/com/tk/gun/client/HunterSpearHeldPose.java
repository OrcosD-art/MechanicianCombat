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

/** Evaluates the spear deployment clip for the rendered player prop. */
final class HunterSpearHeldPose {
	private static final Identifier RESOURCE =
			TkGun.id("animations/entity/hunter_spear_deploy.animation.json");
	private static Map<String, BoneChannels> bones;

	static void clearCache() {
		bones = null;
	}

	static void apply(GeoBone bone, double seconds) {
		BoneSnapshot initial = bone.getInitialSnapshot();
		if (initial == null) {
			return;
		}
		bone.setPosX(initial.getOffsetX());
		bone.setPosY(initial.getOffsetY());
		bone.setPosZ(initial.getOffsetZ());
		bone.setRotX(initial.getRotX());
		bone.setRotY(initial.getRotY());
		bone.setRotZ(initial.getRotZ());
		bone.setScaleX(initial.getScaleX());
		bone.setScaleY(initial.getScaleY());
		bone.setScaleZ(initial.getScaleZ());

		BoneChannels channels = channels(bone.getName());
		if (channels == null) {
			return;
		}
		Vec3 rotation = sample(channels.rotation(), seconds);
		Vec3 position = sample(channels.position(), seconds);
		Vec3 scale = sample(channels.scale(), seconds);
		if (rotation != null) {
			bone.setRotX(initial.getRotX()
					+ (float) Math.toRadians(rotation.x()));
			bone.setRotY(initial.getRotY()
					+ (float) Math.toRadians(rotation.y()));
			bone.setRotZ(initial.getRotZ()
					+ (float) Math.toRadians(rotation.z()));
		}
		if (position != null) {
			bone.setPosX(initial.getOffsetX() + position.x());
			bone.setPosY(initial.getOffsetY() + position.y());
			bone.setPosZ(initial.getOffsetZ() + position.z());
		}
		if (scale != null) {
			bone.setScaleX(initial.getScaleX() * scale.x());
			bone.setScaleY(initial.getScaleY() * scale.y());
			bone.setScaleZ(initial.getScaleZ() * scale.z());
		}
	}

	private static BoneChannels channels(String boneName) {
		load();
		return bones.get(boneName);
	}

	private static void load() {
		if (bones != null) {
			return;
		}
		Map<String, BoneChannels> loaded = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		try (var stream = client.getResourceManager().getResource(RESOURCE)
				.orElseThrow().getInputStream();
			 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			var animation = com.google.gson.JsonParser.parseReader(reader)
					.getAsJsonObject().getAsJsonObject("animations")
					.getAsJsonObject("skill_flag")
					.getAsJsonObject("bones");
			for (String boneName : animation.keySet()) {
				var channels = animation.getAsJsonObject(boneName);
				loaded.put(boneName, new BoneChannels(
						channel(channels.get("rotation")),
						channel(channels.get("position")),
						channel(channels.get("scale"))));
			}
		} catch (Exception exception) {
			TkGun.LOGGER.error(
					"[mechaniciancombat] Failed to load hunter spear deploy pose {}",
					RESOURCE, exception);
		}
		bones = Map.copyOf(loaded);
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
		return new Vec3(
				a.x() + (b.x() - a.x()) * t,
				a.y() + (b.y() - a.y()) * t,
				a.z() + (b.z() - a.z()) * t);
	}

	private record BoneChannels(TreeMap<Double, Vec3> rotation,
			TreeMap<Double, Vec3> position, TreeMap<Double, Vec3> scale) {
	}

	private record Vec3(float x, float y, float z) {
	}

	private HunterSpearHeldPose() {
	}
}
