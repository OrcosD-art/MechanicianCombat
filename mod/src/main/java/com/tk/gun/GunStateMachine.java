package com.tk.gun;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 破垒重炮的服务端权威状态规则。
 *
 * <p>状态集合以 {@link TkGun.State} 为准。主要分组：
 * <ul>
 *   <li>{@code IDLE} / {@code AIM*}：待机、行走、右键瞄准及保持；移动表现由客户端根据输入选择。</li>
 *   <li>专注：按 Z 切换 {@code deployed}，进入 {@code AIM*}，按住左键自动射击；专注期间移动、跳跃和
 *       除射击/翻滚外的按键输入被屏蔽。</li>
 *   <li>{@code FIRE} / {@code RELOAD}：射击队列和主动装填；腰射与专注分别使用 6 / 10 发容量。</li>
 *   <li>{@code ROLL} / {@code SLIDE}：翻滚、滑铲及其过渡状态；动作期间禁止开火。</li>
 *   <li>{@code OVERHEAT}：专注弹匣最后一发后进入，禁止开火、移动、跳跃和装填，可被翻滚打断。</li>
 *   <li>{@code OVER}：退出专注的 Focus_over 过门，结束后回到 {@code IDLE}。</li>
 * </ul>
 *
 * <p>{@code RUN} 仍在枚举中，但当前服务端不主动广播；站立、行走和奔跑由客户端动画层按移动输入区分。
 * 客户端只镜像服务端状态，真正的状态转移发生在服务端。
 */
public final class GunStateMachine {
	/** 翻滚之后的"加速冲刺"持续 tick（用户 2026-10-16："持续个 3 秒呗…相当于获得一个加速爆发冲刺"）。 */
	public static final int DASH_TICKS = 60;
	/** A completed slide adds this much speed-only buff after the current dash ends. */
	public static final int SLIDE_SPEED_BOOST_EXTENSION_TICKS = 40;
	/**
	 * 冲刺时的移速加成（乘算：**+50% 总移速**）。
	 *
	 * <p>2026-10-17 用户："将翻滚后的速度加成提升至 50%"（原来是 +30%）。
	 */
	public static final double DASH_SPEED_BONUS = 0.50D;
	/** 两次闪避（翻滚/滑铲）间隔小于这个 tick = **连锁** → 之后闪避键失效。 */
	public static final int DODGE_CHAIN_WINDOW = 40;
	/**
	 * **"水平速度大到像在翻滚"的阈值**（格/tick）。
	 *
	 * <p>⚠️ 2026-10-17（用户把冲刺加成提到 +50%）：疾跑 0.28 × 1.5 ≈ **0.42** —— 再用老的 0.35
	 * 当阈值，"加了速的疾跑"会被当成翻滚（过热里一跑就被判成翻滚打断动画、`tickImmovable` 也不压速度了）。
	 * 取 0.55：比冲刺疾跑高一截，又低于我们自己补的那次翻滚冲刺（{@link TkGun#ROLL_DASH_SPEED} = 0.75）。
	 */
	public static final double ROLL_DETECT_SPEED = 0.55D;
	/** 连锁之后闪避键失效的 tick。 */
	public static final int DODGE_LOCK_TICKS = 60;
	/** 冲刺加速用的属性修饰符 id（同一份，反复加/删）。 */
	private static final Identifier DASH_MODIFIER = TkGun.id("roll_dash");
	/** "散热期间跳不起来"用的属性修饰符 id（同一份，反复加/删）。 */
	private static final Identifier NO_JUMP_MODIFIER = TkGun.id("overheat_no_jump");
	private static final Identifier GUN_STEP_MODIFIER = TkGun.id("gun_step_height");

	// ---------------------------------------------------------------- 状态表（问这里，别在调用点写 if）

	/**
	 * 这个状态里**能不能扣扳机**。
	 * 翻滚/滑铲（用户："翻滚途中肯定不能开枪"）、过热、以及摆姿势/换弹期间都不行。
	 */
	public static boolean canFire(TkGun.State state) {
		return switch (state) {
			case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY, OVERHEAT, RELOAD, OVER,
					MELEE, SKILL_JUMP -> false;
			default -> true;          // IDLE / AIM* / FIRE / INSPECT
		};
	}

	/** 返回需要由服务端主动压制移动的状态；专注移动锁由客户端输入 mixin 负责。 */
	public static boolean movementLocked(TkGun.State state) {
		return state == TkGun.State.OVERHEAT;
	}

