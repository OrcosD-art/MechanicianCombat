import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Converts the authored player locomotion rig into PlayerAnimator clips.
 *
 * Source joints use MechanicianCombat/Blockbench names. The output uses
 * PlayerAnimator names and intentionally never emits a head channel.
 */
public final class ExportPlayerLocomotion {
	private static final double TICKS_PER_SECOND = 20.0D;
	private static final double CHEST_TORSO_WEIGHT = clamp(
			Double.parseDouble(System.getProperty(
					"locomotion.chestWeight", "0.4")), 0.0D, 1.0D);
	private static final Map<String, String> SOURCE_TO_PLAYER = Map.of(
			"chest", "torso",
			"head", "head",
			"arm_right", "rightArm",
			"arm_left", "leftArm",
			"leg_right", "rightLeg",
			"leg_left", "leftLeg");
	private static final Map<String, double[]> REST_PIVOT = Map.of(
			"torso", new double[]{0.0D, 0.0D, 0.0D},
			"head", new double[]{0.0D, 0.0D, 0.0D},
			"rightArm", new double[]{-5.0D, 2.0D, 0.0D},
			"leftArm", new double[]{5.0D, 2.0D, 0.0D},
			"rightLeg", new double[]{-1.9D, 12.0D, 0.0D},
			"leftLeg", new double[]{1.9D, 12.0D, 0.0D});
	private static final Map<String, String> OUTPUT_NAMES = Map.of(
			"idle", "player_idle",
			"player_walk_front", "player_walk_front",
			"player_walk_back", "player_walk_back",
			"player_walk_left", "player_walk_left",
			"player_walk_right", "player_walk_right",
			"player_run", "player_run",
			"jump", "player_jump",
			"fall", "player_fall",
			"fall_back-idle", "player_fall_back_idle");

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: ExportPlayerLocomotion <source.bbmodel> <output.json>");
		}
		JsonObject source = JsonParser.parseString(Files.readString(
				Path.of(args[0]), StandardCharsets.UTF_8)).getAsJsonObject();
		Rig rig = readRig(source);
		Map<String, double[][]> bindWorld =
				rig.worldTransforms(Map.of(), 0.0D);
		double[][] playerMapping = mul(translate(0.0D, 1.5D, 0.0D),
				scale(-1.0D, -1.0D, 1.0D));
		double[][] playerMappingInv = inverse(playerMapping);
		double[][] gameMapping = scale(-1.0D, 1.0D, 1.0D);
		double[][] gameMappingInv = inverse(gameMapping);

		Map<String, double[][]> bindWorldPlayer = new HashMap<>();
		for (String name : rig.groups.keySet()) {
			bindWorldPlayer.put(name,
					mul(mul(playerMapping, bindWorld.get(name)),
							playerMappingInv));
		}

		JsonObject root = new JsonObject();
		JsonObject animations = new JsonObject();
		root.add("animations", animations);
		for (JsonElement element : source.getAsJsonArray("animations")) {
			JsonObject animation = element.getAsJsonObject();
			String sourceName = animation.get("name").getAsString();
			String outputName = OUTPUT_NAMES.get(sourceName);
			if (outputName == null) {
				continue;
			}
			JsonObject clip = exportAnimation(animation, rig, bindWorld,
					bindWorldPlayer, playerMapping, playerMappingInv,
					gameMapping, gameMappingInv);
			animations.add(outputName, clip);
		}
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.disableHtmlEscaping().create();
		Files.writeString(Path.of(args[1]), gson.toJson(root),
				StandardCharsets.UTF_8);
		System.out.println("Wrote " + args[1] + " animations="
				+ animations.keySet() + " chestWeight="
				+ CHEST_TORSO_WEIGHT);
	}

	private static JsonObject exportAnimation(JsonObject animation, Rig rig,
			Map<String, double[][]> bindWorld,
			Map<String, double[][]> bindWorldPlayer,
			double[][] playerMapping, double[][] playerMappingInv,
			double[][] gameMapping, double[][] gameMappingInv) {
		Map<String, Map<String, TreeMap<Double, double[]>>> channels =
				new HashMap<>();
		for (Map.Entry<String, JsonElement> entry
				: animation.getAsJsonObject("animators").entrySet()) {
			JsonObject animator = entry.getValue().getAsJsonObject();
			if (!"bone".equals(optionalString(animator, "type", ""))) {
				continue;
			}
			String name = animator.get("name").getAsString();
			Map<String, TreeMap<Double, double[]>> bone =
					channels.computeIfAbsent(name, ignored -> new HashMap<>());
			for (JsonElement frameElement : animator.getAsJsonArray("keyframes")) {
				JsonObject frame = frameElement.getAsJsonObject();
				JsonArray points = frame.getAsJsonArray("data_points");
				if (points == null || points.isEmpty()) {
					continue;
				}
				JsonObject point = points.get(points.size() - 1).getAsJsonObject();
				String channel = frame.get("channel").getAsString();
				double x = number(point, "x");
				if (!"body".equals(name)) {
					x = -x;
				}
				double[] vector = new double[]{x, number(point, "y"),
						number(point, "z")};
				double time = number(frame, "time");
				bone.computeIfAbsent(channel, ignored -> new TreeMap<>())
						.put(time, vector);
			}
		}
		Set<String> animatedSources = new HashSet<>(channels.keySet());
		animatedSources.retainAll(new HashSet<>(List.of(
				"body", "chest", "head", "arm_left", "arm_right",
				"leg_left", "leg_right")));
		// Head rotation stays vanilla, but its position must inherit the
		// authored body/chest motion in every clip.
		animatedSources.add("head");

		double length = number(animation, "length");
		int ticks = (int) Math.ceil(length * TICKS_PER_SECOND);
		JsonObject clip = new JsonObject();
		JsonElement loop = animation.get("loop");
		if (loop != null && loop.isJsonPrimitive()
				&& loop.getAsJsonPrimitive().isBoolean()) {
			clip.addProperty("loop", loop.getAsBoolean());
		} else if (loop != null && loop.isJsonPrimitive()
				&& "loop".equals(loop.getAsString())) {
			clip.addProperty("loop", true);
		} else if (loop != null && loop.isJsonPrimitive()
				&& "hold".equals(loop.getAsString())) {
			clip.addProperty("loop", "hold_on_last_frame");
		} else {
			clip.addProperty("loop", false);
		}
		clip.addProperty("animation_length", ticks / TICKS_PER_SECOND);
		JsonObject bones = new JsonObject();
		clip.add("bones", bones);

		for (int tick = 0; tick <= ticks; tick++) {
			double seconds = tick / TICKS_PER_SECOND;
			String timeKey = trim(seconds);
			Map<String, double[][]> worlds =
					rig.worldTransforms(channels, seconds);
			Map<String, double[][]> worldsPlayer = new HashMap<>();
			for (String name : rig.groups.keySet()) {
				worldsPlayer.put(name,
						mul(mul(playerMapping, worlds.get(name)),
								playerMappingInv));
			}

			double[][] torsoWorldPlayer = bindWorldPlayer.get("chest");
			if (animatedSources.contains("body")
					|| animatedSources.contains("chest")) {
				// PlayerAnimator has one rigid torso. Blend only part of the
				// source chest delta into it. Head and arm channels below are
				// re-solved against this blended torso, so their authored world
				// poses remain exact while the lower body sways less.
				double[][] bindChestDelta = mul(
						inverse(bindWorld.get("body")),
						bindWorld.get("chest"));
				double[][] currentChestDelta = mul(
						inverse(worlds.get("body")),
						worlds.get("chest"));
				double[][] blendedChestDelta = blendAffine(
						bindChestDelta, currentChestDelta,
						CHEST_TORSO_WEIGHT);
				double[][] desiredTorsoWorld = mul(
						worlds.get("body"), blendedChestDelta);
				torsoWorldPlayer = mul(
						mul(playerMapping, desiredTorsoWorld),
						playerMappingInv);
				double[][] torsoChange = mul(desiredTorsoWorld,
						inverse(bindWorld.get("chest")));
				double[][] torsoGame = mul(mul(gameMapping, torsoChange),
						gameMappingInv);
				Affine torso = affine(torsoGame);
				double[] angles = eulerZxy(torso.rotation);
				angles[0] = -angles[0];
				double[][] torsoRotation = mul(
						mul(rotZ(Math.toDegrees(angles[2])),
								rotY(Math.toDegrees(angles[1]))),
						rotX(Math.toDegrees(angles[0])));
				double[] sourceCenter = new double[]{0.0D, 0.75D, 0.0D};
				double[] torsoTranslation = new double[3];
				for (int row = 0; row < 3; row++) {
					torsoTranslation[row] = sourceCenter[row]
							- dotRow(torsoRotation, row, sourceCenter);
				}
				double[] center = new double[]{0.0D, 0.7D, 0.0D};
				double[] offset = new double[3];
				for (int row = 0; row < 3; row++) {
					offset[row] = torsoTranslation[row] - center[row]
							+ dotRow(torsoRotation, row, center);
				}
				addBoneFrame(bones, "torso", timeKey,
						new double[]{
								-Math.toDegrees(angles[0]),
								-Math.toDegrees(angles[1]),
								Math.toDegrees(angles[2])},
						new double[]{
								-offset[0] * 16.0D,
								offset[1] * 16.0D,
								offset[2] * 16.0D});
			}

			for (Map.Entry<String, String> mapping
					: SOURCE_TO_PLAYER.entrySet()) {
				String sourceName = mapping.getKey();
				if (!animatedSources.contains(sourceName)) {
					continue;
				}
				// The body/chest pair is emitted once above as the composed
				// "torso" channel. Letting "chest" run through this loop would
				// overwrite it with only the child-local chest transform.
				if ("chest".equals(sourceName)) {
					continue;
				}
				String playerName = mapping.getValue();
				// Head and arms are re-solved against the blended torso. Their
				// complete authored chest motion therefore remains intact even
				// when only part of that motion reaches the rigid torso block.
				boolean upperBody = "head".equals(playerName)
						|| "rightArm".equals(playerName)
						|| "leftArm".equals(playerName);
				double[][] inverseParent = upperBody
						? inverse(torsoWorldPlayer)
						: inverse(worldsPlayer.get("body"));
				double[][] inverseBindParent = upperBody
						? inverse(bindWorldPlayer.get("chest"))
						: inverse(bindWorldPlayer.get("body"));
				double[][] bindRelative = mul(inverseBindParent,
						bindWorldPlayer.get(sourceName));
				double[][] posedRelative = mul(inverseParent,
						worldsPlayer.get(sourceName));
				Affine change = affine(mul(posedRelative,
						inverse(bindRelative)));
				double[] angles = eulerZxy(change.rotation);
				double[] rest = REST_PIVOT.get(playerName);
				double[] restBlocks = new double[]{
						rest[0] / 16.0D, rest[1] / 16.0D,
						rest[2] / 16.0D};
				double[] pivot = new double[3];
				for (int row = 0; row < 3; row++) {
					pivot[row] = (change.translation[row]
							+ dotRow(change.rotation, row, restBlocks))
							* 16.0D;
				}
				addBoneFrame(bones, playerName, timeKey,
						"head".equals(playerName) ? null : new double[]{
								Math.toDegrees(angles[0]),
								Math.toDegrees(angles[1]),
								Math.toDegrees(angles[2])},
						new double[]{
								pivot[0] - rest[0],
								rest[1] - pivot[1],
								pivot[2] - rest[2]});
			}
		}
		return clip;
	}

	private static void addBoneFrame(JsonObject bones, String boneName,
			String time, double[] rotation, double[] position) {
		JsonObject bone = bones.has(boneName)
				? bones.getAsJsonObject(boneName) : new JsonObject();
		if (!bones.has(boneName)) {
			bones.add(boneName, bone);
		}
		JsonObject positions = bone.has("position")
				? bone.getAsJsonObject("position") : new JsonObject();
		bone.add("position", positions);
		positions.add(time, vector(position));
		if (rotation != null) {
			JsonObject rotations = bone.has("rotation")
					? bone.getAsJsonObject("rotation") : new JsonObject();
			bone.add("rotation", rotations);
			rotations.add(time, vector(rotation));
		}
	}

	private static JsonArray vector(double[] values) {
		JsonArray array = new JsonArray();
		for (double value : values) {
			array.add(clean(value));
		}
		return array;
	}

	private static double clean(double value) {
		double rounded = Math.round(value * 1.0E9D) / 1.0E9D;
		return Math.abs(rounded) < 5.0E-10D ? 0.0D : rounded;
	}

	private static String trim(double value) {
		String text = String.format(Locale.ROOT, "%.5f", value);
		text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.isEmpty() ? "0" : text;
	}

	private static String optionalString(JsonObject object, String name,
			String fallback) {
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? fallback
				: value.getAsString();
	}

	private static double number(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? 0.0D
				: value.getAsDouble();
	}

	private static Rig readRig(JsonObject source) {
		Rig rig = new Rig();
		for (JsonElement element : source.getAsJsonArray("outliner")) {
			readNode(element, null, rig);
		}
		return rig;
	}

	private static void readNode(JsonElement element, String parent, Rig rig) {
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject node = element.getAsJsonObject();
		String name = optionalString(node, "name", "");
		if (!name.isEmpty()) {
			rig.groups.put(name, node);
			rig.parents.put(name, parent);
		}
		JsonArray children = node.getAsJsonArray("children");
		if (children != null) {
			for (JsonElement child : children) {
				readNode(child, name, rig);
			}
		}
	}

	private static final class Rig {
		final Map<String, JsonObject> groups = new LinkedHashMap<>();
		final Map<String, String> parents = new HashMap<>();
		final Map<String, Integer> order = new LinkedHashMap<>();

		Map<String, double[][]> worldTransforms(
				Map<String, Map<String, TreeMap<Double, double[]>>> channels,
				double seconds) {
			Map<String, double[][]> worlds = new HashMap<>();
			for (String name : groups.keySet()) {
				JsonObject group = groups.get(name);
				double[][] local = local(group,
						channels.getOrDefault(name, Map.of()), seconds);
				String parent = parents.get(name);
				worlds.put(name, parent == null ? local
						: mul(worlds.get(parent), local));
			}
			return worlds;
		}

		private static double[][] local(JsonObject group,
				Map<String, TreeMap<Double, double[]>> channels,
				double seconds) {
			double[] pivot = divide(array(group, "origin", 3), 16.0D);
			double[] bind = array(group, "rotation", 3);
			double[] sampled = sample(channels.get("rotation"), seconds);
			double[] rotation = new double[]{
					bind[0] + sampled[0], bind[1] + sampled[1],
					bind[2] + sampled[2]};
			double[] sampledPosition =
					sample(channels.get("position"), seconds);
			double[] offset = divide(sampledPosition, 16.0D);
			double[][] result = translate(offset[0], offset[1], offset[2]);
			result = mul(result, translate(pivot[0], pivot[1], pivot[2]));
			result = mul(result, rotZ(rotation[2]));
			result = mul(result, rotY(rotation[1]));
			result = mul(result, rotX(rotation[0]));
			return mul(result,
					translate(-pivot[0], -pivot[1], -pivot[2]));
		}
	}

	private static double[] sample(TreeMap<Double, double[]> channel,
			double seconds) {
		if (channel == null || channel.isEmpty()) {
			return new double[]{0.0D, 0.0D, 0.0D};
		}
		Map.Entry<Double, double[]> first = channel.firstEntry();
		Map.Entry<Double, double[]> last = channel.lastEntry();
		if (seconds <= first.getKey()) {
			return first.getValue();
		}
		if (seconds >= last.getKey()) {
			return last.getValue();
		}
		Map.Entry<Double, double[]> left = channel.floorEntry(seconds);
		Map.Entry<Double, double[]> right = channel.ceilingEntry(seconds);
		double span = right.getKey() - left.getKey();
		double factor = span <= 1.0E-9D ? 0.0D
				: (seconds - left.getKey()) / span;
		return new double[]{
				left.getValue()[0]
						+ (right.getValue()[0] - left.getValue()[0]) * factor,
				left.getValue()[1]
						+ (right.getValue()[1] - left.getValue()[1]) * factor,
				left.getValue()[2]
						+ (right.getValue()[2] - left.getValue()[2]) * factor};
	}

	private record Affine(double[][] rotation, double[] translation) {
	}

	private static Affine affine(double[][] matrix) {
		return new Affine(new double[][]{
				{matrix[0][0], matrix[0][1], matrix[0][2]},
				{matrix[1][0], matrix[1][1], matrix[1][2]},
				{matrix[2][0], matrix[2][1], matrix[2][2]}},
				new double[]{matrix[0][3], matrix[1][3], matrix[2][3]});
	}

	private static double[][] blendAffine(double[][] from, double[][] to,
			double amount) {
		double[][] rotationFrom = affine(from).rotation;
		double[][] rotationTo = affine(to).rotation;
		double[] q0 = quaternion(rotationFrom);
		double[] q1 = quaternion(rotationTo);
		double[] q = slerp(q0, q1, amount);
		double[][] result = identity();
		double[][] rotation = quaternionMatrix(q);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				result[row][column] = rotation[row][column];
			}
			result[row][3] = from[row][3]
					+ (to[row][3] - from[row][3]) * amount;
		}
		return result;
	}

	/** Quaternion for a row-major 3x3 rotation matrix. */
	private static double[] quaternion(double[][] matrix) {
		double trace = matrix[0][0] + matrix[1][1] + matrix[2][2];
		double x;
		double y;
		double z;
		double w;
		if (trace > 0.0D) {
			double scale = Math.sqrt(trace + 1.0D) * 2.0D;
			w = 0.25D * scale;
			x = (matrix[2][1] - matrix[1][2]) / scale;
			y = (matrix[0][2] - matrix[2][0]) / scale;
			z = (matrix[1][0] - matrix[0][1]) / scale;
		} else if (matrix[0][0] > matrix[1][1]
				&& matrix[0][0] > matrix[2][2]) {
			double scale = Math.sqrt(1.0D + matrix[0][0]
					- matrix[1][1] - matrix[2][2]) * 2.0D;
			w = (matrix[2][1] - matrix[1][2]) / scale;
			x = 0.25D * scale;
			y = (matrix[0][1] + matrix[1][0]) / scale;
			z = (matrix[0][2] + matrix[2][0]) / scale;
		} else if (matrix[1][1] > matrix[2][2]) {
			double scale = Math.sqrt(1.0D + matrix[1][1]
					- matrix[0][0] - matrix[2][2]) * 2.0D;
			w = (matrix[0][2] - matrix[2][0]) / scale;
			x = (matrix[0][1] + matrix[1][0]) / scale;
			y = 0.25D * scale;
			z = (matrix[1][2] + matrix[2][1]) / scale;
		} else {
			double scale = Math.sqrt(1.0D + matrix[2][2]
					- matrix[0][0] - matrix[1][1]) * 2.0D;
			w = (matrix[1][0] - matrix[0][1]) / scale;
			x = (matrix[0][2] + matrix[2][0]) / scale;
			y = (matrix[1][2] + matrix[2][1]) / scale;
			z = 0.25D * scale;
		}
		double length = Math.sqrt(x * x + y * y + z * z + w * w);
		return new double[]{x / length, y / length, z / length, w / length};
	}

	private static double[] slerp(double[] from, double[] to,
			double amount) {
		double dot = from[0] * to[0] + from[1] * to[1]
				+ from[2] * to[2] + from[3] * to[3];
		double[] target = to.clone();
		if (dot < 0.0D) {
			dot = -dot;
			for (int index = 0; index < target.length; index++) {
				target[index] = -target[index];
			}
		}
		if (dot > 0.9995D) {
			for (int index = 0; index < target.length; index++) {
				target[index] = from[index]
						+ (target[index] - from[index]) * amount;
			}
			return normalize(target);
		}
		double theta = Math.acos(clamp(dot, -1.0D, 1.0D));
		double sinTheta = Math.sin(theta);
		double fromWeight = Math.sin((1.0D - amount) * theta) / sinTheta;
		double toWeight = Math.sin(amount * theta) / sinTheta;
		double[] result = new double[4];
		for (int index = 0; index < result.length; index++) {
			result[index] = from[index] * fromWeight
					+ target[index] * toWeight;
		}
		return normalize(result);
	}

	private static double[] normalize(double[] quaternion) {
		double length = Math.sqrt(quaternion[0] * quaternion[0]
				+ quaternion[1] * quaternion[1]
				+ quaternion[2] * quaternion[2]
				+ quaternion[3] * quaternion[3]);
		if (length <= 1.0E-12D) {
			return new double[]{0.0D, 0.0D, 0.0D, 1.0D};
		}
		for (int index = 0; index < quaternion.length; index++) {
			quaternion[index] /= length;
		}
		return quaternion;
	}

	private static double[][] quaternionMatrix(double[] quaternion) {
		double x = quaternion[0];
		double y = quaternion[1];
		double z = quaternion[2];
		double w = quaternion[3];
		return new double[][]{
				{1.0D - 2.0D * (y * y + z * z),
						2.0D * (x * y - z * w),
						2.0D * (x * z + y * w)},
				{2.0D * (x * y + z * w),
						1.0D - 2.0D * (x * x + z * z),
						2.0D * (y * z - x * w)},
				{2.0D * (x * z - y * w),
						2.0D * (y * z + x * w),
						1.0D - 2.0D * (x * x + y * y)}};
	}

	private static double[] eulerZxy(double[][] rotation) {
		double y = Math.asin(clamp(-rotation[2][0], -1.0D, 1.0D));
		double x;
		double z;
		if (Math.abs(rotation[2][0]) < 0.99999D) {
			x = Math.atan2(rotation[2][1], rotation[2][2]);
			z = Math.atan2(rotation[1][0], rotation[0][0]);
		} else {
			x = Math.atan2(-rotation[1][2], rotation[1][1]);
			z = 0.0D;
		}
		return new double[]{x, y, z};
	}

	private static double[] array(JsonObject object, String name, int fallback) {
		JsonElement element = object.get(name);
		if (element == null || !element.isJsonArray()) {
			return new double[fallback];
		}
		JsonArray array = element.getAsJsonArray();
		double[] result = new double[array.size()];
		for (int index = 0; index < array.size(); index++) {
			result[index] = array.get(index).getAsDouble();
		}
		return result;
	}

	private static double[] divide(double[] values, double divisor) {
		double[] result = new double[values.length];
		for (int index = 0; index < values.length; index++) {
			result[index] = values[index] / divisor;
		}
		return result;
	}

	private static double dotRow(double[][] matrix, int row, double[] vector) {
		return matrix[row][0] * vector[0] + matrix[row][1] * vector[1]
				+ matrix[row][2] * vector[2];
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double[][] identity() {
		return new double[][]{
				{1.0D, 0.0D, 0.0D, 0.0D},
				{0.0D, 1.0D, 0.0D, 0.0D},
				{0.0D, 0.0D, 1.0D, 0.0D},
				{0.0D, 0.0D, 0.0D, 1.0D}};
	}

	private static double[][] translate(double x, double y, double z) {
		double[][] result = identity();
		result[0][3] = x;
		result[1][3] = y;
		result[2][3] = z;
		return result;
	}

	private static double[][] scale(double x, double y, double z) {
		double[][] result = identity();
		result[0][0] = x;
		result[1][1] = y;
		result[2][2] = z;
		return result;
	}

	private static double[][] rotX(double degrees) {
		double radians = Math.toRadians(degrees);
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		double[][] result = identity();
		result[1][1] = c;
		result[1][2] = -s;
		result[2][1] = s;
		result[2][2] = c;
		return result;
	}

	private static double[][] rotY(double degrees) {
		double radians = Math.toRadians(degrees);
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		double[][] result = identity();
		result[0][0] = c;
		result[0][2] = s;
		result[2][0] = -s;
		result[2][2] = c;
		return result;
	}

	private static double[][] rotZ(double degrees) {
		double radians = Math.toRadians(degrees);
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		double[][] result = identity();
		result[0][0] = c;
		result[0][1] = -s;
		result[1][0] = s;
		result[1][1] = c;
		return result;
	}

	private static double[][] mul(double[][] left, double[][] right) {
		double[][] result = new double[4][4];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				double sum = 0.0D;
				for (int index = 0; index < 4; index++) {
					sum += left[row][index] * right[index][column];
				}
				result[row][column] = sum;
			}
		}
		return result;
	}

	private static double[][] inverse(double[][] matrix) {
		double[][] work = new double[4][8];
		for (int row = 0; row < 4; row++) {
			System.arraycopy(matrix[row], 0, work[row], 0, 4);
			work[row][4 + row] = 1.0D;
		}
		for (int column = 0; column < 4; column++) {
			int pivot = column;
			for (int row = column + 1; row < 4; row++) {
				if (Math.abs(work[row][column])
						> Math.abs(work[pivot][column])) {
					pivot = row;
				}
			}
			if (Math.abs(work[pivot][column]) < 1.0E-12D) {
				throw new IllegalArgumentException("singular matrix");
			}
			double[] swap = work[column];
			work[column] = work[pivot];
			work[pivot] = swap;
			double divisor = work[column][column];
			for (int index = 0; index < 8; index++) {
				work[column][index] /= divisor;
			}
			for (int row = 0; row < 4; row++) {
				if (row == column || work[row][column] == 0.0D) {
					continue;
				}
				double factor = work[row][column];
				for (int index = 0; index < 8; index++) {
					work[row][index] -= factor * work[column][index];
				}
			}
		}
		double[][] result = new double[4][4];
		for (int row = 0; row < 4; row++) {
			System.arraycopy(work[row], 4, result[row], 0, 4);
		}
		return result;
	}

	private ExportPlayerLocomotion() {
	}
}
