package com.tk.gun;

import com.tk.gun.entity.SoulAnchorEntity;
import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import com.tk.gun.net.SoulAnchorSkillPayload;
import com.tk.gun.net.SoulAnchorStatusPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Chestplate T1 Soul Anchor skill, independent of the gun state machine. */
public final class SoulAnchorSkillService {
	/** First-person throw reaches its final authored keyframe at 0.25s. */
	private static final int THROW_TICKS = 5;
	private static final int GUN_THROW_TICKS = 5;
	private static final int PLAYER_DRAG_TIMEOUT_TICKS = 40;
	private static final int ENTITY_DRAG_TICKS = 10;
	private static final int ANCHOR_FLIGHT_TIMEOUT_TICKS = 100;
	/** Hit lead-in + soul_anchor_3.gif -> static chain before any drag. */
	private static final int ANCHOR_TRANSITION_TICKS = 6;
	private static final int CHAIN_SLIDING_TICKS = 14;
	private static final int CHAIN_SLIDING_TO_IDLE_TICKS = 5;
	private static final int CHAIN_SLIDING_TO_RUN_TICKS = 5;
	/** Covers throw + maximum flight + impact stall + either recovery path. */
	private static final int ACCELERATION_PAUSE_HOLD_TICKS = 200;
	private static final double CHAIN_SLIDING_DISTANCE = 12.0D;
	private static final double CHAIN_SLIDING_ARRIVAL_DISTANCE = 0.5D;
	private static final int CLIENT_HOLD_TIMEOUT_TICKS = 1200;
	private static final double TARGET_RANGE = 32.0D;
	private static final double FIREWORK_FORWARD_SPEED = 1.5D;
	private static final double FIREWORK_FORWARD_BIAS = 0.1D;
	private static final double FIREWORK_VELOCITY_BLEND = 0.5D;

	private enum Stage {
		READY,
		THROW,
		ANCHOR_FLYING,
		PLAYER_DRAG,
		ENTITY_DRAG,
		GUN_THROWING,
		GUN_FLYING,
		GUN_ENTITY_DRAG,
		GUN_SLIDING,
		GUN_SLIDING_TO_IDLE,
		GUN_SLIDING_TO_RUN,
		GUN_RECALL
	}

	private static final class Skill {
		Stage stage = Stage.THROW;
		boolean gun;
		long stageStart;
		long stageEnd;
		int anchorId = -1;
		long anchorLandedAt;
		int lastPhase = -1;
		boolean throwSoundPlayed;
		int targetId = -1;
		boolean targetHighlighted;
		double slideDirX;
		double slideDirZ;
		boolean returnToRunAfterSlide;
		boolean slideDashPaused;
		long slidePausedDashTicks;
		long slidePausedSpeedBoostTicks;
		double slideResumeSpeed;
	}

	private static final Map<UUID, Skill> SKILLS = new HashMap<>();

