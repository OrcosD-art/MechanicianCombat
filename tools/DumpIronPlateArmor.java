import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelCuboidData;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dumps the four compiled Armor of the Ages iron-plate armor layers into a
 * Blockbench free-model file. Run against a named copy of the armor mod.
 */
public final class DumpIronPlateArmor {
	private static final String PACKAGE =
			"org.dawnoftime.armoroftheages.client.models.iron_plate_armor.";
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().setPrettyPrinting().create();

	private static final Field TEXTURED_DATA = field(
			TexturedModelData.class, "data");
	private static final Field PART_CUBOIDS = field(
			ModelPartData.class, "cuboidData");
	private static final Field PART_TRANSFORM = field(
			ModelPartData.class, "rotationData");
	private static final Field PART_CHILDREN = field(
			ModelPartData.class, "children");
	private static final Field CUBE_OFFSET = field(
			ModelCuboidData.class, "offset");
	private static final Field CUBE_DIMENSIONS = field(
			ModelCuboidData.class, "dimensions");
	private static final Field CUBE_DILATION = field(
			ModelCuboidData.class, "extraSize");
	private static final Field CUBE_MIRROR = field(
			ModelCuboidData.class, "mirror");
	private static final Field CUBE_UV = field(
			ModelCuboidData.class, "textureUV");
	private static final Field CUBE_UV_SCALE = field(
			ModelCuboidData.class, "textureScale");
	private static final Field CUBE_DIRECTIONS = field(
			ModelCuboidData.class, "directions");
	private static final Field DILATION_X = field(Dilation.class, "radiusX");
	private static final Field DILATION_Y = field(Dilation.class, "radiusY");
	private static final Field DILATION_Z = field(Dilation.class, "radiusZ");

	public static void main(String[] args) throws Exception {
		Path output = Path.of(args[0]);
		Path texture = Path.of(args[1]);
		JsonObject model = new JsonObject();
		JsonObject meta = new JsonObject();
		meta.addProperty("format_version", "4.10");
		meta.addProperty("model_format", "free");
		meta.addProperty("box_uv", false);
		model.add("meta", meta);
		model.addProperty("name", "iron_plate_armor");
		model.addProperty("model_identifier", "");
		model.addProperty("variable_placeholders", "");
		model.add("variable_placeholder_buttons", new JsonArray());
		model.add("timeline_setups", new JsonArray());
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", 128);
		resolution.addProperty("height", 64);
		model.add("resolution", resolution);

		JsonArray elements = new JsonArray();
		JsonArray outliner = new JsonArray();
		String[] pieces = {"Head", "Chest", "Legs", "Feet"};
		for (String piece : pieces) {
			Class<?> type = Class.forName(PACKAGE + piece
					+ "IronPlateArmorModel");
			TexturedModelData textured = (TexturedModelData) type
					.getMethod("createLayerDefinition").invoke(null);
			ModelData data = (ModelData) TEXTURED_DATA.get(textured);
			ModelPartData root = data.getRoot();
			JsonObject group = new JsonObject();
			String groupName = piece.toLowerCase() + "_iron_plate_armor";
			group.addProperty("name", groupName);
			group.add("origin", vec(0, 0, 0));
			group.addProperty("uuid", uuid("group:" + groupName));
			group.add("children", appendChildren(root, groupName, elements));
			outliner.add(group);
		}

		model.add("elements", elements);
		model.add("outliner", outliner);
		JsonArray textures = new JsonArray();
		JsonObject textureEntry = new JsonObject();
		textureEntry.addProperty("path", texture.getFileName().toString());
		textureEntry.addProperty("name", texture.getFileName().toString());
		textureEntry.addProperty("folder", "");
		textureEntry.addProperty("namespace", "");
		textureEntry.addProperty("id", "0");
		textureEntry.addProperty("particle", true);
		byte[] image = Files.readAllBytes(texture);
		textureEntry.addProperty("source", "data:image/png;base64,"
				+ Base64.getEncoder().encodeToString(image));
		textures.add(textureEntry);
		model.add("textures", textures);
		Files.createDirectories(output.getParent());
		Files.writeString(output, GSON.toJson(model), StandardCharsets.UTF_8);
		System.out.println("Wrote " + output + " elements=" + elements.size());
	}

