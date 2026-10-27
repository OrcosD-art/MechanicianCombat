import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

/**
 * Commands:
 *   BuildNewGunRig baseGeo baseAnim sourceBbmodel rawGeo rawAnim
 *                  outputGeo outputAnim outputTexture
 */
public final class BuildNewGunRig {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final String MODEL_ID = "geometry.tkgun.abyssal_ancient_cannon_rig";
	private static final List<String> EMPTY_EFFECT_BONES = List.of(
			"smoke", "dash1", "dash2", "dash3", "dash4");
	private static final Set<String> STRUCTURAL_GUN_BONES = Set.of(
			"gun", "barrel", "case", "change3", "change4", "change5", "A", "B");

	public static void main(String[] args) throws Exception {
		Path baseGeoPath = Path.of(args[0]);
		Path baseAnimPath = Path.of(args[1]);
		Path sourceBbmodelPath = Path.of(args[2]);
		Path rawGeoPath = Path.of(args[3]);
		Path rawAnimPath = Path.of(args[4]);
		Path outputGeo = Path.of(args[5]);
		Path outputAnim = Path.of(args[6]);
		Path outputTexture = Path.of(args[7]);

		JsonObject fullGeo = read(baseGeoPath);
		JsonObject fullAnim = read(baseAnimPath);
		JsonObject source = read(sourceBbmodelPath);
		JsonObject rawGeo = read(rawGeoPath);
		JsonObject rawAnim = read(rawAnimPath);

		Map<String, Integer> textureOffsets = buildTextureAtlas(source, outputTexture);
		JsonObject fullGeometry = fullGeo.getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject();
		JsonObject rawGeometry = rawGeo.getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject();
		rewriteGunUv(rawGeometry, source, textureOffsets);
		for (JsonElement element : rawGeometry.getAsJsonArray("bones")) {
			scaleHorizontalUv(element.getAsJsonObject(), 64.0D / 288.0D);
		}

		JsonArray baseBones = fullGeometry.getAsJsonArray("bones");
		Set<String> baseBoneNames = boneNames(baseBones);
		JsonArray mergedBones = new JsonArray();
		for (JsonElement element : baseBones) {
			JsonObject bone = element.getAsJsonObject().deepCopy();
			String name = bone.get("name").getAsString();
			if (isGunSubtreeBone(name, baseBones)) {
				continue;
			}
			if (EMPTY_EFFECT_BONES.contains(name)) {
				bone.remove("cubes");
			} else if ("shells".equals(name)) {
				bone.remove("cubes");
			} else if (name.startsWith("shell")) {
				offsetHorizontalUv(bone, 64.0D);
				scaleHorizontalUv(bone, 64.0D / 288.0D);
			}
			mergedBones.add(bone);
		}

		JsonArray gunBones = rawGeometry.getAsJsonArray("bones");
		Set<String> gunBoneNames = boneNames(gunBones);
		for (JsonElement element : gunBones) {
			JsonObject bone = element.getAsJsonObject().deepCopy();
			if ("gun".equals(bone.get("name").getAsString())) {
				bone.addProperty("parent", "chest");
			}
			mergedBones.add(bone);
		}
		fullGeometry.add("bones", mergedBones);

		JsonObject description = fullGeometry.getAsJsonObject("description");
		description.addProperty("identifier", MODEL_ID);
		description.addProperty("texture_width", 64);
		description.addProperty("texture_height", 64);

		Set<String> finalBoneNames = new HashSet<>(baseBoneNames);
		finalBoneNames.removeIf(name -> isGunSubtreeBone(name, baseBones));
		finalBoneNames.addAll(gunBoneNames);

		JsonObject animations = fullAnim.getAsJsonObject("animations");
		for (String name : List.copyOf(animations.keySet())) {
			JsonObject animation = animations.getAsJsonObject(name);
			JsonObject bones = animation.getAsJsonObject("bones");
			if (bones == null) {
				continue;
			}
			for (String bone : List.copyOf(bones.keySet())) {
				if (!finalBoneNames.contains(bone)) {
					bones.remove(bone);
				}
			}
		}

		JsonObject rawAnimations = rawAnim.getAsJsonObject("animations");
		mergeFocusAnimation(animations, rawAnimations, "Focus_ready", finalBoneNames);
		mergeFocusAnimation(animations, rawAnimations, "Focus_over", finalBoneNames);
		addGunFrameOverride(animations, rawAnimations, "open",
				"Focus_hold");
		addGunFrameOverride(animations, rawAnimations, "close",
				"idle");
		JsonObject openHold = animations.getAsJsonObject("open").deepCopy();
		openHold.getAsJsonObject("bones").remove("fire");
		animations.add("gun_open_hold", openHold);

		write(outputGeo, fullGeo);
		write(outputAnim, fullAnim);
		System.out.println("Geometry bones: " + mergedBones.size());
		System.out.println("Gun subtree bones: " + String.join(", ", gunBoneNames));
		System.out.println("Animations: " + animations.size());
		System.out.println("Texture atlas: " + outputTexture + " (288x64)");
	}

