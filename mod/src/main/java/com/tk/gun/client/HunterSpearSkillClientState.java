package com.tk.gun.client;

import com.tk.gun.net.HunterSpearAnchorPayload;
import com.tk.gun.net.HunterSpearSkillPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tracks the hunter spear deployment pose and sends the authored marker. */
public final class HunterSpearSkillClientState {
	private record Entry(UUID playerUuid, long startedAt, long endsAt) {
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static final Map<Integer, Vec3d> MARKERS = new HashMap<>();

	public static void apply(HunterSpearSkillPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null
				|| !(client.world.getEntityById(payload.playerEntityId())
				instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		ENTRIES.put(payload.playerEntityId(), new Entry(
				player.getUuid(), payload.startedAt(),
				payload.startedAt() + payload.durationTicks()));
		MARKERS.remove(payload.playerEntityId());
		HunterSpearPlayerAnimation.play(player);
	}

	public static void updateMarker(int playerId, Vec3d marker) {
		MARKERS.put(playerId, marker);
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
		return Math.max(0.0D, Math.min(1.125D,
				(now - entry.startedAt() + tickDelta) / 20.0D));
	}

	public static void tick(long now) {
		Iterator<Map.Entry<Integer, Entry>> iterator =
				ENTRIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Entry> item = iterator.next();
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null
					&& client.player.getId() == item.getKey()
					&& now >= item.getValue().endsAt() - 3L
					&& now < item.getValue().endsAt()) {
				Vec3d marker = MARKERS.get(item.getKey());
				if (marker != null) {
					ClientPlayNetworking.send(new HunterSpearAnchorPayload(
							marker.x, marker.y, marker.z,
							client.player.getYaw()));
				}
			}
			if (now < item.getValue().endsAt()) {
				continue;
			}
			HunterSpearPlayerAnimation.clear(item.getValue().playerUuid());
			MARKERS.remove(item.getKey());
			iterator.remove();
		}
	}

	public static void reset() {
		ENTRIES.clear();
		MARKERS.clear();
		HunterSpearPlayerAnimation.clearAll();
	}

	private HunterSpearSkillClientState() {
	}
}
