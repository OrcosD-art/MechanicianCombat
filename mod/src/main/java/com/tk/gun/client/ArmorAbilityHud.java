package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Armor-module charge and cooldown rows in the right-side backpack HUD. */
public final class ArmorAbilityHud {
	private static final Identifier AMMO_INFO_TEXTURE =
			Identifier.of("tkgun", "ammo_info");

	public static int render(DrawContext context, MinecraftClient client,
							 int startY) {
		if (client.player == null || client.world == null
				|| (!ArmorAbilityClientState.shieldInstalled()
				&& !ArmorAbilityClientState.totemInstalled())) {
			return startY;
		}
		String shield = shieldText();
		String totem = totemText();
		int rows = (shield == null ? 0 : 1) + (totem == null ? 0 : 1);
		String widest = shield != null ? shield : totem;
		if (totem != null && client.textRenderer.getWidth(totem)
				> client.textRenderer.getWidth(widest)) {
			widest = totem;
		}
		int width = client.textRenderer.getWidth(widest) + 10;
		int height = rows * 10 + 8;
		int x = context.getScaledWindowWidth() - width - 6;
		context.drawGuiTexture(AMMO_INFO_TEXTURE,
				x - 3, startY - 3, width + 6, height + 6);
		int lineY = startY + 4;
		if (shield != null) {
			context.drawText(client.textRenderer, shield, x + 5, lineY,
					ArmorAbilityClientState.shieldCharges() > 0
							? 0xFFE8E8E8 : 0xFFFF7777, true);
			lineY += 10;
		}
		if (totem != null) {
			context.drawText(client.textRenderer, totem, x + 5, lineY,
					ArmorAbilityClientState.totemCooldownTicks() > 0
							? 0xFFFFB060 : 0xFF9BE0FF, true);
		}
		return startY + height;
	}

	public static boolean visible() {
		return ArmorAbilityClientState.shieldInstalled()
				|| ArmorAbilityClientState.totemInstalled();
	}

	private static String shieldText() {
		if (!ArmorAbilityClientState.shieldInstalled()) {
			return null;
		}
		String text = "左肩盾：" + ArmorAbilityClientState.shieldCharges()
				+ "/" + com.tk.gun.item.ArmorUpgradeData.MAX_SHIELD_CHARGES;
		int cooldown = ArmorAbilityClientState.shieldCooldownTicks();
		if (cooldown > 0) {
			text += "  冷却 " + seconds(cooldown) + "秒";
			if (ArmorAbilityClientState.shieldCharges() == 0) {
				if (ArmorAbilityClientState.shieldRechargeWindowTicks() > 0) {
					text += "  重充能 "
							+ ArmorAbilityClientState.shieldRechargeKills()
							+ "/" + com.tk.gun.ArmorAbilities.SHIELD_RECHARGE_KILLS
							+ " 窗口 "
							+ seconds(ArmorAbilityClientState
									.shieldRechargeWindowTicks())
							+ "秒";
				} else {
					text += "  击杀恢复失败";
				}
			}
		}
		return text;
	}

	private static String totemText() {
		if (!ArmorAbilityClientState.totemInstalled()) {
			return null;
		}
		int cooldown = ArmorAbilityClientState.totemCooldownTicks();
		return cooldown > 0
				? "守护图腾：冷却 " + seconds(cooldown) + "秒"
				: "守护图腾：就绪";
	}

	private static int seconds(int ticks) {
		return Math.max(1, (ticks + 19) / 20);
	}

	private ArmorAbilityHud() {
	}
}
