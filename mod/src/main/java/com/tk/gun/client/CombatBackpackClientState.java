package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.CombatBackpackAnimation;
import com.tk.gun.item.CombatBackpackItem;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.net.CombatBackpackStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class CombatBackpackClientState {
	private static final int FIRE_RESULT_TIMEOUT_TICKS = 40;
	private static final int MAX_CHARGES = 10;
	private static final double PROJECTILE_OFFSET_X = -3.0D / 16.0D;
	private static final double PROJECTILE_OFFSET_Y = 13.0D / 16.0D;
	private static final double PROJECTILE_OFFSET_Z = 8.0D / 16.0D;

	private static final class Entry {
		boolean firingRequested;
		int firingRequestedAt;
		int targetEntityId = -1;
		int phase = CombatBackpackStatusPayload.PHASE_READY;
		int missilePhase = CombatBackpackStatusPayload.MISSILE_NONE;
		int jumpEnergyPixels = com.tk.gun.TkGun.JUMP_BACKPACK_ENERGY_MAX_PIXELS;
		boolean jumpEnergyLocked;
		int charges;
		int remainingTicks;
		boolean aimLatched;
		BackpackUpgradeData.LauncherType launcherType = BackpackUpgradeData.LauncherType.NONE;
		CombatBackpackAnimation.Phase animationPhase = CombatBackpackAnimation.Phase.READY;
		boolean animationInitialized;
		int animationStartedAt;
		CombatBackpackAnimation.Phase missileAnimationPhase =
				CombatBackpackAnimation.Phase.READY;
		boolean missileAnimationInitialized;
		int missileAnimationStartedAt;
		int openSoundAt = -1;
		int closeSoundAt = -1;
		long bannerCastUntil;
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static int ticks;
	private static int nextViewSyncAt;

	public static void trigger(ClientPlayerEntity player) {
		if (player == null) {
			return;
		}
		BackpackUpgradeData.LauncherType launcherType =
				BackpackUpgradeData.launcherType(
						CombatBackpackItem.equippedStack(player));
		if (launcherType == BackpackUpgradeData.LauncherType.NONE
				|| launcherType == BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			return;
		}
		if (launcherType == BackpackUpgradeData.LauncherType.BATTLE_BANNER) {
			ENTRIES.computeIfAbsent(player.getId(),
					ignored -> new Entry()).bannerCastUntil = ticks + 23;
			sendAction(player);
			return;
		}
		Entry entry = ENTRIES.computeIfAbsent(player.getId(), ignored -> new Entry());
		if (entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				|| entry.firingRequested) {
			return;
		}
		if (hasActiveTarget(player, entry)) {
			entry.firingRequested = true;
			entry.firingRequestedAt = ticks;
		}
		sendAction(player);
	}

	public static boolean triggerMissileBoxStart(ClientPlayerEntity player) {
		ItemStack backpack = CombatBackpackItem.equippedStack(player);
		if (!BackpackUpgradeData.canUseMissileBox(backpack)
				|| !player.getOffHandStack().isOf(net.minecraft.item.Items.FIREWORK_ROCKET)) {
			return false;
		}
		Entry entry = ENTRIES.computeIfAbsent(player.getId(), ignored -> new Entry());
		if (entry.missilePhase == CombatBackpackStatusPayload.MISSILE_AIM
				|| entry.missilePhase == CombatBackpackStatusPayload.MISSILE_CD) {
			return false;
		}
		sendMissileBoxAction(player, false);
		entry.missilePhase = CombatBackpackStatusPayload.MISSILE_AIM;
		entry.launcherType = BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
		updateMissileAnimationPhase(player.getId(), entry);
		return true;
	}

	public static void releaseMissileBox(ClientPlayerEntity player) {
		sendMissileBoxAction(player, true);
	}

	public static void sendMissileBoxView(ClientPlayerEntity player) {
		sendMissileBoxAction(player, false);
	}

	public static void tick(MinecraftClient client) {
		ticks++;
		for (Map.Entry<Integer, Entry> pair : ENTRIES.entrySet()) {
			Entry state = pair.getValue();
			if (state.remainingTicks > 0) {
				state.remainingTicks--;
			}
			if (state.phase == CombatBackpackStatusPayload.PHASE_MARKED
					&& state.remainingTicks == 0) {
				state.phase = CombatBackpackStatusPayload.PHASE_READY;
				state.targetEntityId = -1;
			}
			updateAnimationPhase(pair.getKey(), state);
			updateMissileAnimationPhase(pair.getKey(), state);
			boolean suppressMechanismSounds = client.player != null
					&& TkGun.holdingGun(client.player)
					&& state.launcherType
							== BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
			if (suppressMechanismSounds) {
				state.openSoundAt = -1;
				state.closeSoundAt = -1;
			}
			if (pair.getKey() == localPlayerId()
					&& !suppressMechanismSounds
					&& state.openSoundAt >= 0 && ticks >= state.openSoundAt) {
				state.openSoundAt = -1;
				playBackpackSound("open");
			}
			if (pair.getKey() == localPlayerId()
					&& !suppressMechanismSounds
					&& state.closeSoundAt >= 0 && ticks >= state.closeSoundAt) {
				state.closeSoundAt = -1;
				playBackpackSound("close");
			}
		}
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		Entry entry = ENTRIES.computeIfAbsent(player.getId(), ignored -> new Entry());
		if (CombatBackpackItem.equippedStack(player).isEmpty()) {
			reset(player.getId());
			return;
		}
		if (ticks >= nextViewSyncAt
				&& (BackpackUpgradeData.launcherType(
						CombatBackpackItem.equippedStack(player))
						== BackpackUpgradeData.LauncherType.AUTO
						|| ticks < entry.bannerCastUntil)) {
			sendView(player);
			nextViewSyncAt = ticks + 2;
		}
		if (entry.firingRequested
				&& ticks - entry.firingRequestedAt >= FIRE_RESULT_TIMEOUT_TICKS) {
			// A disconnect or rejected packet must not strand C3 in the firing state.
			entry.firingRequested = false;
		}
	}

	private static void sendAction(ClientPlayerEntity player) {
		Vec3d front = projectileSpawnPosition(player);
		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		double verticalFov = Math.toRadians(
				Math.max(1.0D, Math.min(170.0D, GunCamera.renderedFovDegrees())));
		double halfVerticalTan = Math.tan(verticalFov * 0.5D);
		int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
		int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
		double halfHorizontalTan = halfVerticalTan
				* framebufferWidth / (double) framebufferHeight;
		ClientPlayNetworking.send(new com.tk.gun.net.CombatBackpackFirePayload(
				front.x, front.y, front.z,
				cameraPos.x, cameraPos.y, cameraPos.z,
				camera.getYaw(), camera.getPitch(),
				halfHorizontalTan, halfVerticalTan));
	}

	private static void sendMissileBoxAction(ClientPlayerEntity player,
											 boolean release) {
		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		double verticalFov = Math.toRadians(
				Math.max(1.0D, Math.min(170.0D, GunCamera.renderedFovDegrees())));
		double halfVerticalTan = Math.tan(verticalFov * 0.5D);
		int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
		int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
		double halfHorizontalTan = halfVerticalTan
				* framebufferWidth / (double) framebufferHeight;

		String[] names = {
				"port1_right", "port2_right", "port3_right",
				"port1_left", "port2_left", "port3_left"
		};
		java.util.Map<String, Vec3d> positions =
				CombatBackpackGeoRenderer.missilePortPositions(player.getId());
		double[] ports = new double[18];
		for (int index = 0; index < names.length; index++) {
			Vec3d position = positions.get(names[index]);
			if (position == null) {
				position = player.getBoundingBox().getCenter();
			}
			ports[index * 3] = position.x;
			ports[index * 3 + 1] = position.y;
			ports[index * 3 + 2] = position.z;
		}
		ClientPlayNetworking.send(new com.tk.gun.net.MissileBoxPayload(
				release,
				cameraPos.x, cameraPos.y, cameraPos.z,
				camera.getYaw(), camera.getPitch(),
				halfHorizontalTan, halfVerticalTan, ports));
	}

	private static void sendView(ClientPlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		ClientPlayNetworking.send(new com.tk.gun.net.CombatBackpackViewPayload(
				cameraPos.x, cameraPos.y, cameraPos.z,
				camera.getYaw(), camera.getPitch()));
	}

	public static void applyStatus(int ownerEntityId, int targetEntityId, int phase,
								   int charges, int remainingTicks, int launcherType,
								   int missilePhase, int jumpEnergyPixels,
								   boolean jumpEnergyLocked) {
		Entry entry = ENTRIES.computeIfAbsent(ownerEntityId, ignored -> new Entry());
		entry.targetEntityId = Math.max(-1, targetEntityId);
		entry.phase = Math.max(CombatBackpackStatusPayload.PHASE_READY,
				Math.min(CombatBackpackStatusPayload.PHASE_CLOSE, phase));
		entry.missilePhase = Math.max(CombatBackpackStatusPayload.MISSILE_NONE,
				Math.min(CombatBackpackStatusPayload.MISSILE_CD, missilePhase));
		entry.jumpEnergyPixels = Math.max(0, Math.min(
				com.tk.gun.TkGun.JUMP_BACKPACK_ENERGY_MAX_PIXELS,
				jumpEnergyPixels));
		entry.jumpEnergyLocked = jumpEnergyLocked;
		entry.launcherType = switch (launcherType) {
			case 1 -> BackpackUpgradeData.LauncherType.PORTABLE;
			case 2 -> BackpackUpgradeData.LauncherType.AUTO;
			case 3 -> BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
			case 4 -> BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
			case 5 -> BackpackUpgradeData.LauncherType.BATTLE_BANNER;
			default -> BackpackUpgradeData.LauncherType.NONE;
		};
		entry.charges = Math.max(0, Math.min(MAX_CHARGES, charges));
		entry.remainingTicks = Math.max(0, remainingTicks);
		if (entry.phase == CombatBackpackStatusPayload.PHASE_MARKED) {
			entry.aimLatched = true;
		} else if (entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				|| entry.phase == CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT) {
			entry.aimLatched = false;
		}
		JumpBackpackClientState.applyStatus(ownerEntityId, entry.phase,
				entry.remainingTicks);
		updateAnimationPhase(ownerEntityId, entry);
		updateMissileAnimationPhase(ownerEntityId, entry);
	}

	private static boolean hasActiveTarget(ClientPlayerEntity player, Entry entry) {
		if (entry.phase != CombatBackpackStatusPayload.PHASE_MARKED
				|| entry.targetEntityId < 0 || entry.remainingTicks <= 0) {
			return false;
		}
		net.minecraft.entity.Entity target = player.getWorld().getEntityById(entry.targetEntityId);
		if (target == null || !target.isAlive()) {
			clearTarget(entry);
			return false;
		}
		return entry.charges > 0;
	}

	private static void clearTarget(Entry entry) {
		entry.targetEntityId = -1;
	}

	public static void completeFire(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return;
		}
		entry.firingRequested = false;
	}

	public static void reset(int playerId) {
		ENTRIES.remove(playerId);
		CombatBackpackAnimation.clear(playerId);
		com.tk.gun.client.rig.CombatBackpackRigLayer.clearCachedStack(playerId);
	}

	public static void resetAll() {
		ENTRIES.clear();
		CombatBackpackAnimation.clearAll();
		com.tk.gun.client.rig.CombatBackpackRigLayer.clearAllCachedStacks();
	}

	private static void updateAnimationPhase(int playerId, Entry entry) {
		CombatBackpackAnimation.Phase next = animationPhase(entry);
		if (entry.animationInitialized && next == entry.animationPhase) {
			return;
		}
		entry.animationInitialized = true;
		CombatBackpackAnimation.Phase previous = entry.animationPhase;
		entry.animationPhase = next;
		entry.animationStartedAt = ticks;
		CombatBackpackAnimation.setPhase(playerId, next);
		if (entry.launcherType == BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			TkGun.LOGGER.info("[mechaniciancombat] jump backpack animation {} -> {} player={}",
					previous, next, playerId);
		}
		entry.openSoundAt = -1;
		entry.closeSoundAt = -1;
		if (playerId != localPlayerId()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && TkGun.holdingGun(client.player)
				&& entry.launcherType
						== BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			return;
		}
		if (next == CombatBackpackAnimation.Phase.READY) {
			playBackpackSound("open");
			entry.closeSoundAt = ticks + 6;
		} else if (next == CombatBackpackAnimation.Phase.READY_TO_FLY) {
			playBackpackSound("open");
			entry.closeSoundAt = ticks + 6;
		} else if (next == CombatBackpackAnimation.Phase.CLOSE) {
			playBackpackSound("close");
		} else if (next == CombatBackpackAnimation.Phase.CD) {
			entry.openSoundAt = ticks + 1;
			entry.closeSoundAt = ticks + 7;
		}
	}

	private static void updateMissileAnimationPhase(int playerId, Entry entry) {
		CombatBackpackAnimation.Phase next =
				entry.missilePhase == CombatBackpackStatusPayload.MISSILE_AIM
						? CombatBackpackAnimation.Phase.MISSILE_AIM
						: entry.missilePhase == CombatBackpackStatusPayload.MISSILE_CD
						? CombatBackpackAnimation.Phase.MISSILE_CD
						: CombatBackpackAnimation.Phase.READY;
		if (entry.missileAnimationInitialized
				&& next == entry.missileAnimationPhase) {
			return;
		}
		entry.missileAnimationInitialized = true;
		entry.missileAnimationPhase = next;
		entry.missileAnimationStartedAt = ticks;
		CombatBackpackAnimation.setMissilePhase(playerId, next);
	}

	private static int localPlayerId() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player == null ? -1 : client.player.getId();
	}

	private static void playBackpackSound(String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getSoundManager() == null) {
			return;
		}
		SoundEvent sound = SoundEvent.of(TkGun.id("backpack_" + name));
		client.getSoundManager().play(PositionedSoundInstance.master(
				sound, 1.0F, TkGun.BACKPACK_SOUND_VOLUME));
	}

	public static CombatBackpackAnimation.Phase phase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackAnimation.Phase.READY : animationPhase(entry);
	}

	public static int statusPhase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackStatusPayload.PHASE_READY : entry.phase;
	}

	public static BackpackUpgradeData.LauncherType launcherType(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? BackpackUpgradeData.LauncherType.NONE
				: entry.launcherType;
	}

	public static double animationAgeTicks(int playerId, float tickDelta) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0.0D
				: Math.max(0, ticks - entry.animationStartedAt) + tickDelta;
	}

	public static int missilePhase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackStatusPayload.MISSILE_NONE
				: entry.missilePhase;
	}

	public static int jumpEnergyPixels(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? com.tk.gun.TkGun.JUMP_BACKPACK_ENERGY_MAX_PIXELS
				: entry.jumpEnergyPixels;
	}

	public static boolean jumpEnergyLocked(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.jumpEnergyLocked;
	}

	public static double missileAnimationAgeTicks(int playerId, float tickDelta) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return 0.0D;
		}
		return Math.max(0, ticks - entry.missileAnimationStartedAt) + tickDelta;
	}

	public static int targetEntityId(int ownerEntityId) {
		Entry entry = ENTRIES.get(ownerEntityId);
		if (entry == null
				|| (entry.phase != CombatBackpackStatusPayload.PHASE_MARKED
						&& entry.phase != CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE)
				|| entry.targetEntityId < 0 || entry.remainingTicks <= 0) {
			return -1;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		net.minecraft.entity.Entity target = client.world == null ? null
				: client.world.getEntityById(entry.targetEntityId);
		if (target == null || !target.isAlive()) {
			clearTarget(entry);
			return -1;
		}
		return entry.targetEntityId;
	}

	public static int charges(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0 : entry.charges;
	}

	public static int remainingTicks(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0 : entry.remainingTicks;
	}

	private static CombatBackpackAnimation.Phase animationPhase(Entry entry) {
		if (entry.launcherType == BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			return switch (entry.phase) {
				case CombatBackpackStatusPayload.PHASE_READY_TO_FLY ->
						CombatBackpackAnimation.Phase.READY_TO_FLY;
				case CombatBackpackStatusPayload.PHASE_FLYING,
						CombatBackpackStatusPayload.PHASE_FALLING ->
						CombatBackpackAnimation.Phase.FLYING;
				case CombatBackpackStatusPayload.PHASE_CLOSE ->
						CombatBackpackAnimation.Phase.CLOSE;
				case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
						CombatBackpackAnimation.Phase.CD;
				default -> CombatBackpackAnimation.Phase.CD;
			};
		}
		if (entry.launcherType == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			return entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
					|| entry.phase == CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT
					? CombatBackpackAnimation.Phase.CD
					: CombatBackpackAnimation.Phase.READY;
		}
		if (entry.launcherType == BackpackUpgradeData.LauncherType.BATTLE_BANNER) {
			return CombatBackpackAnimation.Phase.CD;
		}
		if (entry.phase == CombatBackpackStatusPayload.PHASE_READY && entry.aimLatched) {
			return CombatBackpackAnimation.Phase.AIM;
		}
		return switch (entry.phase) {
			case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
					CombatBackpackAnimation.Phase.CD;
			case CombatBackpackStatusPayload.PHASE_MARKED ->
					CombatBackpackAnimation.Phase.AIM;
			case CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE ->
					CombatBackpackAnimation.Phase.AIM;
			case CombatBackpackStatusPayload.PHASE_AUTO_PAUSED ->
					CombatBackpackAnimation.Phase.AIM;
			default -> CombatBackpackAnimation.Phase.READY;
		};
	}

	public static Vec3d projectileSpawnPosition(ClientPlayerEntity player) {
		Vec3d front = CombatBackpackGeoRenderer.canonicalLaunchPosition(player.getId());
		if (front == null) {
			front = player.getPos().add(0.0D, 1.0D, 0.0D);
		}
		double yaw = Math.toRadians(player.getBodyYaw());
		Vec3d right = new Vec3d(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
		Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
		Vec3d offset = right.multiply(PROJECTILE_OFFSET_X)
				.add(0.0D, PROJECTILE_OFFSET_Y, 0.0D)
				.add(forward.multiply(PROJECTILE_OFFSET_Z));
		return front.add(offset);
	}

	private CombatBackpackClientState() {
	}
}