	private static void mergeFocusAnimation(JsonObject animations, JsonObject rawAnimations,
										 String name, Set<String> finalBones) {
		JsonObject target = animations.getAsJsonObject(name);
		JsonObject source = rawAnimations.getAsJsonObject(name);
		if (target == null || source == null) {
			throw new IllegalStateException("Missing " + name + " animation.");
		}
		JsonObject targetBones = target.getAsJsonObject("bones");
		JsonObject sourceBones = source.getAsJsonObject("bones");
		for (String bone : sourceBones.keySet()) {
			if (finalBones.contains(bone)) {
				targetBones.add(bone, sourceBones.get(bone).deepCopy());
			}
		}
	}

	private static JsonObject terminalPose(JsonObject source) {
		JsonObject pose = new JsonObject();
		pose.addProperty("loop", "hold");
		pose.addProperty("animation_length", 0.125D);
		JsonObject bones = new JsonObject();
		JsonObject sourceBones = source.getAsJsonObject("bones");
		for (String boneName : STRUCTURAL_GUN_BONES) {
			JsonObject sourceBone = sourceBones.getAsJsonObject(boneName);
			if (sourceBone == null) {
				continue;
			}
			JsonObject bone = new JsonObject();
			for (Map.Entry<String, JsonElement> channel : sourceBone.entrySet()) {
				JsonElement terminal = terminalValue(channel.getValue());
				if (terminal != null) {
					bone.add(channel.getKey(), terminal);
				}
			}
			if (!bone.isEmpty()) {
				bones.add(boneName, bone);
			}
		}
		pose.add("bones", bones);
		return pose;
	}

	private static void addGunFrameOverride(JsonObject animations,
										 JsonObject rawAnimations,
										 String staticAnimation,
										 String fortressState) {
		JsonObject source = rawAnimations.getAsJsonObject(staticAnimation);
		if (source == null) {
			throw new IllegalStateException("Missing " + staticAnimation + " animation.");
		}
		JsonObject target = source.deepCopy();
		JsonObject gun = animations.getAsJsonObject(fortressState)
				.getAsJsonObject("bones")
				.getAsJsonObject("gun");
		if (gun == null) {
			throw new IllegalStateException("Fortress state " + fortressState
					+ " has no gun channel.");
		}
		target.getAsJsonObject("bones").add("gun", gun.deepCopy());
		animations.add(staticAnimation, target);
	}

	private static JsonElement terminalValue(JsonElement value) {
		if (value == null || !value.isJsonObject()) {
			return value == null ? null : value.deepCopy();
		}
		JsonObject object = value.getAsJsonObject();
		if (object.has("vector")) {
			return object.deepCopy();
		}
		String lastKey = object.keySet().stream()
				.filter(key -> key.matches("-?\\d+(?:\\.\\d+)?"))
				.max(Comparator.comparingDouble(Double::parseDouble))
				.orElse(null);
		if (lastKey == null) {
			return object.deepCopy();
		}
		JsonElement terminal = object.get(lastKey);
		if (terminal.isJsonObject() && terminal.getAsJsonObject().has("post")) {
			return terminal.getAsJsonObject().get("post").deepCopy();
		}
		return terminal.deepCopy();
	}

