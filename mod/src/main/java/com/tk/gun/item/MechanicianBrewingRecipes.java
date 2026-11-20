package com.tk.gun.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.TkGun;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Item brewing recipes authored by the visual recipe editor. */
public final class MechanicianBrewingRecipes {
	private static final String DATA_PACK_ID = "mechaniciancombat_live";
	private static final String RECIPE_NAMESPACE =
			"mechaniciancombat_custom";
	private static volatile Map<Item, Map<Item, ItemStack>> recipes =
			Map.of();

	public static void reload(MinecraftServer server) {
		Map<Item, Map<Item, ItemStack>> loaded = new HashMap<>();
		Map<net.minecraft.util.Identifier, Resource> builtIns =
				server.getResourceManager().findResources("brewing",
						id -> RECIPE_NAMESPACE.equals(id.getNamespace())
								&& id.getPath().endsWith(".json"));
		for (Map.Entry<net.minecraft.util.Identifier, Resource> entry
				: builtIns.entrySet()) {
			try {
				load(JsonParser.parseReader(entry.getValue().getReader())
						.getAsJsonObject(), loaded);
			} catch (Throwable error) {
				TkGun.LOGGER.warn(
						"[mechaniciancombat] Invalid built-in brewing recipe {}",
						entry.getKey(), error);
			}
		}
		Path root = server.getSavePath(WorldSavePath.DATAPACKS)
				.resolve(DATA_PACK_ID);
		Path directory = root.resolve("data").resolve(RECIPE_NAMESPACE)
				.resolve("brewing");
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> files =
					Files.newDirectoryStream(directory, "*.json")) {
				for (Path file : files) {
					try {
						load(JsonParser.parseString(
								Files.readString(file, StandardCharsets.UTF_8))
								.getAsJsonObject(), loaded);
					} catch (Throwable error) {
						TkGun.LOGGER.warn(
								"[mechaniciancombat] Invalid brewing recipe {}",
								file, error);
					}
				}
			} catch (Throwable error) {
				TkGun.LOGGER.error(
						"[mechaniciancombat] Failed to scan brewing recipes",
						error);
			}
		}
		recipes = loaded;
		TkGun.LOGGER.info(
				"[mechaniciancombat] Loaded custom item brewing recipes: {}",
				loaded.values().stream().mapToInt(Map::size).sum());
	}

	private static void load(JsonObject json,
			Map<Item, Map<Item, ItemStack>> loaded) {
		Item input = item(json, "input_item");
		Item reagent = item(json, "reagent_item");
		Item output = item(json, "output_item");
		if (input == Items.AIR || reagent == Items.AIR
				|| output == Items.AIR) {
			return;
		}
		int count = Math.max(1, Math.min(output.getMaxCount(),
				json.has("output_count")
						? json.get("output_count").getAsInt() : 1));
		loaded.computeIfAbsent(input, ignored -> new HashMap<>())
				.put(reagent, new ItemStack(output, count));
	}

	public static ItemStack find(ItemStack input, ItemStack reagent) {
		if (input.isEmpty() || reagent.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Map<Item, ItemStack> reagents =
				recipes.get(input.getItem());
		if (reagents == null) {
			return ItemStack.EMPTY;
		}
		ItemStack output = reagents.get(reagent.getItem());
		return output == null ? ItemStack.EMPTY : output.copy();
	}

	public static boolean acceptsInput(ItemStack stack) {
		return !stack.isEmpty()
				&& recipes.containsKey(stack.getItem());
	}

	public static boolean acceptsReagent(ItemStack input, ItemStack reagent) {
		return !find(input, reagent).isEmpty();
	}

	private static Item item(JsonObject json, String key) {
		if (!json.has(key)) {
			return Items.AIR;
		}
		Identifier id = Identifier.tryParse(json.get(key).getAsString());
		return id == null ? Items.AIR : Registries.ITEM.get(id);
	}

	private MechanicianBrewingRecipes() {
	}
}
