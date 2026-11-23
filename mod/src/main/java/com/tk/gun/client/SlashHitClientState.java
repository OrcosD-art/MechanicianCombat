package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Client mirror for the foreground slash-hit flipbook.
 *
 * <p>Each effect keeps the authored pattern and target-facing surface
 * direction chosen on impact. The renderer projects it over the target entity
 * for five ticks, one authored frame per tick.</p>
 */
public final class SlashHitClientState {
	public static final int PATTERN_COUNT = 3;
	public static final int FRAME_COUNT = 5;
	public static final int LIFETIME_TICKS = FRAME_COUNT;

	public record Effect(int targetEntityId, int pattern, long startedAt,
						 double outwardX, double outwardZ) {
		public int age(long now) {
			return Math.max(0, (int) (now - startedAt));
		}
	}

	private static final List<Effect> EFFECTS = new ArrayList<>();

	private SlashHitClientState() {
	}

	public static void spawn(Entity attacker, Entity target, int pattern) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (target == null || client.world == null) {
			return;
		}
		Vec3d center = target.getBoundingBox().getCenter();
		Vec3d outward = attacker == null ? Vec3d.ZERO
				: attacker.getEyePos().subtract(center);
		outward = new Vec3d(outward.x, 0.0D, outward.z);
		if (outward.lengthSquared() < 1.0E-6D) {
			double yaw = Math.toRadians(target.getYaw());
			outward = new Vec3d(Math.sin(yaw), 0.0D, -Math.cos(yaw));
		}
		outward = outward.normalize();
		EFFECTS.add(new Effect(target.getId(),
				Math.floorMod(pattern, PATTERN_COUNT),
				client.world.getTime(), outward.x, outward.z));
	}

	public static Vec3d position(Effect effect, Entity target) {
		double surfaceOffset = target.getWidth() * 0.5D + 0.2D;
		return new Vec3d(
				target.getX() + effect.outwardX() * surfaceOffset,
				target.getY() + target.getHeight() * 0.62D,
				target.getZ() + effect.outwardZ() * surfaceOffset);
	}

	public static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (EFFECTS.isEmpty()) {
			return;
		}
		if (client.world == null) {
			clear();
			return;
		}
		long now = client.world.getTime();
		EFFECTS.removeIf(effect -> {
			if (effect.age(now) >= LIFETIME_TICKS) {
				return true;
			}
			Entity target = client.world.getEntityById(
					effect.targetEntityId());
			return target == null || !target.isAlive();
		});
	}

	public static List<Effect> effects() {
		return new ArrayList<>(EFFECTS);
	}

	public static void clear() {
		EFFECTS.clear();
	}
}
