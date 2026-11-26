package com.tk.gun;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot server authorization for station-owned armor slot mutations. */
public final class ArmorEquipmentTransaction {
	private static final Set<UUID> AUTHORIZED =
			ConcurrentHashMap.newKeySet();

	public static void begin(PlayerEntity player) {
		if (player != null) {
			AUTHORIZED.add(player.getUuid());
		}
	}

	public static void end(PlayerEntity player) {
		if (player != null) {
			AUTHORIZED.remove(player.getUuid());
		}
	}

	public static boolean authorized(PlayerEntity player) {
		return player != null && AUTHORIZED.contains(player.getUuid());
	}

	private ArmorEquipmentTransaction() {
	}
}