	private static JsonArray appendChildren(ModelPartData data, String path,
											JsonArray elements) throws Exception {
		JsonArray result = new JsonArray();
		@SuppressWarnings("unchecked")
		Map<String, ModelPartData> children =
				(Map<String, ModelPartData>) PART_CHILDREN.get(data);
		for (Map.Entry<String, ModelPartData> entry : children.entrySet()) {
			ModelPartData part = entry.getValue();
			ModelTransform transform =
					(ModelTransform) PART_TRANSFORM.get(part);
			JsonObject bone = new JsonObject();
			String boneName = path + "/" + entry.getKey();
			bone.addProperty("name", boneName);
			bone.add("origin", bbPivot(transform));
			bone.add("rotation", bbRotation(transform));
			bone.addProperty("uuid", uuid("bone:" + boneName));
			JsonArray boneChildren = new JsonArray();
			@SuppressWarnings("unchecked")
			List<ModelCuboidData> cubes =
					(List<ModelCuboidData>) PART_CUBOIDS.get(part);
			for (int index = 0; index < cubes.size(); index++) {
				JsonObject element = cubeElement(cubes.get(index),
						boneName + "_" + index, transform);
				elements.add(element);
				boneChildren.add(element.get("uuid").getAsString());
			}
			boneChildren.addAll(appendChildren(part, boneName, elements));
			bone.add("children", boneChildren);
			result.add(bone);
		}
		return result;
	}

	private static JsonObject cubeElement(ModelCuboidData cube, String name,
										  ModelTransform parentTransform)
			throws Exception {
		Vector3f offset = (Vector3f) CUBE_OFFSET.get(cube);
		Vector3f dimensions = (Vector3f) CUBE_DIMENSIONS.get(cube);
		Dilation dilation = (Dilation) CUBE_DILATION.get(cube);
		float dx = DILATION_X.getFloat(dilation);
		float dy = DILATION_Y.getFloat(dilation);
		float dz = DILATION_Z.getFloat(dilation);
		Vector2f uv = (Vector2f) CUBE_UV.get(cube);
		Vector2f scale = (Vector2f) CUBE_UV_SCALE.get(cube);
		JsonObject element = new JsonObject();
		element.addProperty("name", name);
		element.addProperty("box_uv", false);
		element.addProperty("rescale", false);
		element.addProperty("locked", false);
		element.addProperty("light_emission", 0);
		element.addProperty("render_order", "default");
		element.addProperty("allow_mirror_modeling", true);
		element.add("from", vec(offset.x - dx, -(offset.y + dimensions.y + dy),
				offset.z - dz));
		element.add("to", vec(offset.x + dimensions.x + dx, -(offset.y - dy),
				offset.z + dimensions.z + dz));
		element.addProperty("autouv", 0);
		element.addProperty("color", 7);
		element.addProperty("inflate", 0);
		element.add("origin", bbPivot(parentTransform));
		element.add("faces", faces(uv, scale, dimensions));
		element.addProperty("type", "cube");
		element.addProperty("uuid", uuid("cube:" + name));
		return element;
	}

	private static JsonObject faces(Vector2f uv, Vector2f scale,
									Vector3f dimensions) {
		float u = uv.getX();
		float v = uv.getY();
		float dx = dimensions.x * scale.getX();
		float dy = dimensions.y * scale.getY();
		float dz = dimensions.z;
		JsonObject faces = new JsonObject();
		face(faces, "north", u + dz, v + dz, u + dz + dx, v + dz + dy);
		face(faces, "east", u, v + dz, u + dz, v + dz + dy);
		face(faces, "south", u + dz + dx + dz, v + dz,
				u + dz + dx + dz + dx, v + dz + dy);
		face(faces, "west", u + dz + dx, v + dz,
				u + dz + dx + dz, v + dz + dy);
		face(faces, "up", u + dz, v, u + dz + dx, v + dz);
		face(faces, "down", u + dz + dx, v, u + dz + dx + dz, v + dz);
		return faces;
	}

	private static void face(JsonObject faces, String name,
							 float x1, float y1, float x2, float y2) {
		JsonObject face = new JsonObject();
		JsonArray uv = new JsonArray();
		uv.add(x1);
		uv.add(y1);
		uv.add(x2);
		uv.add(y2);
		face.add("uv", uv);
		faces.add(name, face);
	}

	private static JsonArray bbPivot(ModelTransform transform) {
		return vec(transform.pivotX, -transform.pivotY, transform.pivotZ);
	}

	private static JsonArray bbRotation(ModelTransform transform) {
		return vec((float) -Math.toDegrees(transform.pitch),
				(float) -Math.toDegrees(transform.yaw),
				(float) Math.toDegrees(transform.roll));
	}

	private static JsonArray vec(float x, float y, float z) {
		JsonArray array = new JsonArray();
		array.add(round(x));
		array.add(round(y));
		array.add(round(z));
		return array;
	}

	private static double round(float value) {
		return Math.round(value * 100000.0D) / 100000.0D;
	}

	private static String uuid(String key) {
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
				.toString();
	}

	private static Field field(Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(
					"Missing field " + owner.getName() + "." + name, exception);
		}
	}

	private DumpIronPlateArmor() {
	}
}