	/** Only the ATK_3 active swing owns the player. Hold and hold-over stages allow movement. */
	public static void tickAtk3MovementLock(ServerPlayerEntity player, TkGunServer.PState st) {
		if (st.state != TkGun.State.MELEE || st.meleeCombo != 3
				|| st.meleeDashAttack || st.meleeReturning
				|| st.meleeAtk3MainEnded || st.meleeHoldOver
				|| st.meleeInputHeld) {
			return;
		}
		player.setSprinting(false);
		player.forwardSpeed = 0.0F;
		player.sidewaysSpeed = 0.0F;
		Vec3d velocity = player.getVelocity();
		if (Math.abs(velocity.x) > 1.0E-7D || Math.abs(velocity.z) > 1.0E-7D) {
			player.setVelocity(0.0D, velocity.y, 0.0D);
			player.velocityModified = true;
		}
	}

	// 缓慢效果只绑定退出专注和过热；专注期间的移动输入由 FocusMovementLockMixin 清零。

	/** 闪避键现在是不是被锁了（连锁闪避之后的冷却）。 */
	public static boolean dodgeLocked(TkGunServer.PState st, long now) {
		return now < st.dodgeLockUntil;
	}

	// ---------------------------------------------------------------- 每 tick 的规则落实

	/**
	 * 过热期"不可移动"：把水平速度按住（原版没有"禁止移动"的开关，只能每 tick 压速度）。
	 * 顺便取消疾跑 —— 不然玩家能靠疾跑硬顶出去。
	 */
	public static void tickImmovable(ServerPlayerEntity player, TkGunServer.PState st, long now) {
		if (!movementLocked(st.state)) {
			return;
		}
		// ⚠️ 2026-10-17 用户："翻滚打断相关功能未实现" —— 这条"过热不可移动"每 tick 把水平速度压到 12%，
		//    而 combat_roll 的翻滚就是"给一下水平冲刺"→ 那一下被压掉，看着就是"按了翻滚没反应/没打断"。
		//    所以：**玩家此刻在被高速推着（>0.35 格/tick，疾跑才 0.28）= 正在翻滚 → 不压速度**。
		//    （`tickAll` 里那条"高速位移兜底"会同时把状态切成 ROLL 并打断过热/退门动画。）
		// 冲刺加成还在（`dashUntil` 未到）也算"这是翻滚带来的速度"，同样不压。
		if (st.state == TkGun.State.ROLL || now < st.dashUntil
				|| player.getVelocity().horizontalLength() > ROLL_DETECT_SPEED) {
			return;
		}
		player.setSprinting(false);
		Vec3d v = player.getVelocity();
		Vec3d flat = new Vec3d(v.x, 0.0D, v.z);
		if (flat.length() > 0.02D) {
			Vec3d damped = flat.multiply(0.12D);
			player.setVelocity(damped.x, v.y, damped.z);
			player.velocityModified = true;
		}
	}

