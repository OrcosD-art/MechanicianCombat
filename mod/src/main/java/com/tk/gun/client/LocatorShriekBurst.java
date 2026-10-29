package com.tk.gun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ShriekParticleEffect;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Re-creates the sculk shrieker's ten-step particle delay while resolving the
 * attachment point again for every step. Vanilla resolves all ten positions at
 * once, which leaves late particles behind when a moving gun or antenna is used.
 */
public final class LocatorShriekBurst {
	public enum Source {
		GUN,
		SUMMON_ANTENNA
	}

	private record Key(int playerId, Source source) {
	}

	private static final class Burst {
		long startedAt;
		int spawned;
		Vec3d fallbackOffset;

		Burst(long startedAt, int spawned, Vec3d fallbackOffset) {
			this.startedAt = startedAt;
			this.spawned = spawned;
			this.fallbackOffset = fallbackOffset;
		}
	}

	private static final Map<Key, Burst> BURSTS = new HashMap<>();

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
		Vec3d offset = resolved.subtract(entity.getEyePos());
		BURSTS.put(new Key(playerId, source),
				new Burst(client.world.getTime(), 1, offset));
		spawn(client, resolved);
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			BURSTS.clear();
			return;
		}
		long now = client.world.getTime();
		Iterator<Map.Entry<Key, Burst>> iterator = BURSTS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Key, Burst> entry = iterator.next();
			Key key = entry.getKey();
			Burst burst = entry.getValue();
			Entity entity = client.world.getEntityById(key.playerId());
			long age = now - burst.startedAt;
			if (entity == null || age > 50L) {
				iterator.remove();
				continue;
			}
			int targetCount = (int) Math.min(10L, age / 5L + 1L);
			while (burst.spawned < targetCount) {
				Vec3d locator = key.source() == Source.GUN
						? com.tk.gun.client.rig.BowgunRigRenderer.locatorPosition(key.playerId())
						: CombatBackpackGeoRenderer.summonLocatorPosition(key.playerId());
				Vec3d position = locator != null
						? locator : entity.getEyePos().add(burst.fallbackOffset);
				spawn(client, position);
				burst.spawned++;
			}
		}
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
