package com.tk.gun.client;

import com.tk.gun.CombatRollHook;
import com.tk.gun.GunStateMachine;
import com.tk.gun.TkGun;
import com.tk.gun.net.GunStatePayload;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端镜像：每个玩家的动作状态 + 已经播了多少 tick + **这一发是弹夹里第几发**。
 *
 * <p>2026-10-13 机械师新素材：射击动画按"弹夹里第几发"分段（Focus_shooting / 2..7），
 * 所以状态包里带了 `shotIndex`；过热也有专用动画（Overheat）了，不再"保持当前姿势"。
 */
public final class GunClientState {
	public static final class Entry {
		public TkGun.State state = TkGun.State.IDLE;
		public int elapsed;
		public int total;
		public int heat;
		/** 弹夹里第几发（1..10）：1..3 用 extra，4..10 用 Focus_shooting[2..7]。 */
		public int shotIndex = 1;
		/** 弹夹里**已经打掉**几发（0..MAG_CAPACITY）：HUD 显示用（剩余 = MAG_CAPACITY - mag）。 */
		public int mag;
		/** Per-gun capacities mirrored from the authoritative state packet. */
		public int hipMagazineCapacity = TkGun.MAG_CAPACITY_HIP;
		public int focusMagazineCapacity = TkGun.MAG_CAPACITY_FOCUS;
		public int maxShotIndex = TkGun.SHOT_ANIM_MAX;
		/** Authoritative normal-airborne hold flag from the server. */
		public boolean airborneIdle;
		/** 服务端正在执行主动装填；HUD 据此显示“装填”。 */
		public boolean reloading;
		/**
		 * **专注（架枪）状态**（2026-10-15；这一位原来叫 `over` = 强化/过载）。
		 * true = 扣扳机播 `Focus_shooting[2..7]`（抬着炮精瞄 + 仰角档）；
		 * false = 播 `stand_shooting`（腰射 / 行走射击的叠层动画）。
		 */
		public boolean focus;
	/**
	 * **翻滚后的加速冲刺窗口里**（服务端 `dashUntil` 未到，2026-10-17 加）—— 客户端据此**禁止右键瞄准**
	 * （用户："疾跑与翻滚加速状态下不允许进行右键瞄准"）。
	 */
	public boolean dashing;
		public boolean dashMeleeActive;
		public boolean meleeReturning;
		public boolean dashMeleeReturn;
		public boolean dashMeleeReturnRun;
		public boolean meleeHoldOver;
		public String lastMeleeAnimation;
		public int meleeGraceUntil;
		/** Local melee lifetime ended; wait briefly for the authoritative server state. */
		public int meleeAwaitUntil;
		public String magAmmo = "";
	/**
	 * 翻滚 / 滑铲结束后继续屏蔽右键瞄准的客户端截止 tick（和服务端 `aimLockUntil` 对齐）。
	 * 注意这只是**右键输入锁**，不是 Alt 闪避冷却。
	 */
	public int aimLockUntil;
	/** Authoritative remaining ticks of the full overheat cooling lock. */
	public int heatLockUntil;
		/**
		 * 服务端的**射击序号**（每入队一发 +1）。
		 *
		 * <p>2026-10-17 加：客户端靠"这个数变了"来决定**重播这一段射击动画**。原来靠"包刚到的
		 * 那一两 tick（`elapsed <= 1`）"，点按一快、两包挨在一起时就会漏掉一次重播 ——
		 * 用户报的"点按过快射击动画丢失"就是这个。
		 */
		public int shotSeq;
		/**
		 * FIRE 状态结束之后的"余韵"截止 tick：到这之前都算还在打枪的姿势里。
		 * 存在的理由（2026-10-13 实测）：服务端的 FIRE(24 tick) 和紧随其后的 AIM_HOLD
		 * 是相隔 1~2 tick 到的（包要在下一个客户端 tick 才处理），中间那一下客户端会掉回 idle，
		 * 而 AIM_HOLD 是 CONTINUE（保持当前姿势）→ 结果"保持"保成了 idle（用户看到的就是"收枪动作"）。
		 */
		public int holdUntil;
		/**
		 * 快速蹲射过门动画的尾帧保持名。
		 *
		 * <p>AIM_SLIDE / AIM_ROLL 只有几 tick，客户端镜像可能先过期回 IDLE，
		 * 而服务端的 AIM_HOLD 包晚一拍到达。这里跨过这个边界保留同一段
		 * sliding_fast_ready / rolling_fast_ready，直到首次射击或切入别的动作，
		 * 避免 GeckoLib 在 Focus_hold 与快速预备动画之间来回取样造成前段重播。
		 */
		public String readyHoldAnimation;
		/** 本地先按下的翻滚：服务端状态包到达前先锁一帧 ROLL，避免闪一帧 run。 */
		public boolean predictedRoll;
		public int predictedRollTicks;
		/** 客户端镜像的循环意图：true = 下一次 Alt 应进入滑铲。 */
		public boolean nextDodgeSlide;
		/** Client-side deadline of the roll movement bonus, used only for Alt prediction. */
		public int dashUntil;
		/** Server-authoritative native roll cooldown, mirrored onto the client tick clock. */
		public int nativeRollReadyAt;
		/** 滑铲结束后客户端本地屏蔽 Alt 的截止 tick（与服务器 1 秒锁对齐）。 */
		public int localDodgeLockUntil;
		/** Keep the final skill-hit pose until the authoritative follow-up arrives. */
		public int fortressSkillAwaitUntil;

