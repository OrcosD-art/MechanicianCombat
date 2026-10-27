import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts the gun hierarchy and its deploy/retract animation channels from a player rig. */
public final class ExtractGunSubtreeTemplate {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Set<String> KEPT_ANIMATIONS = Set.of("Focus_ready", "Focus_over");

	public static void main(String[] args) throws Exception {
		Path source = Path.of(args[0]);
		Path destination = Path.of(args[1]);
		JsonObject root = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8),
				JsonObject.class);

		JsonObject gun = findGroup(root.getAsJsonArray("outliner"), "gun");
		if (gun == null) {
			throw new IllegalStateException("No gun group in source model.");
		}

		Set<String> groupNames = new LinkedHashSet<>();
		Set<String> elementUuids = new LinkedHashSet<>();
		collect(gun, groupNames, elementUuids);

		JsonArray filteredElements = new JsonArray();
		for (JsonElement element : root.getAsJsonArray("elements")) {
			JsonObject cube = element.getAsJsonObject();
			if (cube.has("uuid") && elementUuids.contains(cube.get("uuid").getAsString())) {
				filteredElements.add(cube.deepCopy());
			}
		}
		root.add("elements", filteredElements);

		JsonArray filteredOutliner = new JsonArray();
		filteredOutliner.add(gun.deepCopy());
		root.add("outliner", filteredOutliner);

		JsonArray filteredAnimations = new JsonArray();
		for (JsonElement element : root.getAsJsonArray("animations")) {
			JsonObject animation = element.getAsJsonObject();
			String name = animation.get("name").getAsString();
			if (!KEPT_ANIMATIONS.contains(name)) {
				continue;
			}
			JsonObject copy = animation.deepCopy();
			JsonObject animators = copy.getAsJsonObject("animators");
			for (String key : List.copyOf(animators.keySet())) {
				JsonObject animator = animators.getAsJsonObject(key);
				String bone = animator.has("name") ? animator.get("name").getAsString() : "";
				String type = animator.has("type") ? animator.get("type").getAsString() : "";
				if (!"bone".equals(type) || !groupNames.contains(bone)) {
					animators.remove(key);
				}
			}
			copy.addProperty("selected", false);
			filteredAnimations.add(copy);
		}
		root.add("animations", filteredAnimations);

		root.addProperty("name", "MechanicianCombat_GunOnly_Deploy_Template");
		root.addProperty("model_identifier", "tkgun:gun_only_deploy");
		Files.createDirectories(destination.getParent());
		Files.writeString(destination, GSON.toJson(root), StandardCharsets.UTF_8);

		System.out.println("Groups: " + String.join(", ", new ArrayList<>(groupNames)));
		System.out.println("Elements: " + filteredElements.size());
		System.out.println("Animations: " + filteredAnimations.size());
		for (JsonElement element : filteredAnimations) {
			JsonObject animation = element.getAsJsonObject();
			System.out.println("  " + animation.get("name").getAsString() + " -> "
					+ animation.getAsJsonObject("animators").size() + " bone channels");
		}
	}

	private static JsonObject findGroup(JsonArray nodes, String name) {
		for (JsonElement element : nodes) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject node = element.getAsJsonObject();
			if (name.equals(node.has("name") ? node.get("name").getAsString() : "")) {
				return node;
			}
			if (node.has("children")) {
				JsonObject nested = findGroup(node.getAsJsonArray("children"), name);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private static void collect(JsonObject group, Set<String> groups, Set<String> elementUuids) {
		groups.add(group.get("name").getAsString());
		if (!group.has("children")) {
			return;
		}
		for (JsonElement child : group.getAsJsonArray("children")) {
			if (child.isJsonObject()) {
				collect(child.getAsJsonObject(), groups, elementUuids);
			} else if (child.isJsonPrimitive()) {
				elementUuids.add(child.getAsString());
			}
		}
	}

	private ExtractGunSubtreeTemplate() {
	}
}