	private static void scaleHorizontalUv(JsonObject bone, double factor) {
		JsonArray cubes = bone.getAsJsonArray("cubes");
		if (cubes == null) {
			return;
		}
		for (JsonElement cubeElement : cubes) {
			JsonObject uv = cubeElement.getAsJsonObject().getAsJsonObject("uv");
			if (uv == null) {
				continue;
			}
			for (Map.Entry<String, JsonElement> face : uv.entrySet()) {
				JsonObject entry = face.getValue().getAsJsonObject();
				JsonArray values = entry.getAsJsonArray("uv");
				JsonArray size = entry.getAsJsonArray("uv_size");
				values.set(0, new com.google.gson.JsonPrimitive(
						values.get(0).getAsDouble() * factor));
				if (size != null) {
					size.set(0, new com.google.gson.JsonPrimitive(
							size.get(0).getAsDouble() * factor));
				}
			}
		}
	}

	private static void offsetHorizontalUv(JsonObject bone, double offset) {
		JsonArray cubes = bone.getAsJsonArray("cubes");
		if (cubes == null) {
			return;
		}
		for (JsonElement cubeElement : cubes) {
			JsonObject uv = cubeElement.getAsJsonObject().getAsJsonObject("uv");
			if (uv == null) {
				continue;
			}
			for (Map.Entry<String, JsonElement> face : uv.entrySet()) {
				JsonArray values = face.getValue().getAsJsonObject().getAsJsonArray("uv");
				values.set(0, new com.google.gson.JsonPrimitive(
						values.get(0).getAsDouble() + offset));
			}
		}
	}

	private static Map<String, Integer> buildTextureAtlas(JsonObject source, Path output)
			throws Exception {
		JsonArray textures = source.getAsJsonArray("textures");
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (JsonElement element : textures) {
			JsonObject texture = element.getAsJsonObject();
			byId.put(texture.get("id").getAsString(), texture);
		}
		List<String> order = List.of("0", "2", "1");
		Map<String, Integer> offsets = new LinkedHashMap<>();
		int width = 0;
		for (String id : order) {
			if (!byId.containsKey(id)) {
				continue;
			}
			offsets.put(id, width);
			width += byId.get(id).get("width").getAsInt();
		}
		BufferedImage atlas = new BufferedImage(width, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = atlas.createGraphics();
		for (String id : order) {
			if (!byId.containsKey(id)) {
				continue;
			}
			JsonObject texture = byId.get(id);
			String sourceUri = texture.get("source").getAsString();
			byte[] bytes = Base64.getDecoder().decode(sourceUri.substring(sourceUri.indexOf(',') + 1));
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			graphics.drawImage(image, offsets.get(id), 0, null);
		}
		graphics.dispose();
		Files.createDirectories(output.getParent());
		ImageIO.write(atlas, "png", output.toFile());
		return offsets;
	}

	private static void rewriteGunUv(JsonObject rawGeometry, JsonObject source,
									 Map<String, Integer> offsets) {
		Map<String, ArrayDeque<JsonObject>> sourceElements = sourceElementsByGroup(source);
		for (JsonElement element : rawGeometry.getAsJsonArray("bones")) {
			JsonObject bone = element.getAsJsonObject();
			String boneName = bone.get("name").getAsString();
			ArrayDeque<JsonObject> candidates = sourceElements.getOrDefault(
					boneName, new ArrayDeque<>());
			JsonArray cubes = bone.getAsJsonArray("cubes");
			if (cubes == null) {
				continue;
			}
			for (JsonElement cubeElement : cubes) {
				JsonObject cube = cubeElement.getAsJsonObject();
				JsonObject sourceCube = matchSourceCube(candidates, cube);
				if (sourceCube == null) {
					continue;
				}
				JsonObject sourceFaces = sourceCube.getAsJsonObject("faces");
				JsonObject uv = cube.getAsJsonObject("uv");
				for (String face : List.copyOf(uv.keySet())) {
					JsonObject sourceFace = sourceFaces.getAsJsonObject(face);
					JsonElement textureElement = sourceFace == null ? null : sourceFace.get("texture");
					if (sourceFace == null || textureElement == null || textureElement.isJsonNull()) {
						uv.remove(face);
						continue;
					}
					String textureId = textureElement.getAsString();
					Integer offset = offsets.get(textureId);
					if (offset != null) {
						JsonObject entry = uv.getAsJsonObject(face);
						JsonArray rawUv = entry.getAsJsonArray("uv");
						rawUv.set(0, new com.google.gson.JsonPrimitive(
								rawUv.get(0).getAsDouble() + offset));
					}
				}
			}
		}
	}

	private static Map<String, ArrayDeque<JsonObject>> sourceElementsByGroup(JsonObject source) {
		Map<String, JsonObject> byUuid = new HashMap<>();
		for (JsonElement element : source.getAsJsonArray("elements")) {
			JsonObject cube = element.getAsJsonObject();
			byUuid.put(cube.get("uuid").getAsString(), cube);
		}
		Map<String, ArrayDeque<JsonObject>> out = new HashMap<>();
		collectElements(source.getAsJsonArray("outliner"), byUuid, out);
		return out;
	}

	private static void collectElements(JsonArray nodes, Map<String, JsonObject> byUuid,
										Map<String, ArrayDeque<JsonObject>> out) {
		for (JsonElement element : nodes) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			String group = node.get("name").getAsString();
			ArrayDeque<JsonObject> cubes = out.computeIfAbsent(group, ignored -> new ArrayDeque<>());
			for (JsonElement child : node.getAsJsonArray("children")) {
				if (child.isJsonObject()) {
					collectElements(new JsonArrayBuilder().add(child).build(), byUuid, out);
				} else if (child.isJsonPrimitive()) {
					JsonObject cube = byUuid.get(child.getAsString());
					if (cube != null) {
						cubes.add(cube);
					}
				}
			}
		}
	}

