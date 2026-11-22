package com.tk.gun.client;

import com.tk.gun.net.SoulAnchorSkillPayload;

import java.util.HashMap;
import java.util.Map;

/** Per-player Soul Anchor skill animation phase. */
public final class SoulAnchorSkillClientState {
	public record Entry(int phase, long startedAt, long endsAt,
			boolean gunHeld, boolean swordSkill, boolean readyFromRun,
			long revision) {
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	/** Sword pulls retain their held tail after END until the player lands. */
	private static final Map<Integer, Boolean> POST_SWORD_DRAG = new HashMap<>();
	private static long ticks;
	private static long revisions;

	public static void apply(SoulAnchorSkillPayload payload) {
		Entry previous = ENTRIES.get(payload.playerEntityId());
		if (payload.phase() == SoulAnchorSkillPayload.END) {
			ENTRIES.remove(payload.playerEntityId());
			if (previous != null && previous.swordSkill()
					&& previous.phase() == SoulAnchorSkillPayload.DRAG) {
				POST_SWORD_DRAG.put(payload.playerEntityId(), Boolean.TRUE);
			} else {
				POST_SWORD_DRAG.remove(payload.playerEntityId());
			}
			return;
		}
		POST_SWORD_DRAG.remove(payload.playerEntityId());
		long revision = previous == null
				|| previous.phase() != payload.phase()
				? ++revisions : previous.revision();
		long startedAt = previous == null
				|| previous.phase() != payload.phase()
				? ticks : previous.startedAt();
		ENTRIES.put(payload.playerEntityId(), new Entry(
				payload.phase(), startedAt,
				ticks + payload.durationTicks(),
				payload.gunHeld(), payload.swordSkill(),
				payload.readyFromRun(), revision));
	}

	public static void tick() {
		ticks++;
		ENTRIES.entrySet().removeIf(entry ->
				entry.getValue().endsAt() <= ticks);
	}

	public static void clear(int playerId) {
		ENTRIES.remove(playerId);
		POST_SWORD_DRAG.remove(playerId);
	}

	public static void clearAll() {
		ENTRIES.clear();
		POST_SWORD_DRAG.clear();
	}

	public static Entry get(int playerId) {
		return ENTRIES.get(playerId);
	}

	public static boolean active(int playerId) {
		return ENTRIES.containsKey(playerId);
	}

	public static boolean playerDragging(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && (!entry.gunHeld() || entry.swordSkill())
				&& entry.phase() == SoulAnchorSkillPayload.DRAG;
	}

	public static boolean postSwordDragActive(int playerId) {
		return POST_SWORD_DRAG.containsKey(playerId);
	}

	public static void clearPostSwordDrag(int playerId) {
		POST_SWORD_DRAG.remove(playerId);
	}

	public static boolean dragging(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& (entry.phase() == SoulAnchorSkillPayload.DRAG
						|| entry.phase() == SoulAnchorSkillPayload.DRAG_ENTITY
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_DRAG_ENTITY);
	}

	public static boolean ready(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& entry.phase() == SoulAnchorSkillPayload.READY;
	}

	/** Every active anchor phase used while the gun is in hand keeps the view coupled. */
	public static boolean gunSkillCameraCoupled(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.gunHeld();
	}

	public static boolean anchorStall(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& entry.phase() == SoulAnchorSkillPayload.ANCHOR_STALL;
	}

	public static boolean gunSliding(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& entry.phase() == SoulAnchorSkillPayload.GUN_SLIDING;
	}

	public static boolean gunSlidingToIdle(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE;
	}

	public static boolean gunSlidingToRun(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN;
	}

	/** Chain-slide motion owns movement without entering the dodge state machine. */
	public static boolean chainSlideLocksDodge(int playerId) {
		return gunSliding(playerId) || gunSlidingToIdle(playerId)
				|| gunSlidingToRun(playerId);
	}

	/** Hold-gun phases that require input only for their own movement controller. */
	public static boolean gunAnchorLocksMovement(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null || !entry.gunHeld()) {
			return false;
		}
		return entry.phase() == SoulAnchorSkillPayload.GUN_SLIDING
				|| entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE
				|| entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN;
	}