		public boolean active() {
			return total > 0 && elapsed < total;
		}
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	/** 客户端 tick 计数（只给 {@link Entry#holdUntil} 用）。 */
	private static int ticks;
	/** FIRE 状态结束后还保持射击尾帧多少 tick（等下一个状态包）。 */
	public static final int FIRE_HOLD_GRACE = 10;

	public static void apply(GunStatePayload payload) {
		Entry e = ENTRIES.computeIfAbsent(payload.playerId(), k -> new Entry());
		TkGun.State newState = TkGun.State.byId(payload.state());
		TkGun.State previousState = e.state;
		boolean newShotFrame = e.shotSeq != payload.shotSeq();
		boolean sameFrame = e.state == newState && !newShotFrame;
		if (newState == TkGun.State.MELEE) {
			e.meleeGraceUntil = 0;
		} else if (previousState == TkGun.State.MELEE) {
			e.meleeGraceUntil = Math.max(e.meleeGraceUntil, ticks + 2);
		}
		if (!sameFrame) {
			e.state = newState;
			e.total = payload.ticks();
			e.elapsed = 0;
			e.shotSeq = payload.shotSeq();
		}
		e.heat = payload.heat();
		e.hipMagazineCapacity = Math.max(1, payload.hipMagazineCapacity());
		e.focusMagazineCapacity = Math.max(1, payload.focusMagazineCapacity());
		e.maxShotIndex = Math.max(1, payload.maxShotIndex());
		e.airborneIdle = payload.airborneIdle();
		e.dashMeleeActive = payload.dashMelee();
		e.meleeReturning = payload.meleeReturning();
		e.dashMeleeReturn = payload.dashMeleeReturn();
		e.dashMeleeReturnRun = payload.dashMeleeReturnRun();
		e.meleeHoldOver = payload.meleeHoldOver();
		if (newState != TkGun.State.MELEE || payload.meleeReturning()) {
			e.meleeAwaitUntil = 0;
		}
		if (newState != TkGun.State.FORTRESS_SKILL_HIT) {
			e.fortressSkillAwaitUntil = 0;
		}
		e.shotIndex = Math.max(1, Math.min(e.maxShotIndex, payload.shotIndex()));
		e.mag = Math.max(0,
				Math.min(payload.focus() ? e.focusMagazineCapacity : e.hipMagazineCapacity, payload.mag()));
		e.reloading = payload.reloading();
		e.focus = payload.focus();
		if (e.focus) {
			e.localDodgeLockUntil = 0;
			e.nextDodgeSlide = false;
		}
		if (newShotFrame && e.state == TkGun.State.AIM_HOLD) {
			e.readyHoldAnimation = null;
		}
		if (e.state == TkGun.State.AIM_SLIDE) {
			e.readyHoldAnimation = "sliding_fast_ready";
		} else if (e.state == TkGun.State.AIM_ROLL) {
			e.readyHoldAnimation = "rolling_fast_ready";
		} else if (e.state != TkGun.State.IDLE && e.state != TkGun.State.AIM_HOLD) {
			e.readyHoldAnimation = null;
		}
		if (!e.focus && e.state == TkGun.State.IDLE) {
			e.readyHoldAnimation = null;
		}
		boolean enteredRoll = newState == TkGun.State.ROLL && previousState != TkGun.State.ROLL;
		e.dashing = payload.dashing();
		e.dashUntil = payload.dashTicks() > 0 ? ticks + payload.dashTicks() : 0;
		e.magAmmo = payload.magAmmo();
		// 服务端每包都带权威剩余时间；允许快速蹲射结束时用 0 立即解锁。
		e.aimLockUntil = ticks + payload.aimLockTicks();
		e.heatLockUntil = payload.heatLockTicks() > 0
				? ticks + payload.heatLockTicks() : 0;
		e.nativeRollReadyAt = !e.focus && payload.rollCooldownTicks() > 0
				? ticks + payload.rollCooldownTicks() : 0;
		if (newState == TkGun.State.SKILL_JUMP
				|| previousState == TkGun.State.SKILL_JUMP
						&& newState == TkGun.State.IDLE) {
			e.localDodgeLockUntil = 0;
			e.nativeRollReadyAt = 0;
			e.nextDodgeSlide = false;
		}
		e.predictedRoll = false;
		e.predictedRollTicks = 0;
		if (e.state == TkGun.State.ROLL || e.state == TkGun.State.ROLL_TO_IDLE) {
			e.nextDodgeSlide = true;
		} else if (e.state == TkGun.State.SLIDE || e.state == TkGun.State.SLIDE_READY) {
			e.nextDodgeSlide = false;
			e.localDodgeLockUntil = ticks + TkGun.SLIDE_TICKS + 20;
		}
		boolean movementInput = TkGunClient.hasMovementInput(payload.playerId());
		if (e.state == TkGun.State.ROLL) {
			// 翻滚/翻滚回待机的位移不是玩家输入；别让 MOVING_HOLD_TICKS 把它续成 run。
			TkGunClient.clearMovingHold(payload.playerId());
		} else if (e.state == TkGun.State.ROLL_TO_IDLE) {
			// If the player has already resumed movement, seed the movement
			// sampler from the keys now so there is no one-frame idle_lower.
			if (movementInput) {
				TkGunClient.primeMovementFromInput(payload.playerId());
			} else {
				TkGunClient.clearMovingHold(payload.playerId());
			}
		} else if (previousState == TkGun.State.ROLL && e.state == TkGun.State.IDLE
				&& movementInput) {
			TkGunClient.primeMovementFromInput(payload.playerId());
		}
		if (e.state != TkGun.State.FIRE) {
			e.holdUntil = 0;
		}
	}

