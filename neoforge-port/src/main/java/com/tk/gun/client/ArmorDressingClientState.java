package com.tk.gun.client;

import com.tk.gun.net.ArmorDressingStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client mirror for station dressing input locks and temporary camera mode. */
public final class ArmorDressingClientState {
	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static final Map<Integer, MountedEntry> MOUNTED = new HashMap<>();
	private static final Map<Integer, Integer> MOUNT_MISSING_TICKS =
			new HashMap<>();
	private static final int RELEASE_GRACE_TICKS = 12;
	private static final int MOUNT_VEHICLE_GRACE_TICKS = 40;
	private static final int TAIL_HOLD_TICKS = 7;
	private static PendingRelease pendingRelease;
	private static Perspective sessionPerspective;
	private static boolean stationActivateTransition;
	private static int previewPlayerId = -1;

	private static final class MountedEntry {
		final BlockPos stationPos;
		final Perspective previousPerspective;

		private MountedEntry(BlockPos stationPos,
				Perspective previousPerspective) {
			this.stationPos = stationPos.toImmutable();
			this.previousPerspective = previousPerspective;
		}
	}

	private static final class Entry {
		final Perspective previousPerspective;
		final float anchorBodyYaw;
		final BlockPos stationPos;
		int releaseTicks;

		private Entry(Perspective previousPerspective, float anchorBodyYaw,
				BlockPos stationPos) {
			this.previousPerspective = previousPerspective;
			this.anchorBodyYaw = anchorBodyYaw;
			this.stationPos = stationPos.toImmutable();
		}
	}

	private static final class PendingRelease {
		final int playerId;
		final BlockPos stationPos;
		final Perspective previousPerspective;
		int ticks;

		private PendingRelease(int playerId, BlockPos stationPos,
				Perspective previousPerspective, int ticks) {
			this.playerId = playerId;
			this.stationPos = stationPos.toImmutable();
			this.previousPerspective = previousPerspective;
			this.ticks = ticks;
		}
	}

