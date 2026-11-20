package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ShriekParticleEffect;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Reproduces the delayed ten-sprite sequence emitted by a sculk shrieker. */
public final class LocatorShriekBurst {
	private static final int PARTICLE_COUNT = 10;
	private static final int FRAME_DELAY_TICKS = 5;
	private static final long DUPLICATE_WINDOW_TICKS = 2;
	private static final ArrayDeque<Burst> BURSTS = new ArrayDeque<>();
	private static final Map<String, Long> LAST_STARTED = new HashMap<>();

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
		long now = client.world.getTime();
		String key = playerId + ":" + source.name();
		Long previous = LAST_STARTED.put(key, now);
		if (previous != null && now - previous < DUPLICATE_WINDOW_TICKS) {
			return;
		}
		BURSTS.addLast(new Burst(resolved, now));
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || BURSTS.isEmpty()) {
			return;
		}
		long now = client.world.getTime();
		Iterator<Burst> iterator = BURSTS.iterator();
		while (iterator.hasNext()) {
			Burst burst = iterator.next();
			long due = burst.startedAt
					+ (long) burst.emitted * FRAME_DELAY_TICKS;
			if (now < due) {
				continue;
			}
			// Spawn one sprite now. Using ShriekParticleEffect(0) avoids
			// implementations that reveal every queued delayed particle in the
			// same render frame.
			client.world.addParticle(new ShriekParticleEffect(0),
					burst.origin.x, burst.origin.y, burst.origin.z,
					0.0D, 0.0D, 0.0D);
			burst.emitted++;
			if (burst.emitted >= PARTICLE_COUNT) {
				iterator.remove();
			}
		}
		if (BURSTS.isEmpty()) {
			LAST_STARTED.clear();
		}
	}

	private static final class Burst {
		private final Vec3d origin;
		private final long startedAt;
		private int emitted;

		private Burst(Vec3d origin, long startedAt) {
			this.origin = origin;
			this.startedAt = startedAt;
		}
	}

	private LocatorShriekBurst() {
	}
}
