package com.tk.gun.client;

import com.tk.gun.client.rig.BowgunRigRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Veritas ATK3 E1 cyclone; front and back share one authored frame clock. */
public final class CycloneClientState {
	public static final int FRAME_COUNT = 6;
	public static final int FRAME_STEP_TICKS = 2;
	private static final int LIFETIME_TICKS = FRAME_COUNT * FRAME_STEP_TICKS;
	private static final Map<Integer, Effect> EFFECTS = new HashMap<>();

	public record Effect(int entityId, String ownerAnimation,
						 long startedAt, Vec3d position) {
		public int age(long now) {
			return (int) Math.max(0L, now - startedAt);
		}
	}

	private CycloneClientState() {
	}

	public static boolean spawn(Entity entity, Vec3d position,
			String ownerAnimation) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == null || position == null || client.world == null) {
			return false;
		}
		EFFECTS.put(entity.getId(), new Effect(entity.getId(),
				ownerAnimation, client.world.getTime(), position));
		return true;
	}

	public static void tick(MinecraftClient client) {
		if (EFFECTS.isEmpty() || client.world == null) {
			return;
		}
		long now = client.world.getTime();
		EFFECTS.entrySet().removeIf(entry ->
				entry.getValue().age(now) >= LIFETIME_TICKS);
		for (Map.Entry<Integer, Effect> entry : EFFECTS.entrySet()) {
			Vec3d position = BowgunRigRenderer.e1Position(entry.getKey());
			if (position != null) {
				Effect effect = entry.getValue();
				entry.setValue(new Effect(effect.entityId(),
						effect.ownerAnimation(), effect.startedAt(), position));
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
