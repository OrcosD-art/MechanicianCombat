package com.tk.gun.client.screen;

import com.tk.gun.net.RecipeEditorActionPayload;
import com.tk.gun.screen.RecipeEditorScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Visual shaped/shapeless crafting recipe editor. */
public final class RecipeEditorScreen extends HandledScreen<RecipeEditorScreenHandler> {
	private static final Identifier CRAFTING_TABLE_TEXTURE =
			Identifier.ofVanilla("textures/gui/container/crafting_table.png");
	private static final Identifier FURNACE_TEXTURE =
			Identifier.ofVanilla("textures/gui/container/furnace.png");
	private static final Identifier BREWING_STAND_TEXTURE =
			Identifier.ofVanilla("textures/gui/container/brewing_stand.png");
	private static final int WIDTH = 176;
	private static final int BASE_HEIGHT = 166;
	private static final int HEIGHT = 238;
	private boolean shaped = true;
	private TextFieldWidget recipeIdField;
	private TextFieldWidget experienceField;
	private TextFieldWidget cookingTimeField;
	private ButtonWidget modeButton;

	public RecipeEditorScreen(RecipeEditorScreenHandler handler,
			PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = WIDTH;
		this.backgroundHeight = HEIGHT;
		this.titleX = 8;
		this.titleY = 6;
		this.playerInventoryTitleX = 8;
		this.playerInventoryTitleY = 72;
	}

