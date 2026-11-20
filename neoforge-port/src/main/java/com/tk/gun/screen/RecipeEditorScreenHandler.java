package com.tk.gun.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.tk.gun.TkGun;
import com.tk.gun.item.MechanicianBrewingRecipes;
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

/** Visual crafting, furnace, and brewing recipe editor. */
public final class RecipeEditorScreenHandler extends ScreenHandler {
	public static final int PAGE_CRAFTING = 0;
	public static final int PAGE_FURNACE = 1;
	public static final int PAGE_BREWING = 2;
	public static final int PAGE_COUNT = 3;

	public static final int GRID_SIZE = 9;
	public static final int CRAFTING_RESULT_SLOT = 9;
	public static final int FURNACE_INPUT_SLOT = 10;
	public static final int FURNACE_RESULT_SLOT = 11;
	public static final int BREWING_INPUT_SLOT = 12;
	public static final int BREWING_REAGENT_SLOT = 13;
	public static final int BREWING_RESULT_SLOT = 14;
	public static final int EDITOR_SLOT_COUNT = 15;
	public static final int PLAYER_SLOT_START = EDITOR_SLOT_COUNT;
	public static final int HOTBAR_SLOT_START = PLAYER_SLOT_START + 27;
	/** Vanilla crafting-table slot positions. */
	public static final int GRID_X = 30;
	public static final int GRID_Y = 17;
	public static final int RESULT_X = 124;
	public static final int RESULT_Y = 35;
	public static final int FURNACE_INPUT_X = 56;
	public static final int FURNACE_INPUT_Y = 17;
	public static final int FURNACE_RESULT_X = 116;
	public static final int FURNACE_RESULT_Y = 35;
	public static final int BREWING_INPUT_X = 79;
	public static final int BREWING_INPUT_Y = 17;
	public static final int BREWING_REAGENT_X = 56;
	public static final int BREWING_REAGENT_Y = 51;
	public static final int BREWING_RESULT_X = 102;
	public static final int BREWING_RESULT_Y = 51;
	public static final int PLAYER_X = 8;
	public static final int PLAYER_Y = 84;
	public static final int HOTBAR_Y = 142;

	private static final String DATA_PACK_ID = "mechaniciancombat_live";
	private static final String RECIPE_NAMESPACE = "mechaniciancombat_custom";
	private static final int DATA_PACK_FORMAT = 48;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final ScreenHandlerType<RecipeEditorScreenHandler> TYPE =
			ExtendedScreenHandlerType.create(
					(syncId, inventory, data) ->
							new RecipeEditorScreenHandler(syncId, inventory),
					PacketCodec.of((value, buffer) -> { },
							buffer -> (byte) 0));

	private final PlayerEntity owner;
	private final Inventory ingredients = new SimpleInventory(GRID_SIZE);
	private final Inventory result = new SimpleInventory(1);
	private final Inventory furnaceInput = new SimpleInventory(1);
	private final Inventory furnaceResult = new SimpleInventory(1);
	private final Inventory brewingInput = new SimpleInventory(1);
	private final Inventory brewingReagent = new SimpleInventory(1);
	private final Inventory brewingResult = new SimpleInventory(1);
	private int page = PAGE_CRAFTING;

