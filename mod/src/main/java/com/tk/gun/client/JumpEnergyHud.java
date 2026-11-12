package com.tk.gun.client;

import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/** Bottom-left pixel energy gauge for the jump thruster. */
public final class JumpEnergyHud {
	private static final Identifier TEXTURE =
			Identifier.of("mechaniciancombat", "textures/gui/hud/jet_energy.png");
	private static final int MAX_HEIGHT = 28;
	private static final int METER_X = 2;
	private static final int METER_Y = 2;

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden
				|| !BackpackUpgradeData.canUseJumpThruster(
						CombatBackpackItem.equippedStack(client.player))) {
			return;
		}
		int x = 6;
		int y = context.getScaledWindowHeight() - 40;
		// Left 8x32 frame; the authored right 4x28 cluster is the fill mask.
		context.drawTexture(TEXTURE, x, y, 0.0F, 0.0F, 8, 32, 32, 32);
		if (CombatBackpackClientState.jumpEnergyLocked(client.player.getId())) {
			float cycle = (Util.getMeasuringTimeMs() % 1000L) / 1000.0F;
			float pulse = 0.5F - 0.5F * (float) Math.cos(cycle * Math.PI * 2.0D);
			int alpha = 32 + Math.round(pulse * 176.0F);
			context.fill(x + METER_X, y + METER_Y,
					x + METER_X + 4, y + METER_Y + MAX_HEIGHT,
					(alpha << 24) | 0x00FF2020);
		}
		int height = Math.max(0, Math.min(MAX_HEIGHT,
				CombatBackpackClientState.jumpEnergyPixels(client.player.getId())));
		if (height <= 0) {
			return;
		}
		int sourceY = METER_Y + MAX_HEIGHT - height;
		int destinationY = y + METER_Y + MAX_HEIGHT - height;
		context.drawTexture(TEXTURE, x + METER_X, destinationY,
				28.0F, sourceY, 4, height, 32, 32);
	}

	private JumpEnergyHud() {
	}
}
