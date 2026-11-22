package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Independent-clock sampling for the Kinetic Sword rotate effect groups. */
public final class KineticSwordRotateEffect {
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/kinetic_sword_rig.animation.json");
	private static final Map<String, Map<String, BoneChannels>> CACHE =
			new HashMap<>();
	private static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);
	private static final Vec3 ONE = new Vec3(1.0F, 1.0F, 1.0F);

	public static boolean isEffectBone(String name) {
		return "rotate_effect".equals(name)
				|| name != null && name.startsWith("effect_step_");
	}

	public static boolean isVisible(Entity entity, String animation,
			float partialTick) {
		GunClientState.Entry entry = entity == null ? null
				: GunClientState.get(entity.getId());
		if (entry == null || entry.state != TkGun.State.MELEE
				|| entry.meleeReturning || animation == null) {
			return false;
		}
		double seconds = (GunClientState.effectElapsed(entity.getId())
				+ partialTick) / 20.0D;
		for (int step = 1; step <= 5; step++) {
			BoneChannels channels = channels(animation,
					"effect_step_" + step);
			Vec3 scale = sample(channels == null ? null
					: channels.scale(), seconds);
			if (scale != null && Math.abs(scale.x()) > 1.0E-4F
					&& Math.abs(scale.y()) > 1.0E-4F
					&& Math.abs(scale.z()) > 1.0E-4F) {
				return true;
			}
		}
		return false;
	}

	public static void apply(GeoBone bone, Entity entity, String animation,
			float partialTick) {
		if (!isEffectBone(bone.getName())) {
			return;
		}
		GunClientState.Entry entry = entity == null ? null
				: GunClientState.get(entity.getId());
		if (entry == null || entry.state != TkGun.State.MELEE
				|| entry.meleeReturning || animation == null) {
			hide(bone);
			return;
		}
		BoneChannels channels = channels(animation, bone.getName());
		double seconds = (GunClientState.effectElapsed(entity.getId())
				+ partialTick) / 20.0D;
		if ("rotate_effect".equals(bone.getName())) {
			Vec3 rotation = sample(channels == null ? null
					: channels.rotation(), seconds);
			if (rotation == null) {
				rotation = ZERO;
			}
			bone.setRotX((float) Math.toRadians(rotation.x()));
			bone.setRotY((float) Math.toRadians(rotation.y()));
			bone.setRotZ((float) Math.toRadians(rotation.z()));
			Vec3 scale = sample(channels == null ? null
					: channels.scale(), seconds);
			setScale(bone, scale == null ? ONE : scale);
			return;
		}
		Vec3 scale = sample(channels == null ? null
				: channels.scale(), seconds);
		setScale(bone, scale == null ? ZERO : scale);
	}

	private static void setScale(GeoBone bone, Vec3 scale) {
		bone.setScaleX(scale.x());
		bone.setScaleY(scale.y());
		bone.setScaleZ(scale.z());
	}

	private static void hide(GeoBone bone) {
		setScale(bone, ZERO);
	}

	private static BoneChannels channels(String animation, String bone) {
		Map<String, BoneChannels> bones = load().get(animation);
		return bones == null ? null : bones.get(bone);
	}

	private static Map<String, Map<String, BoneChannels>> load() {
		if (!CACHE.isEmpty()) {
			return CACHE;
		}
		try (var stream = MinecraftClient.getInstance().getResourceManager()
				.getResource(ANIMATION).orElseThrow().getInputStream();
			 var reader = new InputStreamReader(stream,
						StandardCharsets.UTF_8)) {
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
					if (!isEffectBone(boneName)) {
						continue;
					}
					var bone = bones.getAsJsonObject(boneName);
					parsed.put(boneName, new BoneChannels(
							channel(bone.get("rotation")),
							channel(bone.get("scale"))));
				}
				CACHE.put(animationName, Map.copyOf(parsed));
			}
		} catch (Exception exception) {
			TkGun.LOGGER.error(
					"[mechaniciancombat] Failed to load Kinetic Sword rotate effect",
					exception);
		}
		return CACHE;
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
			if (value.isJsonObject() && value.getAsJsonObject().has("post")) {
				value = value.getAsJsonObject().get("post");
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
		var frame = frames.floorEntry(seconds);
		return frame == null ? frames.firstEntry().getValue()
				: frame.getValue();
	}

	private record BoneChannels(TreeMap<Double, Vec3> rotation,
			TreeMap<Double, Vec3> scale) {
	}

	private record Vec3(float x, float y, float z) {
	}

	private KineticSwordRotateEffect() {
	}
}