	public RecipeEditorScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(TYPE, syncId);
		this.owner = playerInventory.player;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				addSlot(new PageSlot(ingredients, column + row * 3,
						GRID_X + column * 18, GRID_Y + row * 18,
						PAGE_CRAFTING));
			}
		}
		addSlot(new PageSlot(result, 0, RESULT_X, RESULT_Y,
				PAGE_CRAFTING));
		addSlot(new PageSlot(furnaceInput, 0, FURNACE_INPUT_X,
				FURNACE_INPUT_Y, PAGE_FURNACE));
		addSlot(new PageSlot(furnaceResult, 0, FURNACE_RESULT_X,
				FURNACE_RESULT_Y, PAGE_FURNACE));
		addSlot(new PageSlot(brewingInput, 0, BREWING_INPUT_X,
				BREWING_INPUT_Y, PAGE_BREWING));
		addSlot(new PageSlot(brewingReagent, 0, BREWING_REAGENT_X,
				BREWING_REAGENT_Y, PAGE_BREWING));
		addSlot(new PageSlot(brewingResult, 0, BREWING_RESULT_X,
				BREWING_RESULT_Y, PAGE_BREWING));
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

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = Math.max(PAGE_CRAFTING,
				Math.min(PAGE_BREWING, page));
	}

	private final class PageSlot extends Slot {
		private final int slotPage;

		private PageSlot(Inventory inventory, int index, int x, int y,
				int slotPage) {
			super(inventory, index, x, y);
			this.slotPage = slotPage;
		}

		@Override
		public boolean isEnabled() {
			return page == slotPage;
		}

		@Override
		public boolean canBeHighlighted() {
			return isEnabled();
		}
	}

	public ItemStack resultTemplate() {
		return outputForPage(page).getStack(0);
	}

	public void handleAction(ServerPlayerEntity player,
			RecipeEditorActionPayload action) {
		if (player.currentScreenHandler != this
				|| !player.hasPermissionLevel(2)) {
			return;
		}
		setPage(action.page());
		if (action.action() == RecipeEditorActionPayload.ACTION_SET_PAGE) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		try {
			boolean delete =
					action.action() == RecipeEditorActionPayload.ACTION_DELETE;
			String recipeId = normalizeRecipeId(action.recipeId());
			if (recipeId.isEmpty() && !delete) {
				recipeId = defaultRecipeId();
			}
			if (recipeId.isEmpty()) {
				player.sendMessage(Text.translatable(
						"message.mechaniciancombat.recipe_editor.invalid_id"), false);
				return;
			}
			Path root = server.getSavePath(WorldSavePath.DATAPACKS)
					.resolve(DATA_PACK_ID);
			Path recipeDir = recipeDirectory(root, page);
			Files.createDirectories(recipeDir);
			Path target = recipeDir.resolve(recipeId + ".json").normalize();
			if (!target.startsWith(recipeDir.normalize())) {
				throw new IllegalArgumentException("Invalid recipe path");
			}
			if (delete) {
				Files.deleteIfExists(target);
				writePackMetadata(root);
				if (page == PAGE_BREWING) {
					MechanicianBrewingRecipes.reload(server);
				}
				enableAndReload(server);
				player.sendMessage(Text.translatable(
						"message.mechaniciancombat.recipe_editor.deleted",
						RECIPE_NAMESPACE + ":" + recipeId), false);
				return;
			}

			JsonObject recipe = page == PAGE_BREWING
					? buildBrewingRecipe()
					: buildRecipe(page, action.shaped(),
							action.experience(), action.cookingTime());
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
			if (page == PAGE_BREWING) {
				MechanicianBrewingRecipes.reload(server);
			}
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

	private JsonObject buildRecipe(int page, boolean shaped,
			float experience, int cookingTime) {
		ItemStack output = outputForPage(page).getStack(0);
		if (output.isEmpty()) {
			return null;
		}
		JsonObject recipe = new JsonObject();
		JsonObject resultJson = serializeStack(output);
		if (resultJson == null) {
			return null;
		}
		if (page == PAGE_FURNACE) {
			ItemStack input = furnaceInput.getStack(0);
			if (input.isEmpty()) {
				return null;
			}
			recipe.addProperty("type", "minecraft:smelting");
			recipe.addProperty("category", "misc");
			recipe.add("ingredient", ingredient(input));
			recipe.add("result", resultJson);
			recipe.addProperty("experience",
					Math.max(0.0F, experience));
			recipe.addProperty("cookingtime",
					Math.max(1, cookingTime));
			return recipe;
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

	private JsonObject buildBrewingRecipe() {
		ItemStack input = brewingInput.getStack(0);
		ItemStack reagent = brewingReagent.getStack(0);
		ItemStack output = brewingResult.getStack(0);
		if (input.isEmpty() || reagent.isEmpty() || output.isEmpty()) {
			return null;
		}
		JsonObject recipe = new JsonObject();
		recipe.addProperty("type", "mechaniciancombat:brewing");
		recipe.addProperty("input_item",
				Registries.ITEM.getId(input.getItem()).toString());
		recipe.addProperty("reagent_item",
				Registries.ITEM.getId(reagent.getItem()).toString());
		recipe.addProperty("output_item",
				Registries.ITEM.getId(output.getItem()).toString());
		recipe.addProperty("output_count", Math.max(1,
				Math.min(output.getMaxCount(), output.getCount())));
		return recipe;
	}

	private Inventory outputForPage(int page) {
		return switch (page) {
			case PAGE_FURNACE -> furnaceResult;
			case PAGE_BREWING -> brewingResult;
			default -> result;
		};
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
		ItemStack output = outputForPage(page).getStack(0);
		String item = output.isEmpty() ? "recipe"
				: Registries.ITEM.getId(output.getItem()).getPath();
		return "custom_" + item + "_" + System.currentTimeMillis();
	}

	private static Path recipeDirectory(Path root, int page) {
		return root.resolve("data").resolve(RECIPE_NAMESPACE)
				.resolve(page == PAGE_BREWING ? "brewing" : "recipe");
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
		for (Inventory inventory : new Inventory[]{
				result, furnaceInput, furnaceResult, brewingInput,
				brewingReagent, brewingResult}) {
			ItemStack stack = inventory.removeStack(0);
			if (!stack.isEmpty()) {
				player.getInventory().offerOrDrop(stack);
			}
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
