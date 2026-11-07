package com.tk.gun;

import com.tk.gun.item.ArmorUpgradeData;
import com.tk.gun.item.MechanicianUndersuitItem;
import com.tk.gun.net.HelmetStatePayload;
import com.tk.gun.net.HelmetTogglePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative expansion state for the head piece included with the full suit. */
public final class ArmorHelmetController {
	private ArmorHelmetController() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(
				HelmetTogglePayload.ID, HelmetTogglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				HelmetStatePayload.ID, HelmetStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(
				HelmetTogglePayload.ID,
				(payload, context) -> context.server().execute(
						() -> toggle(context.player())));

		ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) ->
						syncAll(handler.getPlayer(), server));

		// Resend once per second. This restores the state after a client reloads
		// resources or a spectator starts tracking a player later.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) {
				return;
			}
			for (ServerPlayerEntity viewer :
					server.getPlayerManager().getPlayerList()) {
				syncAll(viewer, server);
			}
		});
	}

	private static void toggle(ServerPlayerEntity player) {
		if (!MechanicianUndersuitItem.isWearingFullSet(player)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		long now = player.getWorld().getTime();
		long startedAt = ArmorUpgradeData.helmetTransitionStarted(chest);
		int duration = ArmorUpgradeData.helmetTransitionDuration(chest);
		if (startedAt > 0L && duration > 0 && now < startedAt + duration) {
			// Deliberately discard the press instead of queueing it. The next
			// accepted toggle must start only after the current tail frame.
			TkGun.LOGGER.info(
					"[tkgun] helmet toggle ignored while animation is active "
							+ "(remaining {} ticks)",
					startedAt + duration - now);
			return;
		}
		ArmorUpgradeData.beginHelmetTransition(
				chest, !ArmorUpgradeData.helmetExpanded(chest), now);
		for (ServerPlayerEntity viewer :
				player.getServerWorld().getPlayers()) {
			send(viewer, player, chest);
		}
	}

	private static void syncAll(ServerPlayerEntity viewer, MinecraftServer server) {
		for (ServerPlayerEntity target :
				server.getPlayerManager().getPlayerList()) {
			if (viewer.getServerWorld() != target.getServerWorld()) {
				continue;
			}
			send(viewer, target,
					ArmorUpgradeData.equippedChestplate(target));
		}
	}

	private static void send(ServerPlayerEntity viewer,
							 ServerPlayerEntity target, ItemStack chest) {
		ServerPlayNetworking.send(viewer, new HelmetStatePayload(
				target.getUuid(),
				ArmorUpgradeData.helmetExpanded(chest),
				ArmorUpgradeData.helmetTransitionStarted(chest),
				ArmorUpgradeData.helmetTransitionDuration(chest)));
	}
}
