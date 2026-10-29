package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.GunAmmoData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** 右下角弹药与热量 HUD：腰射显示弹匣计数，专注显示热量与当前弹仓余量。 */
public final class GunHud {
	private static final Identifier AMMO_INFO_TEXTURE =
			Identifier.of("tkgun", "ammo_info");

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden
				|| client.currentScreen != null) {
			return;
		}
		if (!TkGun.holdingGun(client.player)) {
			return;
		}
		int id = client.player.getId();
		int left = GunClientState.roundsLeft(id);
		boolean reloading = GunClientState.reloading(id);
		String focusTag = GunClientState.focusText(id);
		boolean focused = GunClientState.focusish(id) || GunClientState.overheated(id);

		String main = focused
				? "热量槽：" + GunClientState.heatPercent(id) + "%"
				: loadedMagazineText(id, left);
		String ammo = selectedAmmoText(client);
		int mainW = client.textRenderer.getWidth(main);
		int ammoW = client.textRenderer.getWidth(ammo);
		int pad = 4;
		int boxW = Math.max(mainW, ammoW) + pad * 2;
		int boxH = 32;
		int x = context.getScaledWindowWidth() - boxW - 6;
		int y = context.getScaledWindowHeight() - 74;

		context.drawGuiTexture(AMMO_INFO_TEXTURE,
				x - 3, y - 3, boxW + 6, boxH + 6);
		int heat = GunClientState.heatPercent(id);
		int colour = reloading ? 0xFFE0A030
				: focused ? (heat >= 75 ? 0xFFFF5555 : 0xFFFFB060)
				: (left == 0 ? 0xFFFF5555 : 0xFFFFF0A0);
		context.drawText(client.textRenderer, main, x + pad, y + 4, colour, true);
		context.drawText(client.textRenderer, ammo, x + pad, y + 16, 0xFFC8C8C8, true);

		// 专注：上面单独一行（青色）
		if (focusTag != null) {
			int tw = client.textRenderer.getWidth(focusTag);
			int bx = x + boxW - tw - pad;
			context.fill(bx - pad, y - 12, bx + tw + pad, y - 1, 0x90102828);
			context.drawText(client.textRenderer, focusTag, bx, y - 10, 0xFF9BE0FF, true);
		}
		AmmoSwitchHud.render(context);
	}

	private static String loadedMagazineText(int entityId, int left) {
		String id = GunClientState.magAmmo(entityId);
		if (id.isEmpty()) {
			return "空弹匣 " + left + "/6";
		}
		return itemName(id) + " " + left + "/6";
	}

	private static String selectedAmmoText(MinecraftClient client) {
		ItemStack gun = GunAmmoData.heldGun(client.player);
		if (gun.isEmpty()) {
			return "→空";
		}
		var items = GunAmmoData.read(gun, client.world.getRegistryManager());
		int selected = AmmoSwitchHud.displayedSelectedSlot();
		if (selected < 0) {
			selected = GunAmmoData.selected(gun);
		}
		ItemStack ammo = items.get(Math.floorMod(selected, GunAmmoData.SIZE));
		if (ammo.isEmpty()) {
			return "→空";
		}
		Identifier ammoId = Registries.ITEM.getId(ammo.getItem());
		int total = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(ammoId)) {
				total += stack.getCount();
			}
		}
		return "→" + ammo.getName().getString() + " " + total;
	}

	private static String itemName(String registryId) {
		try {
			var item = Registries.ITEM.get(Identifier.of(registryId));
			return item == net.minecraft.item.Items.AIR
					? "空弹匣" : new ItemStack(item).getName().getString();
		} catch (RuntimeException ignored) {
			return "空弹匣";
		}
	}

	private GunHud() {
	}
}
