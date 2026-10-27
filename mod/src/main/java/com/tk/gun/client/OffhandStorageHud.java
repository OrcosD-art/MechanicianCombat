package com.tk.gun.client;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** Keeps the native offhand storage frame visible before it contains an item. */
public final class OffhandStorageHud {
	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden
				|| !BackpackLauncherRules.hasActiveLauncher(client.player)) {
			return;
		}
		ItemStack offhand = client.player.getStackInHand(Hand.OFF_HAND);
		if (!offhand.isEmpty()) {
			return; // Vanilla draws both the occupied frame and item icon.
		}

		Arm offhandArm = client.player.getMainArm().getOpposite();
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int x = offhandArm == Arm.LEFT ? width / 2 - 91 - 29 : width / 2 + 91;
		int y = height - 23;
		Identifier texture = Identifier.ofVanilla(offhandArm == Arm.LEFT
				? "hud/hotbar_offhand_left" : "hud/hotbar_offhand_right");
		context.drawGuiTexture(texture, x, y, 29, 24);
	}

	private OffhandStorageHud() {
	}
}
