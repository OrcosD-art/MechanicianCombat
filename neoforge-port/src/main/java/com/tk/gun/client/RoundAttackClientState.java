package com.tk.gun.client;

import com.tk.gun.client.rig.BowgunRigRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ATK3 E1-attached ten-frame ground attack effect. */
public final class RoundAttackClientState {
	private static final int LIFETIME_TICKS = 17;
	private static final Map<Integer, Effect> EFFECTS = new HashMap<>();

	public record Effect(int entityId, String ownerAnimation,
						 long startedAt, Vec3d position) {
		public int age(long now) {
			return (int) Math.max(0L, now - startedAt);
		}
	}

	private RoundAttackClientState() {
	}

	public static boolean spawn(Entity entity, Vec3d position,
			String ownerAnimation) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == null || position == null || client.world == null) {
			return false;
		}
		EFFECTS.put(entity.getId(), new Effect(
				entity.getId(), ownerAnimation,
				client.world.getTime(), position));
		return true;
	}

	public static void tick(MinecraftClient client) {
		if (EFFECTS.isEmpty() || client.world == null) {
			return;
		}
		long now = client.world.getTime();
		EFFECTS.entrySet().removeIf(entry -> {
			Effect effect = entry.getValue();
			return effect.age(now) >= LIFETIME_TICKS;
		});
		for (Map.Entry<Integer, Effect> entry : EFFECTS.entrySet()) {
			Vec3d position = BowgunRigRenderer.e1Position(
					entry.getKey());
			if (position != null) {
				Effect effect = entry.getValue();
				entry.setValue(new Effect(effect.entityId(),
						effect.ownerAnimation(), effect.startedAt(),
						position));
			}
		}
	}

	public static List<Effect> effects() {
		return new ArrayList<>(EFFECTS.values());
	}

	public static void clear() {
		EFFECTS.clear();
	}
}
