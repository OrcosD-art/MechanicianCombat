import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Syncs only the authored effect bones, effect animation channels, and effect
 * textures from the Kinetic Sword bbmodel into the runtime assets.
 */
public final class SyncKineticSwordEffects {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().create();
	private static final List<String> CUBE_GROUPS = List.of(
			"effect_step_1", "effect_step_2", "effect_step_3",
			"effect_step_4", "effect_step_5");

	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			throw new IllegalArgumentException(
					"usage: SyncKineticSwordEffects <bbmodel> <geo> <animation>"
							+ " <energyPng> <rotatePng>");
		}
		JsonObject model = read(args[0]);
		JsonObject geometry = read(args[1]);
		JsonObject animation = read(args[2]);
		syncGeometry(model, geometry);
		syncRotateAnimations(model, animation);
		syncAttack6(model, animation);
		write(args[1], geometry);
		write(args[2], animation);
		writeTexture(model, 1, Path.of(args[3]));
		writeTexture(model, 2, Path.of(args[4]));
		System.out.println("Synced Kinetic Sword effect groups and textures.");
	}

	private static JsonObject read(String path) throws Exception {
		return JsonParser.parseString(Files.readString(Path.of(path),
				StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void write(String path, JsonObject document)
			throws Exception {
		Files.writeString(Path.of(path), GSON.toJson(document),
				StandardCharsets.UTF_8);
	}

	private static void syncGeometry(JsonObject model, JsonObject geometry) {
		JsonObject root = geometry.getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject();
		JsonArray bones = root.getAsJsonArray("bones");
		Map<String, JsonObject> boneByName = new LinkedHashMap<>();
		for (JsonElement element : bones) {
			JsonObject bone = element.getAsJsonObject();
			boneByName.put(bone.get("name").getAsString(), bone);
		}
		Map<String, JsonObject> elements = new LinkedHashMap<>();
		for (JsonElement element : model.getAsJsonArray("elements")) {
			JsonObject cube = element.getAsJsonObject();
			elements.put(cube.get("uuid").getAsString(), cube);
		}
		for (String name : CUBE_GROUPS) {
			JsonObject group = findGroup(model.getAsJsonArray("outliner"),
					name);
			JsonObject bone = boneByName.get(name);
			if (group == null || bone == null) {
				throw new IllegalStateException("Missing effect bone " + name);
			}
			JsonArray sourceOrigin = group.getAsJsonArray("origin");
			bone.add("pivot", array(
					-number(sourceOrigin, 0),
					number(sourceOrigin, 1),
					number(sourceOrigin, 2)));
			JsonArray cubes = new JsonArray();
			collectCubes(group.getAsJsonArray("children"), elements, cubes);
			bone.add("cubes", cubes);
		}
	}

	private static void collectCubes(JsonArray children,
			Map<String, JsonObject> elements, JsonArray output) {
		for (JsonElement child : children) {
			if (child.isJsonPrimitive()) {
				JsonObject source = elements.get(child.getAsString());
				if (source != null) {
					output.add(convertCube(source));
				}
				continue;
			}
			JsonObject group = child.getAsJsonObject();
			JsonArray nested = group.getAsJsonArray("children");
			if (nested != null) {
				collectCubes(nested, elements, output);
			}
		}
	}

	private static JsonObject convertCube(JsonObject source) {
		JsonArray from = source.getAsJsonArray("from");
		JsonArray to = source.getAsJsonArray("to");
		JsonObject cube = new JsonObject();
		cube.add("origin", array(
				-number(to, 0), number(from, 1), number(from, 2)));
		cube.add("size", array(
				number(to, 0) - number(from, 0),
				number(to, 1) - number(from, 1),
				number(to, 2) - number(from, 2)));
		if (source.has("rotation")) {
			JsonArray rotation = source.getAsJsonArray("rotation");
			if (length(rotation) != 0) {
				cube.add("rotation", rotation.deepCopy());
			}
		}
		JsonObject faces = new JsonObject();
		for (Map.Entry<String, JsonElement> entry
				: source.getAsJsonObject("faces").entrySet()) {
			JsonObject face = entry.getValue().getAsJsonObject();
			JsonElement texture = face.get("texture");
			if (texture == null || texture.isJsonNull()) {
				continue;
			}
			JsonArray uv = face.getAsJsonArray("uv");
			JsonObject converted = new JsonObject();
			converted.add("uv", array(number(uv, 0), number(uv, 1)));
			converted.add("uv_size", array(
					number(uv, 2) - number(uv, 0),
					number(uv, 3) - number(uv, 1)));
			if (face.has("rotation")) {
				converted.add("uv_rotation",
						face.get("rotation").deepCopy());
			}
			faces.add(entry.getKey(), converted);
		}
		cube.add("uv", faces);
		return cube;
	}

	private static void syncRotateAnimations(JsonObject model,
			JsonObject animation) {
		JsonObject targets = animation.getAsJsonObject("animations");
		for (JsonElement animationElement
				: model.getAsJsonArray("animations")) {
			JsonObject source = animationElement.getAsJsonObject();
			JsonObject sourceRotate = findAnimator(source, "rotate_effect");
			JsonObject target = targets.getAsJsonObject(
					source.get("name").getAsString());
			if (sourceRotate == null || target == null
					|| !target.has("bones")) {
				continue;
			}
			JsonObject bones = target.getAsJsonObject("bones");
			JsonObject targetRotate = bones.has("rotate_effect")
					? bones.getAsJsonObject("rotate_effect")
					: new JsonObject();
			for (String channel : List.of("rotation", "scale")) {
				TreeMap<Double, JsonArray> values = new TreeMap<>();
				for (JsonElement keyframeElement
						: sourceRotate.getAsJsonArray("keyframes")) {
					JsonObject frame = keyframeElement.getAsJsonObject();
					if (!channel.equals(
							frame.get("channel").getAsString())) {
						continue;
					}
					JsonArray points = frame.getAsJsonArray("data_points");
					if (points == null || points.isEmpty()) {
						continue;
					}
					JsonObject point = points.get(points.size() - 1)
							.getAsJsonObject();
					values.put(rounded(frame.get("time").getAsDouble()),
							array(number(point, "x"), number(point, "y"),
									number(point, "z")));
				}
				if (!values.isEmpty()) {
					JsonObject output = new JsonObject();
					for (Map.Entry<Double, JsonArray> value
							: values.entrySet()) {
						output.add(time(value.getKey()),
								value.getValue());
					}
					targetRotate.add(channel, output);
				}
			}
			if (!targetRotate.isEmpty()) {
				bones.add("rotate_effect", targetRotate);
			}
		}
	}

	private static void syncAttack6(JsonObject model, JsonObject animation) {
		JsonObject source = findAnimation(model, "GS_ATK_6");
		JsonObject target = animation.getAsJsonObject("animations")
				.getAsJsonObject("GS_ATK_6");
		JsonObject bones = new JsonObject();
		for (JsonElement animatorElement
				: source.getAsJsonObject("animators").asMap().values()) {
			JsonObject animator = animatorElement.getAsJsonObject();
			if (!"bone".equals(animator.get("type").getAsString())) {
				continue;
			}
			Map<String, TreeMap<Double, JsonArray>> channels =
					new LinkedHashMap<>();
			Map<String, java.util.Set<Double>> stepped =
					new java.util.HashMap<>();
			for (JsonElement keyframeElement
					: animator.getAsJsonArray("keyframes")) {
				JsonObject keyframe = keyframeElement.getAsJsonObject();
				String channel = keyframe.get("channel").getAsString();
				if (!List.of("rotation", "position", "scale").contains(channel)) {
					continue;
				}
				JsonArray points = keyframe.getAsJsonArray("data_points");
				if (points == null || points.isEmpty()) {
					continue;
				}
				JsonObject point = points.get(points.size() - 1)
						.getAsJsonObject();
				double time = rounded(keyframe.get("time").getAsDouble());
				channels.computeIfAbsent(channel, ignored -> new TreeMap<>())
						.put(time, array(number(point, "x"),
								number(point, "y"), number(point, "z")));
				if ("step".equals(keyframe.get("interpolation").getAsString())) {
					stepped.computeIfAbsent(channel,
							ignored -> new java.util.HashSet<>()).add(time);
				}
			}
			JsonObject bone = new JsonObject();
			for (Map.Entry<String, TreeMap<Double, JsonArray>> entry
					: channels.entrySet()) {
				TreeMap<Double, JsonArray> keys = entry.getValue();
				bakeSteps(keys, stepped.getOrDefault(entry.getKey(),
						java.util.Set.of()));
				JsonObject output = new JsonObject();
				for (Map.Entry<Double, JsonArray> key : keys.entrySet()) {
					output.add(time(key.getKey()), key.getValue().deepCopy());
				}
				bone.add(entry.getKey(), output);
			}
			if (!bone.isEmpty()) {
				bones.add(animator.get("name").getAsString(), bone);
			}
		}
		target.add("bones", bones);
		target.addProperty("animation_length",
				source.get("length").getAsDouble());
		syncAttack6Events(source, target);
	}

	private static void syncAttack6Events(JsonObject source,
			JsonObject target) {
		JsonObject timeline = new JsonObject();
		JsonObject sounds = new JsonObject();
		for (JsonElement animatorElement
				: source.getAsJsonObject("animators").asMap().values()) {
			JsonObject animator = animatorElement.getAsJsonObject();
			if (!"effect".equals(animator.get("type").getAsString())) {
				continue;
			}
			for (JsonElement keyframeElement
					: animator.getAsJsonArray("keyframes")) {
				JsonObject keyframe = keyframeElement.getAsJsonObject();
				JsonArray points = keyframe.getAsJsonArray("data_points");
				if (points == null || points.isEmpty()) {
					continue;
				}
				JsonObject point = points.get(points.size() - 1)
						.getAsJsonObject();
				String key = time(rounded(
						keyframe.get("time").getAsDouble()));
				String channel = keyframe.get("channel").getAsString();
				if ("timeline".equals(channel)) {
					timeline.addProperty(key,
							point.get("script").getAsString());
				} else if ("sound".equals(channel)) {
					JsonObject sound = new JsonObject();
					sound.add("effect", point.get("effect").deepCopy());
					sound.add("locator", point.get("locator").deepCopy());
					sounds.add(key, sound);
				}
			}
		}
		if (!timeline.isEmpty()) {
			target.add("timeline", timeline);
		}
		if (!sounds.isEmpty()) {
			target.add("sound_effects", sounds);
		}
	}

	private static void bakeSteps(TreeMap<Double, JsonArray> keys,
			java.util.Set<Double> stepped) {
		for (Double step : List.copyOf(stepped)) {
			JsonArray value = keys.get(step);
			Double next = keys.higherKey(step);
			if (value == null || next == null) {
				continue;
			}
			double hold = rounded(next - 0.0001D);
			if (hold > step && !keys.containsKey(hold)) {
				keys.put(hold, value.deepCopy());
			}
		}
	}

	private static JsonObject findGroup(JsonArray nodes, String name) {
		for (JsonElement element : nodes) {
			if (element.isJsonPrimitive()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			if (name.equals(node.get("name").getAsString())) {
				return node;
			}
			JsonArray children = node.getAsJsonArray("children");
			if (children != null) {
				JsonObject found = findGroup(children, name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JsonObject findAnimation(JsonObject model, String name) {
		for (JsonElement element : model.getAsJsonArray("animations")) {
			JsonObject animation = element.getAsJsonObject();
			if (name.equals(animation.get("name").getAsString())) {
				return animation;
			}
		}
		return null;
	}

	private static JsonObject findAnimator(JsonObject animation,
			String name) {
		for (Map.Entry<String, JsonElement> entry
				: animation.getAsJsonObject("animators").entrySet()) {
			JsonObject animator = entry.getValue().getAsJsonObject();
			JsonElement animatorName = animator.get("name");
			if (animatorName != null && name.equals(animatorName.getAsString())) {
				return animator;
			}
		}
		return null;
	}

	private static void writeTexture(JsonObject model, int index, Path output)
			throws Exception {
		String source = model.getAsJsonArray("textures").get(index)
				.getAsJsonObject().get("source").getAsString();
		int comma = source.indexOf(',');
		byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1));
		Files.createDirectories(output.getParent());
		Files.write(output, bytes);
	}

	private static double number(JsonElement element, int index) {
		return element.getAsJsonArray().get(index).getAsDouble();
	}

	private static double number(JsonObject object, String key) {
		return object.get(key).getAsDouble();
	}

	private static double number(JsonObject object, int index) {
		return object.getAsJsonArray().get(index).getAsDouble();
	}

	private static int length(JsonArray array) {
		double sum = 0.0D;
		for (JsonElement value : array) {
			sum += Math.abs(value.getAsDouble());
		}
		return sum == 0.0D ? 0 : array.size();
	}

	private static JsonArray array(double... values) {
		JsonArray array = new JsonArray();
		for (double value : values) {
			array.add(value);
		}
		return array;
	}

	private static String time(double value) {
		String text = String.format(java.util.Locale.ROOT, "%.5f", value);
		text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.isEmpty() ? "0" : text;
	}

	private static double rounded(double value) {
		return Math.round(value * 10_000.0D) / 10_000.0D;
	}

	private SyncKineticSwordEffects() {
	}
}