	public static Entry get(int entityId) {
		return ENTRIES.get(entityId);
	}

	/** Extend the client-side melee mirror while the server pauses its timeline. */
	public static void extendMelee(int entityId, int ticks) {
		Entry entry = ENTRIES.get(entityId);
		if (entry != null && entry.state == TkGun.State.MELEE && ticks > 0) {
			entry.total += ticks;
		}
	}

	/** Whether an Alt press may start a real roll or the roll-to-slide follow-up. */
	public static boolean canTriggerDodge(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return false;
		}
		if (e.state == TkGun.State.AIM || e.state == TkGun.State.AIM_FAST
				|| e.state == TkGun.State.AIM_ROLL || e.state == TkGun.State.AIM_SLIDE
				|| e.state == TkGun.State.ROLL || e.state == TkGun.State.ROLL_TO_IDLE
				|| e.state == TkGun.State.SLIDE || e.state == TkGun.State.SLIDE_READY
				|| e.state == TkGun.State.SKILL_JUMP
				|| e.state == TkGun.State.FORTRESS_SKILL_LOAD
				|| e.state == TkGun.State.FORTRESS_SKILL_GATHER
				|| e.state == TkGun.State.FORTRESS_SKILL_READY_HIT) {
			return false;
		}
		if (e.state == TkGun.State.FORTRESS_SKILL_HIT
				&& e.elapsed < TkGun.FORTRESS_SKILL_HIT_DODGE_TICK) {
			return false;
		}
		if (e.state == TkGun.State.MELEE && !dashMeleeRecovery(e)) {
			return false;
		}
		if (ticks < e.localDodgeLockUntil) {
			return false;
		}
		if (dashMeleeRecovery(e)) {
			return true;
		}
		boolean slideWindowOpen = e.nextDodgeSlide && !e.focus && ticks < e.dashUntil;
		return slideWindowOpen || ticks >= e.nativeRollReadyAt;
	}

