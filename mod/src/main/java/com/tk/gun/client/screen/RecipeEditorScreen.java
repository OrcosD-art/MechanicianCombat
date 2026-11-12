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

/** Visual shaped/shapeless crafting recipe editor. */
public final class RecipeEditorScreen extends HandledScreen<RecipeEditorScreenHandler> {
	private static final int WIDTH = 200;
	private static final int HEIGHT = 230;
	private boolean shaped = true;
	private TextFieldWidget recipeIdField;
	private ButtonWidget modeButton;

	public RecipeEditorScreen(RecipeEditorScreenHandler handler,
			PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = WIDTH;
		this.backgroundHeight = HEIGHT;
		this.titleX = 12;
		this.titleY = 5;
	}

	@Override
	protected void init() {
		super.init();
		recipeIdField = new TextFieldWidget(textRenderer,
				x + 78, y + 10, 108, 18,
				Text.translatable("screen.mechaniciancombat.recipe_editor.id"));
		recipeIdField.setMaxLength(128);
		recipeIdField.setPlaceholder(Text.translatable(
				"screen.mechaniciancombat.recipe_editor.id_hint"));
		addDrawableChild(recipeIdField);

		modeButton = addDrawableChild(ButtonWidget.builder(
				modeText(), button -> {
					shaped = !shaped;
					button.setMessage(modeText());
				}).dimensions(x + 12, y + 208, 80, 18).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.save"),
				button -> ClientPlayNetworking.send(
						new RecipeEditorActionPayload(shaped,
								recipeIdField.getText(), false)))
				.dimensions(x + 96, y + 208, 44, 18).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.delete"),
				button -> ClientPlayNetworking.send(
						new RecipeEditorActionPayload(shaped,
								recipeIdField.getText(), true)))
				.dimensions(x + 142, y + 208, 44, 18).build());
	}

	private Text modeText() {
		return Text.translatable(shaped
				? "screen.mechaniciancombat.recipe_editor.shaped"
				: "screen.mechaniciancombat.recipe_editor.shapeless");
	}

	@Override
	protected void drawBackground(DrawContext context, float delta,
			int mouseX, int mouseY) {
		context.fill(x, y, x + WIDTH, y + HEIGHT, 0xE018181A);
		context.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4,
				0xE02A2A2E);
		context.fill(x + 8, y + 28, x + 80, y + 84, 0xE0101012);
		context.fill(x + 118, y + 28, x + 178, y + 84, 0xE0101012);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY,
			float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer,
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.ingredients"),
				x + 12, y + 20, 0xFFE8C66A, false);
		context.drawText(textRenderer,
				Text.translatable(
						"screen.mechaniciancombat.recipe_editor.result"),
				x + 122, y + 20, 0xFFE8C66A, false);
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
