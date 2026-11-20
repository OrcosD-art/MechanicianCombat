import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Imports the authored chain-slide recovery clips into every gun rig. */
public final class SyncSoulAnchorSlideAnimations {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping().create();
	private static final List<String> CLIPS = List.of(
			"chain_sliding_to_idle", "chain_sliding_to_run");

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			throw new IllegalArgumentException(
					"Usage: <chain_skill.bbmodel> <rig.json>...");
		}
		JsonObject source = read(Path.of(args[0]));
		for (int index = 1; index < args.length; index++) {
			Path targetPath = Path.of(args[index]);
			JsonObject target = read(targetPath);
			JsonObject animations = target.getAsJsonObject("animations");
			for (String name : CLIPS) {
				JsonObject base = convert(findAnimation(source, name));
				animations.add(name, base);
				JsonObject upper = base.deepCopy();
				JsonObject bones = upper.getAsJsonObject("bones");
				bones.remove("leg_left");
				bones.remove("leg_right");
				animations.add(name + "_upper", upper);
			}
			Files.writeString(targetPath, GSON.toJson(target),
					StandardCharsets.UTF_8);
			System.out.println("synchronized " + targetPath);
		}
	}

	private static JsonObject findAnimation(JsonObject source, String name) {
		for (JsonElement element : source.getAsJsonArray("animations")) {
			JsonObject animation = element.getAsJsonObject();
			if (name.equals(animation.get("name").getAsString())) {
				return animation;
			}
		}
		throw new IllegalStateException("Missing source animation " + name);
	}

	private static JsonObject convert(JsonObject animation) {
		JsonObject result = new JsonObject();
		result.addProperty("loop",
				"loop".equalsIgnoreCase(animation.get("loop").getAsString())
						? "loop" : "hold_on_last_frame");
		result.add("animation_length", number(
				animation.get("length").getAsString()));

		JsonObject bones = new JsonObject();
		JsonObject sounds = new JsonObject();
		for (JsonElement entry :
				animation.getAsJsonObject("animators").asMap().values()) {
			JsonObject animator = entry.getAsJsonObject();
			String type = animator.get("type").getAsString();
			String name = animator.get("name").getAsString();
			if ("bone".equals(type)) {
				JsonObject channels = new JsonObject();
				for (JsonElement keyframeElement :
						animator.getAsJsonArray("keyframes")) {
					JsonObject keyframe = keyframeElement.getAsJsonObject();
					String channel = keyframe.get("channel").getAsString();
					JsonArray points =
							keyframe.getAsJsonArray("data_points");
					if (points.isEmpty()
							|| (!"rotation".equals(channel)
							&& !"position".equals(channel)
							&& !"scale".equals(channel))) {
						continue;
					}
					JsonObject channelFrames = channels.has(channel)
							? channels.getAsJsonObject(channel)
							: new JsonObject();
					JsonObject point = points.get(0).getAsJsonObject();
					JsonArray vector = new JsonArray();
					vector.add(number(point.get("x").getAsString()));
					vector.add(number(point.get("y").getAsString()));
					vector.add(number(point.get("z").getAsString()));
					channelFrames.add(timeKey(
							keyframe.get("time").getAsString()), vector);
					channels.add(channel, channelFrames);
				}
				bones.add(name, channels);
			} else if ("effect".equals(type)) {
				for (JsonElement keyframeElement :
						animator.getAsJsonArray("keyframes")) {
					JsonObject keyframe = keyframeElement.getAsJsonObject();
					if (!"sound".equals(
							keyframe.get("channel").getAsString())) {
						continue;
					}
					for (JsonElement pointElement :
							keyframe.getAsJsonArray("data_points")) {
						JsonObject point = pointElement.getAsJsonObject();
						String effect = point.has("effect")
								? point.get("effect").getAsString() : "chain";
						if (effect.endsWith(".mp3")) {
							effect = effect.substring(0, effect.length() - 4);
						}
						JsonObject sound = new JsonObject();
						sound.addProperty("effect", effect);
						sound.addProperty("locator",
								point.has("locator")
										? point.get("locator").getAsString()
										: "locator");
						sounds.add(timeKey(
								keyframe.get("time").getAsString()), sound);
					}
				}
			}
		}
		result.add("bones", bones);
		if (!sounds.isEmpty()) {
			result.add("sound_effects", sounds);
		}
		return result;
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value).stripTrailingZeros();
	}

	private static JsonPrimitive number(String value) {
		return new JsonPrimitive(decimal(value));
	}

	private static String timeKey(String value) {
		String text = decimal(value).toPlainString();
		return text.contains(".") ? text : text + ".0";
	}

	private static JsonObject read(Path path) throws Exception {
		return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
				JsonObject.class);
	}

	private SyncSoulAnchorSlideAnimations() {
	}
}