	public static void apply(ArmorDressingStatePayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		int entityId = payload.playerId();
		if (!payload.active()) {
			Entry entry = ENTRIES.get(entityId);
			if (entry != null && entry.releaseTicks == 0
					&& MOUNTED.containsKey(entityId)) {
				// The active station is already visible underneath the held
				// dressing pose. Keep the authored tail and input lock for a
				// few extra ticks so the model hand-off is hidden.
				entry.releaseTicks = TAIL_HOLD_TICKS;
				return;
			}
			finishEntryRelease(client, entityId);
			return;
		}
		if (client.world == null) {
			return;
		}
		if (!(client.world.getEntityById(entityId)
				instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		Entry entry = ENTRIES.get(entityId);
		if (entry == null) {
			entry = new Entry(player == client.player
					? client.options.getPerspective() : null,
					player.bodyYaw, payload.stationPos());
			ENTRIES.put(entityId, entry);
		}
		if (player == client.player) {
			FallbackShoulderCamera.enterStation(client);
			if (sessionPerspective == null) {
				sessionPerspective = entry.previousPerspective;
			}
			if (client.options.getPerspective()
					!= Perspective.THIRD_PERSON_BACK) {
				CameraPerspectiveTransition.beginStationActivate(client);
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		FallbackShoulderCamera.tickStation(client);
		tickTailReleases(client);
		reconcilePhysicalMount(client);
		tickPendingRelease(client);
		Entry local = ENTRIES.get(client.player.getId());
		if (local != null
				&& client.options.getPerspective()
						!= Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (MOUNTED.containsKey(client.player.getId())
				&& client.options.getPerspective()
						!= Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		for (Map.Entry<Integer, Entry> pair : ENTRIES.entrySet()) {
			if (client.world.getEntityById(pair.getKey())
					instanceof AbstractClientPlayerEntity player) {
				BlockPos stationPos = pair.getValue().stationPos;
				var state = client.world.getBlockState(stationPos);
				float yaw = com.tk.gun.block.ArmorDressingStationBlock
						.isStation(state)
								? com.tk.gun.block.ArmorDressingStationBlock
										.displayYaw(state)
								: pair.getValue().anchorBodyYaw;
				player.setBodyYaw(yaw);
				player.prevBodyYaw = yaw;
				player.setHeadYaw(yaw);
				player.prevHeadYaw = yaw;
			}
		}
	}

	private static void tickTailReleases(MinecraftClient client) {
		for (Map.Entry<Integer, Entry> pair :
				List.copyOf(ENTRIES.entrySet())) {
			Entry entry = pair.getValue();
			if (entry.releaseTicks <= 0) {
				continue;
			}
			if (--entry.releaseTicks == 0) {
				finishEntryRelease(client, pair.getKey());
			}
		}
	}

	private static void finishEntryRelease(MinecraftClient client,
			int entityId) {
		if (entityId >= 0) {
			ArmorDressingPlayerAnimation.clear(
					client.world != null
							&& client.world.getEntityById(entityId)
									instanceof AbstractClientPlayerEntity player
							? player.getUuid() : null);
		}
		ENTRIES.remove(entityId);
	}

	private static void reconcilePhysicalMount(MinecraftClient client) {
		int localId = client.player.getId();
		BlockPos seatPos = physicalSeatStation(client);
		if (seatPos != null) {
			MOUNT_MISSING_TICKS.remove(localId);
			Entry dressing = ENTRIES.get(localId);
			MountedEntry mounted = MOUNTED.get(localId);
			if (!stationActivateTransition) {
				if (client.options.getPerspective()
						!= Perspective.THIRD_PERSON_BACK) {
					CameraPerspectiveTransition.beginStationActivate(client);
				}
				stationActivateTransition = true;
			}
			Perspective previous;
			if (dressing != null && dressing.previousPerspective != null) {
				previous = dressing.previousPerspective;
			} else if (mounted != null
					&& mounted.previousPerspective != null) {
				previous = mounted.previousPerspective;
			} else if (pendingRelease != null
					&& pendingRelease.previousPerspective != null) {
				previous = pendingRelease.previousPerspective;
			} else if (sessionPerspective != null) {
				previous = sessionPerspective;
			} else {
				previous = client.options.getPerspective();
			}
			pendingRelease = null;
			MOUNTED.put(localId, new MountedEntry(seatPos, previous));
			if (client.options.getPerspective()
					!= Perspective.THIRD_PERSON_BACK) {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
		} else if (MOUNTED.containsKey(localId)
				&& !(client.player.getVehicle()
						instanceof MinecartEntity)) {
			int missingTicks = MOUNT_MISSING_TICKS.getOrDefault(localId, 0) + 1;
			MOUNT_MISSING_TICKS.put(localId, missingTicks);
			if (missingTicks <= MOUNT_VEHICLE_GRACE_TICKS) {
				return;
			}
			MountedEntry mounted = MOUNTED.remove(localId);
			MOUNT_MISSING_TICKS.remove(localId);
			Perspective previous = mounted == null ? null
					: mounted.previousPerspective != null
							? mounted.previousPerspective
							: sessionPerspective;
			if (mounted != null && previous != null
					&& client.options.getPerspective()
							== Perspective.THIRD_PERSON_BACK) {
				CameraPerspectiveTransition.beginUnequip(client);
				client.options.setPerspective(previous);
			}
			if (mounted != null) {
				sessionPerspective = null;
				stationActivateTransition = false;
			}
		} else if (!MOUNTED.containsKey(localId)) {
			MOUNT_MISSING_TICKS.remove(localId);
		}
	}

	private static BlockPos physicalSeatStation(MinecraftClient client) {
		if (client.world == null || client.player == null
				|| !(client.player.getVehicle()
						instanceof MinecartEntity cart)) {
			return null;
		}
		BlockPos pos = cart.getBlockPos();
		var state = client.world.getBlockState(pos);
		if (!com.tk.gun.block.ArmorDressingStationBlock.isStation(state)
				|| state.get(com.tk.gun.block.ArmorDressingStationBlock.PHASE)
						!= com.tk.gun.block.ArmorDressingStationBlockEntity
								.Phase.ACTIVE.ordinal()) {
			return null;
		}
		return pos.toImmutable();
	}

	private static void tickPendingRelease(MinecraftClient client) {
		if (pendingRelease == null
				|| client.player == null
				|| pendingRelease.playerId != client.player.getId()
				|| physicalSeatStation(client) != null) {
			return;
		}
		if (--pendingRelease.ticks > 0) {
			return;
		}
		if (pendingRelease.previousPerspective != null
				&& client.options.getPerspective()
						== Perspective.THIRD_PERSON_BACK) {
			CameraPerspectiveTransition.beginUnequip(client);
			client.options.setPerspective(
					pendingRelease.previousPerspective);
		}
		pendingRelease = null;
	}

	public static boolean locksInput(int entityId) {
		return ENTRIES.containsKey(entityId);
	}

	public static void applyMount(
			com.tk.gun.net.ArmorDressingMountPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (payload.mounted()) {
			PendingRelease release = pendingRelease;
			pendingRelease = null;
			boolean local = client.player != null
					&& client.player.getId() == payload.playerId();
			Entry dressing = ENTRIES.get(payload.playerId());
			boolean recoveringDressing = local
					&& (dressing != null || release != null
							|| sessionPerspective != null);
			Perspective original = local
					? dressing != null && dressing.previousPerspective != null
							? dressing.previousPerspective
							: release != null
									&& release.previousPerspective != null
									? release.previousPerspective
									: sessionPerspective != null
											? sessionPerspective
											: client.options.getPerspective()
					: null;
			MountedEntry existing = MOUNTED.get(payload.playerId());
			if (existing == null
					|| !existing.stationPos.equals(payload.stationPos())
					|| existing.previousPerspective == null) {
				MOUNTED.put(payload.playerId(), new MountedEntry(
						payload.stationPos(), local ? original : null));
			}
			if (local) {
				FallbackShoulderCamera.enterStation(client);
				if (!stationActivateTransition) {
					if (client.options.getPerspective()
							!= Perspective.THIRD_PERSON_BACK) {
						CameraPerspectiveTransition.beginStationActivate(
								client);
					}
					stationActivateTransition = true;
				}
				if (client.options.getPerspective()
						!= Perspective.THIRD_PERSON_BACK) {
					client.options.setPerspective(
							Perspective.THIRD_PERSON_BACK);
				}
				if (recoveringDressing) {
					CameraPerspectiveTransition.beginStationSettle(client);
				}
			}
		} else {
			pendingRelease = null;
			finishEntryRelease(client, payload.playerId());
			MountedEntry entry = MOUNTED.remove(payload.playerId());
			MOUNT_MISSING_TICKS.remove(payload.playerId());
			Perspective previous = entry != null
					&& entry.previousPerspective != null
							? entry.previousPerspective
							: sessionPerspective;
			if (client.player != null
					&& client.player.getId() == payload.playerId()
					&& previous != null
					&& client.options.getPerspective()
							== Perspective.THIRD_PERSON_BACK) {
				CameraPerspectiveTransition.beginUnequip(client);
				client.options.setPerspective(previous);
			}
			sessionPerspective = null;
			stationActivateTransition = false;
		}
	}

	public static BlockPos mountedStationPos(int entityId) {
		MountedEntry entry = MOUNTED.get(entityId);
		return entry == null ? null : entry.stationPos;
	}

	public static boolean mounted(int entityId) {
		return MOUNTED.containsKey(entityId);
	}

	public static boolean holdsCameraSession(int entityId) {
		return ENTRIES.containsKey(entityId) || MOUNTED.containsKey(entityId)
				|| sessionPerspective != null;
	}

	public static boolean hidesPlayer(int entityId) {
		return ENTRIES.containsKey(entityId) || MOUNTED.containsKey(entityId);
	}

	/**
	 * The mounted player is intentionally hidden in the world, but GUI player
	 * previews reuse the same PlayerEntityRenderer. Suppress only the world
	 * render while the armor workstation asks for a full-body preview.
	 */
	public static boolean hidesPlayerRenderer(int entityId) {
		return hidesPlayer(entityId) && previewPlayerId != entityId;
	}

	public static boolean dressingHandoffActive(BlockPos stationPos) {
		if (stationPos == null) {
			return false;
		}
		for (Entry entry : ENTRIES.values()) {
			if (entry.stationPos.equals(stationPos)) {
				return true;
			}
		}
		return false;
	}

	public static int beginPlayerPreview(int entityId) {
		int previous = previewPlayerId;
		previewPlayerId = entityId;
		return previous;
	}

	public static void endPlayerPreview(int previousPlayerId) {
		previewPlayerId = previousPlayerId;
	}

	public static AbstractClientPlayerEntity playerAt(BlockPos stationPos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || stationPos == null) {
			return null;
		}
		for (Map.Entry<Integer, Entry> pair : ENTRIES.entrySet()) {
			if (!pair.getValue().stationPos.equals(stationPos)) {
				continue;
			}
			if (client.world.getEntityById(pair.getKey())
					instanceof AbstractClientPlayerEntity player) {
				return player;
			}
		}
		for (Map.Entry<Integer, MountedEntry> pair : MOUNTED.entrySet()) {
			if (pair.getValue().stationPos.equals(stationPos)
					&& client.world.getEntityById(pair.getKey())
							instanceof AbstractClientPlayerEntity player) {
				return player;
			}
		}
		return null;
	}

	public static void clearAll() {
		MinecraftClient client = MinecraftClient.getInstance();
		Entry local = client.player == null ? null
				: ENTRIES.get(client.player.getId());
		MountedEntry mounted = client.player == null ? null
				: MOUNTED.get(client.player.getId());
		Perspective previous = local != null
				&& local.previousPerspective != null
						? local.previousPerspective
						: mounted == null ? null
								: mounted.previousPerspective != null
										? mounted.previousPerspective
										: sessionPerspective;
		if (previous != null && client.player != null) {
			CameraPerspectiveTransition.beginUnequip(client);
			client.options.setPerspective(previous);
		}
		ENTRIES.clear();
		MOUNTED.clear();
		MOUNT_MISSING_TICKS.clear();
		pendingRelease = null;
		sessionPerspective = null;
		stationActivateTransition = false;
		previewPlayerId = -1;
		ArmorDressingPlayerAnimation.clearAll();
		FallbackShoulderCamera.leaveStation(client);
	}

	private ArmorDressingClientState() {
	}
}
