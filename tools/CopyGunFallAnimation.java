import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Copies the Quiet Storm fall clip into another gun animation file, removing
 * only bones that do not exist in that gun's geometry.
 */
public final class CopyGunFallAnimation {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().create();

	public static void main(String[] args) throws Exception {
		if (args.length < 4 || args.length % 2 != 0) {
			throw new IllegalArgumentException(
					"usage: CopyGunFallAnimation <source-animation>"
							+ " <source-geo> <target-animation> <target-geo>"
							+ " [...]");
		}
		JsonObject source = read(args[0]);
		JsonObject sourceClip = source.getAsJsonObject("animations")
				.getAsJsonObject("fall");
		if (sourceClip == null) {
			throw new IllegalStateException(
					"Source animation has no fall clip: " + args[0]);
		}
		for (int index = 2; index < args.length; index += 2) {
			Path targetPath = Path.of(args[index]);
			Set<String> targetBones = geometryBones(Path.of(args[index + 1]));
			JsonObject target = read(targetPath.toString());
			JsonObject clip = sourceClip.deepCopy();
			JsonObject bones = clip.getAsJsonObject("bones");
			for (String bone : Set.copyOf(bones.keySet())) {
				if (!targetBones.contains(bone)) {
					bones.remove(bone);
				}
			}
			target.getAsJsonObject("animations")
					.add("fall", clip);
			write(targetPath, target);
			System.out.println("Copied fall to " + targetPath
					+ " bones=" + bones.size());
		}
	}

	private static Set<String> geometryBones(Path path) throws Exception {
		JsonObject root = read(path.toString()).getAsJsonArray(
				"minecraft:geometry").get(0).getAsJsonObject();
		Set<String> names = new HashSet<>();
		for (JsonElement element : root.getAsJsonArray("bones")) {
			names.add(element.getAsJsonObject().get("name").getAsString());
		}
		return names;
	}

	private static JsonObject read(String path) throws Exception {
		return JsonParser.parseString(Files.readString(Path.of(path),
				StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void write(Path path, JsonObject value) throws Exception {
		Files.writeString(path, GSON.toJson(value),
				StandardCharsets.UTF_8);
	}

	private CopyGunFallAnimation() {
	}
}