	/** 本地按下翻滚键时先锁一帧 ROLL；服务端状态包到达后会覆盖这份预测。 */
	public static void beginLocalRoll(int entityId) {
		Entry e = ENTRIES.computeIfAbsent(entityId, k -> new Entry());
		// 专注状态等待服务端权威 ROLL 包，避免被冷却拒绝时先播放一段假翻滚。
		if (e.state == TkGun.State.AIM || e.state == TkGun.State.AIM_FAST
				|| e.state == TkGun.State.AIM_ROLL || e.state == TkGun.State.AIM_SLIDE) {
			return;
		}
		if (e.state == TkGun.State.ROLL || e.state == TkGun.State.ROLL_TO_IDLE
				|| e.state == TkGun.State.SLIDE || e.state == TkGun.State.SLIDE_READY
				|| e.state == TkGun.State.SKILL_JUMP
				|| e.state == TkGun.State.FORTRESS_SKILL_LOAD
				|| e.state == TkGun.State.FORTRESS_SKILL_GATHER
				|| e.state == TkGun.State.FORTRESS_SKILL_READY_HIT) {
			return;
		}
		if (e.state == TkGun.State.FORTRESS_SKILL_HIT
				&& e.elapsed < TkGun.FORTRESS_SKILL_HIT_DODGE_TICK) {
			return;
		}
		if (e.state == TkGun.State.MELEE && !dashMeleeRecovery(e)) {
			return;
		}
		// Alt enters slide only while the roll movement bonus is still active.
		boolean slideWindowOpen = e.nextDodgeSlide && !e.focus && ticks < e.dashUntil;
		boolean dashRecovery = dashMeleeRecovery(e);
		if (slideWindowOpen || ticks < e.localDodgeLockUntil
				|| (!dashRecovery && ticks < e.nativeRollReadyAt)) {
			return;
		}
		e.nextDodgeSlide = false;
		e.dashing = false;
		e.dashMeleeActive = false;
		e.meleeReturning = false;
		e.dashMeleeReturn = false;
		e.dashMeleeReturnRun = false;
		e.meleeHoldOver = false;
		e.dashUntil = 0;
		e.state = TkGun.State.ROLL;
		e.total = CombatRollHook.ROLL_STATE_TICKS;
		e.elapsed = 0;
		e.predictedRoll = true;
		e.predictedRollTicks = 0;
	}

