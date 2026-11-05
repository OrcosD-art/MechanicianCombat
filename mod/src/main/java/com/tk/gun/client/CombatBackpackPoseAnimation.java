package com.tk.gun.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.TkGun;
import com.tk.gun.item.CombatBackpackAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Small deterministic sampler for the jump backpack's hand-drawn states.
 *
 * <p>The accessory renderer is invoked manually by several rendering layers.
 * Driving these bones directly avoids depending on the item animation manager's
 * tick ownership, which is not stable when the same accessory renderer is used
 * for the local and remote players.</p>
 */
public final class CombatBackpackPoseAnimation {
	private static final Identifier RESOURCE =
			TkGun.id("animations/entity/combat_backpack.animation.json");
	private static final Identifier JUMP_COOLDOWN_RESOURCE =
			TkGun.id("animations/entity/jump_backpack_states.animation.json");
	private static Map<String, Clip> clips;

	public static void clearCache() {
		clips = null;
	}

	public static String clipName(CombatBackpackAnimation.Phase phase) {
		return switch (phase) {
			case READY_TO_FLY -> "ready_to_fly";
			case FLYING -> "flying";
			case CLOSE -> "close";
			case CD, READY -> "jump_cooldown";
			default -> null;
		};
	}

	public static void apply(GeoBone bone, CombatBackpackAnimation.Phase phase,
			double seconds) {
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

		String name = clipName(phase);
		if (name == null) {
			return;
		}
		Clip clip = clips().get(name);
		BoneChannels channels = clip == null ? null : clip.bones().get(bone.getName());
		if (channels == null) {
			return;
		}
		Vec3 rotation = sample(channels.rotation(), seconds);
		Vec3 position = sample(channels.position(), seconds);
		Vec3 scale = sample(channels.scale(), seconds);
		if (rotation != null) {
			// GeckoLib animation rotations are additive to the bind pose and stored
			// in radians. The authored JSON stores degrees. Writing those raw values
			// caused values such as 45 degrees to become 45 radians.
			float baseX = initial == null ? 0.0F : initial.getRotX();
			float baseY = initial == null ? 0.0F : initial.getRotY();
			float baseZ = initial == null ? 0.0F : initial.getRotZ();
			bone.setRotX(baseX + (float) Math.toRadians(rotation.x()));
			bone.setRotY(baseY + (float) Math.toRadians(rotation.y()));
			bone.setRotZ(baseZ + (float) Math.toRadians(rotation.z()));
		}
		if (position != null) {
			bone.setPosX(position.x());
			bone.setPosY(position.y());
			bone.setPosZ(position.z());
		}
		if (scale != null) {
			bone.setScaleX(scale.x());
			bone.setScaleY(scale.y());
			bone.setScaleZ(scale.z());
		}
	}

	private static Map<String, Clip> clips() {
		if (clips != null) {
			return clips;
		}
		Map<String, Clip> loaded = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			loadClips(client, loaded, RESOURCE);
			loadClips(client, loaded, JUMP_COOLDOWN_RESOURCE);
		} catch (Exception exception) {
			TkGun.LOGGER.error("[tkgun] Failed to load backpack state animation {}",
					RESOURCE, exception);
		}
		clips = Map.copyOf(loaded);
		return clips;
	}

	private static void loadClips(MinecraftClient client, Map<String, Clip> loaded,
			Identifier resourceId) throws Exception {
		var resource = client.getResourceManager().getResource(resourceId)
				.orElseThrow();
		try (var stream = resource.getInputStream();
			 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject animations = JsonParser.parseReader(reader)
					.getAsJsonObject().getAsJsonObject("animations");
			for (Map.Entry<String, JsonElement> animation : animations.entrySet()) {
				JsonObject bones = animation.getValue().getAsJsonObject()
						.getAsJsonObject("bones");
				Map<String, BoneChannels> parsed = new HashMap<>();
				if (bones != null) {
					for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
						JsonObject channels = bone.getValue().getAsJsonObject();
						parsed.put(bone.getKey(), new BoneChannels(
								channel(channels, "rotation"),
								channel(channels, "position"),
								channel(channels, "scale")));
					}
				}
				loaded.put(animation.getKey(), new Clip(Map.copyOf(parsed)));
			}
		}
	}

	private static TreeMap<Double, Vec3> channel(JsonObject bone, String name) {
		JsonElement source = bone.get(name);
		if (source == null || !source.isJsonObject()) {
			return null;
		}
		TreeMap<Double, Vec3> frames = new TreeMap<>();
		JsonObject sourceObject = source.getAsJsonObject();
		if (sourceObject.has("vector")) {
			Vec3 constant = vector(sourceObject.get("vector"));
			if (constant != null) {
				frames.put(0.0D, constant);
			}
			return frames.isEmpty() ? null : frames;
		}
		for (Map.Entry<String, JsonElement> frame : sourceObject.entrySet()) {
			JsonElement value = frame.getValue().isJsonObject()
					? frame.getValue().getAsJsonObject().get("vector")
					: frame.getValue();
			Vec3 vector = vector(value);
			if (vector == null) {
				continue;
			}
			frames.put(Double.parseDouble(frame.getKey()), vector);
		}
		return frames.isEmpty() ? null : frames;
	}

	private static Vec3 vector(JsonElement value) {
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
		Map.Entry<Double, Vec3> first = frames.firstEntry();
		Map.Entry<Double, Vec3> last = frames.lastEntry();
		if (seconds <= first.getKey()) {
			return first.getValue();
		}
		if (seconds >= last.getKey()) {
			return last.getValue();
		}
		Map.Entry<Double, Vec3> before = frames.floorEntry(seconds);
		Map.Entry<Double, Vec3> after = frames.ceilingEntry(seconds);
		if (before == null || after == null) {
			return after == null ? before.getValue() : after.getValue();
		}
		double span = after.getKey() - before.getKey();
		if (span <= 1.0E-8D) {
			return after.getValue();
		}
		float t = (float) ((seconds - before.getKey()) / span);
		Vec3 a = before.getValue();
		Vec3 b = after.getValue();
		return new Vec3(a.x() + (b.x() - a.x()) * t,
				a.y() + (b.y() - a.y()) * t,
				a.z() + (b.z() - a.z()) * t);
	}

	private record Clip(Map<String, BoneChannels> bones) {
	}

	private record BoneChannels(TreeMap<Double, Vec3> rotation,
			TreeMap<Double, Vec3> position,
			TreeMap<Double, Vec3> scale) {
	}

	private record Vec3(float x, float y, float z) {
	}

	private CombatBackpackPoseAnimation() {
	}
}
