package com.tk.gun.block;

import com.tk.gun.ArmorEquipmentTransaction;
import com.tk.gun.ArmorHelmetController;
import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import com.tk.gun.item.MechanicianArmorItem;
import com.tk.gun.item.MechanicianArmorPiece;
import com.tk.gun.item.MechanicianUndersuitItem;
import com.tk.gun.net.ArmorDressingStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative dressing sequence and temporary player containment. */
public final class ArmorDressingStationService {
	/** 2.5 s authored clip plus a 3-tick hold on its final pose. */
	public static final int DRESSING_TICKS = 53;
	private static final int ACTIVE_ENTRY_TICKS = 23;
	private static final int HELMET_CLOSE_TICKS = 25;
	private static final String DRESSING_ANIMATION = "盔甲着装";
	private static final String ACTIVE_DRESSING_ANIMATION = "盔甲着装2";
	private static final String ACTIVE_ENTRY_ANIMATION = "盔甲着装3";
	public static final String SEAT_NAME =
			"mechaniciancombat_armor_station_seat";
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, MountedSession> MOUNTED = new HashMap<>();
	private static final Map<UUID, Integer> DRESSING_SOUND_MASKS =
			new HashMap<>();
	private static final Set<UUID> HELMET_CLOSES_SENT =
			new HashSet<>();
	private static final int MOUNT_SETTLE_TICKS = 2;
	private static final int[] DRESSING_SOUND_TICKS =
			{0, 4, 8, 20, 21, 28, 40};
	private static final Identifier[] DRESSING_SOUNDS = {
			com.tk.gun.TkGun.SND_HELMET_STEAM_HISS,
			com.tk.gun.TkGun.SND_RELOAD2,
			com.tk.gun.TkGun.SND_HUNTER_MECH4,
			com.tk.gun.TkGun.SND_ARMOR_DRESSING_BOLT,
			com.tk.gun.TkGun.SND_IMPACT,
			com.tk.gun.TkGun.SND_ARMOR_DRESSING_CHARGE,
			com.tk.gun.TkGun.SND_ARMOR_DRESSING_BOLT
	};
	private static final int[] ACTIVE_ENTRY_SOUND_TICKS = {0, 4, 8};
	private static final Identifier[] ACTIVE_ENTRY_SOUNDS = {
			com.tk.gun.TkGun.SND_HELMET_STEAM_HISS,
			com.tk.gun.TkGun.SND_RELOAD2,
			com.tk.gun.TkGun.SND_HUNTER_MECH4
	};

	private record Session(ServerWorld world, BlockPos stationPos,
			Vec3d returnPosition, float returnYaw, float returnPitch,
			Vec3d stationPosition, float stationYaw, long endTick,
			long helmetCloseTick, boolean activeRefresh,
			boolean alreadyArmored) {
	}

	private static final class MountedSession {
		final ServerWorld world;
		final BlockPos stationPos;
		final UUID seatUuid;
		final Vec3d position;
		final float yaw;
		final float returnYaw;
		final float returnPitch;
		int settleTicks = MOUNT_SETTLE_TICKS;

		private MountedSession(ServerWorld world, BlockPos stationPos,
				UUID seatUuid, Vec3d position, float yaw,
				float returnYaw, float returnPitch) {
			this.world = world;
			this.stationPos = stationPos;
			this.seatUuid = seatUuid;
			this.position = position;
			this.yaw = yaw;
			this.returnYaw = returnYaw;
			this.returnPitch = returnPitch;
		}
	}

