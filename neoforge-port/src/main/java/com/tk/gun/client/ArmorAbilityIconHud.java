package com.tk.gun.client;

import com.tk.gun.item.ArmorUpgradeData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Shield charges over armor and the ready totem over the first heart. */
public final class ArmorAbilityIconHud {
	private static final Identifier TEXTURE =
			Identifier.of("mechaniciancombat", "textures/gui/gui_chest.png");
	private static final int SHIELD_U = 1;
	private static final int SHIELD_V = 1;
	private static final int SHIELD_SIZE = 9;
	private static final int TOTEM_U = 16;
	private static final int TOTEM_V = 1;
	private static final int TOTEM_SIZE = 7;

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		int left = context.getScaledWindowWidth() / 2 - 91;
		int healthY = context.getScaledWindowHeight() - 39;
		int armorY = healthY - 10;

		if (ArmorAbilityClientState.shieldInstalled()) {
			int charges = Math.max(0, Math.min(
					ArmorUpgradeData.MAX_SHIELD_CHARGES,
					ArmorAbilityClientState.shieldCharges()));
			for (int index = 0; index < charges; index++) {
				context.drawTexture(TEXTURE,
						left + index * SHIELD_SIZE, armorY,
						SHIELD_U, SHIELD_V,
						SHIELD_SIZE, SHIELD_SIZE, 32, 32);
			}
		}

		if (ArmorAbilityClientState.totemInstalled()
				&& ArmorAbilityClientState.totemCooldownTicks() <= 0) {
			context.drawTexture(TEXTURE,
					left + 1, healthY + 1,
					TOTEM_U, TOTEM_V,
					TOTEM_SIZE, TOTEM_SIZE, 32, 32);
		}
	}

	private ArmorAbilityIconHud() {
	}
}
