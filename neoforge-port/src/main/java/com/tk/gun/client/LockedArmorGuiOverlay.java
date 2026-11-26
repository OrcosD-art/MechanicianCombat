package com.tk.gun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tk.gun.TkGun;
import com.tk.gun.item.MechanicianUndersuitItem;
import com.tk.gun.mixin.CreativeInventoryScreenAccessor;
import com.tk.gun.mixin.HandledScreenCoordinateAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.WeakHashMap;

/** Faded locked-armor replacements for the survival and creative inventories. */
public final class LockedArmorGuiOverlay {
	private static final Identifier SURVIVAL =
			TkGun.id("textures/gui/locked_armor_inventory.png");
	private static final Identifier CREATIVE =
			TkGun.id("textures/gui/locked_armor_creative_inventory.png");
	private static final long FADE_NANOS = 180_000_000L;
	private static final Map<Object, Long> OPENED = new WeakHashMap<>();
	private static final Map<Object, Long> CLOSING = new WeakHashMap<>();
	private static final Map<Object, Boolean> ALLOW_CLOSE = new WeakHashMap<>();
	private static final Map<Object, Boolean> CLOSE_SCHEDULED =
			new WeakHashMap<>();

	public static void render(DrawContext context, Object screen) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!shouldDraw(client, screen)) {
			OPENED.remove(screen);
			CLOSING.remove(screen);
			return;
		}
		long now = System.nanoTime();
		long openedAt = OPENED.computeIfAbsent(screen, ignored -> now);
		long closeAt = CLOSING.getOrDefault(screen, Long.MAX_VALUE);
		if (closeAt <= now) {
			if (CLOSE_SCHEDULED.put(screen, Boolean.TRUE) == null) {
				OPENED.remove(screen);
				CLOSING.remove(screen);
				ALLOW_CLOSE.put(screen, Boolean.TRUE);
				client.execute(() -> ((Screen) screen).close());
			}
			return;
		}
		double fadeIn = Math.min(1.0D,
				(now - openedAt) / (double) FADE_NANOS);
		double fadeOut = closeAt == Long.MAX_VALUE ? 1.0D
				: Math.max(0.0D,
						(closeAt - now) / (double) FADE_NANOS);
		float alpha = (float) Math.min(fadeIn, fadeOut);
		boolean creative = screen instanceof CreativeInventoryScreen;
		HandledScreenCoordinateAccessor coordinates =
				(HandledScreenCoordinateAccessor) screen;
		int x = coordinates.mechaniciancombat$getGuiX();
		int y = coordinates.mechaniciancombat$getGuiY();
		int width = creative ? 195 : 176;
		int height = creative ? 136 : 166;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		context.drawTexture(creative ? CREATIVE : SURVIVAL,
				x, y, 0.0F, 0.0F, width, height, 256, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}

	public static boolean shouldDelayClose(Screen screen) {
		if (ALLOW_CLOSE.remove(screen) != null) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (!shouldDraw(client, screen)) {
			return false;
		}
		CLOSING.computeIfAbsent(screen,
				ignored -> System.nanoTime() + FADE_NANOS);
		CLOSE_SCHEDULED.remove(screen);
		return true;
	}

	private static boolean shouldDraw(MinecraftClient client,
			Object screen) {
		if (client.player == null
				|| !MechanicianUndersuitItem.isLockedOuterSet(
						client.player)) {
			return false;
		}
		if (screen instanceof InventoryScreen) {
			return true;
		}
		return screen instanceof CreativeInventoryScreen
				&& CreativeInventoryScreenAccessor
						.mechaniciancombat$getSelectedTab().getType()
						== ItemGroup.Type.INVENTORY;
	}

	private LockedArmorGuiOverlay() {
	}
}
