import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Replaces only rotate_effect bones with the exact Blockbench GeckoLib export.
 * Pre/post step keyframes are preserved instead of flattened manually.
 */
public final class MergeRotateEffectExport {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().create();

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: MergeRotateEffectExport <exported-animation> <runtime-animation>");
		}
		JsonObject exported = read(args[0]);
		JsonObject runtime = read(args[1]);
		JsonObject exportedAnimations =
				exported.getAsJsonObject("animations");
		JsonObject runtimeAnimations =
				runtime.getAsJsonObject("animations");
		int merged = 0;
		for (Map.Entry<String, JsonElement> entry
				: exportedAnimations.entrySet()) {
			JsonObject exportedAnimation = entry.getValue().getAsJsonObject();
			JsonObject runtimeAnimation =
					runtimeAnimations.getAsJsonObject(entry.getKey());
			if (runtimeAnimation == null || !exportedAnimation.has("bones")
					|| !runtimeAnimation.has("bones")) {
				continue;
			}
			JsonObject exportedBones =
					exportedAnimation.getAsJsonObject("bones");
			JsonObject runtimeBones =
					runtimeAnimation.getAsJsonObject("bones");
			JsonElement rotateEffect =
					exportedBones.get("rotate_effect");
			if (rotateEffect == null) {
				continue;
			}
			runtimeBones.add("rotate_effect", rotateEffect.deepCopy());
			merged++;
		}
		Files.writeString(Path.of(args[1]), GSON.toJson(runtime),
				StandardCharsets.UTF_8);
		System.out.println("Merged rotate_effect bones: " + merged);
	}

	private static JsonObject read(String path) throws Exception {
		return JsonParser.parseString(Files.readString(Path.of(path),
				StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private MergeRotateEffectExport() {
	}
}
