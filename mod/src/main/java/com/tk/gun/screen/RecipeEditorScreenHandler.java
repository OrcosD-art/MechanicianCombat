package com.tk.gun.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.tk.gun.TkGun;
import com.tk.gun.net.RecipeEditorActionPayload;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Visual editor that writes standard crafting recipes into an enabled world data pack. */
public final class RecipeEditorScreenHandler extends ScreenHandler {
	public static final int GRID_SIZE = 9;
	public static final int RESULT_SLOT = GRID_SIZE;
	public static final int PLAYER_SLOT_START = GRID_SIZE + 1;
	public static final int HOTBAR_SLOT_START = PLAYER_SLOT_START + 27;
	public static final int GRID_X = 42;
	public static final int GRID_Y = 32;
	public static final int PLAYER_X = 19;
	public static final int PLAYER_Y = 102;
	public static final int HOTBAR_Y = 160;

	private static final String DATA_PACK_ID = "mechaniciancombat_live";
	private static final String RECIPE_NAMESPACE = "mechaniciancombat_custom";
	private static final int DATA_PACK_FORMAT = 48;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final ScreenHandlerType<RecipeEditorScreenHandler> TYPE =
			new ExtendedScreenHandlerType<>(
					(syncId, inventory, data) ->
							new RecipeEditorScreenHandler(syncId, inventory),
					PacketCodec.of((value, buffer) -> { },
							buffer -> (byte) 0));

	private final PlayerEntity owner;
	private final Inventory ingredients = new SimpleInventory(GRID_SIZE);
	private final Inventory result = new SimpleInventory(1);