	private static JsonObject matchSourceCube(ArrayDeque<JsonObject> candidates, JsonObject cube) {
		JsonArray origin = cube.getAsJsonArray("origin");
		JsonArray size = cube.getAsJsonArray("size");
		for (JsonObject candidate : candidates) {
			JsonArray from = candidate.getAsJsonArray("from");
			JsonArray to = candidate.getAsJsonArray("to");
			boolean sameOrigin = close(origin.get(0).getAsDouble(), from.get(0).getAsDouble())
					&& close(origin.get(1).getAsDouble(), from.get(1).getAsDouble())
					&& close(origin.get(2).getAsDouble(), from.get(2).getAsDouble());
			boolean sameSize = close(size.get(0).getAsDouble(),
					to.get(0).getAsDouble() - from.get(0).getAsDouble())
					&& close(size.get(1).getAsDouble(),
					to.get(1).getAsDouble() - from.get(1).getAsDouble())
					&& close(size.get(2).getAsDouble(),
					to.get(2).getAsDouble() - from.get(2).getAsDouble());
			if (sameOrigin && sameSize) {
				candidates.remove(candidate);
				return candidate;
			}
		}
		// Blockbench may rewrite mirrored/rotated cube origins while preserving
		// source element order. Fall back to that order when bounds no longer match.
		return candidates.pollFirst();
	}

	private static boolean close(double left, double right) {
		return Math.abs(left - right) < 1.0E-4D;
	}

	private static boolean isGunSubtreeBone(String name, JsonArray bones) {
		if ("gun".equals(name)) {
			return true;
		}
		Map<String, String> parents = new HashMap<>();
		for (JsonElement element : bones) {
			JsonObject bone = element.getAsJsonObject();
			if (bone.has("parent")) {
				parents.put(bone.get("name").getAsString(), bone.get("parent").getAsString());
			}
		}
		String current = name;
		while (parents.containsKey(current)) {
			current = parents.get(current);
			if ("gun".equals(current)) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> boneNames(JsonArray bones) {
		Set<String> names = new HashSet<>();
		for (JsonElement element : bones) {
			names.add(element.getAsJsonObject().get("name").getAsString());
		}
		return names;
	}

	private static JsonObject read(Path path) throws Exception {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
	}

	private static void write(Path path, JsonObject document) throws Exception {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(document), StandardCharsets.UTF_8);
	}

	private static final class JsonArrayBuilder {
		private final JsonArray array = new JsonArray();
		JsonArrayBuilder add(JsonElement element) {
			array.add(element);
			return this;
		}
		JsonArray build() {
			return array;
		}
	}

	private BuildNewGunRig() {
	}
}
