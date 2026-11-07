package com.tk.gun.client;

import com.tk.gun.net.WarBannerSkillPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tracks active War Banner deploy animations and clears their held pose. */
public final class WarBannerSkillClientState {
	private record Entry(UUID playerUuid, long startedAt, long endsAt) {
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

	public static void apply(WarBannerSkillPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null
				|| !(client.world.getEntityById(payload.playerEntityId())
				instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		ENTRIES.put(payload.playerEntityId(), new Entry(
				player.getUuid(), payload.startedAt(),
				payload.startedAt() + payload.durationTicks()));
		WarBannerPlayerAnimation.play(player);
	}

	public static boolean isActive(int playerId) {
		return ENTRIES.containsKey(playerId);
	}

	public static double animationSeconds(int playerId, long now,
			float tickDelta) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(0.875D,
				(now - entry.startedAt() + tickDelta) / 20.0D));
	}

	public static void tick(long now) {
		Iterator<Map.Entry<Integer, Entry>> iterator =
				ENTRIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Entry> item = iterator.next();
			if (now < item.getValue().endsAt()) {
				continue;
			}
			WarBannerPlayerAnimation.clear(item.getValue().playerUuid());
			iterator.remove();
		}
	}

	public static void reset() {
		ENTRIES.clear();
		WarBannerPlayerAnimation.clearAll();
	}

	private WarBannerSkillClientState() {
	}
}