	private SoulAnchorSkillService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server ->
				server.getPlayerManager().getPlayerList()
						.forEach(SoulAnchorSkillService::tick));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				finish(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server ->
				SKILLS.clear());
	}

	public static boolean isAvailable(ServerPlayerEntity player) {
		if (player == null || !player.isAlive() || player.isSpectator()
				|| !player.isOnGround()) {
			return false;
		}
		return hasInstalledModules(player);
	}

	private static boolean hasInstalledModules(
			net.minecraft.entity.player.PlayerEntity player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		ItemStack legs = player.getEquippedStack(
				net.minecraft.entity.EquipmentSlot.LEGS);
		return ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3)
				&& ArmorUpgradeData.hasPart(legs, ArmorPartItem.Kind.T1);
	}

	public static boolean press(ServerPlayerEntity player, boolean sprintingAtPress) {
		if (!isAvailable(player) || player.isUsingItem()) {
			return false;
		}
		TkGunServer.PState current = TkGunServer.state(player);
		if (player.getWorld().getTime() < current.battleBannerCastUntil) {
			return false;
		}
		boolean gun = TkGun.holdingGun(player);
		if (gun) {
			if (current.state != TkGun.State.IDLE || current.deployed) {
				return false;
			}
		}
		return startReady(player, gun, sprintingAtPress);
	}

	public static void release(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		Skill skill = SKILLS.get(player.getUuid());
		if (skill == null || skill.stage != Stage.READY) {
			return;
		}
		pauseAccelerationCountdown(player, skill);
		selectTarget(player, skill);
		clearReadyTarget(player, skill);
		beginStage(player, skill,
				skill.gun ? Stage.GUN_THROWING : Stage.THROW,
				skill.gun ? GUN_THROW_TICKS : THROW_TICKS);
	}

	public static void cancel(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		Skill skill = SKILLS.get(player.getUuid());
		if (skill != null && canSneakRecall(skill.stage)) {
			cancelDrag(player, skill);
		}
	}

	public static boolean isActive(ServerPlayerEntity player) {
		return player != null && SKILLS.containsKey(player.getUuid());
	}

	/** Melee may begin during anchor recall, where it owns the visible pose. */
	public static boolean allowsMeleeStart(ServerPlayerEntity player) {
		Skill skill = player == null ? null : SKILLS.get(player.getUuid());
		return skill == null || skill.stage == Stage.GUN_RECALL;
	}

	private static boolean startReady(ServerPlayerEntity player, boolean gun,
									  boolean sprintingAtPress) {
		if (SKILLS.containsKey(player.getUuid())) {
			return false;
		}
		Skill skill = new Skill();
		skill.gun = gun;
		skill.stage = Stage.READY;
		skill.stageStart = player.getWorld().getTime();
		skill.stageEnd = skill.stageStart;
		TkGunServer.PState gunState = TkGunServer.state(player);
		skill.returnToRunAfterSlide = gun
				&& (sprintingAtPress || player.isSprinting()
				|| skill.stageStart < gunState.dashUntil
				|| skill.stageStart < gunState.speedBoostUntil);
		if (skill.returnToRunAfterSlide) {
			skill.slideResumeSpeed = Math.max(0.28D, Math.min(0.42D,
					player.getVelocity().horizontalLength()));
		}
		SKILLS.put(player.getUuid(), skill);
		suppressReadySprint(player);
		syncPhase(player, skill, SoulAnchorSkillPayload.READY,
				CLIENT_HOLD_TIMEOUT_TICKS);
		selectTarget(player, skill);
		return true;
	}

	private static void tick(ServerPlayerEntity player) {
		Skill skill = SKILLS.get(player.getUuid());
		if (skill == null) {
			return;
		}
		if (!player.isAlive() || !isArmorStillInstalled(player)) {
			finish(player);
			return;
		}
		long now = player.getWorld().getTime();
		if (skill.stage == Stage.READY) {
			suppressReadySprint(player);
			selectTarget(player, skill);
			return;
		}
		if (!skill.gun) {
			player.setSprinting(false);
		}
		if (!skill.throwSoundPlayed && now >= skill.stageStart + 3L
				&& (skill.stage == Stage.THROW
						|| skill.stage == Stage.GUN_THROWING)) {
			skill.throwSoundPlayed = true;
			TkGunServer.playSound(player, TkGun.SND_CHAIN, 1.0F, 1.0F);
		}
		if (skill.gun && locksGunMovement(skill.stage)) {
			player.forwardSpeed = 0.0F;
			player.sidewaysSpeed = 0.0F;
			Vec3d velocity = player.getVelocity();
			player.setVelocity(0.0D, velocity.y, 0.0D);
			player.velocityModified = true;
		}
		switch (skill.stage) {
			case READY -> {
			}
			case THROW -> {
				if (now >= skill.stageEnd) {
					if (!spawnAnchor(player, skill)) {
						finish(player);
					}
				}
			}
			case GUN_THROWING -> {
				if (now >= skill.stageEnd && !spawnAnchor(player, skill)) {
					finish(player);
				}
			}
			case ANCHOR_FLYING, GUN_FLYING -> observeAnchor(player, skill, now);
			case PLAYER_DRAG -> pullPlayerToAnchor(player, skill, now);
			case GUN_SLIDING -> slidePlayerToAnchor(player, skill, now);
			case ENTITY_DRAG, GUN_ENTITY_DRAG, GUN_RECALL ->
					observeRecall(player, skill, now);
			case GUN_SLIDING_TO_IDLE ->
					tickSlidingToIdle(player, skill, now);
			case GUN_SLIDING_TO_RUN ->
					tickSlidingToRun(player, skill, now);
		}
	}

	private static boolean locksGunMovement(Stage stage) {
		return stage == Stage.GUN_SLIDING_TO_IDLE;
	}

	private static void suppressReadySprint(ServerPlayerEntity player) {
		TkGunServer.PState state = TkGunServer.state(player);
		if (TkGun.holdingGun(player)
				&& player.getWorld().getTime() < state.dashUntil) {
			return;
		}
		player.setSprinting(false);
	}

	private static boolean spawnAnchor(ServerPlayerEntity player, Skill skill) {
		Vec3d direction = player.getRotationVector().normalize();
		Entity marked = skill.targetId < 0 ? null
				: player.getServerWorld().getEntityById(skill.targetId);
		if (marked instanceof LivingEntity living && living.isAlive()) {
			Vec3d toTarget = living.getBoundingBox().getCenter()
					.subtract(player.getEyePos());
			if (toTarget.lengthSquared() > 1.0E-6D) {
				direction = toTarget.normalize();
			}
		} else {
			skill.targetId = -1;
		}
		double yaw = Math.toRadians(player.getYaw());
		Vec3d left = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
		Vec3d spawn = player.getPos()
				.add(0.0D, player.getStandingEyeHeight() * 0.72D, 0.0D)
				.add(left.multiply(0.32D))
				.add(direction.multiply(0.45D));
		SoulAnchorEntity anchor = new SoulAnchorEntity(
				TkGunServer.SOUL_ANCHOR_ENTITY, player.getWorld(), player);
		anchor.setPosition(spawn.x, spawn.y, spawn.z);
		anchor.setVelocity(direction.multiply(2.75D));
		player.getServerWorld().spawnEntity(anchor);
		skill.anchorId = anchor.getId();
		skill.anchorLandedAt = 0L;
		skill.stage = skill.gun ? Stage.GUN_FLYING : Stage.ANCHOR_FLYING;
		skill.stageStart = player.getWorld().getTime();
		skill.stageEnd = skill.stageStart + ANCHOR_FLIGHT_TIMEOUT_TICKS;
		// The authored throw clip is a hold-on-last-frame animation.  Keep its
		// final pose active for the anchor's whole flight instead of letting the
		// original 0.5s client phase expire at the exact spawn frame.
		syncPhase(player, skill, SoulAnchorSkillPayload.ANCHOR_FLIGHT,
				CLIENT_HOLD_TIMEOUT_TICKS);
		return true;
	}

	private static void observeAnchor(ServerPlayerEntity player, Skill skill,
			long now) {
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor)
				|| anchor.isRemoved()) {
			finish(player);
			return;
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.FLYING
				&& skill.targetId >= 0) {
			Entity marked = player.getServerWorld()
					.getEntityById(skill.targetId);
			if (marked instanceof LivingEntity living && living.isAlive()) {
				Vec3d steer = living.getBoundingBox().getCenter()
						.subtract(anchor.getPos());
				if (steer.lengthSquared() > 1.0E-6D) {
					anchor.setVelocity(steer.normalize().multiply(2.75D));
					anchor.velocityModified = true;
				}
			} else {
				skill.targetId = -1;
			}
		}
		if (anchor.anchorState() != SoulAnchorEntity.State.ANCHORED) {
			if (now >= skill.stageEnd) {
				anchor.retrieve(player);
				resumeAccelerationCountdown(player, skill, now);
				beginStage(player, skill, Stage.GUN_RECALL,
						recallTicks(skill));
			}
			return;
		}
		if (skill.anchorLandedAt == 0L) {
			skill.anchorLandedAt = now;
			// Entity hooks never enter the gun-slide branch. Continue the
			// acceleration clock immediately instead of staying paused.
			if (skill.gun && anchor.hookedEntityId() >= 0) {
				resumeAccelerationCountdown(player, skill, now);
			}
			syncPhase(player, skill,
					SoulAnchorSkillPayload.ANCHOR_STALL,
					ANCHOR_TRANSITION_TICKS);
		}
		// Keep the landed anchor fixed while the client plays the impact
		// transition; the player remains free to move.
		if (now < skill.anchorLandedAt + ANCHOR_TRANSITION_TICKS) {
			return;
		}
		Entity hooked = anchor.hookedEntity();
		int hookedId = anchor.hookedEntityId();
		if (hookedId >= 0
				&& (hooked == null || !hooked.isAlive())) {
			anchor.retrieve(player);
			resumeAccelerationCountdown(player, skill, now);
			beginStage(player, skill, Stage.GUN_RECALL,
					recallTicks(skill));
		} else if (hooked != null && hooked.isAlive()) {
			if (hooked instanceof LivingEntity living) {
				SoulAnchorMarks.mark(living, player);
			}
			anchor.retrieve(player);
			beginStage(player, skill,
					skill.gun ? Stage.GUN_ENTITY_DRAG : Stage.ENTITY_DRAG,
					ENTITY_DRAG_TICKS);
		} else if (skill.gun) {
			beginGunSliding(player, skill, anchor);
		} else {
			beginStage(player, skill, Stage.PLAYER_DRAG,
					PLAYER_DRAG_TIMEOUT_TICKS);
		}
	}

	private static void pullPlayerToAnchor(ServerPlayerEntity player,
			Skill skill, long now) {
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor)
				|| anchor.isRemoved()) {
			finish(player);
			return;
		}
		Vec3d target = anchor.getPos().add(0.0D, 0.8D, 0.0D);
		Vec3d delta = target.subtract(player.getPos());
		if (delta.lengthSquared() < 2.25D || now >= skill.stageEnd) {
			anchor.discard();
			finish(player);
			return;
		}
		Vec3d direction = delta.normalize();
		Vec3d current = player.getVelocity();
		// Vanilla FireworkRocketEntity's elytra acceleration:
		// current + direction*0.1 + (direction*1.5 - current)*0.5.
		Vec3d accelerated = current.add(
				direction.x * FIREWORK_FORWARD_BIAS
						+ (direction.x * FIREWORK_FORWARD_SPEED - current.x)
								* FIREWORK_VELOCITY_BLEND,
				direction.y * FIREWORK_FORWARD_BIAS
						+ (direction.y * FIREWORK_FORWARD_SPEED - current.y)
								* FIREWORK_VELOCITY_BLEND,
				direction.z * FIREWORK_FORWARD_BIAS
						+ (direction.z * FIREWORK_FORWARD_SPEED - current.z)
								* FIREWORK_VELOCITY_BLEND);
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		player.setSprinting(false);
		player.setVelocity(accelerated);
		player.velocityModified = true;
	}

	private static void beginGunSliding(ServerPlayerEntity player, Skill skill,
			SoulAnchorEntity anchor) {
		Vec3d delta = anchor.getPos().subtract(player.getPos());
		Vec3d direction = new Vec3d(delta.x, 0.0D, delta.z);
		if (direction.lengthSquared() < 1.0E-6D) {
			Vec3d look = player.getRotationVector();
			direction = new Vec3d(look.x, 0.0D, look.z);
		}
		if (direction.lengthSquared() < 1.0E-6D) {
			direction = Vec3d.fromPolar(0.0F, player.getYaw());
		}
		direction = direction.normalize();
		skill.slideDirX = direction.x;
		skill.slideDirZ = direction.z;
		float yaw = (float) Math.toDegrees(
				Math.atan2(-direction.x, direction.z));
		player.setYaw(yaw);
		player.setHeadYaw(yaw);
		player.setBodyYaw(yaw);
		player.setSprinting(false);
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		player.upwardSpeed = 0.0F;
		pauseAccelerationCountdown(player, skill);
		beginStage(player, skill, Stage.GUN_SLIDING,
				CHAIN_SLIDING_TICKS + 1);
	}

	private static void slidePlayerToAnchor(ServerPlayerEntity player,
			Skill skill, long now) {
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor)
				|| anchor.isRemoved()) {
			finish(player);
			return;
		}
		Vec3d toAnchor = anchor.getPos().subtract(player.getPos());
		double remaining = Math.hypot(toAnchor.x, toAnchor.z);
		if (remaining <= CHAIN_SLIDING_ARRIVAL_DISTANCE) {
			TkGun.LOGGER.info("[mechaniciancombat] gun anchor slide stopped at anchor: "
							+ "remaining={} stageTicksLeft={} player={}",
					Math.round(remaining * 1000.0D) / 1000.0D,
					Math.max(0L, skill.stageEnd - now),
					player.getName().getString());
			completeGunSliding(player, skill, anchor, now);
			return;
		}
		if (now >= skill.stageEnd) {
			completeGunSliding(player, skill, anchor, now);
			return;
		}
		int step = (int) Math.max(1L,
				Math.min(CHAIN_SLIDING_TICKS, now - skill.stageStart));
		double before = slideProgress((step - 1)
				/ (double) CHAIN_SLIDING_TICKS);
		double after = slideProgress(step
				/ (double) CHAIN_SLIDING_TICKS);
		double speed = Math.min(remaining,
				CHAIN_SLIDING_DISTANCE * (after - before));
		Vec3d velocity = player.getVelocity();
		player.setSprinting(false);
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		player.upwardSpeed = 0.0F;
		player.setVelocity(skill.slideDirX * speed, velocity.y,
				skill.slideDirZ * speed);
		player.velocityModified = true;
	}

	private static void completeGunSliding(ServerPlayerEntity player,
			Skill skill, SoulAnchorEntity anchor, long now) {
		Vec3d velocity = player.getVelocity();
		player.setVelocity(0.0D, velocity.y, 0.0D);
		player.velocityModified = true;
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		anchor.retrieve(player);
		resumeAccelerationCountdown(player, skill, now);
		if (skill.returnToRunAfterSlide) {
			if (TkGun.holdingGun(player)) {
				player.setSprinting(true);
			}
			beginStage(player, skill, Stage.GUN_SLIDING_TO_RUN,
					CHAIN_SLIDING_TO_RUN_TICKS);
		} else {
			beginStage(player, skill, Stage.GUN_SLIDING_TO_IDLE,
					CHAIN_SLIDING_TO_IDLE_TICKS);
		}
	}

	private static double slideProgress(double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		return clamped * (2.0D - clamped);
	}

	/**
	 * The returning anchor may disappear before the authored transition ends.
	 * Keep the transition phase alive for its full duration so the rig can
	 * finish chain_sliding_to_idle instead of snapping directly to idle.
	 */
	private static void tickSlidingToIdle(ServerPlayerEntity player,
			Skill skill, long now) {
		if (now < skill.stageEnd) {
			return;
		}
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor && !anchor.isRemoved()) {
			anchor.discard();
		}
		finish(player);
	}

	/** Finish the authored run-facing transition before returning input control. */
	private static void tickSlidingToRun(ServerPlayerEntity player,
			Skill skill, long now) {
		applySlidingToRunMovement(player, skill, now);
		if (now < skill.stageEnd) {
			return;
		}
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor && !anchor.isRemoved()) {
			anchor.discard();
		}
		finish(player);
	}

	/**
	 * Reintroduce movement input over the five-tick authored transition. The
	 * speed ramps from 20% to the pre-slide sprint speed so the player does not
	 * snap from the slide's zeroed velocity directly into full sprint.
	 */
	private static void applySlidingToRunMovement(ServerPlayerEntity player,
			Skill skill, long now) {
		if (TkGun.holdingGun(player)) {
			player.setSprinting(true);
		}
		Vec3d direction = new Vec3d(
				player.sidewaysSpeed, 0.0D, player.forwardSpeed)
				.rotateY(-(float) Math.toRadians(player.getYaw()));
		if (direction.lengthSquared() < 1.0E-4D) {
			direction = new Vec3d(
					skill.slideDirX, 0.0D, skill.slideDirZ);
		}
		if (direction.lengthSquared() < 1.0E-6D) {
			return;
		}
		direction = direction.normalize();
		long elapsed = Math.max(1L, Math.min(
				CHAIN_SLIDING_TO_RUN_TICKS,
				now - skill.stageStart + 1L));
		double progress = elapsed / (double) CHAIN_SLIDING_TO_RUN_TICKS;
		double eased = progress * progress * (3.0D - 2.0D * progress);
		double speed = Math.max(0.28D, skill.slideResumeSpeed)
				* (0.2D + 0.8D * eased);
		Vec3d velocity = player.getVelocity();
		player.setVelocity(direction.x * speed, velocity.y,
				direction.z * speed);
		player.velocityModified = true;
	}

	private static void pauseAccelerationCountdown(ServerPlayerEntity player,
			Skill skill) {
		if (!skill.returnToRunAfterSlide || skill.slideDashPaused) {
			return;
		}
		long now = player.getWorld().getTime();
		TkGunServer.PState state = TkGunServer.state(player);
		skill.slidePausedDashTicks = Math.max(0L, state.dashUntil - now);
		skill.slidePausedSpeedBoostTicks =
				Math.max(0L, state.speedBoostUntil - now);
		if (skill.slidePausedDashTicks > 0L) {
			state.dashUntil = now + ACCELERATION_PAUSE_HOLD_TICKS;
		}
		if (skill.slidePausedSpeedBoostTicks > 0L) {
			state.speedBoostUntil = now + ACCELERATION_PAUSE_HOLD_TICKS;
		}
		skill.slideDashPaused = true;
		TkGunServer.rebroadcast(player);
	}

	private static void resumeAccelerationCountdown(ServerPlayerEntity player,
			Skill skill, long now) {
		if (!skill.slideDashPaused) {
			return;
		}
		skill.slideDashPaused = false;
		TkGunServer.PState state = TkGunServer.state(player);
		state.dashUntil = now + skill.slidePausedDashTicks;
		state.speedBoostUntil =
				now + skill.slidePausedSpeedBoostTicks;
		TkGunServer.rebroadcast(player);
	}

	private static void observeRecall(ServerPlayerEntity player, Skill skill,
			long now) {
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor)
				|| anchor.isRemoved() || now >= skill.stageEnd) {
			if (entity instanceof SoulAnchorEntity anchor2) {
				anchor2.discard();
			}
			finish(player);
		}
	}

	private static boolean isDragging(Stage stage) {
		return stage == Stage.PLAYER_DRAG
				|| stage == Stage.ENTITY_DRAG
				|| stage == Stage.GUN_ENTITY_DRAG;
	}

	private static boolean canSneakRecall(Stage stage) {
		return stage == Stage.ANCHOR_FLYING
				|| stage == Stage.GUN_FLYING
				|| isDragging(stage);
	}

	private static void cancelDrag(ServerPlayerEntity player, Skill skill) {
		clearReadyTarget(player, skill);
		Entity entity = player.getServerWorld().getEntityById(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor) {
			anchor.cancelRetrieve(player);
		}
		Vec3d velocity = player.getVelocity();
		player.setVelocity(0.0D, velocity.y, 0.0D);
		player.velocityModified = true;
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		beginStage(player, skill, Stage.GUN_RECALL,
				recallTicks(skill));
	}

	private static void selectTarget(ServerPlayerEntity player, Skill skill) {
		LivingEntity target = findAimTarget(player);
		if (target != null && target.getId() == skill.targetId) {
			return;
		}
		clearReadyTarget(player, skill);
		if (target == null) {
			skill.targetId = -1;
			return;
		}
		skill.targetId = target.getId();
		skill.targetHighlighted = true;
		broadcastStatus(player, skill.targetId,
				SoulAnchorStatusPayload.READY_TARGET,
				CLIENT_HOLD_TIMEOUT_TICKS, SoulAnchorMarks.COLOR);
	}

	private static LivingEntity findAimTarget(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d start = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();
		Vec3d end = start.add(look.multiply(TARGET_RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(
				start, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		EntityHitResult hit = ProjectileUtil.raycast(
				player, start, end,
				player.getBoundingBox().stretch(look.multiply(TARGET_RANGE))
						.expand(1.0D),
				entity -> entity instanceof LivingEntity
						&& entity != player && entity.isAlive()
						&& !entity.isSpectator() && entity.canHit()
						&& CombatTargeting.isTargetable(entity),
				TARGET_RANGE * TARGET_RANGE);
		double blockDistance = block.getType() == HitResult.Type.MISS
				? TARGET_RANGE : start.distanceTo(block.getPos());
		if (hit != null && hit.getEntity() instanceof LivingEntity direct
				&& start.distanceTo(hit.getPos()) <= blockDistance + 0.05D) {
			return direct;
		}
		LivingEntity nearest = null;
		double bestAngle = Math.cos(Math.toRadians(7.5D));
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity living : world.getEntitiesByClass(
				LivingEntity.class,
				player.getBoundingBox().expand(TARGET_RANGE),
				entity -> entity != player && entity.isAlive()
						&& !entity.isSpectator() && entity.canHit()
						&& CombatTargeting.isTargetable(entity))) {
			Vec3d center = living.getBoundingBox().getCenter();
			Vec3d delta = center.subtract(start);
			double distance = delta.length();
			if (distance < 1.0E-4D || distance > TARGET_RANGE
					|| distance > blockDistance + 0.05D) {
				continue;
			}
			double cosine = delta.multiply(1.0D / distance).dotProduct(look);
			if (cosine < bestAngle
					|| cosine == bestAngle && distance >= bestDistance) {
				continue;
			}
			bestAngle = cosine;
			bestDistance = distance;
			nearest = living;
		}
		return nearest;
	}

	private static void clearReadyTarget(ServerPlayerEntity player,
			Skill skill) {
		if (skill.targetId < 0 || !skill.targetHighlighted) {
			return;
		}
		broadcastStatus(player, skill.targetId,
				SoulAnchorStatusPayload.CLEAR_READY_TARGET, 0,
				SoulAnchorMarks.COLOR);
		skill.targetHighlighted = false;
	}

	private static void beginStage(ServerPlayerEntity player, Skill skill,
			Stage stage, int duration) {
		skill.stage = stage;
		skill.stageStart = player.getWorld().getTime();
		skill.stageEnd = skill.stageStart + duration;
		int phase = switch (stage) {
			case PLAYER_DRAG -> SoulAnchorSkillPayload.DRAG;
			case ENTITY_DRAG -> SoulAnchorSkillPayload.DRAG_ENTITY;
			case GUN_ENTITY_DRAG -> SoulAnchorSkillPayload.GUN_DRAG_ENTITY;
			case GUN_RECALL -> skill.gun
					? SoulAnchorSkillPayload.GUN_RECALL
					: SoulAnchorSkillPayload.RECALL;
			case GUN_SLIDING -> SoulAnchorSkillPayload.GUN_SLIDING;
			case GUN_SLIDING_TO_IDLE ->
					SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE;
			case GUN_SLIDING_TO_RUN ->
					SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN;
			default -> skill.gun ? SoulAnchorSkillPayload.GUN_THROW
					: SoulAnchorSkillPayload.THROW;
		};
		// Keep the last authored frame alive until the authoritative END packet
		// arrives; it can cross a tick boundary after the server-side timeout.
		syncPhase(player, skill, phase, duration + 2);
	}

	private static int recallTicks(Skill skill) {
		return ENTITY_DRAG_TICKS;
	}

	private static void syncPhase(ServerPlayerEntity player, Skill skill,
			int phase, int duration) {
		skill.lastPhase = phase;
		SoulAnchorSkillPayload payload = new SoulAnchorSkillPayload(
				player.getId(), phase, duration, skill.gun);
		for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
			if (viewer.squaredDistanceTo(player) <= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static void finish(ServerPlayerEntity player) {
		Skill skill = SKILLS.get(player.getUuid());
		if (skill == null) {
			return;
		}
		resumeAccelerationCountdown(player, skill,
				player.getWorld().getTime());
		SKILLS.remove(player.getUuid());
		clearReadyTarget(player, skill);
		Entity anchor = player.getServerWorld().getEntityById(skill.anchorId);
		if (anchor instanceof SoulAnchorEntity soul && !soul.isRemoved()) {
			soul.discard();
		}
		SoulAnchorSkillPayload payload = new SoulAnchorSkillPayload(
				player.getId(), SoulAnchorSkillPayload.END, 0, skill.gun);
		for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
			if (viewer.squaredDistanceTo(player) <= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static boolean isArmorStillInstalled(ServerPlayerEntity player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		ItemStack legs = player.getEquippedStack(
				net.minecraft.entity.EquipmentSlot.LEGS);
		return ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3)
				&& ArmorUpgradeData.hasPart(legs, ArmorPartItem.Kind.T1);
	}

	private static void broadcastStatus(ServerPlayerEntity player,
			int targetEntityId, int action, int durationTicks, int color) {
		SoulAnchorStatusPayload payload = new SoulAnchorStatusPayload(
				player.getId(), targetEntityId, action, durationTicks, color);
		for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
			if (viewer.squaredDistanceTo(player) <= 64.0D * 64.0D) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}
}
