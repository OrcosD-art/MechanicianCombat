package com.tk.gun.client;

import com.tk.gun.client.rig.BowgunRigRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kinetic Sword-only spark state.
 *
 * <p>The authored five-frame GIF is projected over 0.15 seconds. Locator
 * selection stays shared with the sword rig so the spark follows the authored
 * effect marker while facing the camera.</p>
 */
public final class KineticSwordSparkClientState {
	public static final int FRAME_COUNT = 5;
	public static final int LIFETIME_TICKS = 3;

	public record Effect(int entityId, long startedAt, Vec3d fallback) {
		public int age(long now) {
			return Math.max(0, (int) (now - startedAt));
		}
	}

	private static final Map<Integer, Effect> EFFECTS = new HashMap<>();

	private KineticSwordSparkClientState() {
	}

	public static boolean spawn(Entity entity, Vec3d position) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == null || position == null || client.world == null) {
			return false;
		}
		EFFECTS.put(entity.getId(), new Effect(
				entity.getId(), client.world.getTime(), position));
		return true;
	}

	public static Vec3d position(Effect effect) {
		Vec3d locator = BowgunRigRenderer.effectsPosition(effect.entityId());
		if (locator == null) {
			locator = BowgunRigRenderer.locatorPosition(effect.entityId());
		}
		return locator == null ? effect.fallback() : locator;
	}

	public static void tick(MinecraftClient client) {
		if (EFFECTS.isEmpty()) {
			return;
		}
		if (client.world == null) {
			clear();
			return;
		}
		long now = client.world.getTime();
		EFFECTS.entrySet().removeIf(entry ->
				entry.getValue().age(now) >= LIFETIME_TICKS
						|| client.world.getEntityById(entry.getKey()) == null);
	}

	public static List<Effect> effects() {
		return new ArrayList<>(EFFECTS.values());
	}

	public static void clearForEntity(int entityId) {
		EFFECTS.remove(entityId);
	}

	public static void clear() {
		EFFECTS.clear();
	}
}