	@Override
	protected void init() {
		super.init();
		recipeIdField = new TextFieldWidget(textRenderer,
				x + 58, y + 171, 110, 18,
				Text.translatable("screen.mechaniciancombat.recipe_editor.id"));
		recipeIdField.setMaxLength(128);
		recipeIdField.setPlaceholder(Text.translatable(
				"screen.mechaniciancombat.recipe_editor.id_hint"));
		addDrawableChild(recipeIdField);

		addDrawableChild(ButtonWidget.builder(Text.literal("←"), button ->
				changePage(-1)).dimensions(x + 8, y + 194, 20, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("→"), button ->
				changePage(1)).dimensions(x + 30, y + 194, 20, 18).build());
		modeButton = addDrawableChild(ButtonWidget.builder(
				modeText(), button -> {
					shaped = !shaped;
					button.setMessage(modeText());
				}).dimensions(x + 8, y + 216, 80, 18).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.save"),
				button -> ClientPlayNetworking.send(
						action(RecipeEditorActionPayload.ACTION_SAVE)))
				.dimensions(x + 96, y + 194, 38, 18).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.delete"),
				button -> ClientPlayNetworking.send(
						action(RecipeEditorActionPayload.ACTION_DELETE)))
				.dimensions(x + 136, y + 194, 32, 18).build());
		experienceField = new TextFieldWidget(textRenderer,
				x + 32, y + 216, 50, 18,
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.experience"));
		experienceField.setText("0.1");
		addDrawableChild(experienceField);
		cookingTimeField = new TextFieldWidget(textRenderer,
				x + 108, y + 216, 52, 18,
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.cooking_time"));
		cookingTimeField.setText("200");
		addDrawableChild(cookingTimeField);
		updatePageControls();
	}

	private Text modeText() {
		return Text.translatable(shaped
				? "screen.mechaniciancombat.recipe_editor.shaped"
				: "screen.mechaniciancombat.recipe_editor.shapeless");
	}

	private void changePage(int offset) {
		int next = Math.floorMod(handler.getPage() + offset,
				RecipeEditorScreenHandler.PAGE_COUNT);
		handler.setPage(next);
		updatePageControls();
		ClientPlayNetworking.send(new RecipeEditorActionPayload(
				RecipeEditorActionPayload.ACTION_SET_PAGE, next,
				shaped, "", 0.1F, 200));
	}

	private void updatePageControls() {
		int page = handler.getPage();
		modeButton.visible = page == RecipeEditorScreenHandler.PAGE_CRAFTING;
		experienceField.setVisible(
				page == RecipeEditorScreenHandler.PAGE_FURNACE);
		cookingTimeField.setVisible(
				page == RecipeEditorScreenHandler.PAGE_FURNACE);
	}

	private RecipeEditorActionPayload action(int action) {
		return new RecipeEditorActionPayload(action, handler.getPage(),
				shaped, recipeIdField.getText(), parseFloat(),
				parseInt());
	}

	private float parseFloat() {
		try {
			return Math.max(0.0F,
					Float.parseFloat(experienceField.getText().trim()));
		} catch (NumberFormatException ignored) {
			return 0.1F;
		}
	}

	private int parseInt() {
		try {
			return Math.max(1,
					Integer.parseInt(cookingTimeField.getText().trim()));
		} catch (NumberFormatException ignored) {
			return 200;
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta,
			int mouseX, int mouseY) {
		Identifier texture = switch (handler.getPage()) {
			case RecipeEditorScreenHandler.PAGE_FURNACE -> FURNACE_TEXTURE;
			case RecipeEditorScreenHandler.PAGE_BREWING ->
					BREWING_STAND_TEXTURE;
			default -> CRAFTING_TABLE_TEXTURE;
		};
		context.drawTexture(texture, x, y,
				0.0F, 0.0F, WIDTH, BASE_HEIGHT, 256, 256);
		// Extend the vanilla panel only far enough for the editor controls.
		context.fill(x, y + BASE_HEIGHT, x + WIDTH, y + HEIGHT,
				0xFFC6C6C6);
		context.fill(x, y + BASE_HEIGHT, x + 1, y + HEIGHT,
				0xFF373737);
		context.fill(x + WIDTH - 1, y + BASE_HEIGHT, x + WIDTH,
				y + HEIGHT, 0xFF373737);
		context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT,
				0xFF373737);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY,
			float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer,
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.id"),
				x + 8, y + 176, 0xFF404040, false);
		context.drawText(textRenderer, pageText(),
				x + 56, y + 199, 0xFF404040, false);
		if (handler.getPage() == RecipeEditorScreenHandler.PAGE_BREWING) {
			context.drawText(textRenderer,
					Text.translatable(
							"screen.mechaniciancombat.recipe_editor.brewing_hint"),
					x + 8, y + 221, 0xFF404040, false);
		} else if (handler.getPage()
				== RecipeEditorScreenHandler.PAGE_FURNACE) {
			context.drawText(textRenderer,
					Text.translatable(
							"screen.mechaniciancombat.recipe_editor.experience"),
					x + 8, y + 221, 0xFF404040, false);
			context.drawText(textRenderer,
					Text.translatable(
							"screen.mechaniciancombat.recipe_editor.cooking_time"),
					x + 84, y + 221, 0xFF404040, false);
		}
		com.tk.gun.client.ModGuiFadeOverlay.render(this, context);
	}

	private Text pageText() {
		return Text.translatable(switch (handler.getPage()) {
			case RecipeEditorScreenHandler.PAGE_FURNACE ->
					"screen.mechaniciancombat.recipe_editor.page.furnace";
			case RecipeEditorScreenHandler.PAGE_BREWING ->
					"screen.mechaniciancombat.recipe_editor.page.brewing";
			default ->
					"screen.mechaniciancombat.recipe_editor.page.crafting";
		});
	}

	public static void registerClientScreen() {
		try {
			Class<?> providerType = java.util.Arrays.stream(
					HandledScreens.class.getDeclaredClasses())
					.filter(Class::isInterface)
					.filter(type -> java.util.Arrays.stream(type.getDeclaredMethods())
							.anyMatch(method -> "create".equals(method.getName())
									|| method.getParameterCount() == 3))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"HandledScreens.Provider not found"));
			Object provider = java.lang.reflect.Proxy.newProxyInstance(
					RecipeEditorScreen.class.getClassLoader(),
					new Class<?>[]{providerType},
					(proxy, method, args) -> {
						if ("create".equals(method.getName())) {
							return new RecipeEditorScreen(
									(RecipeEditorScreenHandler) args[0],
									(PlayerInventory) args[1],
									(Text) args[2]);
						}
						return null;
					});
			java.lang.reflect.Method register = java.util.Arrays.stream(
					HandledScreens.class.getDeclaredMethods())
					.filter(method -> method.getParameterCount() == 2)
					.filter(method -> method.getParameterTypes()[0]
							.isAssignableFrom(net.minecraft.screen.ScreenHandlerType.class))
					.filter(method -> method.getParameterTypes()[1] == providerType)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"HandledScreens.register not found"));
			register.setAccessible(true);
			register.invoke(null, RecipeEditorScreenHandler.TYPE, provider);
		} catch (Throwable error) {
			com.tk.gun.TkGun.LOGGER.error(
					"[mechaniciancombat] Unable to register recipe editor screen",
					error);
		}
	}
}
