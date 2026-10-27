import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** One-shot source utility for merging redundant focus-shot clips. */
public final class DedupeGunAnimations {
	private static final List<String> REMOVED_SOURCE_CLIPS = List.of(
			"Focus_shooting", "Focus_shooting2", "Focus_shooting3", "Focus_shooting5");
	private static final List<String> REMOVED_UNREACHABLE_CLIPS = List.of(
			"Focus_ready_upper", "Focus_over_shooting_upper",
			"Focus_shooting_upper", "Focus_shooting2_upper", "Focus_shooting3_upper",
			"Focus_shooting4_upper", "Focus_shooting5_upper", "Focus_shooting6_upper",
			"Focus_shooting7_upper", "Focus_ready_hold");

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static void main(String[] args) throws Exception {
		boolean bbmodelOnly = args.length > 3 && "--bbmodel-only".equals(args[3]);
		Path geckoPath = bbmodelOnly ? null : Path.of(args[0]);
		Path bbmodelPath = Path.of(args[1]);
		Path backupDir = Path.of(args[2]);
		Files.createDirectories(backupDir);
		backup(bbmodelPath, backupDir);

		JsonObject bbmodel = read(bbmodelPath);
		mergeBbmodel(bbmodel);
		write(bbmodelPath, bbmodel);

		if (!bbmodelOnly) {
			backup(geckoPath, backupDir);
			JsonObject gecko = read(geckoPath);
			mergeGeckoLib(gecko);
			write(geckoPath, gecko);
			System.out.println("GeckoLib animations: " + gecko.getAsJsonObject("animations").size());
		}
		System.out.println("Blockbench animations: " + bbmodel.getAsJsonArray("animations").size());
		System.out.println("Merged clips: " + String.join(", ", REMOVED_SOURCE_CLIPS));
		System.out.println("Removed unreachable clips: " + String.join(", ", REMOVED_UNREACHABLE_CLIPS));
		System.out.println("Canonical clips: Focus_shooting_extra (1-3), "
				+ "Focus_shooting1 (4..max-4), Focus_shooting4 (max-3..max-2), "
				+ "Focus_shooting6, Focus_shooting7");
	}

	private static void mergeGeckoLib(JsonObject gecko) {
		JsonObject animations = gecko.getAsJsonObject("animations");
		required(animations, "Focus_shooting_extra");
		required(animations, "Focus_shooting1");
		required(animations, "Focus_shooting4");
		required(animations, "Focus_shooting6");
		required(animations, "Focus_shooting7");

		for (String name : REMOVED_SOURCE_CLIPS) {
			animations.remove(name);
		}
		for (String name : REMOVED_UNREACHABLE_CLIPS) {
			animations.remove(name);
		}
	}

	private static void mergeBbmodel(JsonObject bbmodel) {
		JsonArray animations = bbmodel.getAsJsonArray("animations");
		required(animations, "Focus_shooting_extra");
		required(animations, "Focus_shooting1");
		required(animations, "Focus_shooting4");
		required(animations, "Focus_shooting6");
		required(animations, "Focus_shooting7");

		for (int i = animations.size() - 1; i >= 0; i--) {
			String name = animations.get(i).getAsJsonObject().get("name").getAsString();
			if (REMOVED_SOURCE_CLIPS.contains(name)) {
				animations.remove(i);
			}
		}
	}

	private static JsonObject required(JsonObject animations, String name) {
		JsonElement value = animations.get(name);
		if (value == null || !value.isJsonObject()) {
			throw new IllegalStateException("Missing animation: " + name);
		}
		return value.getAsJsonObject();
	}

	private static JsonObject required(JsonArray animations, String name) {
		for (JsonElement element : animations) {
			JsonObject animation = element.getAsJsonObject();
			if (name.equals(animation.get("name").getAsString())) {
				return animation;
			}
		}
		throw new IllegalStateException("Missing animation: " + name);
	}

	private static JsonObject read(Path path) throws IOException {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
	}

	private static void write(Path path, JsonObject document) throws IOException {
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void backup(Path path, Path backupDir) throws IOException {
		Files.copy(path, backupDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
	}

	private DedupeGunAnimations() {
	}
}