	public static boolean start(ServerPlayerEntity player,
			ArmorDressingStationBlockEntity station) {
		ServerWorld world = player.getServerWorld();
		if (SESSIONS.containsKey(player.getUuid())
				|| isRunning(world, station.getPos())) {
			return false;
		}
		if (!hasCompleteInventorySet(player)) {
			player.sendMessage(Text.translatable(
					"message.mechaniciancombat.requires_armor_inventory"), true);
			return false;
		}
		if (!canEquipUndersuit(player)) {
			player.sendMessage(Text.translatable(
					"message.mechaniciancombat.requires_undersuit_slot"), true);
			return false;
		}
		Vec3d returnPosition = player.getPos();
		float returnYaw = player.getYaw();
		float returnPitch = player.getPitch();
		float stationYaw = ArmorDressingStationBlock.displayYaw(
				station.getCachedState());
		Vec3d destination = new Vec3d(
				station.getPos().getX() + 0.5D,
				station.getPos().getY() + 0.125D,
				station.getPos().getZ() + 0.5D);
		long startTick = world.getTime();
		long endTick = startTick + DRESSING_TICKS;
		player.teleport(world, destination.x, destination.y, destination.z,
				java.util.Set.<PositionFlag>of(), stationYaw,
				player.getPitch());
		player.setSprinting(false);
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		player.setNoGravity(true);
		player.noClip = true;
		UUID seatUuid = createAndMountSeat(player, world, destination,
				stationYaw);
		if (seatUuid == null) {
			abortStart(player, world, returnPosition, returnYaw,
					returnPitch);
			return false;
		}
		Session session = new Session(world, station.getPos(),
				returnPosition, returnYaw, returnPitch,
				destination, stationYaw, endTick,
				startTick + HELMET_CLOSE_TICKS, false, false);
		SESSIONS.put(player.getUuid(), session);
		beginDressingSounds(player.getUuid(), session, false);
		ArmorHelmetController.syncForDressingStation(player);
		station.beginDressing(player.getUuid(), startTick, endTick,
				seatUuid, DRESSING_ANIMATION);
		// Establish the client mirror first so the block renderer can resolve
		// the player's skin on its very first dressing frame.
		broadcast(station.getPos(), player.getId(), true, world);
		return true;
	}

	private static boolean startActiveDressing(ServerPlayerEntity player,
			ArmorDressingStationBlockEntity station) {
		return startActiveEntry(player, station,
				ACTIVE_DRESSING_ANIMATION, DRESSING_TICKS, true, false);
	}

	public static boolean startArmoredEntry(ServerPlayerEntity player,
			ArmorDressingStationBlockEntity station) {
		return startActiveEntry(player, station,
				ACTIVE_ENTRY_ANIMATION, ACTIVE_ENTRY_TICKS, false, true);
	}

