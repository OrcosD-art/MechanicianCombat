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

/** Replaces only the Veritas accelerated-run clip from the authored source. */
public final class MergeVeritasRun {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().setPrettyPrinting().create();

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

		JsonObject authoredRun = source.getAsJsonObject("animations")
				.getAsJsonObject("run");
		if (authoredRun == null) {
			throw new IllegalStateException("Missing source animation: run");
		}
		JsonObject runtimeRun = authoredRun.deepCopy();
		JsonObject animationBones = runtimeRun.getAsJsonObject("bones");
		for (String bone : List.copyOf(animationBones.keySet())) {
			if (!geometryBones.contains(bone)) {
				animationBones.remove(bone);
			}
		}
		target.getAsJsonObject("animations")
				.add("veritas_accelerated_run", runtimeRun);
		Files.writeString(targetPath, GSON.toJson(target),
				StandardCharsets.UTF_8);
		System.out.println("Merged run -> veritas_accelerated_run ("
				+ animationBones.size() + " bones)");
	}

	private static JsonObject read(Path path) throws Exception {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private MergeVeritasRun() {
	}
}