	public static void tickAll() {
		ticks++;
		ENTRIES.forEach((entityId, e) -> {
			if (e.active()) {
				if (!(e.state == TkGun.State.MELEE
						&& com.tk.gun.client.MeleeImpactClientState
								.freezesAnimation(entityId))) {
					e.elapsed++;
				}
				if (e.predictedRoll && ++e.predictedRollTicks > 4) {
					// 服务端迟迟没有回 ROLL（例如闪避冷却拒绝）：撤销预测，避免模型空滚。
					e.predictedRoll = false;
					if (e.state == TkGun.State.ROLL) {
						e.state = TkGun.State.IDLE;
						e.total = 0;
						e.elapsed = 0;
					}
				}
				if (e.state == TkGun.State.FORTRESS_SKILL_LOAD && !e.active()) {
					e.state = TkGun.State.FORTRESS_SKILL_GATHER;
					e.total = TkGun.FORTRESS_SKILL_GATHER_TICKS;
					e.elapsed = 0;
				}
				if (e.state == TkGun.State.FIRE && !e.active()) {
					// ⚠️ 必须在"刚刚过期"这一 tick 就把余韵挂上：晚一 tick 的话，
					//    那一帧 pick() 会掉进 idle 兜底 → 后面 AIM_HOLD 的 CONTINUE 保住的就成了 idle
					//    （2026-10-13 实测过一次，日志里能看到打完紧跟一行 `rig -> idle`）。
					e.holdUntil = ticks + FIRE_HOLD_GRACE;
				}
			} else if (e.state == TkGun.State.FORTRESS_SKILL_GATHER) {
				e.state = TkGun.State.FORTRESS_SKILL_READY_HIT;
				e.total = TkGun.FORTRESS_SKILL_READY_HIT_TICKS;
				e.elapsed = 0;
			} else if (e.state == TkGun.State.FORTRESS_SKILL_READY_HIT) {
				e.state = TkGun.State.FORTRESS_SKILL_HIT;
				e.total = TkGun.FORTRESS_SKILL_HIT_TICKS;
				e.elapsed = 0;
			} else if (e.state == TkGun.State.FORTRESS_SKILL_HIT) {
				if (e.fortressSkillAwaitUntil == 0) {
					e.fortressSkillAwaitUntil = ticks + 6;
				}
				if (ticks >= e.fortressSkillAwaitUntil) {
					e.state = TkGun.State.IDLE;
					e.total = 0;
					e.elapsed = 0;
					e.fortressSkillAwaitUntil = 0;
				}
			} else if (e.state == TkGun.State.FIRE) {
				e.state = TkGun.State.IDLE;
				e.total = 0;
				e.elapsed = 0;
			} else if (e.state == TkGun.State.MELEE) {
				// Server packets own the attack-to-recovery handoff. Keep the
				// authored final pose until that packet arrives rather than
				// exposing idle_upper for one or more local ticks.
				if (e.meleeAwaitUntil == 0) {
					e.meleeAwaitUntil = ticks + 6;
					e.meleeGraceUntil = ticks + 6;
				}
				if (ticks >= e.meleeAwaitUntil) {
					e.state = TkGun.State.IDLE;
					e.total = 0;
					e.elapsed = 0;
					e.meleeAwaitUntil = 0;
				}
			} else if (e.state != TkGun.State.IDLE) {
				e.state = TkGun.State.IDLE;
				e.total = 0;
				e.elapsed = 0;
			}
		});
	}

	/** 刚打完一发的"余韵"里吗（这种时候要保持射击尾帧，不要掉回 idle）。 */
	public static boolean holdingShotFire(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && ticks < e.holdUntil;
	}

	public static void forget(int entityId) {
		ENTRIES.remove(entityId);
	}

