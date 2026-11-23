package com.tk.gun.client;

import com.tk.gun.client.rig.BowgunRigClient;
import com.tk.gun.client.rig.BowgunRigRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Foreground-rendered Veritas skill spark state. */
public final class SkillSparkClientState {
	public static final int LIFETIME_TICKS = 10;

	public record Effect(int entityId, String locator,
						 String ownerAnimation, long startedAt,
						 Vec3d position) {
		public int age(long now) {
			return (int) Math.max(0L, now - startedAt);
		}

		public int framesPerSecond() {
			return ownerAnimation != null
					&& ownerAnimation.startsWith("GS_") ? 36 : 18;
		}
	}

	private static final Map<Integer, Effect> EFFECTS = new HashMap<>();

	private SkillSparkClientState() {
	}

	public static boolean spawn(Entity entity, Vec3d position,
			String locator, String ownerAnimation) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == null || position == null || client.world == null) {
			return false;
		}
		EFFECTS.put(entity.getId(), new Effect(
				entity.getId(), locator == null ? "aim" : locator,
				ownerAnimation, client.world.getTime(), position));
		return true;
	}

	public static void tick(MinecraftClient client) {
		if (EFFECTS.isEmpty() || client.world == null) {
			return;
		}
		long now = client.world.getTime();
		EFFECTS.entrySet().removeIf(entry -> {
			Effect effect = entry.getValue();
			if (effect.age(now) >= LIFETIME_TICKS) {
				return true;
			}
			String active = BowgunRigClient.activeAnimation(
					effect.entityId());
			return effect.ownerAnimation() != null
					&& effect.age(now) >= 2
					&& !effect.ownerAnimation().equals(active);
		});
		for (Map.Entry<Integer, Effect> entry : EFFECTS.entrySet()) {
			Effect effect = entry.getValue();
			Vec3d locator = switch (effect.locator()) {
				case "aim" -> BowgunRigRenderer.aimPosition(effect.entityId());
				case "effects" -> BowgunRigRenderer.effectsPosition(
						effect.entityId());
				default -> BowgunRigRenderer.locatorPosition(
						effect.entityId());
			};
			if (locator != null) {
				entry.setValue(new Effect(effect.entityId(),
						effect.locator(), effect.ownerAnimation(),
						effect.startedAt(), locator));
			}
		}
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
