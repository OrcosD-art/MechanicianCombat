package com.tk.gun.client;

import com.tk.gun.item.CombatBackpackItem;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.net.CombatBackpackStatusPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Right-side backpack charge and phase readout. */
public final class CombatBackpackHud {
	private static final Identifier AMMO_INFO_TEXTURE =
			Identifier.of("tkgun", "ammo_info");

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}

		int id = client.player.getId();
		BackpackUpgradeData.LauncherType launcherType =
				BackpackUpgradeData.launcherType(
						CombatBackpackItem.equippedStack(client.player));
		boolean backpackVisible = BackpackUpgradeData.canUseLauncher(
				CombatBackpackItem.equippedStack(client.player));
		int bottomY = 30 + client.player.getStatusEffects().size() * 24;
		if (backpackVisible) {
			bottomY = renderBackpack(context, client, id, launcherType);
		}
		ArmorAbilityHud.render(context, client, bottomY + 4);
	}

	private static int renderBackpack(DrawContext context, MinecraftClient client,
			int id, BackpackUpgradeData.LauncherType launcherType) {
		int phase = CombatBackpackClientState.statusPhase(id);
		int charges = CombatBackpackClientState.charges(id);
		int remaining = CombatBackpackClientState.remainingTicks(id);
		if (launcherType == BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			return renderJumpThruster(context, client, phase, remaining);
		}
		if (launcherType == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			return renderSummonAntenna(context, client, phase, remaining);
		}
		int maxCharges = launcherType == BackpackUpgradeData.LauncherType.AUTO ? 10 : 3;
		String stateText = switch (phase) {
			case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
					"背包状态：装填" + Math.max(1, (remaining + 19) / 20) + "秒";
			case CombatBackpackStatusPayload.PHASE_MARKED ->
					"背包状态：已标记";
			case CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE ->
					"背包状态：自动攻击";
			case CombatBackpackStatusPayload.PHASE_AUTO_PAUSED ->
					"背包状态：自动待命";
			default -> "背包状态：预备";
		};
		String chargeText = "当前充能：" + charges + "/" + maxCharges;
		int width = Math.max(client.textRenderer.getWidth(stateText),
				client.textRenderer.getWidth(chargeText)) + 10;
		int effectRows = client.player.getStatusEffects().size();
		int x = context.getScaledWindowWidth() - width - 6;
		int y = 30 + effectRows * 24;
		int height = 28;

		drawAmmoInfoBackground(context, x, y, width, height);
		int stateColour = phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				? 0xFFFFB060
				: (phase == CombatBackpackStatusPayload.PHASE_MARKED
						|| phase == CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE
						|| phase == CombatBackpackStatusPayload.PHASE_AUTO_PAUSED)
				? 0xFF9BE0FF : 0xFFE8E8E8;
		context.drawText(client.textRenderer, stateText, x + 6, y + 5, stateColour, true);
		context.drawText(client.textRenderer, chargeText, x + 6, y + 15,
				charges > 0 ? 0xFFFFF0A0 : 0xFFFF6666, true);
		return y + height;
	}

	private static int renderSummonAntenna(DrawContext context,
			MinecraftClient client, int phase, int remaining) {
		String stateText = switch (phase) {
			case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.summon_antenna.cooldown",
							Math.max(1, (remaining + 19) / 20)).getString();
			case CombatBackpackStatusPayload.PHASE_SUMMON_READY ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.summon_antenna.ready").getString();
			case CombatBackpackStatusPayload.PHASE_SUMMON_ARMED ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.summon_antenna.armed").getString();
			case CombatBackpackStatusPayload.PHASE_SUMMON_MARKED ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.summon_antenna.marked").getString();
			case CombatBackpackStatusPayload.PHASE_SUMMON_CAST ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.summon_antenna.cast").getString();
			default -> net.minecraft.text.Text.translatable(
					"hud.tkgun.summon_antenna.dormant").getString();
		};
		String chargeText = net.minecraft.text.Text.translatable(
				"hud.tkgun.summon_antenna.charge").getString();
		int width = Math.max(client.textRenderer.getWidth(stateText),
				client.textRenderer.getWidth(chargeText)) + 10;
		int effectRows = client.player.getStatusEffects().size();
		int x = context.getScaledWindowWidth() - width - 6;
		int y = 30 + effectRows * 24;
		int height = 28;
		drawAmmoInfoBackground(context, x, y, width, height);
		int colour = phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				? 0xFFFFB060
				: phase == CombatBackpackStatusPayload.PHASE_SUMMON_ARMED
						|| phase == CombatBackpackStatusPayload.PHASE_SUMMON_MARKED
						|| phase == CombatBackpackStatusPayload.PHASE_SUMMON_CAST
						? 0xFF9BE0FF : 0xFFE8E8E8;
		context.drawText(client.textRenderer, stateText, x + 6, y + 5, colour, true);
		context.drawText(client.textRenderer, chargeText, x + 6, y + 15,
				0xFFFFF0A0, true);
		return y + height;
	}

	private static int renderJumpThruster(DrawContext context,
			MinecraftClient client, int phase, int remaining) {
		String jumpKey = jumpBindingText(client);
		String stateText = switch (phase) {
			case CombatBackpackStatusPayload.PHASE_READY_TO_FLY ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.jump_backpack.ready").getString();
			case CombatBackpackStatusPayload.PHASE_FLYING ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.jump_backpack.flying").getString();
			case CombatBackpackStatusPayload.PHASE_FALLING ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.jump_backpack.falling").getString();
			case CombatBackpackStatusPayload.PHASE_CLOSE ->
					remaining > 0
							? net.minecraft.text.Text.translatable(
									"hud.tkgun.jump_backpack.cooldown",
									Math.max(1, (remaining + 19) / 20)).getString()
							: net.minecraft.text.Text.translatable(
									"hud.tkgun.jump_backpack.idle", jumpKey).getString();
			case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
					net.minecraft.text.Text.translatable(
							"hud.tkgun.jump_backpack.cooldown",
							Math.max(1, (remaining + 19) / 20)).getString();
			default -> net.minecraft.text.Text.translatable(
					"hud.tkgun.jump_backpack.idle", jumpKey).getString();
		};
		int width = client.textRenderer.getWidth(stateText) + 10;
		int effectRows = client.player.getStatusEffects().size();
		int x = context.getScaledWindowWidth() - width - 6;
		int y = 30 + effectRows * 24;
		drawAmmoInfoBackground(context, x, y, width, 16);
		int colour = phase == CombatBackpackStatusPayload.PHASE_READY_TO_FLY
				|| phase == CombatBackpackStatusPayload.PHASE_FLYING
				? 0xFF9BE0FF
				: phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
						? 0xFFFFB060 : 0xFFE8E8E8;
		context.drawText(client.textRenderer, stateText, x + 6, y + 5, colour, true);
		return y + 16;
	}

	private static String jumpBindingText(MinecraftClient client) {
		String key = client.options.jumpKey.getBoundKeyLocalizedText().getString();
		if (client.options.language.startsWith("zh") && !key.endsWith("键")) {
			return key + "键";
		}
		return key;
	}

	private static void drawAmmoInfoBackground(DrawContext context,
			int x, int y, int width, int height) {
		context.drawGuiTexture(AMMO_INFO_TEXTURE,
				x - 3, y - 3, width + 6, height + 6);
	}

	private CombatBackpackHud() {
	}
}
