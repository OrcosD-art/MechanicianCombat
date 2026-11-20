package com.tk.gun.entity;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Shared per-tick collision cache and hit-sound coalescing for one grapeshot volley. */
public final class GrapeshotVolley {
	private final int ownerId;
	private final Box searchBox;
	private long candidateTick = Long.MIN_VALUE;
	private long soundTick = Long.MIN_VALUE;
	private List<Entity> candidates = List.of();

	public GrapeshotVolley(int ownerId, Vec3d muzzle, Vec3d forward) {
		this.ownerId = ownerId;
		Vec3d end = muzzle.add(forward.multiply(TkGun.GRAPESHOT_RANGE));
		double radius = TkGun.GRAPESHOT_CONE_RADIUS + 1.0D;
		this.searchBox = new Box(
				Math.min(muzzle.x, end.x) - radius, Math.min(muzzle.y, end.y) - radius,
				Math.min(muzzle.z, end.z) - radius, Math.max(muzzle.x, end.x) + radius,
				Math.max(muzzle.y, end.y) + radius, Math.max(muzzle.z, end.z) + radius);
	}

	public List<Entity> candidates(ServerWorld world) {
		long now = world.getTime();
		if (candidateTick != now) {
			candidateTick = now;
			candidates = world.getEntitiesByClass(Entity.class, searchBox,
					e -> e.getId() != ownerId && e.isAlive() && !e.isSpectator()
							&& e.canHit() && !CombatTargeting.isGolem(e));
		}
		return candidates;
	}

	public void playMergedHitSound(ServerWorld world, Vec3d pos) {
		long now = world.getTime();
		if (soundTick == now) {
			return;
		}
		soundTick = now;
		world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(pos),
				net.minecraft.sound.SoundEvent.of(TkGun.SND_GRAPESHOT_HIT),
				net.minecraft.sound.SoundCategory.PLAYERS, 0.45F,
				0.92F + world.getRandom().nextFloat() * 0.16F);
		if (world.getEntityById(ownerId) instanceof ServerPlayerEntity owner) {
			ServerPlayNetworking.send(owner, new com.tk.gun.net.GunHitMarkerPayload());
		}
	}
}
