package com.tk.gun;

import com.tk.gun.net.SoulAnchorStatusPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative 15-second Soul Anchor target mark. */
public final class SoulAnchorMarks {
	public static final int DURATION_TICKS = 15 * 20;
	public static final int COLOR = 0xA9FCEA;
	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private record Mark(ServerWorld world, UUID entityId, long expiresAt,
						boolean wasGlowing) {
	}

	private SoulAnchorMarks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SoulAnchorMarks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MARKS.clear());
	}

	public static void mark(LivingEntity target, ServerPlayerEntity owner) {
		if (target == null || owner == null || target.isRemoved()
				|| !target.isAlive()) {
			return;
		}
		ServerWorld world = (ServerWorld) target.getWorld();
		long now = world.getTime();
		long expiresAt = now + DURATION_TICKS;
		Mark previous = MARKS.get(target.getUuid());
		boolean wasGlowing = previous != null
				? previous.wasGlowing() : target.isGlowing();
		MARKS.put(target.getUuid(), new Mark(
				world, target.getUuid(), expiresAt, wasGlowing));
		target.setGlowing(true);
		broadcast(world, target, new SoulAnchorStatusPayload(
				owner.getId(), target.getId(),
				SoulAnchorStatusPayload.GLOW_TARGET,
				DURATION_TICKS, COLOR));
	}

	public static boolean isMarked(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		Mark mark = MARKS.get(entity.getUuid());
		return mark != null
				&& mark.world() == entity.getWorld()
				&& mark.expiresAt() > entity.getWorld().getTime();
	}

	private static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) {
			return;
		}
		long now = server.getOverworld() == null
				? 0L : server.getOverworld().getTime();
		for (Mark mark : new ArrayList<>(MARKS.values())) {
			if (mark.expiresAt() > now) {
				continue;
			}
			MARKS.remove(mark.entityId());
			Entity entity = mark.world().getEntity(mark.entityId());
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (!mark.wasGlowing()) {
				living.setGlowing(false);
			}
			broadcast(mark.world(), living,
					new SoulAnchorStatusPayload(
							-1, living.getId(),
							SoulAnchorStatusPayload.CLEAR_GLOW_TARGET,
							0, COLOR));
		}
	}

	private static void broadcast(ServerWorld world, Entity target,
								  SoulAnchorStatusPayload payload) {
		Vec3d center = target.getBoundingBox().getCenter();
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer.squaredDistanceTo(center) <= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}
}
