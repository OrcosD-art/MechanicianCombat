import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reflection-only dump that keeps the armor mod in intermediary names. */
public final class DumpIronPlateArmorIntermediary {
	private static final String PACKAGE =
			"org.dawnoftime.armoroftheages.client.models.iron_plate_armor.";
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().setPrettyPrinting().create();

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
		model.add("variable_placeholder_buttons", new JsonArray());
		model.add("timeline_setups", new JsonArray());
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", 128);
		resolution.addProperty("height", 64);
		model.add("resolution", resolution);

		Class<?> texturedClass = Class.forName("net.minecraft.class_5607");
		Field texturedData = field(texturedClass, "field_27723");
		Class<?> modelDataClass = Class.forName("net.minecraft.class_5609");
		Method getRoot = modelDataClass.getMethod("method_32111");
		JsonArray elements = new JsonArray();
		JsonArray outliner = new JsonArray();
		for (String piece : new String[]{"Head", "Chest", "Legs", "Feet"}) {
			Class<?> type = Class.forName(PACKAGE + piece
					+ "IronPlateArmorModel");
			Object textured = type.getMethod("createLayerDefinition")
					.invoke(null);
			Object root = getRoot.invoke(texturedData.get(textured));
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

		JsonObject textureEntry = new JsonObject();
		textureEntry.addProperty("path", texture.getFileName().toString());
		textureEntry.addProperty("name", texture.getFileName().toString());
		textureEntry.addProperty("folder", "");
		textureEntry.addProperty("namespace", "");
		textureEntry.addProperty("id", "0");
		textureEntry.addProperty("particle", true);
		textureEntry.addProperty("source", "data:image/png;base64,"
				+ Base64.getEncoder().encodeToString(
						Files.readAllBytes(texture)));
		JsonArray textures = new JsonArray();
		textures.add(textureEntry);
		model.add("textures", textures);
		Files.createDirectories(output.getParent());
		Files.writeString(output, GSON.toJson(model), StandardCharsets.UTF_8);
		System.out.println("Wrote " + output + " elements=" + elements.size());
	}

	private static JsonArray appendChildren(Object data, String path,
											JsonArray elements) throws Exception {
		Class<?> partClass = Class.forName("net.minecraft.class_5610");
		Field cuboidData = field(partClass, "field_27728");
		Field transformData = field(partClass, "field_27729");
		Field childData = field(partClass, "field_27730");
		@SuppressWarnings("unchecked")
		Map<String, Object> children =
				(Map<String, Object>) childData.get(data);
		JsonArray result = new JsonArray();
		for (Map.Entry<String, Object> entry : children.entrySet()) {
			Object part = entry.getValue();
			Object transform = transformData.get(part);
			JsonObject bone = new JsonObject();
			String boneName = path + "/" + entry.getKey();
			bone.addProperty("name", boneName);
			bone.add("origin", bbPivot(transform));
			bone.add("rotation", bbRotation(transform));
			bone.addProperty("uuid", uuid("bone:" + boneName));
			JsonArray boneChildren = new JsonArray();
			@SuppressWarnings("unchecked")
			List<Object> cubes =
					(List<Object>) cuboidData.get(part);
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

	private static JsonObject cubeElement(Object cube, String name,
										  Object parentTransform)
			throws Exception {
		Class<?> cubeClass = Class.forName("net.minecraft.class_5604");
		Vector3f offset = (Vector3f) field(cubeClass, "field_27709").get(cube);
		Vector3f dimensions =
				(Vector3f) field(cubeClass, "field_27710").get(cube);
		Object dilation = field(cubeClass, "field_27711").get(cube);
		Class<?> dilationClass = Class.forName("net.minecraft.class_5605");
		float dx = field(dilationClass, "field_27716").getFloat(dilation);
		float dy = field(dilationClass, "field_27717").getFloat(dilation);
		float dz = field(dilationClass, "field_27718").getFloat(dilation);
		Object uv = field(cubeClass, "field_27713").get(cube);
		Object scale = field(cubeClass, "field_27714").get(cube);
		JsonObject element = new JsonObject();
		element.addProperty("name", name);
		element.addProperty("box_uv", false);
		element.addProperty("rescale", false);
		element.addProperty("locked", false);
		element.addProperty("light_emission", 0);
		element.addProperty("render_order", "default");
		element.addProperty("allow_mirror_modeling", true);
		element.add("from", vec(offset.x - dx,
				-(offset.y + dimensions.y + dy), offset.z - dz));
		element.add("to", vec(offset.x + dimensions.x + dx,
				-(offset.y - dy), offset.z + dimensions.z + dz));
		element.addProperty("autouv", 0);
		element.addProperty("color", 7);
		element.addProperty("inflate", 0);
		element.add("origin", bbPivot(parentTransform));
		element.add("faces", faces(uv, scale, dimensions));
		element.addProperty("type", "cube");
		element.addProperty("uuid", uuid("cube:" + name));
		return element;
	}

	private static JsonObject faces(Object uv, Object scale,
									Vector3f dimensions) throws Exception {
		Class<?> vectorClass = Class.forName("net.minecraft.class_5611");
		float u = (float) vectorClass.getMethod("method_32118")
				.invoke(uv);
		float v = (float) vectorClass.getMethod("method_32119")
				.invoke(uv);
		float scaleU = (float) vectorClass.getMethod("method_32118")
				.invoke(scale);
		float scaleV = (float) vectorClass.getMethod("method_32119")
				.invoke(scale);
		float dx = dimensions.x * scaleU;
		float dy = dimensions.y * scaleV;
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

	private static JsonArray bbPivot(Object transform) throws Exception {
		Class<?> type = Class.forName("net.minecraft.class_5603");
		return vec(field(type, "field_27702").getFloat(transform),
				-field(type, "field_27703").getFloat(transform),
				field(type, "field_27704").getFloat(transform));
	}

	private static JsonArray bbRotation(Object transform) throws Exception {
		Class<?> type = Class.forName("net.minecraft.class_5603");
		return vec((float) -Math.toDegrees(
						field(type, "field_27705").getFloat(transform)),
				(float) -Math.toDegrees(
						field(type, "field_27706").getFloat(transform)),
				(float) Math.toDegrees(
						field(type, "field_27707").getFloat(transform)));
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

	private DumpIronPlateArmorIntermediary() {
	}
}
