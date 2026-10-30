import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Merge the current Silent Storm GeckoLib geometry and animations into the
 * editable player Blockbench project while retaining the player skeleton.
 */
public final class GenerateSilentStormPlayerTemplate {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().setPrettyPrinting().create();

	public static void main(String[] args) throws Exception {
		Path baseBbmodel = Path.of(args[0]);
		Path currentGeo = Path.of(args[1]);
		Path currentAnimation = Path.of(args[2]);
		Path gunTexture = Path.of(args[3]);
		Path output = Path.of(args[4]);

		JsonObject document = readBbmodel(baseBbmodel);
		JsonObject geometry = read(currentGeo).getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject();
		JsonObject animationDocument = read(currentAnimation);

		document.addProperty("name", "静谧风暴_玩家武器动画模板_r517");
		document.addProperty("model_identifier",
				"tkgun:silent_storm_player_weapon_template");

		JsonArray outliner = document.getAsJsonArray("outliner");
		removeGroupRecursively(outliner, "gun");
		JsonArray elements = document.getAsJsonArray("elements");
		Set<String> referenced = new TreeSet<>();
		collectElementReferences(outliner, referenced);
		for (int index = elements.size() - 1; index >= 0; index--) {
			String uuid = elements.get(index).getAsJsonObject()
					.get("uuid").getAsString().trim();
			if (!referenced.contains(uuid)) {
				elements.remove(index);
			}
		}

		JsonObject chest = findGroupRecursively(outliner, "chest");
		if (chest == null) {
			throw new IllegalStateException("Player template has no chest group");
		}

		int textureIndex = nextTextureIndex(document.getAsJsonArray("textures"));
		JsonObject texture = new JsonObject();
		texture.addProperty("id", Integer.toString(textureIndex));
		texture.addProperty("name", gunTexture.getFileName().toString());
		texture.addProperty("source", "data:image/png;base64,"
				+ Base64.getEncoder().encodeToString(Files.readAllBytes(gunTexture)));
		document.getAsJsonArray("textures").add(texture);

		JsonArray bones = geometry.getAsJsonArray("bones");
		Map<String, JsonObject> boneByName = new LinkedHashMap<>();
		for (JsonElement element : bones) {
			JsonObject bone = element.getAsJsonObject();
			boneByName.put(bone.get("name").getAsString(), bone);
		}
		Map<String, JsonObject> newGroups = new LinkedHashMap<>();
		Map<String, String> parentByName = new HashMap<>();
		for (Map.Entry<String, JsonObject> entry : boneByName.entrySet()) {
			String name = entry.getKey();
			if (!isGunSubtree(name, boneByName)) {
				continue;
			}
			JsonObject bone = entry.getValue();
			JsonObject group = new JsonObject();
			group.addProperty("name", name);
			group.add("origin", vector(bone.get("pivot")));
			if (bone.has("rotation")) {
				group.add("rotation", vector(bone.get("rotation")));
			}
			group.addProperty("color", 0);
			group.addProperty("uuid", stableUuid("group:" + name));
			group.addProperty("export", true);
			group.addProperty("mirror_uv", false);
			group.addProperty("isOpen", true);
			group.addProperty("locked", false);
			group.addProperty("visibility", true);
			group.addProperty("autouv", 0);
			group.addProperty("selected", false);
			JsonArray children = new JsonArray();
			JsonArray cubes = bone.getAsJsonArray("cubes");
			if (cubes != null) {
				for (JsonElement cubeElement : cubes) {
					JsonObject cube = cubeElement.getAsJsonObject();
					JsonObject element = convertCube(cube, vector(bone.get("pivot")),
							textureIndex, name);
					elements.add(element);
					children.add(element.get("uuid").getAsString());
				}
			}
			group.add("children", children);
			newGroups.put(name, group);
			if (bone.has("parent")) {
				parentByName.put(name, bone.get("parent").getAsString());
			}
		}
		for (String name : newGroups.keySet()) {
			if (!"gun".equals(name)) {
				putChild(newGroups.get(parentByName.get(name)), newGroups.get(name));
			}
		}
		if (!newGroups.containsKey("gun")) {
			throw new IllegalStateException("Current geometry has no gun group");
		}
		putChild(chest, newGroups.get("gun"));

		Map<String, String> boneUuids = new LinkedHashMap<>();
		collectBoneUuids(outliner, boneUuids);
		JsonArray animations = convertAnimations(
				animationDocument.getAsJsonObject("animations"), boneUuids);
		JsonObject skillTemplate = new JsonObject();
		skillTemplate.addProperty("uuid", stableUuid("animation:Skill_Action_Template"));
		skillTemplate.addProperty("name", "Skill_Action_Template");
		skillTemplate.addProperty("loop", "hold");
		skillTemplate.addProperty("override", false);
		skillTemplate.addProperty("length", 1.0D);
		skillTemplate.addProperty("snapping", 24);
		skillTemplate.addProperty("selected", false);
		skillTemplate.addProperty("anim_time_update", "");
		skillTemplate.addProperty("blend_weight", "");
		skillTemplate.addProperty("start_delay", "");
		skillTemplate.addProperty("loop_delay", "");
		skillTemplate.add("animators", new JsonObject());
		animations.add(skillTemplate);
		document.add("animations", animations);

		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, GSON.toJson(document), StandardCharsets.UTF_8);
		System.out.println("Template: " + output);
		System.out.println("Elements: " + elements.size());
		System.out.println("Bones: " + boneUuids.size());
		System.out.println("Animations: " + animations.size());
		System.out.println("Silent Storm texture index: " + textureIndex);
	}

	private static JsonObject convertCube(JsonObject cube, JsonArray pivot,
			int textureIndex, String boneName) {
		JsonObject element = new JsonObject();
		element.addProperty("name", "cube");
		element.addProperty("box_uv", false);
		element.addProperty("rescale", false);
		element.addProperty("locked", false);
		element.addProperty("light_emission", 0);
		element.addProperty("render_order", "default");
		element.addProperty("allow_mirror_modeling", true);
		element.add("from", vector(cube.get("origin")));
		JsonArray size = cube.getAsJsonArray("size");
		JsonArray from = cube.getAsJsonArray("origin");
		JsonArray to = new JsonArray();
		for (int axis = 0; axis < 3; axis++) {
			to.add(from.get(axis).getAsDouble() + size.get(axis).getAsDouble());
		}
		element.add("to", to);
		element.addProperty("autouv", 0);
		element.addProperty("color", 1);
		element.add("origin", cube.has("pivot") ? vector(cube.get("pivot"))
				: pivot.deepCopy());
		if (cube.has("rotation")) {
			element.add("rotation", vector(cube.get("rotation")));
		}
		JsonObject faces = new JsonObject();
		JsonObject sourceUv = cube.getAsJsonObject("uv");
		if (sourceUv != null) {
			for (Map.Entry<String, JsonElement> entry : sourceUv.entrySet()) {
				JsonObject sourceFace = entry.getValue().getAsJsonObject();
				JsonArray uv = sourceFace.getAsJsonArray("uv");
				JsonArray uvSize = sourceFace.getAsJsonArray("uv_size");
				JsonObject face = new JsonObject();
				JsonArray points = new JsonArray();
				points.add(uv.get(0).getAsDouble());
				points.add(uv.get(1).getAsDouble());
				points.add(uv.get(0).getAsDouble() + uvSize.get(0).getAsDouble());
				points.add(uv.get(1).getAsDouble() + uvSize.get(1).getAsDouble());
				face.add("uv", points);
				if (sourceFace.has("uv_rotation")) {
					face.addProperty("rotation",
							sourceFace.get("uv_rotation").getAsDouble());
				}
				face.addProperty("texture", textureIndex);
				faces.add(entry.getKey(), face);
			}
		}
		element.add("faces", faces);
		element.addProperty("type", "cube");
		element.addProperty("uuid", stableUuid(
				"cube:" + boneName + ":" + GSON.toJson(cube)));
		return element;
	}

	private static JsonArray convertAnimations(JsonObject sourceAnimations,
			Map<String, String> boneUuids) {
		JsonArray result = new JsonArray();
		for (Map.Entry<String, JsonElement> entry : sourceAnimations.entrySet()) {
			String name = entry.getKey();
			JsonObject source = entry.getValue().getAsJsonObject();
			JsonObject animation = new JsonObject();
			animation.addProperty("uuid", stableUuid("animation:" + name));
			animation.addProperty("name", name);
			animation.addProperty("loop", convertLoop(source.get("loop")));
			animation.addProperty("override", false);
			animation.addProperty("length",
					source.get("animation_length").getAsDouble());
			animation.addProperty("snapping", 24);
			animation.addProperty("selected", false);
			animation.addProperty("anim_time_update", "");
			animation.addProperty("blend_weight", "");
			animation.addProperty("start_delay", "");
			animation.addProperty("loop_delay", "");

			JsonObject animators = new JsonObject();
			JsonObject sourceBones = source.getAsJsonObject("bones");
			if (sourceBones != null) {
				for (Map.Entry<String, JsonElement> boneEntry : sourceBones.entrySet()) {
					String boneName = boneEntry.getKey();
					String boneUuid = boneUuids.get(boneName);
					if (boneUuid == null) {
						continue;
					}
					JsonArray keyframes = convertBoneChannels(
							boneEntry.getValue().getAsJsonObject());
					if (keyframes.isEmpty()) {
						continue;
					}
					JsonObject animator = new JsonObject();
					animator.addProperty("name", boneName);
					animator.addProperty("type", "bone");
					animator.add("keyframes", keyframes);
					animators.add(boneUuid, animator);
				}
			}

			JsonArray effects = convertEffects(source);
			if (!effects.isEmpty()) {
				JsonObject effectAnimator = new JsonObject();
				effectAnimator.addProperty("name", "effects");
				effectAnimator.addProperty("type", "effect");
				effectAnimator.add("keyframes", effects);
				animators.add(stableUuid("effect:" + name), effectAnimator);
			}
			animation.add("animators", animators);
			result.add(animation);
		}
		return result;
	}

	private static JsonArray convertBoneChannels(JsonObject channels) {
		JsonArray result = new JsonArray();
		for (Map.Entry<String, JsonElement> channelEntry : channels.entrySet()) {
			String channel = channelEntry.getKey();
			if (!List.of("position", "rotation", "scale").contains(channel)) {
				continue;
			}
			TreeMap<Double, JsonElement> points = new TreeMap<>();
			TreeSet<Double> stepAt = new TreeSet<>();
			JsonElement channelValue = channelEntry.getValue();
			if (!channelValue.isJsonObject()) {
				points.put(0.0D, channelValue.deepCopy());
			} else {
				JsonObject values = channelValue.getAsJsonObject();
				if (values.has("vector")) {
					points.put(0.0D, values.get("vector").deepCopy());
				} else {
					for (Map.Entry<String, JsonElement> value : values.entrySet()) {
						double time = Double.parseDouble(value.getKey());
						JsonElement point = value.getValue();
						if (point.isJsonObject()) {
							JsonObject object = point.getAsJsonObject();
							if (object.has("vector")) {
								points.put(time, object.get("vector").deepCopy());
								continue;
							}
							if (object.has("post")) {
								points.put(time, object.get("post").deepCopy());
								stepAt.add(time);
								continue;
							}
						}
						points.put(time, point.deepCopy());
					}
				}
			}
			List<Double> times = new ArrayList<>(points.keySet());
			for (int index = 0; index < times.size(); index++) {
				double time = times.get(index);
				JsonObject keyframe = new JsonObject();
				keyframe.addProperty("channel", channel);
				JsonArray dataPoints = new JsonArray();
				JsonObject point = new JsonObject();
				JsonArray vector = normalizedVector(points.get(time));
				point.add("x", primitive(vector, 0));
				point.add("y", primitive(vector, 1));
				point.add("z", primitive(vector, 2));
				dataPoints.add(point);
				keyframe.add("data_points", dataPoints);
				keyframe.addProperty("uuid", stableUuid(
						channel + ":" + time + ":" + GSON.toJson(vector)));
				keyframe.addProperty("time", time);
				keyframe.addProperty("color", -1);
				boolean step = index + 1 < times.size()
						&& stepAt.contains(times.get(index + 1));
				keyframe.addProperty("interpolation", step ? "step" : "linear");
				result.add(keyframe);
			}
		}
		return result;
	}

	private static JsonArray convertEffects(JsonObject source) {
		JsonArray result = new JsonArray();
		addEffectKeys(result, source.getAsJsonObject("sound_effects"), "sound", false);
		addEffectKeys(result, source.getAsJsonObject("particle_effects"), "particle", false);
		JsonObject timeline = source.getAsJsonObject("timeline");
		if (timeline != null) {
			for (Map.Entry<String, JsonElement> entry : timeline.entrySet()) {
				JsonObject keyframe = new JsonObject();
				keyframe.addProperty("channel", "timeline");
				JsonArray dataPoints = new JsonArray();
				JsonObject point = new JsonObject();
				point.addProperty("script", entry.getValue().getAsString());
				dataPoints.add(point);
				keyframe.add("data_points", dataPoints);
				keyframe.addProperty("uuid", stableUuid(
						"timeline:" + entry.getKey()));
				keyframe.addProperty("time", Double.parseDouble(entry.getKey()));
				keyframe.addProperty("color", -1);
				keyframe.addProperty("interpolation", "linear");
				result.add(keyframe);
			}
		}
		return result;
	}

	private static void addEffectKeys(JsonArray result, JsonObject effects,
			String channel, boolean unused) {
		if (effects == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : effects.entrySet()) {
			JsonObject sourcePoint = entry.getValue().getAsJsonObject();
			JsonObject keyframe = new JsonObject();
			keyframe.addProperty("channel", channel);
			JsonArray dataPoints = new JsonArray();
			JsonObject point = new JsonObject();
			point.addProperty("effect",
					sourcePoint.has("effect") ? sourcePoint.get("effect").getAsString() : "");
			point.addProperty("locator",
					sourcePoint.has("locator") ? sourcePoint.get("locator").getAsString() : "");
			dataPoints.add(point);
			keyframe.add("data_points", dataPoints);
			keyframe.addProperty("uuid", stableUuid(
					channel + ":" + entry.getKey()));
			keyframe.addProperty("time", Double.parseDouble(entry.getKey()));
			keyframe.addProperty("color", -1);
			keyframe.addProperty("interpolation", "linear");
			result.add(keyframe);
		}
	}

	private static JsonPrimitive primitive(JsonArray vector, int axis) {
		JsonElement value = vector.get(axis);
		if (value.getAsJsonPrimitive().isNumber()) {
			return new JsonPrimitive(value.getAsDouble());
		}
		return new JsonPrimitive(value.getAsString());
	}

	private static JsonArray normalizedVector(JsonElement value) {
		if (value.isJsonArray()) {
			return value.getAsJsonArray();
		}
		JsonArray result = new JsonArray();
		if (value.getAsJsonPrimitive().isNumber()) {
			double scalar = value.getAsDouble();
			result.add(scalar);
			result.add(scalar);
			result.add(scalar);
		} else {
			String scalar = value.getAsString();
			result.add(scalar);
			result.add(scalar);
			result.add(scalar);
		}
		return result;
	}

	private static String convertLoop(JsonElement loop) {
		if (loop == null) {
			return "once";
		}
		if (loop.getAsJsonPrimitive().isBoolean()) {
			return loop.getAsBoolean() ? "loop" : "hold";
		}
		String value = loop.getAsString();
		return "hold_on_last_frame".equals(value) || "hold".equals(value)
				? "hold" : "once";
	}

	private static boolean isGunSubtree(String name,
			Map<String, JsonObject> bones) {
		String current = name;
		while (bones.containsKey(current)) {
			if ("gun".equals(current)) {
				return true;
			}
			JsonObject bone = bones.get(current);
			if (!bone.has("parent")) {
				return false;
			}
			current = bone.get("parent").getAsString();
		}
		return false;
	}

	private static void removeGroupRecursively(JsonArray nodes, String name) {
		for (int index = nodes.size() - 1; index >= 0; index--) {
			JsonElement element = nodes.get(index);
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			if (name.equals(node.get("name").getAsString())) {
				nodes.remove(index);
				continue;
			}
			if (node.has("children")) {
				removeGroupRecursively(node.getAsJsonArray("children"), name);
			}
		}
	}

	private static void collectElementReferences(JsonArray nodes, Set<String> refs) {
		for (JsonElement element : nodes) {
			if (element.isJsonPrimitive()) {
				refs.add(element.getAsString().trim());
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			if (node.has("children")) {
				collectElementReferences(node.getAsJsonArray("children"), refs);
			}
		}
	}

	private static JsonObject findGroupRecursively(JsonArray nodes, String name) {
		for (JsonElement element : nodes) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			if (name.equals(node.get("name").getAsString())) {
				return node;
			}
			if (node.has("children")) {
				JsonObject found = findGroupRecursively(node.getAsJsonArray("children"), name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static void collectBoneUuids(JsonArray nodes, Map<String, String> result) {
		for (JsonElement element : nodes) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			result.put(node.get("name").getAsString(), node.get("uuid").getAsString());
			if (node.has("children")) {
				collectBoneUuids(node.getAsJsonArray("children"), result);
			}
		}
	}

	private static void putChild(JsonObject parent, JsonObject child) {
		if (parent == null || child == null) {
			return;
		}
		JsonArray children = parent.getAsJsonArray("children");
		if (children == null) {
			children = new JsonArray();
			parent.add("children", children);
		}
		children.add(child);
	}

	private static int nextTextureIndex(JsonArray textures) {
		int maximum = -1;
		for (JsonElement element : textures) {
			try {
				maximum = Math.max(maximum,
						Integer.parseInt(element.getAsJsonObject().get("id").getAsString()));
			} catch (RuntimeException ignored) {
			}
		}
		return maximum + 1;
	}

	private static JsonArray vector(JsonElement value) {
		if (value == null) {
			JsonArray zero = new JsonArray();
			zero.add(0);
			zero.add(0);
			zero.add(0);
			return zero;
		}
		return value.deepCopy().getAsJsonArray();
	}

	private static String stableUuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static JsonObject read(Path path) throws Exception {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private static JsonObject readBbmodel(Path path) throws Exception {
		String text = Files.readString(path, StandardCharsets.UTF_8);
		text = text.replaceAll("(?<!\\\\)\\\\(?![\"\\\\/bfnrtu])", "\\\\\\\\");
		return GSON.fromJson(text, JsonObject.class);
	}
}
