package com.tk.gun;

import com.tk.gun.api.FortressDestroyerGun;
import com.tk.gun.api.GunDefinition;
import com.tk.gun.api.GunRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** 常量、状态机状态、音效/资源 id —— 服务端与客户端共用。 */
public final class TkGun {
	public static final String MOD_ID = "tkgun";
	/**
	 * **构建标记**：每次交给你测试的 jar 都换一个。
	 *
	 * <p>为什么要它：进服日志里打出来，"是不是还在跑旧 jar / 客户端服务端是不是同一份"一眼就知道
	 * （2026-10-17 排查时吃过一次亏：所有版本的启动日志长得一模一样）。
	 */
	public static final String BUILD = "r519-2026-10-30";
	/** Shared gain for backpack animation and target-mark feedback sounds. */
	public static final float BACKPACK_SOUND_VOLUME = 0.5F;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 动作状态。全部由服务端权威广播（客户端只做镜像），动画名见
	 * {@link com.tk.gun.client.GunClientState#animationName(int)} / {@code BowgunRig.pick()}。
	 *
	 * <p>待机、过热、专注射击、翻滚和滑铲均使用独立动画状态。
	 */
	public enum State {
		IDLE(0),        // 待机 / 移动表现由客户端决定
		AIM(1),         // 普通举枪        = Focus_ready
		RUN(2),         // 当前服务端不主动广播
		FIRE(3),        // 射击            = 前 3 发 extra，中段 Focus_shooting，末尾 6/7
		OVERHEAT(4),    // 过热            = Overheat
		INSPECT(5),     // 仅调试命令使用的检视动画
		SLIDE(6),       // 滑铲            = sliding
		ROLL(7),        // 翻滚            = roll（资源包里 combat_roll 那段也换成了这套素材）
		SLIDE_READY(8), // 滑铲收势        = sliding_ready，结束后回 IDLE/重新举枪
		AIM_FAST(9),    // 快速举枪        = fast_ready
		AIM_SLIDE(10),  // 滑铲抢枪        = sliding_fast_ready
		/** 打完一枪且仍处于专注时，保持该射击动画的尾帧，不重播举枪动画。 */
		AIM_HOLD(11),
		/** 退出专注时播放 Focus_over，结束后回到 IDLE。 */
		OVER(12),
		/**
		 * **装填**：作者这次新给了真·换弹动画 `reload`（1.125 秒，`change3` 弹夹滑回去 + `case` 转轮），
		 * 所以不再拿 `Focus_ready` 顶（老素材没有换弹动画时的权宜之计）。
		 */
		RELOAD(13),
		/**
		 * **翻滚后的快速蹲射过门**：和普通 `AIM_FAST` 同样是 14 tick，
		 * 但客户端必须播作者新给的 `rolling_fast_ready`，不能借用滑铲的
		 * `sliding_fast_ready`。独立成一个状态，避免换弹后的快速举枪也误播翻滚动作。
		 */
		AIM_ROLL(14),
		/** 翻滚结束、玩家没有继续移动：播作者的 `roll_to_idle` 过渡，再回 IDLE。 */
		ROLL_TO_IDLE(15),
		MELEE(16),
		/** Silent Storm accelerated jump; jump/fall animation follows vertical velocity. */
		SKILL_JUMP(17);

		public static boolean isAiming(State s) {
			return s == AIM || s == AIM_FAST || s == AIM_ROLL || s == AIM_SLIDE
					|| s == AIM_HOLD;
		}

		public final byte id;

		State(int id) {
			this.id = (byte) id;
		}

		public static State byId(int id) {
			for (State s : values()) {
				if (s.id == id) {
					return s;
				}
			}
			return IDLE;
		}
	}

	// ---- 数值 ----
	/**
	 * 弹夹容量：破垒重炮 **10 发**（2026-10-18 用户调整）。
	 * 前 3 发使用 `Focus_shooting_extra`；第 4 发至倒数第 3 发按两段弹链阶段共用
	 * `Focus_shooting1` / `Focus_shooting4`，位移由渲染器按发数叠加。倒数第二发使用
	 * `Focus_shooting6`，最后一发使用 `Focus_shooting7` 把弹夹甩出。
	 */
	public static final int MAG_CAPACITY = 10;
	/** 蹲射（专注）弹匣容量。 */
	public static final int MAG_CAPACITY_FOCUS = 10;
	/** 非蹲射弹匣容量。 */
	public static final int MAG_CAPACITY_HIP = 6;
	/** 射击动画编号上限（前 3 发共用 extra，之后接 Focus_shooting/2..7）。 */
	public static final int SHOT_ANIM_MAX = 10;
	/**
	 * **出伤帧**：子弹真正打出去的时刻 = 开火动画里 `fire1` 的 scale 从 0 翻成 1 的那一针。
	 * 作者七段射击动画都标在 **0.0417s**（≈0.83 tick）→ 按键后 +1 tick 出膛。
	 * 重算：`python tools\muzzle_offset_new.py`（它会把出伤帧一起打出来）。
	 */
	public static final int ATTACK_FRAME_TICKS = 1;
	/** Close-combat hit frame authored at 0.5 seconds in close_combat.bbmodel. */
	public static final int MELEE_HIT_TICK = 10;
	/** ATK_3 holds its main-attack pose at 1.0 s; 1.0-1.25 s is recovery. */
	public static final int MELEE_ATK3_MAIN_END_TICK = 20;
	public static final int MELEE_ATK4_CHARGE_TICKS = 10;
	/** Authored ATK_hold_over is 1.125 s; one extra tick renders its final key. */
	public static final int MELEE_HOLD_OVER_TICKS =
			(int) Math.ceil(1.125D * 20.0D) + 1;
	/** Timelines authored inside ATK_hold_over. */
	public static final int[] MELEE_HOLD_OVER_HIT_TICKS = {3, 10, 18};
	/** Reach four blocks by the second authored hit frame. */
	public static final double MELEE_HOLD_OVER_MOVE_DISTANCE = 4.0D;
	/** Additional decaying momentum between the second and third hit frames. */
	public static final double MELEE_HOLD_OVER_INERTIA_DISTANCE = 2.0D;
	public static final double MELEE_HOLD_OVER_PHASE_VELOCITY = 0.5D;
	public static final int MELEE_HOLD_OVER_MOVE_END_TICK =
			MELEE_HOLD_OVER_HIT_TICKS[1];
	public static final int MELEE_HOLD_OVER_INERTIA_END_TICK =
			MELEE_HOLD_OVER_HIT_TICKS[2];
	public static final int[] MELEE_HOLD_OVER_SOUND_TICKS = {3, 10, 16, 18};
	public static final String[] MELEE_HOLD_OVER_SOUND_KEYS = {
			"glaive_slash02", "imp1", "smash_ground3", "imp1"
	};
	/** A click must stay down for at least 0.25 s to branch into ATK_3. */
	public static final int MELEE_LONG_PRESS_TICKS = 5;
	public static final int MELEE_DASH_MOVE_TICKS = 10;
	public static final int MELEE_DASH_STUN_TICKS = 3;
	/** 0.25 s authored clip plus one tick so its final keyframe is rendered. */
	public static final int MELEE_RETURN_TICKS = 6;
	public static final int MELEE_DASH_TOTAL_TICKS =
			MELEE_HIT_TICK + MELEE_DASH_STUN_TICKS;
	public static final double MELEE_DASH_DISTANCE = 4.6D;
	public static final double MELEE_DASH_STUN_SPEED = 0.16D;
	public static final double MELEE_RANGE = 4.0D;
	public static final double MELEE_LATERAL_RADIUS = 3.0D;
	/** Better Combat diamond axe attack damage. */
	public static final float DIAMOND_AXE_DAMAGE = 9.0F;
	/** Silent Storm accelerated jump: approximately six blocks up and twelve forward. */
	public static final double SKILL_JUMP_HORIZONTAL_SPEED = 1.31D;
	public static final double SKILL_JUMP_VERTICAL_SPEED = 1.11D;
	/** Safety lifetime; normal landing ends the state earlier. */
	public static final int SKILL_JUMP_MAX_TICKS = 40;

	public static String meleeAnimation(int step) {
		return "ATK_" + Math.max(1, Math.min(4, step));
	}

	public static String meleeReturnAnimation(int step) {
		return "ATK_" + Math.max(1, Math.min(4, step)) + "_to_idle";
	}

	public static int meleeTicks(int step) {
		return step == 3 ? MELEE_ATK3_MAIN_END_TICK + MELEE_ATK4_CHARGE_TICKS : 20;
	}
	/**
	 * 连发节奏：两发之间隔几 tick（每发都把该发动画从头播一遍 → 后坐/枪口焰/弹壳跟着重播）。
	 * 12 tick = 0.6s 一发：动画前 0.208s 是四段枪口焰，0.6s 时弹壳正好甩到一半。
	 */
	public static final int SHOT_CYCLE_TICKS = 12;
	/** 调试命令和队列的硬上限；正常专注每轮只允许预输入 1 发，腰射不接受预输入。 */
	public static final int SHOT_QUEUE_MAX = 9;

	/** 当前射击模式的弹匣容量。 */
	public static int magazineCapacity(boolean focus) {
		return magazineCapacity(FortressDestroyerGun.DEFINITION, focus);
	}

	public static int magazineCapacity(ItemStack gun, boolean focus) {
		return magazineCapacity(definition(gun), focus);
	}

	public static int magazineCapacity(PlayerEntity player, boolean focus) {
		return magazineCapacity(definition(player), focus);
	}

	public static int magazineCapacity(GunDefinition definition, boolean focus) {
		GunDefinition resolved = definition == null ? FortressDestroyerGun.DEFINITION : definition;
		return focus ? resolved.focusMagazineCapacity() : resolved.hipMagazineCapacity();
	}
	/**
	 * Focus_shooting 长 1.208s（≈24.2 tick）→ 状态给 **30 tick**：比动画长一截，
	 * 这样动画播完停在尾帧的这段时间里状态还是 FIRE（客户端不会掉进兜底），
	 * 服务端随后的 AIM_HOLD 到了就直接 CONTINUE 保持在尾针上（用户 2026-10-13 的要求）。
	 */
	public static final int FIRE_TICKS = 30;
	/** 散热窗口：5s 里不能开火 / 装填 / 进专注，排汽白汽也在这段持续放。 */
	public static final int OVERHEAT_TICKS = 100;
	/**
	 * **过热动作（`Overheat` 动画）本身的时长** —— 用户 2026-10-17："修改过热动画当前该动作的持续时间"。
	 *
	 * <p>作者那段 `Overheat` 实测 **1.5s**（`animation_length = 1.5` = 30 tick）。原来状态也按
	 * {@link #OVERHEAT_TICKS}（100 tick）播，动画 1.5s 就停在尾帧、后面 3.5s 一直举着枪"装散热"，
	 * 和动画对不上。现在：**过热状态 + 缓慢 10 = 30 tick（动画播完就结束）**；
	 * 散热窗口（能不能开火/装填/进专注、白汽）仍按 {@link #OVERHEAT_TICKS}。
	 */
	public static final int OVERHEAT_ANIM_TICKS = 30;
	/** 检视动画时长，仅用于调试命令。 */
	public static final int INSPECT_TICKS = 80;
	/**
	 * 部署（架枪）时长：普通右键慢、滑铲/蹲着右键快、滑铲里抢架枪最快。见 {@link TkGunServer#startAim}。
	 *
	 * <p>⚠️ 2026-10-14 换新素材后**快档改用作者新做的 `fast_ready`**（0.667 秒，专门是"快速起枪"）——
	 * 老做法是拿 `Focus_ready` 按 2x/3x 倍速播，那是老素材没有 fast_ready 时的凑合。
	 */
	public static final int AIM_TICKS_NORMAL = 24;    // 1.2s（Focus_ready 0.83 倍速）
	public static final int AIM_TICKS_FAST = 14;      // 0.7s（fast_ready 1 倍速）
	public static final int AIM_TICKS_SLIDE = 7;      // 0.35s（fast_ready 约 2 倍速）
	/**
	 * **主动装填**：播放作者的 `reload` 动画。空闲弹匣不会自动装填。
	 *
	 * <p>⚠️ 2026-10-14 新素材才有 `reload`（老素材是拿 `Focus_ready` 的头半段顶的，
	 * 那段 `change3` 从 x=-6/scale(0,1,1) 滑到 x=-1/scale(1,1,1) 正好是"插弹夹"）。
	 */
	public static final int RELOAD_TICKS = 34;
	public static final int SLIDE_TICKS = 16;         // 滑铲时长（越短越"唰"）
	/**
	 * 滑铲滑多远（格）+ 滑多久。用户 2026-10-12 二次要求："不够快也不够远"。
	 * 24 tick 拉到 16 tick、3.8 格拉到 4.6 格（初速 ≈ 0.58 格/tick ≈ 11.6 m/s）。
	 * 位移按"离起点距离"封顶，所以按不按前进键都一样远（日志会打实测值）。
	 */
	public static final double SLIDE_DISTANCE = 4.6D;
	/** Small forward momentum retained when a slide transitions into sliding_fast_ready. */
	public static final double SLIDE_FAST_FOCUS_INERTIA = 0.18D;
	public static final int SLIDE_COOLDOWN = 50;      // 滑铲冷却
	/**
	 * 滑铲收势长度。收势结束后回到 IDLE；若服务端仍标记为专注，则重新进入快速举枪。
	 */
	public static final int SLIDE_READY_TICKS = 14;
	public static final int BOLT_TICKS = 8;           // 拉栓(栓声)时长
	/** 专注连射窗口：两发间隔不超过该 tick 数时继续累积射速与伤害递增。 */
	public static final int COMBO_WINDOW = 60;
	/** 历史兼容常量；当前过热由专注弹匣最后一发决定，不再读取固定连射发数。 */
	public static final int COMBO_TO_OVER = 10;
	/** 专注模式每发增加的热量。 */
	public static final float HEAT_PER_SHOT = 10.0F;
	public static final float HEAT_MAX = 100.0F;
	/** 非专注状态下每 tick 自然散热量；专注期间不自然冷却。 */
	public static final float HEAT_COOL = 0.25F;
	/**
	 * ⚠️ 2026-10-14 用户："弹速也不够快，应该是**瞬间就到**，然后在目标地点产生爆炸，枪炮/机炮那种感觉"。
	 * 所以从这里开始**没有飞行弹丸了**：开火 = 从枪口沿枪管方向打一条**射线**（hitscan），
	 * 立刻在命中点结算伤害 + 爆炸。{@link #HITSCAN_RANGE} 是射线的最大长度（格）。
	 */
	public static final double HITSCAN_RANGE = 96.0D;
	// 2026-10-17：`IMPACT_EXPLOSION_POWER`（命中点的小型非破坏爆炸）**已删除** —— 用户要求命中处
	// 不要 minecraft 原版爆炸特效/音效（`createExplosion` 会自动放原版爆炸云 + 爆炸声）。
	// 现在命中 = hitscan 直伤 + 击退 + 素材粒子（`energy_xplo` 等）+ 素材 `imp1` 命中声。
	/** 历史兼容常量；当前不执行自动换弹，所有装填由玩家主动触发。 */
	public static final int AUTO_RELOAD_DELAY = 24;
	public static final float BULLET_DAMAGE = 9.6F;

	// ---- 专注（架枪）状态 ----
	/** 历史兼容常量；当前进入专注直接使用 AIM、AIM_ROLL 或 AIM_SLIDE。 */
	public static final int FOCUS_ENTER_TICKS = 24;
	/**
	 * **退出专注的过门时长**（tick）：播作者的 `Focus_over`（1.0 秒，把炮从肩上放下来）——
	 * 用户 2026-10-16："按下进入专注模式，再次输入则退出并使用 Focus_over 动画"。
	 * 这段里状态是 {@link State#OVER}，播完 {@code tickAll} 就回 {@link State#IDLE}。
	 */
	public static final int FOCUS_EXIT_TICKS = 22;
	/**
	 * **"缓慢"效果的放大器** —— 现在只绑两件事（用户 2026-10-17：
	 * "将缓慢效果绑定退出专注状态以及过热两种状态"）：
	 * <ol>
	 *   <li>**退出专注**（按 Z 解除 → 播 `Focus_over` 的那段时间）；</li>
	 *   <li>**过热**（包括"专注里打出最后一颗 → 进过热"）。</li>
	 * </ol>
	 * 专注**期间**因为输入被屏蔽，人本来就动不了，所以不再有"移动触发缓慢"这回事
	 * （2026-10-17 用户："清除关于专注状态下移动时给予缓慢的相关内容"）。
	 *
	 * <p>放大器 = 5：原版 HUD 显示的等级是"放大器+1"，所以 5 显示出来是**缓慢 6**。
	 *
	 * <p>⚠️ **为什么不是 10**（2026-10-17 反编译 combat_roll 查出来的硬冲突）：缓慢每级 −15% 乘算，
	 * 放大器 9（−150%）会把 `GENERIC_MOVEMENT_SPEED` 的最终值**压成负数**；而 combat_roll 的
	 * `RollManager#isRollAvailable(...)` 里明确有一条 `getAttributeValue(GENERIC_MOVEMENT_SPEED) > 0`
	 * —— 于是"缓慢 10"期间它会直接判定"你动不了"，**拒绝翻滚**（用户报的"翻滚未生效"就是这个原因）。
	 * 放大器 5（−90%）刚好留 10% 余量：人几乎走不动，但属性仍是正数、翻滚照常可用。
	 * 想更强就只能牺牲"翻滚能挣脱"这条 —— 两个要求在这个数值上互斥，取舍点就是这个常数。
	 */
	public static final int SLOW_ON_EXIT_AMPLIFIER = 5;
	/** 退出专注时的缓慢时长：和退出过门动画（`Focus_over`）一样长。 */
	public static final int SLOW_ON_EXIT_TICKS = FOCUS_EXIT_TICKS;
	/** 历史兼容常量；当前腰射不接受预输入，专注模式每个射击间隔只接受一次预输入。 */
	public static final int HIP_QUEUE_MAX = 2;
	/**
	 * **翻滚键自触发时的冲刺速度**（格/tick）：只在"combat_roll 这次没把玩家带走"时兜底用
	 * （见 `TkGunServer` 的 `GunRollPayload` 接收器）——过热期间它的翻滚常常起不来，
	 * 用户 2026-10-17 要求"检测到翻滚键 → 先清缓慢 → 瞬间触发翻滚"。
	 * 0.75 格/tick 约等于一次短冲刺（疾跑 0.28），摩擦会在几 tick 内把它收住。
	 */
	public static final double ROLL_DASH_SPEED = 0.75D;
	/** Extra launch speed for lateral rolls; forward/back rolls stay unchanged. */
	public static final double ROLL_SIDE_SPEED_MULTIPLIER = 1.5D;
	/**
	 * **专注里的递增曲线**（2026-10-15 用户："进入架枪状态之后，连续射击射速会越来越快、
	 * 伤害也会越来越高，然后就会过热"）。
	 *
	 * <p>每打一发 `ramp` +1（同一梭里连续打才算，停 {@link #COMBO_WINDOW} 就归零），
	 * 射速 = {@link #SHOT_CYCLE_TICKS} 按 ramp 递减到 {@link #FOCUS_CYCLE_MIN}，
	 * 伤害 = {@link #FOCUS_DAMAGE_START} + ramp × {@link #FOCUS_DAMAGE_STEP}，封顶
	 * {@link #FOCUS_DAMAGE_MAX}。**只有专注里才涨热**（腰射永远不过热，见 TkGunServer）。
	 */
	public static final int FOCUS_CYCLE_MIN = 5;
	/**
	 * **专注连射的递减步长**：每打一发，下一发的间隔减少 **0.25 秒**（= 5 tick），
	 * 减到 {@link #FOCUS_CYCLE_MIN}（0.25 秒）为止。
	 *
	 * <p>用户 2026-10-16："专注射击期间…可以按住左键全自动射击，每次射击间隔减少 0.25 秒"。
	 * 节奏 = {@code max(FOCUS_CYCLE_MIN, SHOT_CYCLE_TICKS - ramp × FOCUS_CYCLE_STEP)}：
	 * 第 1 发 12 tick（0.6s）→ 第 2 发 7 tick（0.35s）→ 第 3 发起 5 tick（0.25s）封顶。
	 */
	public static final int FOCUS_CYCLE_STEP = 5;
	public static final float FOCUS_DAMAGE_START = 1.0F;
	public static final float FOCUS_DAMAGE_STEP = 0.12F;
	public static final float FOCUS_DAMAGE_MAX = 2.0F;
	/**
	 * **子弹（看得见的弹丸）**：2026-10-15 用户给了 `粒子/bullet.png` ——"用这个去代表那个弹丸"。
	 * 伤害依旧是 hitscan（瞬时、服务端权威），这个实体只是**画面上飞出去的那颗子弹**
	 * （贴图 48x144 = 3 帧 48x48 循环），飞到头/撞到东西就消失。
	 */
	/** 速度：2.5 格/tick（50 格/秒）—— 看得见一道飞过去，又不至于"贴脸打看不见子弹"。 */
	public static final double BULLET_SPEED = 2.5D;
	public static final int BULLET_MAX_TICKS = 60;
	/** 最短存活 tick：打超近的目标时也要在大半个 tick 里露个面（不然等于没有子弹模型）。 */
	public static final int BULLET_MIN_TICKS = 2;
	/** Grapeshot: 12 independent pellets traveling in a muzzle-centered cone. */
	public static final int GRAPESHOT_PELLETS = 8;
	public static final double GRAPESHOT_RANGE = 12.0D;
	/** Initial speed; each completed block reduces flight speed by 10%. */
	public static final double GRAPESHOT_SPEED = 1.5D;
	public static final double GRAPESHOT_SPEED_DECAY_PER_BLOCK = 0.9D;
	public static final int GRAPESHOT_FLIGHT_TICKS = grapeshotTravelTicks(GRAPESHOT_RANGE);
	/** Damage cylinder: 5-block diameter, extending 12 blocks from the muzzle. */
	public static final double GRAPESHOT_DAMAGE_RADIUS = 2.5D;
	/** Visual cone expands proportionally with range; this is independent of damage radius. */
	public static final double GRAPESHOT_CONE_RADIUS = 5.0D;
	public static final float GRAPESHOT_FOCUS_CYCLE_MULTIPLIER = 0.8F;
	/** 80% of the fixed hip-fire cadence, rounded to a whole server tick. */
	public static final int GRAPESHOT_FOCUS_CYCLE_TICKS =
			Math.max(1, Math.round(SHOT_CYCLE_TICKS * GRAPESHOT_FOCUS_CYCLE_MULTIPLIER));
	public static final double ROSE_QUARTZ_GUIDANCE_RADIUS = 6.0D;
	public static final float ROSE_QUARTZ_DAMAGE = 5.0F;
	public static final int ROSE_QUARTZ_FOCUS_CYCLE_TICKS = GRAPESHOT_FOCUS_CYCLE_TICKS;

	/** Starts at the shotgun's 80% cadence, then shortens with each consecutive focus shot. */
	public static int roseQuartzFocusCycleTicks(int ramp) {
		int normalCycle = Math.max(FOCUS_CYCLE_MIN,
				SHOT_CYCLE_TICKS - Math.max(0, ramp) * FOCUS_CYCLE_STEP);
		return Math.max(1, Math.round(normalCycle * GRAPESHOT_FOCUS_CYCLE_MULTIPLIER));
	}

	/** Uses smooth distance-based decay for flight and delayed damage timing. */
	public static int grapeshotTravelTicks(double distance) {
		double remaining = Math.max(0.0D, distance);
		double speed = GRAPESHOT_SPEED;
		double travelled = 0.0D;
		int ticks = 0;
		while (remaining > 1.0E-6D && ticks < BULLET_MAX_TICKS) {
			double step = Math.min(speed, remaining);
			remaining -= step;
			travelled += step;
			ticks++;
			speed = GRAPESHOT_SPEED * Math.pow(GRAPESHOT_SPEED_DECAY_PER_BLOCK, travelled);
		}
		return Math.max(1, ticks);
	}

	// ---- 枪口（子弹出来的地方）----
	// 模型里作者放了 locator 骨骼当枪口挂点（parent = gun，pivot = 模型坐标 0,10,-37）。
	// 下面三个数 = 按 GeckoLib 的骨骼变换链（`RenderUtil.prepMatrixForBone`）在
	// **七段射击动画的出伤帧**（t=0.0417s，七段完全一致）算出来的：
	//   前 2.675 格 / 模型 +x 方向 -0.250 格 / 高 0.601 格（都以脚底 y=0 为基准）。
	// 同一时刻枪管方向 ≈ 水平正前（上=-0.045，pitch -2.6°，当 0 处理）。
	// 模型 -z = 玩家面朝方向；模型 +x 在世界里 = (-cos yaw, 0, -sin yaw)。重算：tools\muzzle_offset_new.py
	// 2026-10-16 v4 素材重算（七段射击动画在出伤帧一致）：2.675/-0.250/0.601 → 2.733/-0.276/0.603。
	// （只影响"客户端没量到枪口"时的服务端兜底 —— 正常路径用的是客户端渲染时量的真实枪口；
	//   顺带记录：`Focus_shooting6` 那段的枪口偏到 X_hat=+0.335，是作者改动画时挪过，属素材侧差异。）
	public static final double MUZZLE_FORWARD = 2.733D;
	public static final double MUZZLE_SIDE = -0.276D;
	public static final double MUZZLE_HEIGHT = 0.603D;


	/**
	 * 第 shotIndex 发该播的动画名（1..10）。
	 * 1..3 -> Focus_shooting_extra；4..N-4 -> Focus_shooting1；N-3..N-2 -> Focus_shooting4；
	 * 倒数第二发 -> Focus_shooting6；最后一发 -> Focus_shooting7。
	 */
	public static String shotAnimation(int shotIndex) {
		return shotAnimation(shotIndex, SHOT_ANIM_MAX);
	}

	public static String shotAnimation(int shotIndex, int maxShotIndex) {
		int n = Math.max(1, Math.min(maxShotIndex, shotIndex));
		if (n <= 3) {
			return "Focus_shooting_extra";
		}
		if (n <= Math.max(3, maxShotIndex - 4)) {
			return "Focus_shooting1";
		}
		if (mergedFocusShot(n, maxShotIndex)) {
			return "Focus_shooting4";
		}
		return n < maxShotIndex ? "Focus_shooting6" : "Focus_shooting7";
	}

	/** Shots whose chain-phase animation is shared; offsets are applied at render time. */
	public static boolean mergedFocusShot(int shotIndex, int maxShotIndex) {
		return shotIndex > 3 && shotIndex < maxShotIndex - 1;
	}

	/** Holds the authored last frame after shots 4+, or Focus_hold during shots 1-3. */
	public static String focusHoldAnimation(int shotIndex) {
		return focusHoldAnimation(shotIndex, SHOT_ANIM_MAX);
	}

	public static String focusHoldAnimation(int shotIndex, int maxShotIndex) {
		return shotIndex <= 3 ? "Focus_hold" : shotAnimation(shotIndex, maxShotIndex);
	}

	/**
	 * 过载连射那段（只在 `/tkgun state <玩家> inspect` 调试里看）。
	 *
	 * <p>⚠️ 2026-10-15 素材 v3 把 `_22.5` / `_45` 仰角变体**全删了** —— 作者改成在动画里写
	 * Molang：`chest.rotation.x = -query.body_x_rotation / 3`（GeckoLib 支持 `query.body_x_rotation`，
	 * 会自己算）。所以动画名后面再也没有后缀，抬枪角度由素材自己决定。
	 */
	public static String overShootingAnimation() {
		return "Focus_over_shooting";
	}

	// ---- id ----
	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	/** 开火声：素材 `枪击3.ogg`，服务端在每一发的出伤帧统一播放。 */
	public static final Identifier SND_SHOT3 = id("gunshot3");
	/** 切割弹专属开火声：素材 `枪击2.ogg`。 */
	public static final Identifier SND_SHOT2 = id("gunshot2");
	public static final Identifier SND_GRAPESHOT_FIRE = id("grapeshot_fire");
	public static final Identifier SND_GRAPESHOT_HIT = id("grapeshot_hit");
	public static final Identifier SND_CROSSBOW_FIRE = id("crossbow_fire");
	public static final Identifier SND_BOLT = id("bolt");         // 栓声.ogg
	public static final Identifier SND_DRY = id("dryfire");       // 空弹仓.ogg
	public static final Identifier SND_VENT = id("vent");         // 散热.ogg
	public static final Identifier SND_ROLL = id("roll");         // 翻滚.ogg
	/** `弹匣.ogg`（v4 新给的：弹夹甩出/换弹时"啪"的一下）。 */
	public static final Identifier SND_MAG = id("mag");
	/** `换弹.ogg` / `换弹2.ogg`（v4 新给的两段换弹声）。 */
	public static final Identifier SND_RELOAD = id("reload1");
	public static final Identifier SND_RELOAD2 = id("reload2");
	/**
	 * `imp1.ogg` —— **弹头命中点**那一声（用户 2026-10-17："删除弹头击中后的 minecraft 原版爆炸特效及其
	 * 音效，并将声音替换为…sound 中的 imp1"）。
	 *
	 * <p>文件在 `assets/tkgun/sounds/imp1.ogg`（与素材原文件逐字节相同）、`sounds.json` 里注册成
	 * `tkgun:imp1`，**由服务端在命中点放** → 分发出去之后附近所有人都听得见。
	 */
	public static final Identifier SND_IMPACT = id("imp1");
	public static final Identifier SND_CUTTING1 = id("cutting1");
	public static final Identifier SND_EXPLOSION1 = id("explosion1");
	public static final Identifier SND_ROSE_QUARTZ_HIT = id("rose_quartz_hit");
	/** 幽匿弹开火：素材 `音爆开火.ogg`。 */
	public static final Identifier SND_SONIC_SHOT = id("sonic_shot");
	/** 幽匿弹音爆粒子：素材 `音爆1.ogg`（坚守者 sonic_boom1 原始音效）。 */
	public static final Identifier SND_SONIC_BOOM = id("sonic_boom");
	/** Veritas heat vent: source asset `剑_过热.ogg`. */
	public static final Identifier SND_SWORD_HEAT = id("sword_heat");
	/** ATK_2 authored broadsword swing (`阔剑挥砍.ogg`). */
	public static final Identifier SND_CLAYMORE_SWING = id("claymore_swing");
	/** ATK_1 authored glaive slash (`glaive_slash-02.ogg`). */
	public static final Identifier SND_GLAIVE_SLASH2 = id("glaive_slash2");
	/** ATK_2/ATK_3 authored double-axe swing (`double_axe_swing-01.ogg`). */
	public static final Identifier SND_DOUBLE_AXE_SWING1 = id("double_axe_swing1");
	/** ATK_4 authored anchor slam (`anchor_slam-01.ogg`). */
	public static final Identifier SND_ANCHOR_SLAM1 = id("anchor_slam1");
	/** ATK_3_hold authored mace air swing (`smash_air1.ogg`). */
	public static final Identifier SND_SMASH_AIR1 = id("smash_air1");
	/** ATK_3 authored mace ground impact (`smash_ground1.ogg`). */
	public static final Identifier SND_SMASH_GROUND1 = id("smash_ground1");
	/** ATK_hold_over authored mace ground impact (`smash_ground3.ogg`). */
	public static final Identifier SND_SMASH_GROUND3 = id("smash_ground3");
	/** 熔铁弩矢（三叉戟式弹药）开火声：素材 `枪击.ogg`。 */
	public static final Identifier SND_GUNSHOT = id("gunshot");

	/**
	 * **动画音效关键帧**里会出现的那几个名字 → 真音效（名字是导出脚本
	 * `tools/gen_geckolib_mech.py#SOUND_NAMES` 按作者在 bbmodel 里配的 ogg 起的）。
	 *
	 * <p>客户端只把这个**名字**报给服务端（{@link com.tk.gun.net.GunSoundPayload}），服务端查这张表去放 ——
	 * 这样附近所有人都听得见（用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见"）。
	 *
	 * <p>`shot` / `shot2` 等开火关键帧**不在**表里：每发的炮声由服务端在**出伤帧**放
	 * （`TkGunServer#playShotSound`），
	 * 客户端不重复报。`steam`（滑铲摩擦）当年借的是散热音，音量音高沿用当时那套。
	 */
	public record KeyframeSound(Identifier id, float volume, float pitch) {
	}

	public static final Map<String, KeyframeSound> KEYFRAME_SOUNDS = Map.ofEntries(
			Map.entry("bolt", new KeyframeSound(SND_BOLT, 1.0F, 1.0F)),       // 栓声.ogg
			Map.entry("reload", new KeyframeSound(SND_RELOAD, 1.0F, 1.0F)),   // 换弹.ogg
			Map.entry("reload2", new KeyframeSound(SND_RELOAD2, 1.0F, 1.0F)), // 换弹2.ogg
			Map.entry("vent", new KeyframeSound(SND_VENT, 1.0F, 1.0F)),       // 散热.ogg
			Map.entry("mag", new KeyframeSound(SND_MAG, 1.0F, 1.0F)),         // 弹匣.ogg
			Map.entry("empty", new KeyframeSound(SND_DRY, 1.0F, 1.0F)),       // 空弹仓.ogg
			Map.entry("roll", new KeyframeSound(SND_ROLL, 0.9F, 1.0F)),       // 翻滚.ogg
			Map.entry("steam", new KeyframeSound(SND_VENT, 0.6F, 1.2F)),      // 滑铲摩擦
			Map.entry("glaive_slash02",
					new KeyframeSound(SND_GLAIVE_SLASH2, 1.0F, 1.0F)),
			Map.entry("double_axe_swing01",
					new KeyframeSound(SND_DOUBLE_AXE_SWING1, 1.0F, 1.0F)),
			Map.entry("claymore_swing",
					new KeyframeSound(SND_CLAYMORE_SWING, 1.0F, 1.0F)),
			Map.entry("imp1", new KeyframeSound(SND_IMPACT, 1.0F, 1.0F)),
			Map.entry("anchor_slam01",
					new KeyframeSound(SND_ANCHOR_SLAM1, 1.0F, 1.0F)),
			Map.entry("smash_air1",
					new KeyframeSound(SND_SMASH_AIR1, 1.0F, 1.0F)),
			Map.entry("smash_ground1",
					new KeyframeSound(SND_SMASH_GROUND1, 1.0F, 1.0F)),
			Map.entry("smash_ground3",
					new KeyframeSound(SND_SMASH_GROUND3, 1.0F, 1.0F)));

	/** 一个玩家**同一个 tick 里**最多让服务端放几声关键帧音（防改包刷屏；正常最多 2 声）。 */
	public static final int KEYFRAME_SOUNDS_PER_TICK = 6;

	/**
	 * 专注 ramp 达到此值时触发"二级加速弹壳"粒子。
	 *
	 * <p>名字是历史遗留（以前这里也控制连射版枪声）；现在开火声已经统一为
	 * {@link #SND_SHOT3}，它只负责特效阈值。
	 */
	public static final int FOCUS_SHOT2_RAMP = 5;

	// ---- 物品判定：注册的物品本体（服务端/客户端都认）----
	public static boolean isGun(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof com.tk.gun.item.BowgunItem;
	}

	public static GunDefinition definition(ItemStack stack) {
		GunDefinition definition = GunRegistry.get(stack);
		return definition == null ? FortressDestroyerGun.DEFINITION : definition;
	}

	public static GunDefinition definition(PlayerEntity player) {
		if (player == null) {
			return FortressDestroyerGun.DEFINITION;
		}
		if (isGun(player.getMainHandStack())) {
			return definition(player.getMainHandStack());
		}
		return definition(player.getOffHandStack());
	}

	public static boolean holdingGun(PlayerEntity player) {
		return player != null && (isGun(player.getMainHandStack()) || isGun(player.getOffHandStack()));
	}

	private TkGun() {
	}
}