	/** Camera stays free while the player or hooked target owns the movement. */
	public static boolean cameraDecoupledDuringAnchor(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return false;
		}
		return entry.phase() == SoulAnchorSkillPayload.ANCHOR_STALL
				|| entry.phase() == SoulAnchorSkillPayload.GUN_SLIDING
				|| entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE
				|| entry.phase()
						== SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN
				|| entry.phase()
						== SoulAnchorSkillPayload.GUN_DRAG_ENTITY
				|| entry.phase() == SoulAnchorSkillPayload.DRAG
				|| entry.phase() == SoulAnchorSkillPayload.DRAG_ENTITY;
	}

	public static boolean nonGunAnchorActive(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && (!entry.gunHeld() || entry.swordSkill());
	}

	public static boolean firstPersonActive(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return false;
		}
		if (entry.phase() == SoulAnchorSkillPayload.READY) {
			return true;
		}
		boolean throwing =
				entry.phase() == SoulAnchorSkillPayload.THROW
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_THROW;
		return throwing && elapsedTicks(playerId) <= 5L;
	}

	public static boolean firstPersonThrowing(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& (entry.phase() == SoulAnchorSkillPayload.THROW
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_THROW);
	}

	public static double firstPersonSeconds(int playerId) {
		return firstPersonSeconds(playerId, 0.0F);
	}

	public static double firstPersonSeconds(int playerId, float tickDelta) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return 0.0D;
		}
		double elapsed = Math.max(0.0D,
				ticks - entry.startedAt()
						+ net.minecraft.util.math.MathHelper.clamp(
								tickDelta, 0.0F, 1.0F)) / 20.0D;
		return entry.phase() == SoulAnchorSkillPayload.READY
				? Math.min(0.125D, elapsed)
				: Math.min(0.5D, elapsed);
	}

	public static boolean recalling(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& (entry.phase() == SoulAnchorSkillPayload.RECALL
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_RECALL);
	}

	public static boolean suppressSneakPose(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null;
	}

	public static boolean cancelable(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null
				&& (entry.phase() == SoulAnchorSkillPayload.THROW
						|| entry.phase()
								== SoulAnchorSkillPayload.ANCHOR_STALL
						|| entry.phase() == SoulAnchorSkillPayload.DRAG
						|| entry.phase() == SoulAnchorSkillPayload.DRAG_ENTITY
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_THROW
						|| entry.phase()
								== SoulAnchorSkillPayload.ANCHOR_FLIGHT
						|| entry.phase()
								== SoulAnchorSkillPayload.GUN_DRAG_ENTITY);
	}

	public static long elapsedTicks(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? Long.MAX_VALUE : ticks - entry.startedAt();
	}

	public static long startedAt(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? -1L : entry.startedAt();
	}

	public static String animationName(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return null;
		}
		return switch (entry.phase()) {
			case SoulAnchorSkillPayload.READY -> entry.swordSkill()
					? entry.readyFromRun()
							? "chain_run_ready_sword"
							: "chain_ready_sword"
					: entry.gunHeld() ? "chain_ready_gun_upper" : "chain_ready";
			case SoulAnchorSkillPayload.RECALL -> "chain_drag_entity";
			case SoulAnchorSkillPayload.GUN_RECALL ->
					entry.swordSkill() ? "chain_drag_entity"
							: "chain_throw_gun_drag_upper";
			case SoulAnchorSkillPayload.DRAG -> entry.swordSkill()
					? "chain_drag_sword" : "chain_drag";
			case SoulAnchorSkillPayload.DRAG_ENTITY -> "chain_drag_entity";
			case SoulAnchorSkillPayload.GUN_THROW -> entry.swordSkill()
					? "chain_throw_sword"
					: entry.gunHeld() ? "chain_throw_gun_upper" : "chain_throw";
			case SoulAnchorSkillPayload.ANCHOR_FLIGHT -> entry.swordSkill()
					? "chain_throw_sword"
					: entry.gunHeld() ? "chain_throw_gun_upper" : "chain_throw";
			case SoulAnchorSkillPayload.ANCHOR_STALL -> entry.swordSkill()
					? "chain_throw_sword"
					: entry.gunHeld() ? "chain_throw_gun_upper" : "chain_throw";
			case SoulAnchorSkillPayload.GUN_DRAG_ENTITY ->
					"chain_throw_gun_drag_upper";
			case SoulAnchorSkillPayload.GUN_SLIDING ->
					"chain_sliding_upper";
			case SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE ->
					"chain_sliding_to_idle_upper";
			case SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN ->
					"chain_sliding_to_run_upper";
			default -> entry.swordSkill() ? "chain_throw_sword" : "chain_throw";
		};
	}

	private SoulAnchorSkillClientState() {
	}
}
