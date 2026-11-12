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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative expansion state for the head piece included with the full suit. */
public final class ArmorHelmetController {
	private static final Map<UUID, HelmetSound> HELMET_SOUNDS =
			new HashMap<>();
	private static final int[] FOLD_SOUND_TICKS = {0, 8, 9};
	private static final net.minecraft.util.Identifier[] FOLD_SOUNDS = {
			TkGun.SND_HELMET_STEAM_HISS,
			TkGun.SND_HELMET_MECH5,
			TkGun.SND_HELMET_MECH3
	};
	private static final int[] EXPAND_SOUND_TICKS = {2, 3, 7};
	private static final net.minecraft.util.Identifier[] EXPAND_SOUNDS = {
			TkGun.SND_HELMET_MECH5,
			TkGun.SND_HELMET_MECH2,
			TkGun.SND_HELMET_MECH3
	};

	private static final class HelmetSound {
		final long startedAt;
		final int durationTicks;
		final boolean expanded;
		int mask;

		private HelmetSound(long startedAt, int durationTicks,
				boolean expanded) {
			this.startedAt = startedAt;
			this.durationTicks = durationTicks;
			this.expanded = expanded;
		}
	}

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
			tickHelmetSounds(server);
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
					"[mechaniciancombat] helmet toggle ignored while animation is active "
							+ "(remaining {} ticks)",
					startedAt + duration - now);
			return;
		}
		ArmorUpgradeData.beginHelmetTransition(
				chest, !ArmorUpgradeData.helmetExpanded(chest), now);
		boolean expanded = ArmorUpgradeData.helmetExpanded(chest);
		HELMET_SOUNDS.put(player.getUuid(), new HelmetSound(
				now, expanded ? ArmorUpgradeData.HELMET_EXPAND_TICKS
						: ArmorUpgradeData.HELMET_FOLD_TICKS,
				expanded));
		for (ServerPlayerEntity viewer :
				player.getServerWorld().getPlayers()) {
			send(viewer, player, chest);
		}
	}

	private static void tickHelmetSounds(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		Iterator<Map.Entry<UUID, HelmetSound>> iterator =
				HELMET_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, HelmetSound> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager()
					.getPlayer(entry.getKey());
			HelmetSound sound = entry.getValue();
			if (player == null) {
				iterator.remove();
				continue;
			}
			long elapsed = now - sound.startedAt;
			if (elapsed > sound.durationTicks + 2L) {
				iterator.remove();
				continue;
			}
			int[] ticks = sound.expanded
					? EXPAND_SOUND_TICKS : FOLD_SOUND_TICKS;
			net.minecraft.util.Identifier[] sounds = sound.expanded
					? EXPAND_SOUNDS : FOLD_SOUNDS;
			for (int index = 0; index < ticks.length; index++) {
				int bit = 1 << index;
				if (elapsed >= ticks[index]
						&& (sound.mask & bit) == 0) {
					sound.mask |= bit;
					playHelmetSound(player, sounds[index]);
				}
			}
		}
	}

	private static void playHelmetSound(ServerPlayerEntity player,
			net.minecraft.util.Identifier soundId) {
		player.getServerWorld().playSound(null,
				player.getX(), player.getY() + 1.6D, player.getZ(),
				SoundEvent.of(soundId), SoundCategory.PLAYERS,
				1.0F, 1.0F);
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
