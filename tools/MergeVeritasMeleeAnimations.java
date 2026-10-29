import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges the four close-combat clips into the existing Veritas player rig and
 * keeps their leg channels disabled so locomotion remains externally owned.
 */
public final class MergeVeritasMeleeAnimations {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().setPrettyPrinting().create();
	private static final List<String> MELEE_ANIMATIONS =
			List.of("ATK_1", "ATK_1_to_idle",
					"ATK_2", "ATK_2_to_idle", "ATK_2_end",
					"ATK_3", "ATK_3_to_idle", "ATK_3_hold",
					"ATK_4", "ATK_4_to_idle");

	public static void main(String[] args) throws Exception {
		Path targetPath = Path.of(args[0]);
		Path sourcePath = Path.of(args[1]);
		Path geoPath = Path.of(args[2]);
		JsonObject target = read(targetPath);
		JsonObject source = read(sourcePath);
		JsonObject geo = read(geoPath);

		Set<String> geometryBones = new HashSet<>();
		JsonArray bones = geo.getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject().getAsJsonArray("bones");
		for (JsonElement element : bones) {
			geometryBones.add(element.getAsJsonObject().get("name").getAsString());
		}

		JsonObject targetAnimations = target.getAsJsonObject("animations");
		JsonObject sourceAnimations = source.getAsJsonObject("animations");
		for (String name : MELEE_ANIMATIONS) {
			JsonObject animation = sourceAnimations.getAsJsonObject(name);
			if (animation == null) {
				throw new IllegalStateException("Missing source animation: " + name);
			}
			JsonObject copy = animation.deepCopy();
			JsonObject animationBones = copy.getAsJsonObject("bones");
			boolean preserveLegs = "ATK_2_end".equals(name)
					|| name.endsWith("_to_idle");
			for (String bone : List.copyOf(animationBones.keySet())) {
				if (!geometryBones.contains(bone)
						|| (!preserveLegs
						&& ("leg_left".equals(bone) || "leg_right".equals(bone)))) {
					animationBones.remove(bone);
				}
			}
			targetAnimations.add(name, copy);
		}

		Files.writeString(targetPath, GSON.toJson(target), StandardCharsets.UTF_8);
		System.out.println("Merged melee animations: " + String.join(", ", MELEE_ANIMATIONS));
	}

	private static JsonObject read(Path path) throws Exception {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private MergeVeritasMeleeAnimations() {
	}
}
