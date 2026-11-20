package com.tk.gun.client;

import com.tk.gun.item.BackpackLauncherRules;
import com.tk.gun.item.BackpackAmmoData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

/** Draws the launcher's occupied offhand frame and virtual accessory icon. */
public final class OffhandStorageHud {
	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden
				|| !BackpackLauncherRules.hasActiveLauncher(client.player)) {
			return;
		}
		ItemStack stored = BackpackAmmoData.stored(client.player);
		boolean battleBanner =
				BackpackLauncherRules.isBattleBannerInstalled(client.player);

		Arm offhandArm = client.player.getMainArm().getOpposite();
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int x = offhandArm == Arm.LEFT ? width / 2 - 91 - 29 : width / 2 + 91;
		int y = height - 23;
		Identifier texture = Identifier.ofVanilla(offhandArm == Arm.LEFT
				? "hud/hotbar_offhand_left" : "hud/hotbar_offhand_right");
		context.drawGuiTexture(texture, x, y, 29, 24);
		if (battleBanner) {
			ItemStack icon = BackpackLauncherRules.createBattleBannerIcon();
			context.drawItem(icon, x + 3, y + 3);
			// drawItemInSlot applies the vanilla WAR_BANNER cooldown mask.
			context.drawItemInSlot(client.textRenderer, icon, x + 3, y + 3);
		} else if (!stored.isEmpty()) {
			context.drawItem(stored, x + 3, y + 3);
			context.drawItemInSlot(client.textRenderer, stored, x + 3, y + 3);
		}
	}

	private OffhandStorageHud() {
	}
}
