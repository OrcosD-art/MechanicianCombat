package com.tk.gun.client;

import com.tk.gun.net.WarBannerSkillPayload;
import com.tk.gun.net.WarBannerAnchorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tracks active War Banner deploy animations and clears their held pose. */
public final class WarBannerSkillClientState {
	private record Entry(UUID playerUuid, long startedAt, long endsAt,
			boolean gunHeld) {
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static final Map<Integer, Vec3d> LOCATORS = new HashMap<>();

	public static void apply(WarBannerSkillPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null
				|| !(client.world.getEntityById(payload.playerEntityId())
				instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		ENTRIES.put(payload.playerEntityId(), new Entry(
				player.getUuid(), payload.startedAt(),
				payload.startedAt() + payload.durationTicks(),
				payload.gunHeld()));
		LOCATORS.remove(payload.playerEntityId());
		if (player == client.player && payload.gunHeld()) {
			// Stop the walk/sprint pose on the packet frame instead of waiting
			// for the next movement tick reconciliation. The non-gun variant
			// deliberately keeps movement and only drives the upper body.
			player.setSprinting(false);
			player.forwardSpeed = 0.0F;
			player.sidewaysSpeed = 0.0F;
			Vec3d velocity = player.getVelocity();
			player.setVelocity(0.0D, velocity.y, 0.0D);
		}
		WarBannerPlayerAnimation.play(player, payload.gunHeld());
	}

	public static void updateLocator(int playerId, Vec3d position) {
		LOCATORS.put(playerId, position);
	}

	public static boolean isActive(int playerId) {
		return ENTRIES.containsKey(playerId);
	}

	public static boolean usesGunAnimation(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.gunHeld();
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
				Vec3d locator = LOCATORS.get(item.getKey());
				if (locator != null) {
					ClientPlayNetworking.send(new WarBannerAnchorPayload(
							locator.x, locator.y, locator.z,
							client.gameRenderer.getCamera().getYaw()));
				}
			}
			if (now < item.getValue().endsAt()) {
				continue;
			}
			WarBannerPlayerAnimation.clear(item.getValue().playerUuid());
			LOCATORS.remove(item.getKey());
			iterator.remove();
		}
	}

	public static void reset() {
		ENTRIES.clear();
		LOCATORS.clear();
		WarBannerPlayerAnimation.clearAll();
	}

	private WarBannerSkillClientState() {
	}
}