	private static boolean startActiveEntry(ServerPlayerEntity player,
			ArmorDressingStationBlockEntity station, String animationName,
			int durationTicks, boolean activeRefresh,
			boolean alreadyArmored) {
		ServerWorld world = player.getServerWorld();
		if (SESSIONS.containsKey(player.getUuid())
				|| isRunning(world, station.getPos())) {
			return false;
		}
		Vec3d returnPosition = player.getPos();
		float returnYaw = player.getYaw();
		float returnPitch = player.getPitch();
		float stationYaw = ArmorDressingStationBlock.displayYaw(
				station.getCachedState());
		Vec3d destination = new Vec3d(
				station.getPos().getX() + 0.5D,
				station.getPos().getY() + 0.125D,
				station.getPos().getZ() + 0.5D);
		long startTick = world.getTime();
		long endTick = startTick + durationTicks;
		player.teleport(world, destination.x, destination.y, destination.z,
				java.util.Set.<PositionFlag>of(), stationYaw,
				player.getPitch());
		player.setSprinting(false);
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		player.setNoGravity(true);
		player.noClip = true;
		UUID seatUuid = createAndMountSeat(player, world, destination,
				stationYaw);
		if (seatUuid == null) {
			abortStart(player, world, returnPosition, returnYaw,
					returnPitch);
			return false;
		}
		Session session = new Session(world, station.getPos(),
				returnPosition, returnYaw, returnPitch,
				destination, stationYaw, endTick,
				alreadyArmored ? 0L : startTick + HELMET_CLOSE_TICKS,
				activeRefresh, alreadyArmored);
		SESSIONS.put(player.getUuid(), session);
		beginDressingSounds(player.getUuid(), session, alreadyArmored);
		ArmorHelmetController.syncForDressingStation(player);
		station.beginDressing(player.getUuid(), startTick, endTick,
				seatUuid, animationName);
		// Establish the client mirror first so the block renderer can resolve
		// the player's skin on its very first dressing frame.
		broadcast(station.getPos(), player.getId(), true, world);
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			tickMounted();
			return;
		}
		Iterator<Map.Entry<UUID, Session>> iterator =
				List.copyOf(SESSIONS.entrySet()).iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Session> entry = iterator.next();
			UUID playerId = entry.getKey();
			Session session = entry.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			if (player == null) {
				finishWithoutPlayer(session);
				removeSession(playerId);
				continue;
			}
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.velocityModified = true;
			player.fallDistance = 0.0F;
			tickDressingSounds(playerId, session);
			if (session.helmetCloseTick > 0L
					&& session.world.getTime()
							>= session.helmetCloseTick
					&& HELMET_CLOSES_SENT.add(playerId)) {
				com.tk.gun.ArmorHelmetController
						.closeForDressingStation(player);
			}
			if (session.world.getTime() < session.endTick) {
				continue;
			}
			boolean equipped = session.alreadyArmored
					|| (session.activeRefresh
							? equipAvailableParts(player)
							: equipCompleteSet(player));
			// The authored close starts at 1.25 s. This second call is the
			// completion guarantee, and is a no-op when the helmet is closed.
			com.tk.gun.ArmorHelmetController
					.closeForDressingStation(player);
			com.tk.gun.ArmorHelmetController
					.syncForDressingStation(player);
			ArmorDressingStationBlockEntity station =
					station(session.world, session.stationPos);
			if (station == null) {
				restorePlayer(player, session);
				broadcastMount(session.stationPos, player.getId(), false,
						session.world);
				removeSession(playerId);
				continue;
			}
			if (station != null) {
				if (equipped) {
					mountPlayer(player, session, station);
				} else {
					restorePlayer(player, session);
					station.cancelDressing();
					broadcastMount(station.getPos(), player.getId(), false,
							session.world);
				}
				broadcast(station.getPos(), player.getId(), false,
						session.world);
			}
			removeSession(playerId);
		}
		tickMounted();
	}

	public static void cancelForPlayer(ServerPlayerEntity player) {
		dismount(player);
		Session session = removeSession(player.getUuid());
		if (session == null) {
			return;
		}
		restorePlayer(player, session);
		ArmorDressingStationBlockEntity station =
				station(session.world, session.stationPos);
		if (station != null) {
			station.cancelDressing();
			broadcast(station.getPos(), player.getId(), false, session.world);
			broadcastMount(station.getPos(), player.getId(), false,
					session.world);
		}
	}

	public static void cancelAt(ServerWorld world, BlockPos pos) {
		dismountAt(world, pos);
		for (Map.Entry<UUID, Session> entry :
				List.copyOf(SESSIONS.entrySet())) {
			Session session = entry.getValue();
			if (session.world != world || !session.stationPos.equals(pos)) {
				continue;
			}
			removeSession(entry.getKey());
			ServerPlayerEntity player = world.getServer()
					.getPlayerManager().getPlayer(entry.getKey());
			if (player != null) {
				restorePlayer(player, session);
				broadcast(pos, player.getId(), false, world);
				broadcastMount(pos, player.getId(), false, world);
			}
		}
	}

	public static boolean isRunning(ServerWorld world, BlockPos pos) {
		for (Session session : SESSIONS.values()) {
			if (session.world == world && session.stationPos.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	public static void dismount(ServerPlayerEntity player) {
		MountedSession mounted = MOUNTED.remove(player.getUuid());
		if (mounted == null) {
			return;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		if (mounted.world.getEntity(mounted.seatUuid)
				instanceof MinecartEntity seat) {
			seat.discard();
		}
		player.teleport(mounted.world, mounted.position.x,
				mounted.position.y, mounted.position.z,
				java.util.Set.<PositionFlag>of(), mounted.yaw,
				mounted.returnPitch);
		player.setNoGravity(false);
		player.noClip = false;
		player.setYaw(mounted.yaw);
		player.setHeadYaw(mounted.yaw);
		player.setBodyYaw(mounted.yaw);
		player.setPitch(mounted.returnPitch);
		player.prevYaw = mounted.yaw;
		player.prevHeadYaw = mounted.yaw;
		player.prevBodyYaw = mounted.yaw;
		player.prevPitch = mounted.returnPitch;
		ArmorDressingStationBlockEntity station =
				station(mounted.world, mounted.stationPos);
		if (station != null) {
			station.clearSeat();
			station.clearBackpackBinding();
		}
		broadcastMount(mounted.stationPos, player.getId(), false,
				mounted.world);
	}

	public static boolean isMounted(ServerPlayerEntity player) {
		MountedSession mounted = MOUNTED.get(player.getUuid());
		if (mounted == null) {
			return false;
		}
		if (player.getVehicle() instanceof MinecartEntity seat
				&& seat.getUuid().equals(mounted.seatUuid)) {
			return true;
		}
		cleanupMountedEntry(player.getUuid(), mounted, player, true);
		return false;
	}

	public static boolean isTrackedSeat(ServerWorld world, UUID seatUuid) {
		for (MountedSession mounted : MOUNTED.values()) {
			if (mounted.world == world && mounted.seatUuid.equals(seatUuid)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mountExisting(ServerPlayerEntity player,
			ArmorDressingStationBlockEntity station) {
		if (isMounted(player) || isRunning(player.getServerWorld(),
				station.getPos())) {
			return false;
		}
		if (MechanicianUndersuitItem.isWearingFullSet(player)) {
			float returnYaw = player.getYaw();
			float returnPitch = player.getPitch();
			float yaw = ArmorDressingStationBlock.displayYaw(
					station.getCachedState());
			Vec3d position = new Vec3d(
					station.getPos().getX() + 0.5D,
					station.getPos().getY() + 0.125D,
					station.getPos().getZ() + 0.5D);
			player.teleport(player.getServerWorld(), position.x,
					position.y, position.z,
					java.util.Set.<PositionFlag>of(), yaw, 0.0F);
			return mountAt(player, player.getServerWorld(),
					station.getPos(), station, position, yaw,
					returnYaw, returnPitch);
		} else if (hasAnyInventoryArmorPart(player)) {
			return startActiveDressing(player, station);
		}
		return false;
	}

	public static boolean unequipAtStation(ServerPlayerEntity player,
			BlockPos stationPos) {
		if (!isMounted(player)) {
			return false;
		}
		MountedSession mounted = MOUNTED.get(player.getUuid());
		if (mounted == null || !mounted.stationPos.equals(stationPos)) {
			return false;
		}
		ArmorDressingStationBlockEntity station =
				station(player.getServerWorld(), stationPos);
		if (station == null || station.phase()
				!= ArmorDressingStationBlockEntity.Phase.ACTIVE) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		var registries = world.getRegistryManager();
		for (ArmorPartItem.Kind kind : ArmorPartItem.Kind.values()) {
			EquipmentSlot slot = kind.equipmentSlot();
			ItemStack armor = player.getEquippedStack(slot);
			if (!(armor.getItem() instanceof MechanicianArmorItem item)
					|| !containsKind(item.piece(), kind)) {
				continue;
			}
			ItemStack stored = ArmorUpgradeData.storedPart(
					armor, kind, registries);
			if (stored.isEmpty()) {
				String id = ArmorUpgradeData.partId(armor, kind);
				Identifier parsed = Identifier.tryParse(id);
				if (parsed != null) {
					Item maybePart = net.minecraft.registry.Registries.ITEM
							.get(parsed);
					if (maybePart instanceof ArmorPartItem partItem
							&& partItem.kind() == kind) {
						stored = new ItemStack(maybePart);
					}
				}
			}
			if (!stored.isEmpty()) {
				returnStack(player, stored.copyWithCount(1));
			}
			ArmorUpgradeData.setStoredPart(
					armor, kind, ItemStack.EMPTY, registries);
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET}) {
			ArmorEquipmentTransaction.begin(player);
			ItemStack armor = player.getEquippedStack(slot);
			if (!(armor.getItem() instanceof MechanicianArmorItem)) {
				ArmorEquipmentTransaction.end(player);
				continue;
			}
			ItemStack removed = armor.copy();
			ArmorUpgradeData.setStationLocked(removed, false);
			if (slot == EquipmentSlot.CHEST) {
				ArmorUpgradeData.clearHelmetTransition(removed);
			}
			try {
				player.equipStack(slot, ItemStack.EMPTY);
			} finally {
				ArmorEquipmentTransaction.end(player);
			}
			returnStack(player, removed);
		}
		ArmorEquipmentTransaction.begin(player);
		ItemStack undersuit = player.getEquippedStack(EquipmentSlot.HEAD);
		if (undersuit.getItem() instanceof MechanicianUndersuitItem) {
			ItemStack removed = undersuit.copy();
			ArmorUpgradeData.setStationLocked(removed, false);
			try {
				player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			} finally {
				ArmorEquipmentTransaction.end(player);
			}
			returnStack(player, removed);
		} else {
			ArmorEquipmentTransaction.end(player);
		}
		player.closeHandledScreen();
		player.currentScreenHandler.syncState();
		dismount(player);
		com.tk.gun.ArmorAbilities.sendSnapshot(player);
		return true;
	}

	private static boolean containsKind(MechanicianArmorPiece piece,
			ArmorPartItem.Kind kind) {
		return switch (kind.group()) {
			case CHEST -> piece == MechanicianArmorPiece.CHESTPLATE;
			case LEGS -> piece == MechanicianArmorPiece.LEGGINGS;
			case FEET -> piece == MechanicianArmorPiece.BOOTS;
		};
	}

	private static void dismountAt(ServerWorld world, BlockPos pos) {
		for (Map.Entry<UUID, MountedSession> entry :
				List.copyOf(MOUNTED.entrySet())) {
			MountedSession mounted = entry.getValue();
			if (mounted.world != world || !mounted.stationPos.equals(pos)) {
				continue;
			}
			ServerPlayerEntity player = world.getServer()
					.getPlayerManager().getPlayer(entry.getKey());
			if (player != null) {
				dismount(player);
			} else {
				cleanupMountedEntry(entry.getKey(), mounted, null, true);
			}
		}
	}

	private static void tickMounted() {
		for (Map.Entry<UUID, MountedSession> entry :
				List.copyOf(MOUNTED.entrySet())) {
			MountedSession mounted = entry.getValue();
			ServerPlayerEntity player = mounted.world.getServer()
					.getPlayerManager().getPlayer(entry.getKey());
			if (player == null
					|| !(player.getVehicle() instanceof MinecartEntity stand)
					|| !stand.getUuid().equals(mounted.seatUuid)) {
				cleanupMountedEntry(entry.getKey(), mounted, player, true);
			} else {
				stand.setVelocity(0.0D, 0.0D, 0.0D);
				stand.velocityModified = true;
				if (mounted.settleTicks > 0
						&& --mounted.settleTicks == 0) {
					player.setNoGravity(false);
					player.noClip = false;
				}
			}
		}
	}

	private static void cleanupMountedEntry(UUID playerUuid,
			MountedSession mounted, ServerPlayerEntity player,
			boolean notifyClients) {
		MOUNTED.remove(playerUuid);
		if (player != null && player.getVehicle() != null) {
			player.stopRiding();
		}
		if (player != null) {
			player.setNoGravity(false);
			player.noClip = false;
		}
		if (mounted.world.getEntity(mounted.seatUuid)
				instanceof MinecartEntity seat) {
			seat.discard();
		}
		ArmorDressingStationBlockEntity station =
				station(mounted.world, mounted.stationPos);
		if (station != null) {
			station.clearSeat();
		}
		if (notifyClients) {
			broadcastMount(mounted.stationPos,
					player == null ? -1 : player.getId(), false,
					mounted.world);
		}
	}

	private static void finishWithoutPlayer(Session session) {
		ArmorDressingStationBlockEntity station =
				station(session.world, session.stationPos);
		if (station != null) {
			station.cancelDressing();
			broadcast(station.getPos(), -1, false, session.world);
			broadcastMount(station.getPos(), -1, false, session.world);
		}
	}

	private static void beginDressingSounds(UUID playerUuid, Session session,
			boolean entryOnly) {
		DRESSING_SOUND_MASKS.put(playerUuid, 1);
		Identifier[] sounds = entryOnly
				? ACTIVE_ENTRY_SOUNDS : DRESSING_SOUNDS;
		playDressingSound(session, sounds[0]);
	}

	private static void tickDressingSounds(UUID playerUuid, Session session) {
		Integer storedMask = DRESSING_SOUND_MASKS.get(playerUuid);
		if (storedMask == null) {
			return;
		}
		boolean entryOnly = session.alreadyArmored;
		int[] ticks = entryOnly
				? ACTIVE_ENTRY_SOUND_TICKS : DRESSING_SOUND_TICKS;
		Identifier[] sounds = entryOnly
				? ACTIVE_ENTRY_SOUNDS : DRESSING_SOUNDS;
		long startedAt = session.endTick
				- (entryOnly ? ACTIVE_ENTRY_TICKS : DRESSING_TICKS);
		int elapsed = (int) Math.max(0L,
				session.world.getTime() - startedAt);
		int mask = storedMask;
		for (int index = 0; index < ticks.length; index++) {
			int bit = 1 << index;
			if (elapsed >= ticks[index] && (mask & bit) == 0) {
				mask |= bit;
				playDressingSound(session, sounds[index]);
			}
		}
		if (mask != storedMask) {
			DRESSING_SOUND_MASKS.put(playerUuid, mask);
		}
	}

	private static void playDressingSound(Session session, Identifier soundId) {
		BlockPos pos = session.stationPos;
		session.world.playSound(null,
				pos.getX() + 0.5D, pos.getY() + 1.0D,
				pos.getZ() + 0.5D, SoundEvent.of(soundId),
				SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	private static Session removeSession(UUID playerUuid) {
		DRESSING_SOUND_MASKS.remove(playerUuid);
		HELMET_CLOSES_SENT.remove(playerUuid);
		return SESSIONS.remove(playerUuid);
	}

	private static ArmorDressingStationBlockEntity station(
			ServerWorld world, BlockPos pos) {
		return world.getBlockEntity(pos)
				instanceof ArmorDressingStationBlockEntity station
						? station : null;
	}

	private static void restorePlayer(ServerPlayerEntity player,
			Session session) {
		player.setNoGravity(false);
		player.noClip = false;
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		player.teleport(session.world,
				session.returnPosition.x, session.returnPosition.y,
				session.returnPosition.z, java.util.Set.<PositionFlag>of(),
				session.returnYaw, session.returnPitch);
	}

	private static void completePlayer(ServerPlayerEntity player,
			Session session) {
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		// The player has occupied the station position for the whole clip.
		// Re-teleporting here sends the pre-entry yaw/pitch back to the client,
		// which snaps a free third-person camera at the exact animation tail.
		// Only orient the authored body; leave the camera rotation untouched.
		player.setBodyYaw(session.stationYaw);
		player.prevBodyYaw = session.stationYaw;
	}

	private static void mountPlayer(ServerPlayerEntity player, Session session,
			ArmorDressingStationBlockEntity station) {
		completePlayer(player, session);
		mountAt(player, session.world, session.stationPos, station,
				session.stationPosition, session.stationYaw,
				session.returnYaw, session.returnPitch);
	}

	private static boolean mountAt(ServerPlayerEntity player,
			ServerWorld world, BlockPos stationPos,
			ArmorDressingStationBlockEntity station,
			Vec3d position, float yaw, float returnYaw,
			float returnPitch) {
		MinecartEntity seat = null;
		UUID existingSeatUuid = station.seatEntityUuid();
		if (existingSeatUuid != null
				&& world.getEntity(existingSeatUuid)
						instanceof MinecartEntity existingSeat) {
			if (player.getVehicle() == existingSeat) {
				seat = existingSeat;
			} else {
				for (Entity passenger :
						List.copyOf(existingSeat.getPassengerList())) {
					passenger.stopRiding();
				}
				existingSeat.discard();
				station.clearSeat();
			}
		} else if (existingSeatUuid != null) {
			station.clearSeat();
		}
		if (seat == null) {
			seat = spawnStationSeat(world, position, yaw);
			if (player.getVehicle() != null) {
				player.stopRiding();
			}
			player.startRiding(seat, true);
		}
		if (player.getVehicle() != seat) {
			seat.discard();
			return false;
		}
		seat.setVelocity(0.0D, 0.0D, 0.0D);
		seat.velocityModified = true;
		MOUNTED.put(player.getUuid(), new MountedSession(
				world, stationPos, seat.getUuid(), position, yaw,
				returnYaw, returnPitch));
		station.finishDressing(seat.getUuid());
		broadcastMount(stationPos, player.getId(), true, world);
		return true;
	}

	private static UUID createAndMountSeat(ServerPlayerEntity player,
			ServerWorld world, Vec3d position, float yaw) {
		MinecartEntity seat = spawnStationSeat(world, position, yaw);
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		player.startRiding(seat, true);
		if (player.getVehicle() != seat) {
			seat.discard();
			return null;
		}
		seat.setVelocity(0.0D, 0.0D, 0.0D);
		seat.velocityModified = true;
		return seat.getUuid();
	}

	private static MinecartEntity spawnStationSeat(ServerWorld world,
			Vec3d position, float yaw) {
		MinecartEntity seat = new MinecartEntity(
				world, position.x, position.y, position.z);
		seat.setInvisible(true);
		seat.setInvulnerable(true);
		seat.setNoGravity(true);
		seat.setSilent(true);
		seat.setCustomName(Text.literal(SEAT_NAME));
		seat.setCustomNameVisible(false);
		seat.setYaw(yaw);
		seat.addCommandTag("mechaniciancombat_armor_station_seat");
		world.spawnEntity(seat);
		return seat;
	}

	private static void abortStart(ServerPlayerEntity player,
			ServerWorld world, Vec3d returnPosition, float returnYaw,
			float returnPitch) {
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		player.setNoGravity(false);
		player.noClip = false;
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		player.teleport(world, returnPosition.x, returnPosition.y,
				returnPosition.z, java.util.Set.<PositionFlag>of(),
				returnYaw, returnPitch);
	}

	private static boolean hasCompleteInventorySet(ServerPlayerEntity player) {
		return findSlot(player, MechanicianUndersuitItem.class) >= 0
				&& findArmorSlot(player,
						MechanicianArmorPiece.CHESTPLATE) >= 0
				&& findArmorSlot(player,
						MechanicianArmorPiece.LEGGINGS) >= 0
				&& findArmorSlot(player,
						MechanicianArmorPiece.BOOTS) >= 0;
	}

	private static boolean hasAnyInventoryArmorPart(
			ServerPlayerEntity player) {
		boolean undersuitEquipped =
				MechanicianUndersuitItem.isEquipped(player);
		boolean undersuitAvailable =
				findSlot(player, MechanicianUndersuitItem.class) >= 0;
		if (!undersuitEquipped && (!undersuitAvailable
				|| !canEquipUndersuit(player))) {
			return false;
		}
		return undersuitAvailable || findArmorSlot(player,
						MechanicianArmorPiece.CHESTPLATE) >= 0
				|| findArmorSlot(player,
						MechanicianArmorPiece.LEGGINGS) >= 0
				|| findArmorSlot(player,
						MechanicianArmorPiece.BOOTS) >= 0;
	}

	private static boolean canEquipUndersuit(ServerPlayerEntity player) {
		return findSlot(player, MechanicianUndersuitItem.class) >= 0;
	}

	private static boolean equipCompleteSet(ServerPlayerEntity player) {
		if (!hasCompleteInventorySet(player)) {
			player.sendMessage(Text.translatable(
					"message.mechaniciancombat.armor_equip_failed"), true);
			return false;
		}
		if (!MechanicianUndersuitItem.isEquipped(player)
				&& !equipUndersuit(player)) {
			return false;
		}
		equipArmor(player, MechanicianArmorPiece.CHESTPLATE,
				EquipmentSlot.CHEST);
		equipArmor(player, MechanicianArmorPiece.LEGGINGS,
				EquipmentSlot.LEGS);
		equipArmor(player, MechanicianArmorPiece.BOOTS,
				EquipmentSlot.FEET);
		player.currentScreenHandler.syncState();
		com.tk.gun.ArmorAbilities.sendSnapshot(player);
		return true;
	}

	private static boolean equipAvailableParts(ServerPlayerEntity player) {
		boolean changed = false;
		if (!MechanicianUndersuitItem.isEquipped(player)) {
			changed = equipUndersuit(player);
		}
		if (!MechanicianUndersuitItem.isEquipped(player)) {
			player.currentScreenHandler.syncState();
			com.tk.gun.ArmorAbilities.sendSnapshot(player);
			return changed;
		}
		changed |= equipArmorIfMissing(player,
				MechanicianArmorPiece.CHESTPLATE, EquipmentSlot.CHEST);
		changed |= equipArmorIfMissing(player,
				MechanicianArmorPiece.LEGGINGS, EquipmentSlot.LEGS);
		changed |= equipArmorIfMissing(player,
				MechanicianArmorPiece.BOOTS, EquipmentSlot.FEET);
		player.currentScreenHandler.syncState();
		com.tk.gun.ArmorAbilities.sendSnapshot(player);
		return changed;
	}

	private static boolean equipUndersuit(ServerPlayerEntity player) {
		if (MechanicianUndersuitItem.isEquipped(player)) {
			return false;
		}
		int undersuitSlot = findSlot(player,
				MechanicianUndersuitItem.class);
		if (undersuitSlot < 0) {
			return false;
		}
		ItemStack undersuit = takeOne(player, undersuitSlot);
		ItemStack previous = player.getEquippedStack(EquipmentSlot.HEAD);
		ArmorEquipmentTransaction.begin(player);
		try {
			player.equipStack(EquipmentSlot.HEAD, undersuit);
		} finally {
			ArmorEquipmentTransaction.end(player);
		}
		if (!previous.isEmpty()) {
			returnStack(player, previous);
		}
		return true;
	}

	private static boolean equipArmorIfMissing(ServerPlayerEntity player,
			MechanicianArmorPiece piece, EquipmentSlot slot) {
		if (player.getEquippedStack(slot).getItem()
				instanceof MechanicianArmorItem armor
				&& armor.piece() == piece) {
			return false;
		}
		int inventorySlot = findArmorSlot(player, piece);
		if (inventorySlot < 0) {
			return false;
		}
		equipArmor(player, piece, slot);
		return true;
	}

	private static void equipArmor(ServerPlayerEntity player,
			MechanicianArmorPiece piece, EquipmentSlot slot) {
		int inventorySlot = findArmorSlot(player, piece);
		if (inventorySlot < 0) {
			return;
		}
		ItemStack armor = takeOne(player, inventorySlot);
		ArmorUpgradeData.setStationLocked(armor, true);
		ItemStack previous = player.getEquippedStack(slot);
		ArmorEquipmentTransaction.begin(player);
		try {
			player.equipStack(slot, armor);
		} finally {
			ArmorEquipmentTransaction.end(player);
		}
		if (!previous.isEmpty()) {
			returnStack(player, previous);
		}
	}

	private static ItemStack takeOne(ServerPlayerEntity player, int slot) {
		ItemStack stack = player.getInventory().getStack(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack result = stack.copyWithCount(1);
		stack.decrement(1);
		player.getInventory().setStack(slot,
				stack.isEmpty() ? ItemStack.EMPTY : stack);
		return result;
	}

	private static void returnStack(ServerPlayerEntity player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	private static int findSlot(ServerPlayerEntity player,
			Class<? extends Item> type) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			if (type.isInstance(player.getInventory().getStack(slot).getItem())) {
				return slot;
			}
		}
		return -1;
	}

	private static int findArmorSlot(ServerPlayerEntity player,
			MechanicianArmorPiece piece) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.getItem() instanceof MechanicianArmorItem armor
					&& armor.piece() == piece) {
				return slot;
			}
		}
		return -1;
	}

	private static void broadcast(BlockPos pos, int playerId, boolean active,
			ServerWorld world) {
		ArmorDressingStatePayload payload =
				new ArmorDressingStatePayload(pos, playerId, active);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private static void broadcastMount(BlockPos pos, int playerId,
			boolean mounted, ServerWorld world) {
		com.tk.gun.net.ArmorDressingMountPayload payload =
				new com.tk.gun.net.ArmorDressingMountPayload(
						pos, playerId, mounted);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private ArmorDressingStationService() {
	}
}