	/**
	 * 切枪时清掉动作/镜头瞬时状态，但保留弹匣、弹药种类和热量。
	 * 重新持枪时服务端会再广播一次权威值。
	 */
	public static void onGunReleased(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return;
		}
		e.state = TkGun.State.IDLE;
		e.total = 0;
		e.elapsed = 0;
		e.focus = false;
		e.dashing = false;
		e.dashMeleeActive = false;
		e.meleeReturning = false;
		e.dashMeleeReturn = false;
		e.dashMeleeReturnRun = false;
		e.meleeHoldOver = false;
		e.lastMeleeAnimation = null;
		e.meleeGraceUntil = 0;
		e.meleeAwaitUntil = 0;
		e.fortressSkillAwaitUntil = 0;
		e.reloading = false;
		e.aimLockUntil = 0;
		e.nativeRollReadyAt = 0;
		e.predictedRoll = false;
		e.predictedRollTicks = 0;
		e.holdUntil = 0;
		e.readyHoldAnimation = null;
		e.nextDodgeSlide = false;
		e.localDodgeLockUntil = 0;
	}

	/** 状态 -> 要播的动画名（null = 这一帧交给别的 mod 的层 / 别的逻辑）。 */
	public static String animationName(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null || !e.active()) {
			return null;
		}
		if (e.airborneIdle) {
			return "idle";
		}
		switch (e.state) {
			case FORTRESS_SKILL_LOAD:
				return "skill_load";
			case FORTRESS_SKILL_GATHER:
				return "gather";
			case FORTRESS_SKILL_HIT:
				return "hit";
			case FORTRESS_SKILL_READY_HIT:
				return "ready_hit";
			case SKILL_JUMP:
				return skillJumpAnimation(entityId);
			case IDLE:
				return "idle";                       // 待机（作者新做的 idle）
			case AIM:
			case AIM_FAST:
			case AIM_SLIDE:
				return "Focus_ready";                // 架枪（倍速按状态分三档，见 speedFor）
			case AIM_ROLL:
				return "rolling_fast_ready";         // 翻滚后的快速蹲射过门
			case AIM_HOLD:
				// 打完保持：客户端其实用 CONTINUE 什么都不换（就停在射击动画尾帧），
				// 这里返回名字只是为了让日志/演示截图上的状态名好看、可读。
				return TkGun.shotAnimation(e.shotIndex, e.maxShotIndex);
			case FIRE:
				// 按"弹夹里第几发"分段（`Focus_shooting` / `2..7`）。仰角后缀由 BowgunRig.pick 按
				// "这一枪要打多高"补上（本地玩家看落点方向，别人看 entity pitch）。
				return TkGun.shotAnimation(e.shotIndex, e.maxShotIndex);
			case OVERHEAT:
				return "Overheat";
			case INSPECT:
				return "Focus_over_shooting";        // 过载连射那段（只在 /tkgun 调试里看到）
			case SLIDE:
				return "sliding";
			case SLIDE_READY:
				// ⚠️ 新素材（BG261013 v2）把 `sliding_ready` 删了，换成同样 0.667 秒的 `fast_ready`
				return "fast_ready";
			case OVER:
				return "Focus_over";                 // 进入强化：把炮抬到肩上（1 秒）
			case RELOAD:
				return "reload";                     // 新素材的真·换弹动画（1.125 秒）
			case ROLL:
				return CombatRollHook.isInstalled()
						? null : "roll";
			case ROLL_TO_IDLE:
				return "roll_to_idle";
			case MELEE:
				if (e.meleeReturning) {
					return TkGun.meleeReturnAnimation(e.shotIndex);
				}
				if (e.meleeHoldOver) {
					return "ATK_hold_over";
				}
				return TkGun.meleeAnimation(e.shotIndex);
			default:
				return null;
		}
	}

	/**
	 * 动画倍速。**架枪三档**和 {@link TkGun#AIM_TICKS_NORMAL}/{@link TkGun#AIM_TICKS_FAST}/
	 * {@link TkGun#AIM_TICKS_SLIDE} 是同一套数：普通右键慢（1.2s）、滑铲后/蹲着快（0.5s）、
	 * 滑铲里抢架枪最快（0.35s）。
	 */
	/** The authored jump clip covers ascent; fall covers the descent. */
	public static String skillJumpAnimation(int entityId) {
		net.minecraft.client.MinecraftClient client =
				net.minecraft.client.MinecraftClient.getInstance();
		if (client.world == null) {
			return "jump";
		}
		net.minecraft.entity.Entity entity = client.world.getEntityById(entityId);
		return entity != null && entity.getVelocity().y > 0.01D ? "jump" : "fall";
	}

	public static double speedFor(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return 1.0D;
		}
		// 短过门状态在客户端到点后先保留原倍速一帧，等服务端 AIM_HOLD 接替；
		// 否则会短暂切回 1.0 倍并重新取样同一段动画。
		if (e.readyHoldAnimation != null
				&& (e.state == TkGun.State.AIM_SLIDE || e.state == TkGun.State.AIM_HOLD
				|| e.state == TkGun.State.AIM_ROLL)) {
			return "sliding_fast_ready".equals(e.readyHoldAnimation) ? 2.0D : 1.0D;
		}
		if (!e.active()) {
			return 1.0D;
		}
		return switch (e.state) {
			case AIM -> 0.83D;
			// 快档现在是作者新做的 `fast_ready`（0.667 秒），所以 1 倍速就够快；
			// 滑铲里抢架枪再压到 2 倍速（0.33 秒）。老做法是拿 Focus_ready 播 2x/3x。
			case AIM_FAST -> 1.0D;
			case AIM_SLIDE -> 2.0D;
			case AIM_ROLL -> 1.0D;
			default -> 1.0D;
		};
	}

	/** 这位玩家现在在**专注（架枪）**里吗（镜像服务端的 focus 位）。 */
	public static boolean focused(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && e.focus;
	}

	/**
	 * 这位玩家**正在过热**吗（镜像服务端的状态位）。
	 *
	 * <p>注意：这只是"过热动画正在播"那一段（`OVERHEAT` 状态）。**散热窗口**（动画播完、热量还没降下来）
	 * 客户端看不到，那一段由服务端权威拦（见 `TkGunServer` 的 F 键接收器）。
	 */
	public static boolean overheated(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && e.state == TkGun.State.OVERHEAT;
	}

	/** 这位玩家现在在**翻滚后的加速冲刺窗口**里吗（服务端广播的 `dashing` 位；禁止右键瞄准）。 */
	public static boolean dashing(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && e.dashing && ticks < e.dashUntil;
	}

	public static boolean meleeGraceActive(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && e.state == TkGun.State.IDLE && ticks < e.meleeGraceUntil
				&& e.lastMeleeAnimation != null && !e.lastMeleeAnimation.isBlank()
				&& !e.lastMeleeAnimation.endsWith("_to_idle")
				&& !e.lastMeleeAnimation.endsWith("_to_run");
	}

	/** 这位玩家现在是否处于翻滚 / 滑铲及其结束后的右键瞄准锁里。 */
	public static boolean aimLocked(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return false;
		}
		return dashing(entityId) || ticks < e.heatLockUntil || e.heat >= TkGun.HEAT_MAX
				|| e.state == TkGun.State.ROLL || e.state == TkGun.State.ROLL_TO_IDLE
				|| e.state == TkGun.State.SLIDE || e.state == TkGun.State.SLIDE_READY
				|| e.state == TkGun.State.OVERHEAT || e.state == TkGun.State.OVER
				|| e.state == TkGun.State.INSPECT || e.state == TkGun.State.MELEE
				|| e.state == TkGun.State.SKILL_JUMP
				|| e.state == TkGun.State.FORTRESS_SKILL_LOAD
				|| e.state == TkGun.State.FORTRESS_SKILL_GATHER
				|| e.state == TkGun.State.FORTRESS_SKILL_READY_HIT
				|| e.state == TkGun.State.FORTRESS_SKILL_HIT
				|| ticks < e.aimLockUntil;
	}

	/**
	 * **"算在专注里"**：focus 位为真，**或者**状态本身就是专注那几个
	 * （`AIM`/`AIM_FAST`/`AIM_SLIDE`/`AIM_HOLD`）。
	 *
	 * <p>⚠️ 2026-10-17：**`OVER`（退出过门 `Focus_over`）不算**。日志里看到过它被算进来的后果：
	 * 退出动画期间状态条目一过期，`pick()` 就掉到兜底分支 → 播成 `Focus` 循环（而不是待机），
	 * 看着像"松手了还架着枪"。退出动画该由 `case OVER -> Focus_over` 负责，兜底应当是 idle。
	 *
	 * <p>为什么要这个"或"（2026-10-17 用户报"按下 z 进入专注后动画会被站立所覆盖"）：
	 * 模型挑动画看的是客户端这份镜像。只要 focus 位因为任何原因（跨版本、丢包、刚进还没收到包）
	 * 还是 false，`upperAnimationFor` 就会把**端枪静姿 `stand_shooting_hold`** 叠上去 ——
	 * 那正是"站立姿势盖住了专注动画"。状态码是同一包里更"硬"的信息：状态在 AIM* 里就说明
	 * 服务端确实在专注，用它兜底比只看单个布尔稳。
	 */
	public static boolean focusish(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return false;
		}
		if (e.focus) {
			return true;
		}
		return e.state == TkGun.State.AIM || e.state == TkGun.State.AIM_FAST
				|| e.state == TkGun.State.AIM_ROLL
				|| e.state == TkGun.State.AIM_SLIDE || e.state == TkGun.State.AIM_HOLD;
	}

	/**
	 * 专注的 HUD 文案（没在专注里 = null）。
	 *
	 * <p>专注**没有倒计时**（2026-10-15 起它不是"持续 30 秒的强化"，而是"架着枪一直保持"），
	 * 所以这里只给两个字。
	 */
	public static String focusText(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null || !e.focus) {
			return null;
		}
		// 原文"专注"：文案集中在 TkGunTexts；空串 = 那一行不画（2026-10-17 用户要求先清空所有状态提示）
		String label = com.tk.gun.TkGunTexts.HUD_FOCUS;
		return label.isEmpty() ? null : label;
	}

	/** 该状态当前应该取动画的第几帧（0 基）。 */
	public static int frameFor(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return 0;
		}
		if (e.state == TkGun.State.ROLL) {
			// combat_roll 把 13 帧的动画压到 ROLL_DURATION 帧播，这里按同样比例换算
			return Math.min(13, (int) Math.round(e.elapsed * 13.0 / CombatRollHook.ROLL_DURATION));
		}
		return e.elapsed;
	}

	public static boolean rolling(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && e.active() && e.state == TkGun.State.ROLL;
	}

	/** Combat Roll may not start a second physical dash while the gun sequence owns dodge input. */
	public static boolean combatRollSuppressed(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return false;
		}
		if (ticks < e.localDodgeLockUntil) {
			return true;
		}
		if (e.state == TkGun.State.FORTRESS_SKILL_LOAD
				|| e.state == TkGun.State.FORTRESS_SKILL_GATHER
				|| e.state == TkGun.State.FORTRESS_SKILL_READY_HIT) {
			return true;
		}
		if (e.state == TkGun.State.FORTRESS_SKILL_HIT) {
			return e.elapsed < TkGun.FORTRESS_SKILL_HIT_DODGE_TICK;
		}
		return switch (e.state) {
			case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY,
					AIM, AIM_FAST, AIM_ROLL, AIM_SLIDE, SKILL_JUMP -> true;
			case MELEE -> !dashMeleeRecovery(e);
			default -> false;
		};
	}

	/** Dash attack is rollable only after its authored hit-stop has ended. */
	private static boolean dashMeleeRecovery(Entry e) {
		return e != null && e.state == TkGun.State.MELEE
				&& e.meleeReturning && e.dashMeleeReturn;
	}

	/** 弹夹里还剩几发（HUD 用；没数据时当满弹夹）。 */
	public static int roundsLeft(int entityId) {
		Entry e = ENTRIES.get(entityId);
		int capacity = capacity(entityId);
		return Math.max(0, capacity - (e == null ? 0 : e.mag));
	}

	/** 当前弹匣容量：专注 10 / 非专注 6。 */
	public static int capacity(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return TkGun.MAG_CAPACITY_HIP;
		}
		return e.focus ? e.focusMagazineCapacity : e.hipMagazineCapacity;
	}

	/** Current server-side heat as a 0..100 percentage for the HUD. */
	public static int heatPercent(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			return 0;
		}
		return Math.max(0, Math.min(100, Math.round(e.heat)));
	}

	/** Registry id of the ammo currently loaded into a hip-fire magazine. */
	public static String magAmmo(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e == null ? "" : e.magAmmo;
	}

	/** 服务端广播的主动装填状态。 */
	public static boolean reloading(int entityId) {
		Entry e = ENTRIES.get(entityId);
		// 同时检查状态存活，旧状态到期后 HUD 不会残留“装填”。
		return e != null && e.reloading && e.active();
	}

	private GunClientState() {
	}
}
