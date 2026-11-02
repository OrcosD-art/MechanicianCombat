package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ShriekParticleEffect;
import net.minecraft.util.math.Vec3d;

/**
 * Creates the same ten shriek sprites as a sculk shrieker in one burst. A tiny
 * visual ring prevents all ten sprites from occupying the exact same point.
 */
public final class LocatorShriekBurst {
	private static final int PARTICLE_COUNT = 10;
	public enum Source {
		GUN,
		SUMMON_ANTENNA
	}

	public static void start(int playerId, Source source, Vec3d origin) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		Entity entity = client.world.getEntityById(playerId);
		if (entity == null) {
			return;
		}
		Vec3d resolved = origin == null
				? entity.getEyePos().add(entity.getRotationVec(1.0F).multiply(0.5D))
				: origin;
		for (int index = 0; index < PARTICLE_COUNT; index++) {
			double angle = Math.PI * 2.0D * index / PARTICLE_COUNT;
			double radius = 0.16D + (index % 3) * 0.08D;
			spawn(client, resolved.add(
					Math.cos(angle) * radius,
					(index % 2) * 0.04D,
					Math.sin(angle) * radius));
		}
	}

	public static void tick(MinecraftClient client) {
		// Kept for the existing client tick hook; the burst is created atomically.
	}

	private static void spawn(MinecraftClient client, Vec3d position) {
		if (client.world == null) {
			return;
		}
		client.world.addParticle(new ShriekParticleEffect(0),
				position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
	}

	private LocatorShriekBurst() {
	}
}