	public RecipeEditorScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(TYPE, syncId);
		this.owner = playerInventory.player;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				addSlot(new Slot(ingredients, column + row * 3,
						GRID_X + column * 18, GRID_Y + row * 18));
			}
		}
		addSlot(new Slot(result, 0, 134, 50));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory,
						column + row * 9 + 9,
						PLAYER_X + column * 18,
						PLAYER_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column,
					PLAYER_X + column * 18, HOTBAR_Y));
		}
	}

	public ItemStack resultTemplate() {
		return result.getStack(0);
	}

	public void handleAction(ServerPlayerEntity player,
			RecipeEditorActionPayload action) {
		if (player.currentScreenHandler != this
				|| !player.hasPermissionLevel(2)) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		try {
			String recipeId = normalizeRecipeId(action.recipeId());
			if (recipeId.isEmpty() && !action.delete()) {
				recipeId = defaultRecipeId();
			}
			if (recipeId.isEmpty()) {
				player.sendMessage(Text.translatable(
						"message.mechaniciancombat.recipe_editor.invalid_id"), false);
				return;
			}
			Path root = server.getSavePath(WorldSavePath.DATAPACKS)
					.resolve(DATA_PACK_ID);
			Path recipeDir = root.resolve("data")
					.resolve(RECIPE_NAMESPACE).resolve("recipe");
			Files.createDirectories(recipeDir);
			Path target = recipeDir.resolve(recipeId + ".json").normalize();
			if (!target.startsWith(recipeDir.normalize())) {
				throw new IllegalArgumentException("Invalid recipe path");
			}
			if (action.delete()) {
				Files.deleteIfExists(target);
				writePackMetadata(root);
				enableAndReload(server);
				player.sendMessage(Text.translatable(
						"message.mechaniciancombat.recipe_editor.deleted",
						RECIPE_NAMESPACE + ":" + recipeId), false);
				return;
			}

			JsonObject recipe = buildRecipe(action.shaped());
			if (recipe == null) {
				player.sendMessage(Text.translatable(
						"message.mechaniciancombat.recipe_editor.invalid_recipe"),
						false);
				return;
			}
			writePackMetadata(root);
			if (Files.isRegularFile(target)) {
				Path backup = root.resolve("backups")
						.resolve(recipeId + ".json");
				Files.createDirectories(backup.getParent());
				Files.copy(target, backup,
						StandardCopyOption.REPLACE_EXISTING);
			}
			Files.writeString(target, GSON.toJson(recipe),
					StandardCharsets.UTF_8);
			enableAndReload(server);
			player.sendMessage(Text.translatable(
					"message.mechaniciancombat.recipe_editor.saved",
					RECIPE_NAMESPACE + ":" + recipeId), false);
			TkGun.LOGGER.info("[mechaniciancombat] Visual recipe saved: {}:{}",
					RECIPE_NAMESPACE, recipeId);
		} catch (Throwable error) {
			TkGun.LOGGER.error("[mechaniciancombat] Recipe editor save failed",
					error);
			player.sendMessage(Text.translatable(
					"message.mechaniciancombat.recipe_editor.failed",
					String.valueOf(error.getMessage())), false);
		}
	}

	private JsonObject buildRecipe(boolean shaped) {
		ItemStack output = result.getStack(0);
		if (output.isEmpty()) {
			return null;
		}
		JsonObject recipe = new JsonObject();
		JsonObject resultJson = serializeStack(output);
		if (resultJson == null) {
			return null;
		}
		recipe.addProperty("type", shaped
				? "minecraft:crafting_shaped"
				: "minecraft:crafting_shapeless");
		recipe.addProperty("category", "misc");
		if (shaped) {
			if (!buildShaped(recipe)) {
				return null;
			}
		} else {
			JsonArray list = new JsonArray();
			for (int index = 0; index < GRID_SIZE; index++) {
				ItemStack stack = ingredients.getStack(index);
				if (!stack.isEmpty()) {
					list.add(ingredient(stack));
				}
			}
			if (list.isEmpty()) {
				return null;
			}
			recipe.add("ingredients", list);
		}
		recipe.add("result", resultJson);
		return recipe;
	}

	private boolean buildShaped(JsonObject recipe) {
		int minRow = 3;
		int maxRow = -1;
		int minColumn = 3;
		int maxColumn = -1;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				if (ingredients.getStack(column + row * 3).isEmpty()) {
					continue;
				}
				minRow = Math.min(minRow, row);
				maxRow = Math.max(maxRow, row);
				minColumn = Math.min(minColumn, column);
				maxColumn = Math.max(maxColumn, column);
			}
		}
		if (maxRow < minRow || maxColumn < minColumn) {
			return false;
		}
		Map<String, Character> symbols = new LinkedHashMap<>();
		JsonObject key = new JsonObject();
		JsonArray pattern = new JsonArray();
		int nextSymbol = 0;
		for (int row = minRow; row <= maxRow; row++) {
			StringBuilder line = new StringBuilder();
			for (int column = minColumn; column <= maxColumn; column++) {
				ItemStack stack = ingredients.getStack(column + row * 3);
				if (stack.isEmpty()) {
					line.append(' ');
					continue;
				}
				String itemId = Registries.ITEM.getId(stack.getItem()).toString();
				Character symbol = symbols.get(itemId);
				if (symbol == null) {
					symbol = (char) ('A' + nextSymbol++);
					symbols.put(itemId, symbol);
					key.add(String.valueOf(symbol), ingredient(stack));
				}
				line.append(symbol);
			}
			pattern.add(line.toString());
		}
		recipe.add("pattern", pattern);
		recipe.add("key", key);
		return true;
	}

	private JsonObject ingredient(ItemStack stack) {
		JsonObject ingredient = new JsonObject();
		ingredient.addProperty("item",
				Registries.ITEM.getId(stack.getItem()).toString());
		return ingredient;
	}

	private JsonObject serializeStack(ItemStack stack) {
		int count = Math.max(1, stack.getCount());
		JsonObject fallback = new JsonObject();
		fallback.addProperty("id",
				Registries.ITEM.getId(stack.getItem()).toString());
		fallback.addProperty("count", count);
		try {
			RegistryOps<JsonElement> ops = RegistryOps.of(
					JsonOps.INSTANCE, owner.getRegistryManager());
			Object encoded = ItemStack.CODEC.encodeStart(ops,
					stack.copyWithCount(count));
			Object result = encoded.getClass().getMethod("result")
					.invoke(encoded);
			if (result instanceof Optional<?> optional
					&& optional.orElse(null) instanceof JsonObject json
					&& json.has("id")) {
				return json;
			}
		} catch (Throwable error) {
			TkGun.LOGGER.warn(
					"[mechaniciancombat] Output components could not be serialized; "
							+ "using item ID and count only: {}",
					error.toString());
		}
		return fallback;
	}

	private String defaultRecipeId() {
		ItemStack output = result.getStack(0);
		String item = output.isEmpty() ? "recipe"
				: Registries.ITEM.getId(output.getItem()).getPath();
		return "custom_" + item + "_" + System.currentTimeMillis();
	}

	private static String normalizeRecipeId(String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		int separator = value.indexOf(':');
		if (separator >= 0) {
			value = value.substring(separator + 1);
		}
		value = value.replaceAll("[^a-z0-9_./-]", "_");
		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		return value;
	}

	private static void writePackMetadata(Path root) throws Exception {
		Files.createDirectories(root);
		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", DATA_PACK_FORMAT);
		pack.addProperty("description",
				"MechanicianCombat visual recipe editor");
		JsonObject metadata = new JsonObject();
		metadata.add("pack", pack);
		Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(metadata),
				StandardCharsets.UTF_8);
	}

	private static void enableAndReload(MinecraftServer server) {
		var manager = server.getDataPackManager();
		manager.scanPacks();
		String packId = "file/" + DATA_PACK_ID;
		if (!manager.getEnabledIds().contains(packId)) {
			manager.enable(packId);
		}
		server.reloadResources(manager.getEnabledIds()).exceptionally(error -> {
			TkGun.LOGGER.error(
					"[mechaniciancombat] Failed to reload visual recipe data pack",
					error);
			return null;
		});
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getStack();
		ItemStack copy = original.copy();
		boolean moved;
		if (slotIndex < PLAYER_SLOT_START) {
			moved = insertItem(original, PLAYER_SLOT_START, slots.size(), true);
		} else if (slotIndex < HOTBAR_SLOT_START) {
			moved = insertItem(original, HOTBAR_SLOT_START, slots.size(), true);
		} else {
			moved = insertItem(original, PLAYER_SLOT_START,
					HOTBAR_SLOT_START, true);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return copy;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (!(player instanceof ServerPlayerEntity)) {
			return;
		}
		for (int index = 0; index < ingredients.size(); index++) {
			ItemStack stack = ingredients.removeStack(index);
			if (!stack.isEmpty()) {
				player.getInventory().offerOrDrop(stack);
			}
		}
		ItemStack output = result.removeStack(0);
		if (!output.isEmpty()) {
			player.getInventory().offerOrDrop(output);
		}
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player == owner && player.isAlive();
	}

	@Override
	public ScreenHandlerType<?> getType() {
		return TYPE;
	}

	public static net.minecraft.screen.NamedScreenHandlerFactory createFactory() {
		Object proxy = Proxy.newProxyInstance(
				RecipeEditorScreenHandler.class.getClassLoader(),
				new Class<?>[]{net.fabricmc.fabric.api.screenhandler.v1
						.ExtendedScreenHandlerFactory.class},
				(p, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return switch (method.getName()) {
							case "toString" -> "mechaniciancombat:recipe_editor_factory";
							case "hashCode" -> System.identityHashCode(p);
							case "equals" -> p == args[0];
							default -> null;
						};
					}
					if (method.getReturnType() == boolean.class) {
						return true;
					}
					if (method.getParameterCount() == 0
							&& Text.class.isAssignableFrom(method.getReturnType())) {
						return Text.translatable(
								"screen.mechaniciancombat.recipe_editor");
					}
					if (method.getParameterCount() == 1) {
						return (byte) 0;
					}
					if (method.getParameterCount() == 3) {
						return new RecipeEditorScreenHandler(
								(int) args[0], (PlayerInventory) args[1]);
					}
					return null;
				});
		return (net.minecraft.screen.NamedScreenHandlerFactory) proxy;
	}
}
