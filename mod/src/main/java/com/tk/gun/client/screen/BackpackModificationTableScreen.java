package com.tk.gun.client.screen;

import com.tk.gun.client.CombatBackpackGeoRenderer;
import com.tk.gun.item.CombatBackpackItem;
import com.tk.gun.screen.BackpackModificationTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Authored backpack-modification layout.
 *
 * <p>The large preview only renders the combat backpack placed in the dedicated
 * slot. Its authored group pivot is placed at the frame center and uses a fixed
 * view angle; the drawing region is clipped to the inside of the frame.</p>
 */
public final class BackpackModificationTableScreen
		extends HandledScreen<BackpackModificationTableScreenHandler> {
	private static final Identifier TEXTURE =
			Identifier.of("tkgun", "textures/gui/container/backpack_modification_table.png");
	private static final int TEXTURE_WIDTH = 256;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int PREVIEW_X = 52;
	private static final int PREVIEW_Y = 7;
	private static final int PREVIEW_WIDTH = 71;
	private static final int PREVIEW_HEIGHT = 72;
	private static final float PREVIEW_CENTER_X = PREVIEW_X + 35.0F;
	private static final float BACKPACK_SCALE = 50.413567F;
	private static final float BACKPACK_OFFSET_X = -9.5F;
	private static final float BACKPACK_OFFSET_Y = -19.809195F;
	private static final float FIXED_YAW = -45.0F;
	private static final float FIXED_PITCH = 22.5F;

	public BackpackModificationTableScreen(
			BackpackModificationTableScreenHandler handler,
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
		ItemStack backpack = handler.getSlot(
				BackpackModificationTableScreenHandler.BACKPACK_SLOT_INDEX).getStack();
		if (!(backpack.getItem() instanceof CombatBackpackItem)) {
			return;
		}
		context.enableScissor(
				x + PREVIEW_X + 1,
				y + PREVIEW_Y + 1,
				x + PREVIEW_X + PREVIEW_WIDTH - 1,
				y + PREVIEW_Y + PREVIEW_HEIGHT - 1);
		try {
			drawBackpackPreview(context, backpack);
		} finally {
			context.disableScissor();
		}
	}

	private void drawBackpackPreview(DrawContext context, ItemStack stack) {
		var matrices = context.getMatrices();
		matrices.push();
		try {
			matrices.translate(
					x + PREVIEW_CENTER_X + BACKPACK_OFFSET_X,
					y + PREVIEW_Y + PREVIEW_HEIGHT / 2.0F
							+ BACKPACK_OFFSET_Y,
					120.0D);
			matrices.scale(BACKPACK_SCALE, BACKPACK_SCALE, BACKPACK_SCALE);
			// The authored GUI orientation is upside down for this item renderer.
			matrices.scale(1.0F, -1.0F, 1.0F);
			matrices.multiply(new Quaternionf().rotationXYZ(
					(float) Math.toRadians(FIXED_PITCH),
					(float) Math.toRadians(FIXED_YAW),
					0.0F));
			CombatBackpackGeoRenderer.getPreview().renderMaintenancePreview(
					stack,
					matrices,
					context.getVertexConsumers(),
					0x00F000F0,
					OverlayTexture.DEFAULT_UV);
		} finally {
			matrices.pop();
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// The authored panel has no separate title region.
	}

	/**
	 * Screen registration stays reflection-based because this build does not carry
	 * a Mixin refmap. The provider signature is the same shape used by the ammo
	 * storage screen.
	 */
	public static void registerClientScreen() {
		try {
			Class<?> providerType = Arrays.stream(HandledScreens.class.getDeclaredClasses())
					.filter(Class::isInterface)
					.filter(type -> Arrays.stream(type.getDeclaredMethods())
							.anyMatch(method -> "create".equals(method.getName())
									|| method.getParameterCount() == 3))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("HandledScreens.Provider not found"));
			Object provider = Proxy.newProxyInstance(
					BackpackModificationTableScreen.class.getClassLoader(),
					new Class<?>[]{providerType},
					(proxy, method, args) -> {
						if ("create".equals(method.getName())) {
							return new BackpackModificationTableScreen(
									(BackpackModificationTableScreenHandler) args[0],
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
					.orElseThrow(() -> new IllegalStateException("HandledScreens.register not found"));
			register.setAccessible(true);
			register.invoke(null, BackpackModificationTableScreenHandler.TYPE, provider);
		} catch (Throwable error) {
			com.tk.gun.TkGun.LOGGER.error(
					"[tkgun] Unable to register backpack modification table screen", error);
		}
	}
}