	/** 翻滚后的"加速冲刺"：`dashUntil` 之前给移速加成，过了就摘掉。 */
	public static boolean tickDash(PlayerEntity player, TkGunServer.PState st, long now) {
		// dashUntil still owns dodge timing. speedBoostUntil only extends the
		// movement modifier after a completed slide and must not revive roll,
		// aim-lock, fast-focus, or dodge-window semantics.
		boolean want = now < st.dashUntil || now < st.speedBoostUntil;
		// Broadcast only the authoritative dodge window. Keeping dashSyncActive
		// tied to speedBoostUntil would make clients retain aim locks after the
		// actual dash expired.
		boolean dashActive = now < st.dashUntil;
		boolean syncChanged = dashActive != st.dashSyncActive;
		st.dashSyncActive = dashActive;
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr == null) {
			return syncChanged;
		}
		boolean has = attr.getModifier(DASH_MODIFIER) != null;
		if (want && !has) {
			attr.addTemporaryModifier(new EntityAttributeModifier(DASH_MODIFIER, DASH_SPEED_BONUS,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!want && has) {
			attr.removeModifier(DASH_MODIFIER);
		}
		return syncChanged;
	}

	/** Applies the one-time two-second speed extension for a completed slide. */
	public static void extendSpeedBoostAfterSlide(ServerPlayerEntity player,
												 TkGunServer.PState st, long now) {
		if (st.slideBoostApplied) {
			return;
		}
		st.slideBoostApplied = true;
		long currentEnd = Math.max(now, Math.max(st.dashUntil, st.speedBoostUntil));
		st.speedBoostUntil = currentEnd + SLIDE_SPEED_BOOST_EXTENSION_TICKS;
		TkGun.LOGGER.info("[tkgun] 滑铲完成 -> 加速效果延长 {} tick（仅移速，原截止 {}）玩家={}",
				SLIDE_SPEED_BOOST_EXTENSION_TICKS, currentEnd, player.getName().getString());
	}

	/** 下线/收回枪时清干净（不然加速会留在身上）。 */
	public static void clearDash(PlayerEntity player) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr != null && attr.getModifier(DASH_MODIFIER) != null) {
			attr.removeModifier(DASH_MODIFIER);
		}
	}

	/**
	 * **散热（过热）期间屏蔽跳跃**（用户 2026-10-17："在散热状态下屏蔽跳跃键"）。
	 *
	 * <p>做法：把<b>跳跃强度属性</b>按成 0（`ADD_MULTIPLIED_TOTAL` 加 -100%）—— 这是原版自己那套
	 * 跳跃数值链，**客户端预测也跟着变**，所以玩家这边根本跳不起来，不需要服务端每 tick 拉位置回来
	 * （那种"橡皮筋"做法会抖）。窗口 = {@code overheatUntil} 之前（过热动画 + 之后还在散热的那段）。
	 */
	public static void tickNoJump(ServerPlayerEntity player, TkGunServer.PState st, long now) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
		if (attr == null) {
			return;
		}
		boolean want = TkGun.holdingGun(player);
		boolean has = attr.getModifier(NO_JUMP_MODIFIER) != null;
		if (want && !has) {
			attr.addTemporaryModifier(new EntityAttributeModifier(NO_JUMP_MODIFIER, -1.0D,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!want && has) {
			attr.removeModifier(NO_JUMP_MODIFIER);
		}
	}

	/** 下线/收回枪时清掉"跳不起来"（不然会留在身上）。 */
	public static void clearNoJump(PlayerEntity player) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
		if (attr != null && attr.getModifier(NO_JUMP_MODIFIER) != null) {
			attr.removeModifier(NO_JUMP_MODIFIER);
		}
	}

	/** While carrying the gun and moving, raise step height from 0.6 to 1.0 block. */
	public static void tickGunStepHeight(ServerPlayerEntity player) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (attr == null) {
			return;
		}
		boolean want = TkGun.holdingGun(player);
		boolean has = attr.getModifier(GUN_STEP_MODIFIER) != null;
		if (want && !has) {
			attr.addTemporaryModifier(new EntityAttributeModifier(GUN_STEP_MODIFIER, 0.5D,
					EntityAttributeModifier.Operation.ADD_VALUE));
		} else if (!want && has) {
			attr.removeModifier(GUN_STEP_MODIFIER);
		}
	}

	/**
	 * 一次闪避（翻滚或滑铲）**开始**时的公共处理：
	 * <ol>
	 *   <li>**连锁检测**：距上次闪避 ≤ {@link #DODGE_CHAIN_WINDOW} → 这次算连锁 → 之后
	 *       {@link #DODGE_LOCK_TICKS} tick 内闪避键失效（用户文本里的"闪避按键输入无效 __ 秒"）；</li>
	 *   <li>给 3 秒**加速冲刺**（用户："每次翻滚之后获得一个加速爆发冲刺的强化，速度更快"）。</li>
	 * </ol>
	 *
	 * @return true = 这次闪避要**忽略**（还在失效期里）
	 */
	public static boolean onDodgeStarted(ServerPlayerEntity player, TkGunServer.PState st, long now,
									 boolean roll) {
		// ⚠️ 同一次翻滚会**两次**走到这里：combat_roll 的事件 + 我们"翻滚键包"那条路（2026-10-25 补的）。
		//    不加这道闸，第二次会被判成"连锁闪避"→ 反而给玩家套上 3 秒闪避锁（自己坑自己）。
		if (st.lastDodgeAt == now) {
			return false;                  // 这一 tick 已经结算过了：不算连锁，也不用重复给冲刺
		}
		if (dodgeLocked(st, now)) {
			TkGunTexts.say(player, String.format(TkGunTexts.DODGE_LOCKED, DODGE_LOCK_TICKS / 20), true);
			return true;
		}
		boolean chained = st.lastDodgeAt > 0 && now - st.lastDodgeAt <= DODGE_CHAIN_WINDOW;
		st.lastDodgeAt = now;
		st.speedBoostUntil = 0;
		st.slideBoostApplied = false;
		// **翻滚 / 滑铲交替循环**：1 = 刚翻滚（下次 Alt 走滑铲），2 = 刚滑铲（下次重新翻滚）。
		st.lastDodgeKind = roll ? 1 : 2;
		st.dashUntil = now + DASH_TICKS;
		if (chained) {
			st.dodgeLockUntil = now + DODGE_LOCK_TICKS;
			TkGun.LOGGER.info("[tkgun] 连锁闪避 -> 闪避键失效 {} tick 玩家={}", DODGE_LOCK_TICKS,
					player.getName().getString());
		}
		TkGun.LOGGER.info("[tkgun] 闪避（{}）-> 加速冲刺 {} tick（+{}% 移速）玩家={}",
				chained ? "连锁" : "单次", DASH_TICKS, (int) (DASH_SPEED_BONUS * 100), player.getName().getString());
		return false;
	}

	private GunStateMachine() {
	}
}
