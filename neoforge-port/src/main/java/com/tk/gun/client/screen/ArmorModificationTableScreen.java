package com.tk.gun.client.screen;

import com.tk.gun.screen.ArmorModificationTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Authored armor-maintenance interface with a fixed player preview. */
public final class ArmorModificationTableScreen
		extends HandledScreen<ArmorModificationTableScreenHandler> {
	private static final Identifier TEXTURE =
			Identifier.of("mechaniciancombat", "textures/gui/container/armor_modification_table.png");
	private static final int TEXTURE_WIDTH = 256;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int PREVIEW_X = 81;
	private static final int PREVIEW_Y = 6;
	private static final int PREVIEW_WIDTH = 49;
	private static final int PREVIEW_HEIGHT = 59;

	public ArmorModificationTableScreen(
			ArmorModificationTableScreenHandler handler,
			PlayerInventory inventory,
			Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(
				TEXTURE,
				x, y,
				0.0F, 0.0F,
				backgroundWidth, backgroundHeight,
				TEXTURE_WIDTH, TEXTURE_HEIGHT);
		if (client == null || client.player == null) {
			return;
		}
		drawRotatingPlayer(context);
	}

	private void drawRotatingPlayer(DrawContext context) {
		var player = client.player;
		if (player == null) {
			return;
		}
		context.enableScissor(
				x + PREVIEW_X,
				y + PREVIEW_Y,
				x + PREVIEW_X + PREVIEW_WIDTH,
				y + PREVIEW_Y + PREVIEW_HEIGHT);
		float oldBodyYaw = player.bodyYaw;
		float oldYaw = player.getYaw();
		float oldPitch = player.getPitch();
		float oldPreviousHeadYaw = player.prevHeadYaw;
		float oldHeadYaw = player.headYaw;
		try {
			player.bodyYaw = 180.0F;
			player.setYaw(180.0F);
			player.setPitch(0.0F);
			player.prevHeadYaw = 180.0F;
			player.headYaw = 180.0F;

			float spin = (Util.getMeasuringTimeMs() % 12000L)
					/ 12000.0F * 360.0F;
			float scale = ArmorPreviewConfig.scale() / player.getScale();
			Vector3f translation = new Vector3f(
					0.0F,
					player.getHeight() / 2.0F + 0.0625F * scale,
					0.0F);
			Quaternionf rotation = new Quaternionf()
					.rotateZ((float) Math.PI)
					.rotateY((float) Math.toRadians(spin));
			Quaternionf camera = new Quaternionf()
					.rotateX((float) Math.toRadians(8.0F));
			InventoryScreen.drawEntity(
					context,
					x + PREVIEW_X + PREVIEW_WIDTH / 2.0F
							+ ArmorPreviewConfig.x(),
					y + PREVIEW_Y + PREVIEW_HEIGHT / 2.0F
							+ ArmorPreviewConfig.y(),
					scale,
					translation,
					rotation,
					camera,
					player);
		} finally {
			player.bodyYaw = oldBodyYaw;
			player.setYaw(oldYaw);
			player.setPitch(oldPitch);
			player.prevHeadYaw = oldPreviousHeadYaw;
			player.headYaw = oldHeadYaw;
			context.disableScissor();
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// The author-provided texture contains all visible labels.
	}

	public static void registerClientScreen() {
		try {
			Class<?> providerType = Arrays.stream(HandledScreens.class.getDeclaredClasses())
					.filter(Class::isInterface)
					.filter(type -> Arrays.stream(type.getDeclaredMethods())
							.anyMatch(method -> "create".equals(method.getName())
									|| method.getParameterCount() == 3))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"HandledScreens.Provider not found"));
			Object provider = Proxy.newProxyInstance(
					ArmorModificationTableScreen.class.getClassLoader(),
					new Class<?>[]{providerType},
					(proxy, method, args) -> {
						if ("create".equals(method.getName())) {
							return new ArmorModificationTableScreen(
									(ArmorModificationTableScreenHandler) args[0],
									(PlayerInventory) args[1],
									(Text) args[2]);
						}
						return null;
					});
			Method register = Arrays.stream(HandledScreens.class.getDeclaredMethods())
					.filter(method -> method.getParameterCount() == 2)
					.filter(method -> method.getParameterTypes()[0]
							.isAssignableFrom(net.minecraft.screen.ScreenHandlerType.class))
					.filter(method -> method.getParameterTypes()[1] == providerType)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"HandledScreens.register not found"));
			register.setAccessible(true);
			register.invoke(null, ArmorModificationTableScreenHandler.TYPE, provider);
		} catch (Throwable error) {
			com.tk.gun.TkGun.LOGGER.error(
					"[mechaniciancombat] Unable to register armor modification table screen",
					error);
		}
	}
}
