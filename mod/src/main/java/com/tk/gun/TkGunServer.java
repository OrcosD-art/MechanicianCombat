package com.tk.gun;

import com.tk.gun.api.FortressDestroyerGun;
import com.tk.gun.api.AbyssalAncientCannonGun;
import com.tk.gun.api.GunDefinition;
import com.tk.gun.api.GunRegistry;
import com.tk.gun.item.BowgunItem;
import com.tk.gun.net.GunFirePayload;
import com.tk.gun.net.GunStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 服务端：物品注册 + 权威状态机（开火/过热/拉栓/滑铲/翻滚广播）。 */
public class TkGunServer implements ModInitializer {
	public static Item BOWGUN;
	public static Item ABYSSAL_ANCIENT_CANNON;
	public static Item BOLT;
	public static net.minecraft.block.Block BACKPACK_MODIFICATION_TABLE;
	public static net.minecraft.item.BlockItem BACKPACK_MODIFICATION_TABLE_ITEM;
	public static net.minecraft.block.entity.BlockEntityType<
			com.tk.gun.block.BackpackModificationTableBlockEntity>
			BACKPACK_MODIFICATION_TABLE_BLOCK_ENTITY;
	public static com.tk.gun.item.CombatBackpackItem COMBAT_BACKPACK;
	public static java.util.List<com.tk.gun.item.CombatBackpackItem> COMBAT_BACKPACKS;
	public static Item BACKPACK_CORE;
	public static Item PORTABLE_LAUNCHER;
	public static Item AUTO_PORTABLE_LAUNCHER;
	public static Item SUMMON_ANTENNA;
	public static Item BACKPACK_COOLDOWN_RESET;
	/** 只用来给创造模式标签当图标的物品（`视觉/创造模式标签.png`）。 */
	public static Item TAB_ICON;
	/** 素材 `子弹` 文件夹里的 5 种子弹（顺序：切割/幽匿/爆破/玫瑰石英/破甲）。 */
	public static java.util.List<Item> BULLET_ITEMS;
	/**
	 * 翻滚结束后仍认作"快速蹲射意图"的宽限（tick）。
	 *
	 * <p>翻滚只有 {@link CombatRollHook#ROLL_DURATION} tick，而 Z 包要跨线程排队；
	 * 没有这段宽限时，输入晚到一拍就会看到 ROLL 已被收成 IDLE，从而降级成普通举枪。
	 * 这是玩法窗口，和 `roll` 动画长度无关。
	 */
	private static final int ROLL_FAST_FOCUS_GRACE_TICKS = 6;
	/**
	 * 翻滚键与专注键的组合输入缓冲：翻滚键之后这段时间内到的 Z（或带组合标记的 Z）
	 * 先缓存，等 ROLL 真正开始再兑现成 0.38s 快速蹲射。
	 */
	private static final int DODGE_COMBO_BUFFER_TICKS = 10;
	/** 翻滚 / 滑铲结束后继续屏蔽右键瞄准的时间（1 秒 = 20 tick）。 */
	public static final int AIM_LOCK_AFTER_DODGE_TICKS = 20;
	/** One-charge fallback cooldown used only when Combat Roll is absent. */
	private static final int FALLBACK_ROLL_COOLDOWN_TICKS = 80;
	/** Backpack firework cycle: 1s CD + 3s cooldown + 1s ready. */
	private static final int BACKPACK_FIREWORK_COOLDOWN_TICKS = 100;
	private static final double BACKPACK_TARGET_RADIUS = 20.0D;
	private static final int BACKPACK_TARGET_WINDOW_TICKS = 160;
	private static final int BACKPACK_TARGET_GLOW_TICKS = BACKPACK_TARGET_WINDOW_TICKS;
	private static final int BACKPACK_MAX_CHARGES = 3;
	private static final int BACKPACK_CHARGE_INTERVAL_TICKS = 40;
	private static final int AUTO_BACKPACK_MAX_CHARGES = 10;
	private static final int AUTO_BACKPACK_ATTACK_INTERVAL_TICKS = 20;
	private static final int AUTO_BACKPACK_CHARGE_INTERVAL_TICKS = 20;
	private static final int AUTO_BACKPACK_COOLDOWN_TICKS = 200;
	private static final double AUTO_BACKPACK_TARGET_RADIUS = 30.0D;
	private static final int SUMMON_ANTENNA_READY_TICKS = 20;
	private static final int SUMMON_ANTENNA_CAST_TICKS = 60;
	private static final int SUMMON_ANTENNA_COOLDOWN_TICKS = 1200;
	private static final int SUMMON_ANTENNA_ALIVE_COOLDOWN_TICKS = 300;
	private static final double SUMMON_ANTENNA_TARGET_RADIUS = 20.0D;
	private static final double SUMMON_ANTENNA_FALLBACK_RANGE = 8.0D;
	private static final Map<UUID, BackpackCharge> BACKPACK_STATES = new HashMap<>();
	private static final Map<UUID, Long> BACKPACK_PROJECTILES = new HashMap<>();
	private static final int BACKPACK_PROJECTILE_TRACK_TICKS = 1200;

	private static final class BackpackCharge {
		int charges;
		long nextChargeAt;
		int targetEntityId = -1;
		UUID targetUuid;
		long targetUntil;
		long cooldownUntil;
		long lastSyncAt = -1L;
		com.tk.gun.item.BackpackUpgradeData.LauncherType launcherType =
				com.tk.gun.item.BackpackUpgradeData.LauncherType.NONE;
		boolean autoActive;
		boolean autoHoldAim;
		long nextAutoAttackAt;
		int autoShotsOnTarget;
		double frontOffsetX;
		double frontOffsetY;
		double frontOffsetZ;
		float frontOffsetYaw;
		long summonReadyUntil;
		long summonCastUntil;
		double summonX;
		double summonY;
		double summonZ;
	}

	/** 每玩家运行时状态 */
	public static final class PState {
		public float heat;
		public long overheatUntil;   // 世界 tick
		public long boltAt;          // 该 tick 播拉栓音
		public long lastFireTick = -1;
		public long stateUntil;
		public long stateStarted;
		public TkGun.State state = TkGun.State.IDLE;
		/**
		 * **翻滚后的快速蹲射宽限窗**（截止 tick）。
		 *
		 * <p>这是玩法意图，不是动画状态：翻滚刚结束的那一两拍里，服务端可能已经把
		 * {@link TkGun.State#ROLL} 收成 IDLE，随后才处理到 Z 包。若只看
		 * `state == ROLL`，这次快速蹲射就会被当成普通举枪甚至直接丢掉。
		 * 因此在每次翻滚开始时开一个短窗，窗口内按 Z 仍进入
		 * {@link TkGun.State#AIM_ROLL}（播 `rolling_fast_ready`）。宽限长度
		 * 基于资源 `roll` 换算出的 {@link CombatRollHook#ROLL_DURATION}。
		 */
		public long rollFocusUntil;
		/** 翻滚期间已经预输入 Z，等 {@link #rollFocusAt} 到点再执行快速蹲射。 */
		public boolean rollFocusRequested;
		/** 本次翻滚允许快速蹲射的绝对 tick（翻滚开始 + 0.38s 对应的 tick）。 */
		public long rollFocusAt;
		/** GunRollPayload 已结算闪避，短暂等待 Combat Roll 的物理翻滚事件。 */
		public boolean rollFallbackPending;
		public long rollFallbackAt;
		/** A focus-state roll was accepted by the key packet before the Combat Roll event arrived. */
		public boolean rollInputAccepted;
		/** 快速专注刚进入时吸收同一按 Z 产生的迟到重复包。 */
		public long quickFocusGuardUntil;
		/** 已经收到组合专注意图，但 ROLL 状态还没开始：等 broadcastRoll 再转成 rollFocusRequested。 */
		public boolean dodgeFocusQueued;
		public long dodgeFocusQueuedAt;
		/** 最近一次由滑铲进入 fast ready 的 tick，用于屏蔽同一个 Z 输入的重复包。 */
		public long lastSlideFastFocusAt;
		public long slideUntil;
		public long slideCooldownUntil;
		/**
		 * 翻滚 / 滑铲结束后继续屏蔽**右键瞄准输入**的截止 tick。
		 *
		 * <p>只影响右键举枪：不会复用 `dodgeBlockUntil`（那一条是 Alt 闪避冷却），
		 * 也不会阻止 Z 技能进入专注。行动状态本身由 {@link #aimInputLocked} 一并覆盖。
		 */
		public long aimLockUntil;
		/** 这段时间内右键 = 快速部署（滑铲/收势后短时间内有效） */
		public long fastAimUntil;
		/** 专注模式标记。按 Z 切换；开火后用于恢复架枪姿势，退出时播放 Focus_over。 */
		public boolean deployed;
		/** 滑铲方向（起铲时定下来） */
		public double slideDirX;
		public double slideDirZ;
		public long slideStart;
		public boolean slideMeasuring;
		public double slideFromX;
		public double slideFromZ;
		/** 开火已经起手、弹药还没出膛（等动画攻击帧） */
		public boolean pendingShot;
		public long shootAt;
		public long shootStartTick;
		public boolean probeShot;
		public ItemStack pendingAmmo;
		/** 等待播放和出膛的射击队列；每项记录对应的弹匣发次与弹药。 */
		public final java.util.ArrayDeque<Pending> shotQueue = new java.util.ArrayDeque<>();
		public boolean shotAnimating;
		public long shotReleaseAt;
		public long shotCycleAt;
		public int shotSeq;
		/** 上一 tick 的水平位置快照。 */
		public double lastX = Double.NaN;
		public double lastZ;
		/** 连射计数与窗口截止；用于状态诊断，不决定过热时机。 */
		public int combo;
		public long comboWindowUntil;
		/** 腰射弹匣已经打出去的子弹数；容量固定为 6 发。 */
		public int mag;
		/** 当前蹲射会话已经发射的数量；与腰射弹匣完全分离。 */
		public int focusShots;
		/** 当前/最近这一发是弹夹里第几发（1..10）：广播给客户端挑动画。 */
		public int shotIndex = 1;
		/** 最近一次扣扳机的 tick，用于射击节流和诊断。 */
		public long lastShotAt;
		/**
		 * **专注里的"越打越快/越打越疼"计数**（2026-10-15 用户："进入架枪状态之后连续射击，
		 * 射速会越来越快、伤害也会越来越高，然后就会过热"）。
		 *
		 * <p>连着打（间隔 ≤ {@link TkGun#COMBO_WINDOW}）就 +1；射出后每发按它算射速/伤害：
		 * 射速 = {@code SHOT_CYCLE_TICKS - ramp}（下限 {@link TkGun#FOCUS_CYCLE_MIN}），
		 * 伤害 = {@link TkGun#FOCUS_DAMAGE_START} + (ramp-1) × {@link TkGun#FOCUS_DAMAGE_STEP}
		 * （封顶 {@link TkGun#FOCUS_DAMAGE_MAX}）。专注弹匣最后一发触发过热。
		 * **腰射不参与**（腰射不涨热、不过热）。
		 */
		public int focusRamp;
		public long focusWindowUntil;
		/** 下一发隔几 tick（专注里随 {@link #focusRamp} 变快）。 */
		public int shotCycle = TkGun.SHOT_CYCLE_TICKS;
		/** 装填窗口的截止 tick（这一段时间里不能开火，HUD 显示"装填"）。 */
		public long reloadUntil;
		/** 换弹第二声（弹夹到位）的 tick —— v4 素材 `换弹2.ogg`，动画里埋在 0.833s 处。 */
		public long reloadClickAt;
		// —— 闪避/冲刺（`GunStateMachine` 用；规则表也在那边）——
		/** 上次闪避（翻滚/滑铲）开始的 tick：判"连锁闪避"。 */
		public long lastDodgeAt;
		/** 连锁闪避之后闪避键失效的截止 tick。 */
		public long dodgeLockUntil;
		/** 加速冲刺的截止 tick（每次闪避给 3 秒）。 */
		public long dashUntil;
		/** Last dash-active value sent to clients; used to broadcast the expiry. */
		public boolean dashSyncActive;
		/** Native fallback roll cooldown used only without Combat Roll. */
		public long fallbackRollReadyAt;
		/** Client-provided horizontal roll direction captured on the Alt press frame. */
		public double rollRequestX;
		public double rollRequestZ;
		// —— 客户端量出来的枪口/朝向（GunFirePayload 带过来，出伤帧用；用完即清） ——
		public double fireMx;
		public double fireMy;
		public double fireMz;
		public double fireDx;
		public double fireDy;
		public double fireDz;
		public boolean fireMuzzleOk;
		/** 上一 tick 是否持枪：重新持枪时广播一次权威弹匣状态。 */
		public boolean heldGunLastTick;
		// —— 动画音效关键帧（GunSoundPayload 报过来、服务端广播给附近所有人）的每 tick 额度桶 ——
		public long soundTick;
		public int soundCount;
		/**
		 * **蹲射准备那一声栓声**的预定 tick（0 = 没有）。
		 *
		 * <p>用户 2026-10-17："将蹲射准备动作的音频替换为…栓声"。作者把这一声埋在 `Focus_ready`
		 * 的 0.25s（快档 `fast_ready` 是 0.0833s）—— 这里按"那一拍占动画的比例 × 状态时长"换算成 tick，
		 * 由**服务端**排（附近所有人都听得见），客户端就不再报这一条关键帧（见 BowgunRig）。
		 */
		public long aimSoundAt;
		/**
		 * **翻滚 / 滑铲交替循环**里上一次是哪个：0 = 都没做过、1 = 刚翻滚、2 = 刚滑铲
		 * （用户 2026-10-17："翻滚后下一次翻滚再按 Alt 设为滑铲，翻滚与滑铲循环进行"）。
		 */
		public int lastDodgeKind;
		/** 循环一次（翻滚 + 滑铲）之后**两个都不许用**的截止 tick（1 秒）。 */
		public long dodgeBlockUntil;
		/** Ammo type loaded by the last hip-fire reload. */
		public boolean magExplosive;
		public String magAmmo = "";
		/** 腰射弹夹里那一发的模板（保留附魔；三叉戟式弹药发射时用）。 */
		public ItemStack magAmmoStack = ItemStack.EMPTY;
		/** 已写回枪械 NBT 的弹匣状态，避免每 tick 重复写入。 */
		public int lastSavedMag = -1;
		public String lastSavedAmmo = null;
		/** Last ammo type fired or prepared while deployed; restored after leaving focus. */
		public String lastFocusAmmo = "";
		/** 最近一次手持的枪械栈，切走后仍可把未开火/弹匣状态写回。 */
		public ItemStack lastHeldGun = ItemStack.EMPTY;
		/** 当前手持枪械的弹匣是否已经从未 NBT 载入（防止默认 0/空覆盖存档）。 */
		public boolean magLoaded;
		/** Last gun family; switching to a different registered definition resets focus-only state. */
		public Identifier gunDefinitionId;
	}

	/**
	 * 连发队列中的一发子弹（{@code shotIndex} = 当前弹匣中的第几发）。
	 */
	public static final class Pending {
		public final int shotIndex;
		public final String ammo;
		public final ItemStack ammoStack;
		public final boolean focus;

		private Pending(int shotIndex, String ammo, ItemStack ammoStack, boolean focus) {
			this.shotIndex = shotIndex;
			this.ammo = ammo == null ? "" : ammo;
			this.ammoStack = ammoStack == null ? ItemStack.EMPTY : ammoStack;
			this.focus = focus;
		}

		public static Pending shot(int shotIndex, String ammo) {
			return new Pending(shotIndex, ammo, ItemStack.EMPTY, false);
		}

		public static Pending shot(int shotIndex, String ammo, ItemStack ammoStack) {
			return new Pending(shotIndex, ammo, ammoStack, false);
		}

		public static Pending shot(int shotIndex, String ammo, ItemStack ammoStack, boolean focus) {
			return new Pending(shotIndex, ammo, ammoStack, focus);
		}
	}

	private static final Map<UUID, PState> STATES = new HashMap<>();

	public static PState state(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), k -> new PState());
	}

	/**
	 * 右键瞄准输入现在是否被权威锁定。
	 *
	 * <p>覆盖范围：整段翻滚 / 翻滚过渡 / 滑铲 / 滑铲收势，以及它们结束后
	 * {@link #AIM_LOCK_AFTER_DODGE_TICKS} tick。锁定期间 {@code BowgunItem#use} 直接失败，
	 * 服务端也会主动停止已经开始的“使用物品”，因此不会再进入举枪减速、镜头缩放或准星状态。
	 */
	public static boolean aimInputLocked(ServerPlayerEntity player) {
		PState st = state(player);
		long now = player.getWorld().getTime();
		return st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE
				|| st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY
				|| st.state == TkGun.State.OVERHEAT || st.state == TkGun.State.OVER
				|| st.state == TkGun.State.INSPECT
				|| now < st.aimLockUntil || now < st.dashUntil
				|| now < st.overheatUntil;
	}

	// ---------------------------------------------------------------- 曳光弹
	/**
	 * 在飞的弹丸 + **出膛时定下的速度**。
	 *
	 * <p>为什么要存速度：MC 的箭类弹丸（{@code PersistentProjectileEntity}）在自己 tick 里
	 * **无条件**加 `getGravity()`、还会乘空气阻力 `0.99` —— `setNoGravity(true)` 对它不管用。
	 * 用户要的是"打个直线、弹速快、一道曳光飞过去"，所以这里每 tick 把速度**掰回出膛值**
	 * （阻力也没了 → 全程 5 格/tick 直线；伤害 = 速度×damage 也保持恒定）。
	 */
	private record Bolt(net.minecraft.entity.projectile.PersistentProjectileEntity projectile, Vec3d velocity) {
	}

	private static final List<Bolt> BOLTS = new ArrayList<>();

	/**
	 * 弩矢弹丸的**实体类型**：tkgun:bolt_projectile。
	 * ⚠️ 用户 2026-10-12 反复强调："不要用弓箭当弹药" —— 之前复用 minecraft:arrow，
	 * 实体类型/死亡信息/掉落全是箭；现在用自己的类型（见 BoltProjectileEntity）。
	 */
	public static EntityType<com.tk.gun.entity.BoltProjectileEntity> BOLT_ENTITY;
	/**
	 * **子弹**（`tkgun:bullet`）的实体类型：只是画面上飞出去的那颗（贴图 = 素材 `粒子/bullet.png`）。
	 * 伤害不走它 —— 见 {@link #fireOnce} 与 {@link com.tk.gun.entity.BulletEntity}。
	 */
	public static EntityType<com.tk.gun.entity.BulletEntity> BULLET_ENTITY;
	/** 熔铁矢专用三叉戟实体（资源模型渲染，独立于原版 minecraft:trident）。 */
	public static EntityType<com.tk.gun.entity.MoltenTridentEntity> MOLTEN_TRIDENT_ENTITY;
	public static EntityType<com.tk.gun.entity.MoltenGolemEntity> MOLTEN_GOLEM;
	public static Item MOLTEN_GOLEM_SPAWN_EGG;
	public static Item ENTITY_REMOVAL_TOOL;
	/** 下一次开火时把弹丸位置反解成"枪口偏移"打进日志（`/tkgun probe`，自检用）。 */
	public static boolean PROBE_NEXT_SHOT = false;

	/**
	 * 枪口位置 = 模型里作者放的 `locator` 骨骼（parent = gun，pivot 模型坐标 0,10,-37）在
	 * **出伤帧**的位置。三个偏移常数（{@link TkGun#MUZZLE_FORWARD} 等）由
	 * `python tools\muzzle_offset_new.py` 从 geo + 动画按 GeckoLib 的变换链算出来，
	 * 七段射击动画在出伤帧完全一致。
	 *
	 * <p>朝向用**视角 yaw**：GeckoLib 的 GeoReplacedEntityRenderer 拿到的是渲染器传进来的
	 * entity yaw（= 玩家的视角 yaw，不像原版 PlayerEntityRenderer 那样换成 bodyYaw），
	 * 所以屏幕上整个 rig 就是按这个 yaw 摆的 —— 弹道用它才和"画面上那把枪"对得上。
	 */
	private static Vec3d muzzlePos(ServerPlayerEntity player) {
		double yaw = Math.toRadians(player.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double xx = -Math.cos(yaw);
		double xz = -Math.sin(yaw);
		return new Vec3d(
				player.getX() + fx * TkGun.MUZZLE_FORWARD + xx * TkGun.MUZZLE_SIDE,
				player.getY() + TkGun.MUZZLE_HEIGHT,
				player.getZ() + fz * TkGun.MUZZLE_FORWARD + xz * TkGun.MUZZLE_SIDE);
	}

	/**
	 * 子弹方向 = **枪管轴向**（水平的面朝方向），不看玩家视角俯仰。
	 * 用户 2026-10-13 原话："子弹射出点是 locator，沿着枪管方向射出，不必看玩家视角"。
	 * （模型里枪管方向在出伤帧 ≈ 水平正前，俯仰只有 -2.6°，当 0 处理。）
	 */
	private static Vec3d muzzleDir(ServerPlayerEntity player) {
		double yaw = Math.toRadians(player.getYaw());
		return new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
	}

	private static double round3(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}

	private static void tickBolts() {
		if (BOLTS.isEmpty()) {
			return;
		}
		Iterator<Bolt> it = BOLTS.iterator();
		while (it.hasNext()) {
			Bolt bolt = it.next();
			net.minecraft.entity.projectile.PersistentProjectileEntity arrow = bolt.projectile();
			if (arrow.isAlive()) {
				// 每 tick 把速度掰回出膛值：箭类弹丸自己会加 getGravity() 并乘 0.99 阻力，
				// setNoGravity(true) 对它无效 —— 掰回去才是"弹速快 + 一直直线"。
				arrow.setVelocity(bolt.velocity().x, bolt.velocity().y, bolt.velocity().z);
				arrow.velocityModified = true;
			}
			if (!(arrow.getWorld() instanceof ServerWorld world)) {
				it.remove();
				continue;
			}
			// 命中判定：死了（打中实体 / 打中方块后自毁）**或者**速度被弹丸自己清零了
			// （箭类弹丸打中方块会"插住"并把速度置 0；我们每 tick 掰速度是在这之后，
			//  所以这里看到速度≈0 就说明它刚插进方块里 → 一样算命中）。
			boolean stuck = arrow.getVelocity().lengthSquared() < 0.02D;
			if (!arrow.isAlive() || stuck) {
				double hx = arrow.getX(), hy = arrow.getY(), hz = arrow.getZ();
				// **命中特效**（用户："打中方块/敌人都有粒子特效"）：
				// 素材 粒子.zip 的 energy_xplo（6 帧翻页）+ 一簇火焰/烟/火花
				world.spawnParticles(TkGunParticles.ENERGY_XPLO, hx, hy, hz, 1, 0.0, 0.0, 0.0, 0.0);
				// ⚠️ 2026-10-17 用户："删除弹头击中后的 minecraft 原版爆炸特效及其音效" —— 原版
				// `ParticleTypes.EXPLOSION` 那团灰白爆炸云已删；素材自己的 `energy_xplo` + 火/烟/火花留着。
				world.spawnParticles(ParticleTypes.FLAME, hx, hy, hz, 14, 0.16, 0.16, 0.16, 0.08);
				world.spawnParticles(ParticleTypes.SMALL_FLAME, hx, hy, hz, 12, 0.2, 0.2, 0.2, 0.06);
				world.spawnParticles(ParticleTypes.CRIT, hx, hy, hz, 16, 0.2, 0.2, 0.2, 0.25);
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hx, hy, hz, 12, 0.18, 0.18, 0.18, 0.15);
				world.spawnParticles(ParticleTypes.SMOKE, hx, hy, hz, 10, 0.18, 0.18, 0.18, 0.05);
				// 命中声不在这里放：`fireOnce` 已经在命中点放了素材 `imp1`（服务端），这里再放就是两声。
				TkGun.LOGGER.info("[tkgun] 命中爆裂粒子已放（energy_xplo+FLAME+SMALL_FLAME+CRIT+"
						+ "ELECTRIC_SPARK+SMOKE，{} 个）位置=({}, {}, {})",
						1 + 14 + 12 + 16 + 12 + 10, round3(hx), round3(hy), round3(hz));
				if (stuck && arrow.isAlive()) {
					arrow.discard();          // 插住的弹丸实体清掉，别留一地看不见的箭
				}
				it.remove();
				continue;
			}
			Vec3d v = arrow.getVelocity();
			Vec3d pos = arrow.getPos();
			double len = v.length();
			if (len < 0.08D) {
				it.remove();     // 停了/卡住了，别在原地拖尾
				continue;
			}
			Vec3d back = pos.subtract(v);
			int steps = Math.max(1, (int) Math.ceil(len / 0.45D));
			for (int i = 0; i < steps; i++) {
				double f = i / (double) steps;
				double x = back.x + (pos.x - back.x) * f;
				double y = back.y + (pos.y - back.y) * f;
				double z = back.z + (pos.z - back.z) * f;
				world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
				if ((i & 1) == 0) {
					world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
				}
			}
		}
	}

	/**
	 * 所有背包变体共用同一个物品类、模型、动画和能力状态；只有贴图资源名不同。
	 */
	private static com.tk.gun.item.CombatBackpackItem registerCombatBackpack(
			String id, String textureName) {
		com.tk.gun.item.CombatBackpackItem item =
				Registry.register(Registries.ITEM, TkGun.id(id),
						new com.tk.gun.item.CombatBackpackItem(
								new Item.Settings().maxCount(1), textureName));
		io.wispforest.accessories.api.AccessoriesAPI.registerAccessory(item, item);
		COMBAT_BACKPACKS.add(item);
		return item;
	}

	@Override
	public void onInitialize() {
		// 持枪左键只用来开火，不进入方块破坏流程（类似拿着剑时原版不会挖方块）。
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
				!TkGun.holdingGun(player));
		TkGunParticles.register();
		Registry.register(
				Registries.SCREEN_HANDLER,
				TkGun.id("backpack_modification_table"),
				com.tk.gun.screen.BackpackModificationTableScreenHandler.TYPE);
		BACKPACK_MODIFICATION_TABLE = Registry.register(
				Registries.BLOCK,
				TkGun.id("backpack_modification_table"),
				new com.tk.gun.block.BackpackModificationTableBlock(
						net.minecraft.block.AbstractBlock.Settings.create()
								.mapColor(net.minecraft.block.MapColor.STONE_GRAY)
								.strength(3.5F)
								.requiresTool()
								.sounds(net.minecraft.sound.BlockSoundGroup.STONE)));
		BACKPACK_MODIFICATION_TABLE_ITEM = Registry.register(
				Registries.ITEM,
				TkGun.id("backpack_modification_table"),
				new net.minecraft.item.BlockItem(
						BACKPACK_MODIFICATION_TABLE,
						new Item.Settings()));
		BACKPACK_MODIFICATION_TABLE_BLOCK_ENTITY = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				TkGun.id("backpack_modification_table"),
				net.fabricmc.fabric.api.object.builder.v1.block.entity
						.FabricBlockEntityTypeBuilder.create(
								com.tk.gun.block.BackpackModificationTableBlockEntity::new,
								BACKPACK_MODIFICATION_TABLE)
						.build());
		com.tk.gun.block.BackpackModificationTableBlockEntity.TYPE =
				BACKPACK_MODIFICATION_TABLE_BLOCK_ENTITY;
		BACKPACK_CORE = Registry.register(
				Registries.ITEM,
				TkGun.id("backpack_core"),
				new com.tk.gun.item.BackpackPartItem(
						com.tk.gun.item.BackpackPartItem.Kind.CORE,
						new Item.Settings().maxCount(1)));
		PORTABLE_LAUNCHER = Registry.register(
				Registries.ITEM,
				TkGun.id("portable_launcher"),
				new com.tk.gun.item.BackpackPartItem(
						com.tk.gun.item.BackpackPartItem.Kind.LAUNCHER,
						new Item.Settings().maxCount(1)));
		AUTO_PORTABLE_LAUNCHER = Registry.register(
				Registries.ITEM,
				TkGun.id("auto_portable_launcher"),
				new com.tk.gun.item.BackpackPartItem(
						com.tk.gun.item.BackpackPartItem.Kind.AUTO_LAUNCHER,
						new Item.Settings().maxCount(1)));
		SUMMON_ANTENNA = Registry.register(
				Registries.ITEM,
				TkGun.id("summon_antenna"),
				new com.tk.gun.item.BackpackPartItem(
						com.tk.gun.item.BackpackPartItem.Kind.SUMMON_ANTENNA,
						new Item.Settings().maxCount(1)));
		BACKPACK_COOLDOWN_RESET = Registry.register(
				Registries.ITEM,
				TkGun.id("backpack_cooldown_reset"),
				new com.tk.gun.item.BackpackCooldownResetItem(new Item.Settings()
						.maxCount(1)
						.component(net.minecraft.component.DataComponentTypes
								.ENCHANTMENT_GLINT_OVERRIDE, true)));
		BOWGUN = GunRegistry.register(
				FortressDestroyerGun.DEFINITION,
				new Item.Settings().maxCount(1));
		ABYSSAL_ANCIENT_CANNON = GunRegistry.register(
				AbyssalAncientCannonGun.DEFINITION,
				new Item.Settings().maxCount(1));
		BOLT = Registry.register(Registries.ITEM, TkGun.id("bolt"),
				new com.tk.gun.item.MoltenTridentItem(new Item.Settings().maxCount(64)
						.attributeModifiers(net.minecraft.component.type.AttributeModifiersComponent.builder()
								.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
										new net.minecraft.entity.attribute.EntityAttributeModifier(
												Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, 9.6D,
												net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
										net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
								.add(EntityAttributes.GENERIC_ATTACK_SPEED,
										new net.minecraft.entity.attribute.EntityAttributeModifier(
												Item.BASE_ATTACK_SPEED_MODIFIER_ID, -2.9D,
												net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
										net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
								.build())
						.component(net.minecraft.component.DataComponentTypes.TOOL,
								net.minecraft.item.TridentItem.createToolComponent())));
		COMBAT_BACKPACKS = new java.util.ArrayList<>();
		COMBAT_BACKPACK = registerCombatBackpack("combat_backpack", "combat_backpack");
		for (String color : new String[]{
				"black", "blue", "brown", "cyan", "gray", "green",
				"light_blue", "light_gray", "lime", "magenta", "orange",
				"pink", "purple", "red", "shulker_box", "white", "yellow"
		}) {
			registerCombatBackpack("combat_backpack_" + color,
					"combat_backpack_" + color);
		}
		// **创造模式标签「机械师」的图标**（用户 2026-10-17）：图标只能用 ItemStack 渲染，所以拿
		// `视觉/创造模式标签.png` 做成一个只用来当图标的物品（不进标签本身）。
		TAB_ICON = Registry.register(Registries.ITEM, TkGun.id("tab_icon"),
				new Item(new Item.Settings()));
		MOLTEN_GOLEM = Registry.register(Registries.ENTITY_TYPE, TkGun.id("molten_golem"),
				EntityType.Builder.<com.tk.gun.entity.MoltenGolemEntity>create(
								com.tk.gun.entity.MoltenGolemEntity::new, SpawnGroup.MISC)
						.dimensions(1.4F, 2.7F)
						.eyeHeight(2.35F)
						.build("molten_golem"));
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
				.register(MOLTEN_GOLEM,
						net.minecraft.entity.passive.IronGolemEntity.createIronGolemAttributes()
								.add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0D));
		net.minecraft.item.SpawnEggItem ironEgg =
				net.minecraft.item.SpawnEggItem.forEntity(EntityType.IRON_GOLEM);
		int eggPrimary = ironEgg == null ? 0xD7D7D7 : ironEgg.getColor(0);
		int eggSecondary = ironEgg == null ? 0x9B9B9B : ironEgg.getColor(1);
		MOLTEN_GOLEM_SPAWN_EGG = Registry.register(Registries.ITEM,
				TkGun.id("molten_golem_spawn_egg"),
				new net.minecraft.item.SpawnEggItem(MOLTEN_GOLEM,
						eggPrimary, eggSecondary, new Item.Settings()
								.component(net.minecraft.component.DataComponentTypes
										.ENCHANTMENT_GLINT_OVERRIDE, true)));
		ENTITY_REMOVAL_TOOL = Registry.register(Registries.ITEM,
				TkGun.id("entity_removal_tool"),
				new com.tk.gun.item.EntityRemovalToolItem(new Item.Settings()
						.maxCount(1)
						.component(net.minecraft.component.DataComponentTypes
								.ENCHANTMENT_GLINT_OVERRIDE, true)));
		// **素材 `子弹` 文件夹里的 5 种子弹**（用户 2026-10-17："将…子弹文件夹中的子弹进行注册"）：
		// 共用一张 16×16 图集（`textures/item/bullets.png`），各自一个 Java 方块模型
		// （`models/item/<id>.json`，UV 用像素坐标）。原本的 `tkgun:bolt`（红头）模型已改成**爆破弹**。
		BULLET_ITEMS = new java.util.ArrayList<>();
		for (String id : new String[]{"cutting_bolt", "sculk_bolt", "explosive_bolt",
				"rose_quartz_bolt", "armor_piercing_bolt"}) {
			BULLET_ITEMS.add(Registry.register(Registries.ITEM, TkGun.id(id),
					new com.tk.gun.item.BoltItem(new Item.Settings())));
		}
		// **创造模式标签「机械师」**：枪 + 子弹放进去（用户 2026-10-17）。
		Registry.register(Registries.ITEM_GROUP, TkGun.id("main"),
				net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
						.displayName(net.minecraft.text.Text.translatable("itemGroup.tkgun"))
						.icon(() -> new ItemStack(TAB_ICON))
						.entries((context, entries) -> {
							entries.add(BACKPACK_MODIFICATION_TABLE_ITEM);
							for (Item gun : GunRegistry.items()) {
								entries.add(gun);
							}
							entries.add(BOLT);
							entries.add(COMBAT_BACKPACK);
							entries.add(BACKPACK_CORE);
							entries.add(PORTABLE_LAUNCHER);
							entries.add(AUTO_PORTABLE_LAUNCHER);
							entries.add(SUMMON_ANTENNA);
							entries.add(BACKPACK_COOLDOWN_RESET);
							entries.add(MOLTEN_GOLEM_SPAWN_EGG);
							entries.add(ENTITY_REMOVAL_TOOL);
							for (Item bullet : BULLET_ITEMS) {
								entries.add(bullet);
							}
						})
						.build());
		Registry.register(Registries.SCREEN_HANDLER, TkGun.id("ammo_storage"),
				com.tk.gun.screen.AmmoStorageScreenHandler.TYPE);
		BOLT_ENTITY = Registry.register(Registries.ENTITY_TYPE, TkGun.id("bolt_projectile"),
				EntityType.Builder.<com.tk.gun.entity.BoltProjectileEntity>create(
								com.tk.gun.entity.BoltProjectileEntity::new, SpawnGroup.MISC)
						.dimensions(0.25F, 0.25F)
						.build("bolt_projectile"));
		BULLET_ENTITY = Registry.register(Registries.ENTITY_TYPE, TkGun.id("bullet"),
				EntityType.Builder.<com.tk.gun.entity.BulletEntity>create(
								com.tk.gun.entity.BulletEntity::new, SpawnGroup.MISC)
						.dimensions(0.25F, 0.25F)
						.build("bullet"));
		MOLTEN_TRIDENT_ENTITY = Registry.register(Registries.ENTITY_TYPE, TkGun.id("molten_trident"),
				EntityType.Builder.<com.tk.gun.entity.MoltenTridentEntity>create(
								com.tk.gun.entity.MoltenTridentEntity::new, SpawnGroup.MISC)
						.dimensions(0.5F, 0.5F)
						.build("molten_trident"));
		PayloadTypeRegistry.playS2C().register(GunStatePayload.ID, GunStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.tk.gun.net.GunHitMarkerPayload.ID,
				com.tk.gun.net.GunHitMarkerPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.tk.gun.net.GunDemoPayload.ID,
				com.tk.gun.net.GunDemoPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.tk.gun.net.CombatBackpackFireResultPayload.ID,
				com.tk.gun.net.CombatBackpackFireResultPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.tk.gun.net.CombatBackpackStatusPayload.ID,
				com.tk.gun.net.CombatBackpackStatusPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GunFirePayload.ID, GunFirePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GunFirePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					if (TkGun.holdingGun(p)) {
						// 客户端量的枪口/朝向（渲染 locator 骨骼拿到的那个点）：合理性校验一次再用
						PState st = state(p);
						Vec3d pos = new Vec3d(payload.x(), payload.y(), payload.z());
						Vec3d dir = new Vec3d(payload.dx(), payload.dy(), payload.dz());
						double dist = pos.distanceTo(p.getPos());
						if (dir.lengthSquared() > 1.0E-6D && dist <= 6.0D) {
							st.fireMx = pos.x;
							st.fireMy = pos.y;
							st.fireMz = pos.z;
							st.fireDx = dir.x;
							st.fireDy = dir.y;
							st.fireDz = dir.z;
							st.fireMuzzleOk = true;
						} else {
							st.fireMuzzleOk = false;
							TkGun.LOGGER.warn("[tkgun] 客户端报的枪口不合理（离玩家 {} 格），这一发退回服务端计算",
									round3(dist));
						}
						tryFire(p, false);
					}
				}));
		// —— Z 键（按住 = 专注）/ F 键（装填）（2026-10-17 改）——
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.GunFocusPayload.ID,
				com.tk.gun.net.GunFocusPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.GunReloadPayload.ID,
				com.tk.gun.net.GunReloadPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunFocusPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					if (!TkGun.holdingGun(p)) {
						return;
					}
					PState st = state(p);
					long now = p.getWorld().getTime();
					// **闪避键 + Z 同时按**：翻滚与滑铲共用同一个“组合专注”缓冲。
				// 如果 Z 先到，直接 startFocus 会先进普通 Focus_ready，随后翻滚/滑铲键又因为
				// “蹲射准备中”被拒。带组合标记 / 刚收到闪避键时先缓存，等真正的 ROLL 或 SLIDE 开始再兑现。
					boolean comboIntent = payload.dodgeCombo()
							|| st.state == TkGun.State.ROLL
							|| st.state == TkGun.State.SLIDE
							|| st.state == TkGun.State.SLIDE_READY;
					// 同一次组合输入可能已经在这一 tick 由 rollFocusRequested 兑现了；
					// 晚到的重复 Z 包不要再排队，否则缓冲过期会把已经开始的快速专注又 toggle 掉。
					if ((st.state == TkGun.State.AIM_ROLL || st.state == TkGun.State.AIM_SLIDE)
							&& (comboIntent || now < st.quickFocusGuardUntil)) {
						return;
					}
					// 动作已经真正开始：直接走 startFocus 已有的 ROLL/SLIDE 分支。
					if (comboIntent && (st.state == TkGun.State.ROLL
							|| st.state == TkGun.State.SLIDE
							|| st.state == TkGun.State.SLIDE_READY)) {
						st.dodgeFocusQueued = false;
						st.dodgeFocusQueuedAt = 0;
						startFocus(p);
						return;
					}
					// 动作还没开始（包顺序不定）：先存一个通用闪避专注意图，
					// 由第一个真正开始的 ROLL / SLIDE 消费，避免两种组合互相冲突。
					if (comboIntent) {
						st.dodgeFocusQueued = true;
						st.dodgeFocusQueuedAt = now;
						TkGun.LOGGER.info("[tkgun] Z+闪避组合输入先缓存（当前 {}，等 ROLL/SLIDE 开始）玩家={}",
								st.state, p.getName().getString());
						return;
					}
					// **Z = 切换专注**（用户 2026-10-17："将 z 的长按专注回调为切换"）：
					// 已专注 → 解除（播 `Focus_over`）；否则 → 进入（播 `Focus_ready`）。
					// `startFocus` 内部就是这两个分支（部署中会转成 stopFocus）。
					boolean entering = !st.deployed;
					startFocus(p);
					TkGun.LOGGER.info("[tkgun] Z -> {} 玩家={}",
							entering ? "进入专注" : "解除专注（Focus_over）", p.getName().getString());
				}));
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunReloadPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					if (!TkGun.holdingGun(p)) {
						return;
					}
					PState st = state(p);
					long now = p.getWorld().getTime();
					if (st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE
							|| st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY) {
						return;
					}
					if (now < st.reloadUntil) {
						return;                       // 正在装填
					}
					// **过热期间屏蔽装填**（用户 2026-10-17）：这里拦的是**整个散热窗口**
					// （`overheatUntil` 到期前，含过热动画播完之后还在散热的那段）——客户端只看得见
					// `OVERHEAT` 状态那一段，所以权威判定放在服务端。
					// **过热里"翻滚后冲刺窗口"内允许立刻装弹**（用户 2026-10-17："过热状态翻滚后的加速期间
					// 允许无视过热立即装弹"）：`now < st.dashUntil` 就放行；其它时间照旧拦。
					if (now < st.overheatUntil && now >= st.dashUntil) {
						playSound(p, TkGun.SND_VENT, 0.7F, 1.1F);
						TkGunTexts.say(p, TkGunTexts.OVERHEAT_NO_RELOAD, true);
						TkGun.LOGGER.info("[tkgun] F 被拒（过热中，还剩 {} tick）玩家={}",
								st.overheatUntil - now, p.getName().getString());
						return;
					}
					TkGun.LOGGER.info("[tkgun] F -> 手动装填（当前弹夹 {}/{}）玩家={}",
							activeMag(st), TkGun.magazineCapacity(p, st.deployed),
							p.getName().getString());
					reload(p);
				}));
		// —— 翻滚键（combat_roll 的）：见 GunRollPayload 的注释 ——
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.GunRollPayload.ID,
				com.tk.gun.net.GunRollPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunRollPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					PState st = state(p);
					st.rollRequestX = payload.directionX();
					st.rollRequestZ = payload.directionZ();
					long rollNow = p.getWorld().getTime();
					if (st.rollFallbackPending) {
						return;
					}
					if (st.dodgeFocusQueued
							&& rollNow > st.dodgeFocusQueuedAt + DODGE_COMBO_BUFFER_TICKS) {
						st.dodgeFocusQueued = false;
						st.dodgeFocusQueuedAt = 0;
					}
					// 滑铲/收势期间 Alt 不参与翻滚；等这一段结束后由状态机决定。
					if (st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY) {
						TkGun.LOGGER.info("[tkgun] 滑铲/收势中，这次 Alt 忽略 玩家={}", p.getName().getString());
						return;
					}
					// 任何仍在翻滚/翻滚过渡中的 Alt 都不能再次启动翻滚。
					if (st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE) {
						TkGun.LOGGER.info("[tkgun] 翻滚/过渡中，这次 Alt 忽略 玩家={}", p.getName().getString());
						return;
					}
					// **蹲射准备期间（AIM/AIM_FAST/AIM_SLIDE 过门）屏蔽闪避键**（用户 2026-10-17）；
					// **蹲射保持（AIM_HOLD）里闪避一定是翻滚**（把 kind 归 0，跳过下面的滑铲分支）。
					if (st.state == TkGun.State.AIM || st.state == TkGun.State.AIM_FAST
							|| st.state == TkGun.State.AIM_ROLL
							|| st.state == TkGun.State.AIM_SLIDE) {
						TkGun.LOGGER.info("[tkgun] 翻滚键：蹲射准备中，忽略 玩家={}", p.getName().getString());
						return;
					}
					if (st.state == TkGun.State.AIM_HOLD) {
						st.lastDodgeKind = 0;
					}
					// **蹲射准备期间不能翻滚**（用户 2026-10-17）：AIM / AIM_FAST / AIM_SLIDE 是"架枪过门"，
					// 这段里按 Alt 一律忽略（不翻滚、不滑铲，动画不被打断）。
					if (st.state == TkGun.State.AIM || st.state == TkGun.State.AIM_FAST
							|| st.state == TkGun.State.AIM_ROLL
							|| st.state == TkGun.State.AIM_SLIDE) {
						TkGun.LOGGER.info("[tkgun] 蹲射准备中（{}），这次 Alt 忽略 玩家={}",
								st.state, p.getName().getString());
						return;
					}
					// **蹲射时闪避必定是翻滚**（用户 2026-10-17）：`deployed`（架枪/蹲射）里不走滑铲分支，
					// 一律交给下面的翻滚流程。
					boolean forceRoll = st.deployed;
					// **2026-10-17 用户**："翻滚后下一次翻滚再按 Alt 设为滑铲，翻滚与滑铲循环进行，
					// 循环一次后屏蔽 1 秒，翻滚与滑铲皆不可进行"。
					//   lastDodgeKind = 1（刚翻滚）→ 这次 Alt = **强制位移滑铲**（`sliding` 动画）；
					//                      滑完把 kind 归 0，并屏蔽 20 tick（1 秒，两个都不能用）；
					//   lastDodgeKind = 0/2     → 这次 Alt 走**翻滚**（下面原来的流程，含 combat_roll 的物理翻滚）。
					if (rollNow < st.dodgeBlockUntil) {
						return;
					}
					if (st.lastDodgeKind == 1 && !forceRoll && rollNow < st.dashUntil) {
						// **只有翻滚播完才能滑铲**（用户 2026-10-17）：翻滚途中按 Alt 直接忽略（不打断动画）。
						if (st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE) {
							TkGun.LOGGER.info("[tkgun] 翻滚还没播完（剩 {} tick），这次 Alt 不滑铲 玩家={}",
									st.stateUntil - rollNow, p.getName().getString());
							return;
						}
						startModSlide(p, st, rollNow);
						st.lastDodgeKind = 0;
						return;
					}
					if (st.lastDodgeKind == 1) {
						st.lastDodgeKind = 0;
						TkGun.LOGGER.info("[tkgun] roll dash window expired -> reset dodge cycle, this Alt starts a new roll player={}",
								p.getName().getString());
					}
					// The native roll cooldown gates a new roll, not the slide that belongs
					// to the previous roll's dash window.
					if (!CombatRollHook.isInstalled()
							&& rollNow < st.fallbackRollReadyAt) {
						TkGun.LOGGER.info("[tkgun] 内置翻滚冷却中，拒绝 Alt（剩余 {} tick）玩家={}",
								st.fallbackRollReadyAt - rollNow, p.getName().getString());
						rebroadcast(p);
						return;
					}
					// 只有"过热 / 退出过门（Focus_over）"这两段需要我们插手：
					// **回到 idle 并立刻让翻滚接管** = 打断动画 + 清掉缓慢（缓慢会把移速属性压小，
					// combat_roll 判定 `GENERIC_MOVEMENT_SPEED > 0` 才允许翻滚）+ 状态切成 ROLL。
					if (st.state == TkGun.State.OVERHEAT || st.state == TkGun.State.OVER) {
						interruptForRoll(p, st, "翻滚按键");
					}
					// **和普通翻滚同一套奖励**（用户 2026-10-17："专注状态下进行翻滚…给予相同效果"）：
					// 3 秒 +30% 加速冲刺。走这条路的多半是"combat_roll 因为移速被压住而没滚成"的情况，
					// 以前只补了冲刺位移、没有这份加成。连锁闪避冷却里 `onDodgeStarted` 会拒绝（返回 true）。
					if (GunStateMachine.onDodgeStarted(p, st, p.getWorld().getTime(), true)) {
						return;
					}
					st.rollInputAccepted = true;
					clearPendingShots(p, st, "翻滚离开专注");
					// 翻滚 = 离开专注（和翻滚事件那条路一致）：不然人会带着"专注里被屏蔽的输入"原地晃，
					// 而且模型还停在 Focus 循环上、看不到翻滚动作。
					st.deployed = false;
					st.focusShots = 0;
					st.focusRamp = 0;
					if (!CombatRollHook.isInstalled()) {
						startFallbackRoll(p, st, rollNow);
						return;
					}
					// Combat Roll 事件可能比 GunRollPayload 晚 1～2 tick 到达。先等待事件；
					// 只有确认玩家仍未被物理翻滚带走时才执行兜底，避免双重初速和方向覆盖。
					if (p.getVelocity().horizontalLength() < 0.15D) {
						st.rollFallbackPending = true;
						st.rollFallbackAt = rollNow + 2;
					}
				}));
		// —— 动画音效关键帧：客户端"到点了"，服务端**放给附近所有人** ——
		// 用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见"。这件事两边各出一半：关键帧的**时间**
		// 只有客户端知道（GeckoLib 按动画时间回调，倍速也会自动跟着缩放），而**让附近所有人都听见**只有
		// 服务端做得到（`ServerWorld#playSound` 广播给范围内所有玩家，连发的人自己也会收到那一份）。
		// ⚠️ 这个 id 只能注册一次（第二十六轮那次启动崩溃就是同一个 id 注册了两遍）。
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.GunSoundPayload.ID,
				com.tk.gun.net.GunSoundPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.CombatBackpackFirePayload.ID,
				com.tk.gun.net.CombatBackpackFirePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunSoundPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					TkGun.KeyframeSound kf = TkGun.KEYFRAME_SOUNDS.get(payload.sound());
					if (kf == null || !TkGun.holdingGun(p)) {
						return;             // 不认识的名字 / 手里不是破垒重炮：忽略（别让改包的拿它当点唱机）
					}
					long now = p.getWorld().getTime();
					PState st = state(p);
					if (st.soundTick != now) {           // 每 tick 限制关键帧音效数量，避免重复触发刷屏
						st.soundTick = now;
						st.soundCount = 0;
					}
					if (++st.soundCount > TkGun.KEYFRAME_SOUNDS_PER_TICK) {
						return;
					}
					playSound(p, kf.id(), kf.volume(), kf.pitch());
				}));
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.CombatBackpackFirePayload.ID,
				(payload, context) -> context.server().execute(() ->
						handleBackpackAction(context.player(), payload)));
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.OpenAmmoStoragePayload.ID,
				com.tk.gun.net.OpenAmmoStoragePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.tk.gun.net.SwitchAmmoPayload.ID,
				com.tk.gun.net.SwitchAmmoPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.OpenAmmoStoragePayload.ID,
				(payload, context) -> context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					if (!TkGun.holdingGun(p)) {
						return;
					}
					p.openHandledScreen(com.tk.gun.screen.AmmoStorageScreenHandler.createFactory());
				}));
		ServerPlayNetworking.registerGlobalReceiver(com.tk.gun.net.SwitchAmmoPayload.ID,
				(payload, context) -> context.server().execute(() -> {
					ServerPlayerEntity p = context.player();
					if (!TkGun.holdingGun(p)) {
						return;
					}
					ItemStack gun = com.tk.gun.item.GunAmmoData.heldGun(p);
					int before = gun.isEmpty() ? -1 : com.tk.gun.item.GunAmmoData.selected(gun);
					com.tk.gun.item.GunAmmoData.cycle(p, payload.direction());
					int after = gun.isEmpty() ? -1 : com.tk.gun.item.GunAmmoData.selected(gun);
					PState st = state(p);
					if (before != after
							&& (!st.shotQueue.isEmpty() || st.shotAnimating || st.boltAt > 0)) {
						clearPendingShots(p, st, "切换弹种");
					}
				}));
		TkGun.LOGGER.info("[tkgun] 数据包注册完成：C2S = fire / focus_held / reload_key / roll_key / sound_key（都在服务端执行）");
		ServerTickEvents.END_SERVER_TICK.register(TkGunServer::tickAll);
		TkGunCommand.register();
		if (CombatRollHook.isInstalled()) {
			CombatRollHook.register();
		}
		// 动画段数**从资源里数**（别再写死：v1 是 15 段、v2 是 38 段、v3/v4 都是 22 段 —— 硬编码的数字
		// 每次换素材都会过期，2026-10-16 就是这么发现上一行还写着 38 的）。
		TkGun.LOGGER.info("[tkgun] 服务端就绪: 物品={} 玩家/枪动画={}（GeckoLib rig，{} 段）构建={}",
				TkGun.id("bowgun"), "tkgun:animations/entity/bowgun_rig.animation.json",
				animationCount(), TkGun.BUILD);
		TkGun.LOGGER.info("[tkgun] 声音资源自检: {}（sounds.json 每条都要有对应 ogg；"
				+ "分发出去之后所有声音才生效）", soundAssetCheck());
	}

	/**
	 * **声音资源自检**：`sounds.json` 里每一条的 `name` 都要能在包里找到对应的 `sounds/<path>.ogg`。
	 *
	 * <p>用户 2026-10-17："要求此 mod 在分发后所有声音都生效" —— 声音全在 mod 自己的 jar 里
	 * （`assets/tkgun/sounds/*.ogg` + `sounds.json`），**而且全部由服务端播**
	 * （`TkGunServer.playSound` / 命中声 / 动画关键帧那条 `GunSoundPayload`），所以打包分发后
	 * 单机、局域网、专用服、整合包里的旁观者都听得见。这里只是启动时把"有没有漏文件"打出来。
	 */
	private static String soundAssetCheck() {
		String base = "assets/tkgun/";
		java.util.List<String> missing = new java.util.ArrayList<>();
		int total = 0;
		try (java.io.InputStream in = TkGunServer.class.getClassLoader()
				.getResourceAsStream(base + "sounds.json")) {
			if (in == null) {
				return "sounds.json 缺失";
			}
			String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			com.google.gson.JsonObject doc = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
			for (String event : doc.keySet()) {
				com.google.gson.JsonObject entry = doc.getAsJsonObject(event);
				for (com.google.gson.JsonElement el : entry.getAsJsonArray("sounds")) {
					String name = el.getAsJsonObject().get("name").getAsString();
					String path = base + "sounds/" + name.substring(name.indexOf(':') + 1) + ".ogg";
					total++;
					if (TkGunServer.class.getClassLoader().getResource(path) == null) {
						missing.add(event + " -> " + path);
					}
				}
			}
		} catch (Throwable t) {
			return "自检失败: " + t;
		}
		return missing.isEmpty() ? (total + "/" + total + " OK") : ("缺 " + missing);
	}

	/** 数一遍 rig 动画文件里有几段（读不到就返回 "?"，不影响启动）。 */
	private static String animationCount() {
		String path = "assets/tkgun/animations/entity/bowgun_rig.animation.json";
		try (java.io.InputStream in = TkGunServer.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return "?";
			}
			String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			com.google.gson.JsonObject doc = com.google.gson.JsonParser
					.parseString(text).getAsJsonObject().getAsJsonObject("animations");
			return String.valueOf(doc.size());
		} catch (Throwable t) {
			TkGun.LOGGER.warn("[tkgun] 数动画段数失败: {}", t.toString());
			return "?";
		}
	}

	// ---------------------------------------------------------------- 广播
	private static void removeGunFromOffhand(ServerPlayerEntity player) {
		// Server-side final guard for commands, legacy saves and other mods.
		net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
		ItemStack offhand = inventory.getStack(net.minecraft.entity.player.PlayerInventory.OFF_HAND_SLOT);
		if (!TkGun.isGun(offhand)) {
			return;
		}

		ItemStack gun = inventory.removeStack(net.minecraft.entity.player.PlayerInventory.OFF_HAND_SLOT);
		if (inventory.getMainHandStack().isEmpty()) {
			inventory.setStack(inventory.selectedSlot, gun);
		} else {
			inventory.offerOrDrop(gun);
		}
		inventory.markDirty();
		player.playerScreenHandler.syncState();
		if (player.currentScreenHandler != player.playerScreenHandler) {
			player.currentScreenHandler.syncState();
		}
		TkGun.LOGGER.warn("[tkgun] blocked gun in offhand; moved to main hand or inventory, player={}",
				player.getName().getString());
	}

	private static void broadcast(MinecraftServer server, ServerPlayerEntity actor, TkGun.State state,
								  int ticks, float heat) {
		broadcast(server, actor, state, ticks, heat, state(actor).shotIndex);
	}

	private static void broadcast(MinecraftServer server, ServerPlayerEntity actor, TkGun.State state,
								  int ticks, float heat, int shotIndex) {
		PState st = state(actor);
		long now = actor.getWorld().getTime();
		int aimLockTicks = (int) Math.max(0, st.aimLockUntil - now);
		int dashTicks = (int) Math.max(0, st.dashUntil - now);
		int heatLockTicks = (int) Math.max(0, st.overheatUntil - now);
		int rollCooldownTicks = CombatRollHook.isInstalled()
				? 0 : (int) Math.max(0, st.fallbackRollReadyAt - now);
		// "在装填窗口里吗" 跟着状态一起发：HUD 靠它显示"装填"，模型那边靠它知道弹夹正在插回去
		GunStatePayload payload = new GunStatePayload(actor.getId(), state.id, ticks, (int) heat, shotIndex,
				activeMag(st), now < st.reloadUntil,
				st.deployed && TkGun.holdingGun(actor), st.shotSeq,
				now < st.dashUntil, dashTicks, aimLockTicks, heatLockTicks, st.magAmmo,
				rollCooldownTicks,
				TkGun.magazineCapacity(actor, false),
				TkGun.magazineCapacity(actor, true),
				TkGun.definition(actor).maxShotIndex());
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static int activeMag(PState st) {
		return st.deployed ? st.focusShots : st.mag;
	}

	private static void setState(MinecraftServer server, ServerPlayerEntity player, TkGun.State state, int ticks) {
		PState st = state(player);
		long stateNow = player.getWorld().getTime();
		// **翻滚期间不许别的状态顶掉它**（用户 2026-10-17："解决翻滚状态时会被其他动作打断并影响该动画的问题"）。
		// 之前按 Z（进专注）或 F（装填）会直接把 ROLL 换成 AIM_FAST / RELOAD，rig 当场切走，翻滚动画只播一半。
		// 只放行两种：① 又是 ROLL（再滚一次刷新时长）；② ROLL 已经到点（`stateUntil` 过了）——
		// 状态机自己的收尾（IDLE / 翻滚后接快速架枪）都走这一条。
		// 2026-10-17 用户："滑铲会打断翻滚动画，调整为只有完成翻滚后才能够进行滑铲" ——
		// 恢复成"翻滚没播完不许换状态"（SLIDE 也不再放行）；滑铲的入口在下面自己判"翻滚已结束"。
		if (st.state == TkGun.State.ROLL && state != TkGun.State.ROLL && stateNow < st.stateUntil) {
			TkGun.LOGGER.info("[tkgun] 翻滚中 -> 忽略状态切换 {}（翻滚还剩 {} tick，先让动画播完）玩家={}",
					state, st.stateUntil - stateNow, player.getName().getString());
			return;
		}
		// 状态机每一次切换都打一行：自动化验证时不用猜——直接从服务端日志就能读出
		// "何时进了 Focus_ready / Focus_shooting / Focus_over_shooting / sliding / sliding_ready"。
		if (st.state != state) {
			TkGun.LOGGER.info("[tkgun] 状态 {} -> {} ({} ticks) 玩家={}",
					st.state, state, ticks, player.getName().getString());
		}
		final boolean entering = st.state != state;
		st.state = state;
		st.stateStarted = player.getWorld().getTime();
		st.stateUntil = ticks <= 0 ? 0 : st.stateStarted + ticks;
		// **蹲射准备那一声 = 素材 `栓声.ogg`**（用户 2026-10-17："将蹲射准备动作的音频替换为…栓声"）。
		// 作者把它埋在 `Focus_ready` 的 0.25s、`fast_ready` 的 0.0833s —— 按"那一拍占动画的比例 ×
		// 这个状态的 tick 数"换算：AIM（24 tick / 1.0s）→ 6；AIM_FAST（14 tick / 0.667s）→ 2；
		// AIM_SLIDE（7 tick，约 2 倍速）→ 1。统一放在这个**状态切换的唯一入口**里，所有进架枪的路
		// （Z / 滑铲抢枪 / 换弹后重举 / 翻滚收势）都自动带上，不会漏也不会重复。
		// 这一声由服务端放（附近所有人都听得见）；客户端那几段不再报 `bolt`，免得旁观者听到两声。
		if (entering && (state == TkGun.State.AIM || state == TkGun.State.AIM_FAST
				|| state == TkGun.State.AIM_ROLL
				|| state == TkGun.State.AIM_SLIDE)) {
			st.aimSoundAt = st.stateStarted + (state == TkGun.State.AIM ? 6
					: (state == TkGun.State.AIM_FAST || state == TkGun.State.AIM_ROLL ? 2 : 1));
		}
		broadcast(server, player, state, ticks, st.heat);
	}

	/**
	 * 把**当前状态原样再播一包**（动画不会重头播：客户端用的是同一份 RawAnimation 实例）。
	 *
	 * <p>用途：包里的字段变了、但状态没变的时候 —— 比如强化状态到点/中途收枪，
	 * `over` 位要立刻告诉客户端（否则得等 5 秒心跳，那期间扣扳机客户端还会按"连射"发包）。
	 */
	public static void rebroadcast(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PState st = state(player);
		int left = st.stateUntil > 0
				? (int) Math.max(0, st.stateUntil - player.getWorld().getTime()) : 0;
		broadcast(server, player, st.state, left, st.heat, st.shotIndex);
	}

	public static void playSound(ServerPlayerEntity player, Identifier id, float volume, float pitch) {
		player.getServerWorld().playSound(null, player.getBlockPos(),
				SoundEvent.of(id), SoundCategory.PLAYERS, volume, pitch);
	}

	private static void handleBackpackAction(ServerPlayerEntity player,
			com.tk.gun.net.CombatBackpackFirePayload payload) {
		long now = player.getWorld().getTime();
		if (!com.tk.gun.item.BackpackUpgradeData.canUseLauncher(
				com.tk.gun.item.CombatBackpackItem.equippedStack(player))) {
			clearBackpackState(player);
			sendBackpackFireResult(player);
			return;
		}
		BackpackCharge state = backpackState(player, now);
		if (now < state.cooldownUntil) {
			syncBackpackStatus(player, state, now);
			sendBackpackFireResult(player);
			return;
		}
		if (state.launcherType
				== com.tk.gun.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			handleSummonAntennaAction(player, state, payload, now);
			return;
		}
		if (state.launcherType
				== com.tk.gun.item.BackpackUpgradeData.LauncherType.AUTO) {
			toggleAutoBackpack(player, state, payload, now);
			return;
		}
		if (com.tk.gun.item.BackpackLauncherRules.isTridentLike(
				player.getStackInHand(Hand.OFF_HAND))) {
			if (state.charges <= 0) {
				syncBackpackStatus(player, state, now);
				sendBackpackFireResult(player);
			} else {
				clearBackpackMarker(player, state, now);
				launchBackpackProjectile(player, payload, null, state);
			}
			return;
		}
		LivingEntity target = activeBackpackTarget(player, state, now);
		if (target == null) {
			markBackpackTarget(player, state, payload);
		} else if (state.charges <= 0) {
			syncBackpackStatus(player, state, now);
			sendBackpackFireResult(player);
		} else {
			launchBackpackProjectile(player, payload, target, state);
		}
	}

	private static void handleSummonAntennaAction(ServerPlayerEntity player,
			BackpackCharge state, com.tk.gun.net.CombatBackpackFirePayload payload,
			long now) {
		int phase = summonAntennaPhase(state, now);
		if (phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT) {
			clearBackpackTarget(state, now);
			state.summonReadyUntil = now + SUMMON_ANTENNA_READY_TICKS;
			state.summonCastUntil = 0;
		} else if (phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_ARMED) {
			LivingEntity target = findSummonAntennaTarget(player, payload);
			if (target == null) {
				beginSummonAntennaCast(player, state, payload, now, null);
			} else {
				state.targetEntityId = target.getId();
				state.targetUuid = target.getUuid();
				state.targetUntil = now + SUMMON_ANTENNA_COOLDOWN_TICKS;
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
						SUMMON_ANTENNA_COOLDOWN_TICKS, 0, false, false, true), player);
				player.getServerWorld().playSound(null, player.getBlockPos(),
						SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
						SoundCategory.PLAYERS, TkGun.BACKPACK_SOUND_VOLUME, 1.2F);
			}
		} else if (phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_MARKED) {
			LivingEntity target = activeBackpackTarget(player, state, now);
			beginSummonAntennaCast(player, state, payload, now, target);
		}
		syncBackpackStatus(player, state, now);
		sendBackpackFireResult(player);
	}

	private static void tickSummonAntenna(ServerPlayerEntity player,
			BackpackCharge state, long now) {
		if (state.summonCastUntil > 0 && now >= state.summonCastUntil) {
			if (player.getServerWorld().getEntityById(state.targetEntityId)
					instanceof LivingEntity target) {
				target.removeStatusEffect(StatusEffects.GLOWING);
			}
			com.tk.gun.entity.MoltenGolemEntity golem =
					findLivingSummonedGolem(player, state.summonX, state.summonY,
							state.summonZ);
			boolean summoned = false;
			if (golem != null) {
				golem.setSummoner(player.getUuid());
				golem.beginRelocationSummon(state.summonX, state.summonY,
						state.summonZ, player.getYaw());
				summoned = true;
			} else {
				golem = MOLTEN_GOLEM.create(player.getServerWorld());
				if (golem != null) {
					golem.refreshPositionAndAngles(state.summonX, state.summonY,
							state.summonZ, player.getYaw(), 0.0F);
					golem.setSummoner(player.getUuid());
					summoned = player.getServerWorld().spawnEntity(golem);
				}
			}
			clearBackpackTarget(state, now);
			state.summonReadyUntil = 0;
			state.summonCastUntil = 0;
			state.cooldownUntil = now + (summoned
					? SUMMON_ANTENNA_ALIVE_COOLDOWN_TICKS
					: SUMMON_ANTENNA_COOLDOWN_TICKS);
		}
	}

	private static com.tk.gun.entity.MoltenGolemEntity findLivingSummonedGolem(
			ServerPlayerEntity player, double x, double y, double z) {
		Vec3d destination = new Vec3d(x, y, z);
		com.tk.gun.entity.MoltenGolemEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Entity entity : player.getServerWorld().iterateEntities()) {
			if (!(entity instanceof com.tk.gun.entity.MoltenGolemEntity golem)
					|| !golem.isAlive() || golem.isRemoved()
					|| !golem.isSummonedBy(player.getUuid())) {
				continue;
			}
			double distance = golem.squaredDistanceTo(destination);
			if (distance < nearestDistance) {
				nearest = golem;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static int summonAntennaPhase(BackpackCharge state, long now) {
		if (now < state.cooldownUntil) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_COOLDOWN;
		}
		if (state.summonCastUntil > 0) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_CAST;
		}
		if (state.summonReadyUntil > now) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_READY;
		}
		if (state.targetEntityId >= 0 && now < state.targetUntil) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_MARKED;
		}
		if (state.summonReadyUntil > 0) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_ARMED;
		}
		return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT;
	}

	private static LivingEntity findSummonAntennaTarget(ServerPlayerEntity player,
			com.tk.gun.net.CombatBackpackFirePayload payload) {
		Vec3d cameraPos = new Vec3d(payload.cameraX(), payload.cameraY(), payload.cameraZ());
		Vec3d forward = Vec3d.fromPolar(
				(float) payload.cameraPitch(), (float) payload.cameraYaw()).normalize();
		double yaw = Math.toRadians(payload.cameraYaw());
		Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, -Math.sin(yaw));
		Vec3d up = right.crossProduct(forward).normalize();
		double halfHorizontal = Math.max(0.02D,
				Math.min(4.0D, payload.halfHorizontalTan()));
		double halfVertical = Math.max(0.02D,
				Math.min(4.0D, payload.halfVerticalTan()));
		Vec3d playerCenter = player.getBoundingBox().getCenter();
		net.minecraft.util.math.Box search =
				player.getBoundingBox().expand(SUMMON_ANTENNA_TARGET_RADIUS);
		LivingEntity best = null;
		double bestAngle = -2.0D;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : player.getServerWorld().getEntitiesByClass(
				LivingEntity.class, search,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.canHit() && !CombatTargeting.isGolem(entity))) {
			Vec3d offset = candidate.getBoundingBox().getCenter().subtract(cameraPos);
			if (offset.lengthSquared() < 1.0E-8D
					|| playerCenter.squaredDistanceTo(candidate.getBoundingBox().getCenter())
					> SUMMON_ANTENNA_TARGET_RADIUS * SUMMON_ANTENNA_TARGET_RADIUS) {
				continue;
			}
			double depth = offset.dotProduct(forward);
			if (depth <= 0.0D
					|| Math.abs(offset.dotProduct(right)) > depth * halfHorizontal
					|| Math.abs(offset.dotProduct(up)) > depth * halfVertical
					|| !hasBlockLineOfSight(player, cameraPos,
							candidate.getBoundingBox().getCenter())) {
				continue;
			}
			double alignment = offset.normalize().dotProduct(forward);
			double distance = offset.lengthSquared();
			if (alignment > bestAngle
					|| (Math.abs(alignment - bestAngle) < 1.0E-6D
							&& distance < bestDistance)) {
				best = candidate;
				bestAngle = alignment;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean hasBlockLineOfSight(ServerPlayerEntity player,
			Vec3d from, Vec3d to) {
		return player.getServerWorld().raycast(new RaycastContext(from, to,
						RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE, player))
				.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
	}

	private static void beginSummonAntennaCast(ServerPlayerEntity player,
			BackpackCharge state, com.tk.gun.net.CombatBackpackFirePayload payload,
			long now, LivingEntity target) {
		Vec3d origin = new Vec3d(payload.x(), payload.y(), payload.z());
		Vec3d playerCenter = player.getBoundingBox().getCenter();
		if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y)
				|| !Double.isFinite(origin.z)
				|| origin.squaredDistanceTo(playerCenter) > 64.0D) {
			origin = player.getBoundingBox().getCenter();
		}
		Vec3d destination;
		if (target != null && target.isAlive()) {
			destination = new Vec3d(target.getX(), target.getY(), target.getZ());
		} else {
			Vec3d direction = Vec3d.fromPolar(
					(float) payload.cameraPitch(), (float) payload.cameraYaw()).normalize();
			Vec3d start = new Vec3d(payload.cameraX(), payload.cameraY(),
					payload.cameraZ());
			Vec3d end = start.add(direction.multiply(SUMMON_ANTENNA_FALLBACK_RANGE));
			net.minecraft.util.hit.BlockHitResult hit = player.getServerWorld().raycast(
					new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
							RaycastContext.FluidHandling.NONE, player));
			destination = hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
					? hit.getPos() : end;
		}
		state.summonX = destination.x;
		state.summonY = destination.y;
		state.summonZ = destination.z;
		state.summonCastUntil = now + SUMMON_ANTENNA_CAST_TICKS;
		state.summonReadyUntil = 0;
		spawnSummonAntennaShriek(player.getServerWorld(), origin);
	}

	private static void spawnSummonAntennaShriek(ServerWorld world,
			Vec3d origin) {
		// Match a sculk shrieker: ten delayed copies from one point, moving up.
		for (int index = 0; index < 10; index++) {
			world.spawnParticles(
					new net.minecraft.particle.ShriekParticleEffect(index * 5),
					origin.x, origin.y, origin.z,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		world.playSound(null, origin.x, origin.y, origin.z,
				SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
				SoundCategory.BLOCKS, 2.0F,
				0.6F + world.random.nextFloat() * 0.4F);
	}

	public static void clearBackpackCooldown(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		BackpackCharge state = backpackState(player, now);
		if (player.getServerWorld().getEntityById(state.targetEntityId)
				instanceof LivingEntity target) {
			target.removeStatusEffect(StatusEffects.GLOWING);
		}
		clearBackpackTarget(state, now);
		state.cooldownUntil = 0;
		state.summonReadyUntil = 0;
		state.summonCastUntil = 0;
		state.nextChargeAt = 0;
		state.lastSyncAt = -1;
		syncBackpackStatus(player, state, now);
		sendBackpackFireResult(player);
	}

	private static BackpackCharge backpackState(ServerPlayerEntity player, long now) {
		BackpackCharge state = BACKPACK_STATES.computeIfAbsent(
				player.getUuid(), ignored -> new BackpackCharge());
		com.tk.gun.item.BackpackUpgradeData.LauncherType type =
				com.tk.gun.item.BackpackUpgradeData.launcherType(
						com.tk.gun.item.CombatBackpackItem.equippedStack(player));
		if (state.launcherType != type) {
			if (player.getServerWorld().getEntityById(state.targetEntityId)
					instanceof LivingEntity previousTarget) {
				previousTarget.removeStatusEffect(StatusEffects.GLOWING);
			}
			clearBackpackTarget(state, now);
			state.launcherType = type;
			state.autoActive = false;
			state.autoHoldAim = false;
			state.cooldownUntil = type
					== com.tk.gun.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA
					? now + (findLivingSummonedGolem(player, player.getX(),
							player.getY(), player.getZ()) != null
							? SUMMON_ANTENNA_ALIVE_COOLDOWN_TICKS
							: SUMMON_ANTENNA_COOLDOWN_TICKS)
					: 0;
			state.nextAutoAttackAt = 0;
			state.autoShotsOnTarget = 0;
			state.nextChargeAt = 0;
			state.charges = type
					== com.tk.gun.item.BackpackUpgradeData.LauncherType.AUTO
					? AUTO_BACKPACK_MAX_CHARGES : 0;
			state.summonReadyUntil = 0;
			state.summonCastUntil = 0;
		}
		int maxCharges = maxBackpackCharges(state.launcherType);
		state.charges = Math.max(0, Math.min(maxCharges, state.charges));
		if (state.cooldownUntil <= now && state.targetEntityId < 0
				&& state.charges < maxCharges && state.nextChargeAt <= 0
				&& !state.autoActive
				&& state.launcherType
						!= com.tk.gun.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			state.nextChargeAt = now + backpackChargeInterval(state.launcherType);
		}
		return state;
	}

	private static void markBackpackTarget(ServerPlayerEntity player, BackpackCharge state,
										   com.tk.gun.net.CombatBackpackFirePayload payload) {
		ServerWorld world = player.getServerWorld();
		Vec3d center = player.getBoundingBox().getCenter();
		net.minecraft.util.math.Box search = player.getBoundingBox()
				.expand(BACKPACK_TARGET_RADIUS);
		double maxDistanceSq = BACKPACK_TARGET_RADIUS * BACKPACK_TARGET_RADIUS;
		double bestDistanceSq = Double.MAX_VALUE;
		LivingEntity nearest = null;
		for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, search,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.canHit() && !CombatTargeting.isGolem(entity))) {
			double distanceSq = candidate.getBoundingBox().getCenter()
					.squaredDistanceTo(center);
			if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq
					&& backpackTargetVisible(candidate, center, payload)) {
				nearest = candidate;
				bestDistanceSq = distanceSq;
			}
		}
		if (nearest == null) {
			clearBackpackTarget(state, world.getTime());
			syncBackpackStatus(player, state, world.getTime());
			return;
		}

		long now = world.getTime();
		nearest.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
				BACKPACK_TARGET_GLOW_TICKS, 0, false, false, true), player);
		state.targetEntityId = nearest.getId();
		state.targetUuid = nearest.getUuid();
		state.targetUntil = now + BACKPACK_TARGET_WINDOW_TICKS;
		state.nextChargeAt = 0;
		syncBackpackStatus(player, state, now);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
				SoundCategory.PLAYERS, TkGun.BACKPACK_SOUND_VOLUME, 1.0F);
		TkGun.LOGGER.info("[tkgun] 战斗背包锁定最近生物 id={} 距离={} 玩家={}",
				nearest.getId(), round3(Math.sqrt(bestDistanceSq)),
				player.getName().getString());
	}

	/**
	 * Five blocks or closer stay omnidirectional. Farther targets must project
	 * inside the client camera's current viewport, while the 20-block radius is
	 * deliberately unchanged.
	 */
	private static boolean backpackTargetVisible(LivingEntity candidate, Vec3d playerCenter,
			com.tk.gun.net.CombatBackpackFirePayload payload) {
		Vec3d targetCenter = candidate.getBoundingBox().getCenter();
		if (targetCenter.squaredDistanceTo(playerCenter) <= 25.0D) {
			return true;
		}
		Vec3d cameraPos = new Vec3d(payload.cameraX(), payload.cameraY(), payload.cameraZ());
		if (!Double.isFinite(cameraPos.x) || !Double.isFinite(cameraPos.y)
				|| !Double.isFinite(cameraPos.z)
				|| cameraPos.squaredDistanceTo(playerCenter) > 64.0D) {
			return false;
		}
		double halfHorizontal = Math.max(0.02D,
				Math.min(4.0D, payload.halfHorizontalTan()));
		double halfVertical = Math.max(0.02D,
				Math.min(4.0D, payload.halfVerticalTan()));
		Vec3d forward = Vec3d.fromPolar(
				(float) payload.cameraPitch(), (float) payload.cameraYaw());
		if (forward.lengthSquared() < 1.0E-8D) {
			return false;
		}
		double yaw = Math.toRadians(payload.cameraYaw());
		Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, -Math.sin(yaw));
		Vec3d up = right.crossProduct(forward).normalize();
		Vec3d offset = targetCenter.subtract(cameraPos);
		double depth = offset.dotProduct(forward);
		if (depth <= 0.0D) {
			return false;
		}
		return Math.abs(offset.dotProduct(right)) <= depth * halfHorizontal
				&& Math.abs(offset.dotProduct(up)) <= depth * halfVertical;
	}

	private static LivingEntity activeBackpackTarget(ServerPlayerEntity player,
													 BackpackCharge state, long now) {
		if (state.targetEntityId < 0) {
			return null;
		}
		Entity entity = player.getServerWorld().getEntityById(state.targetEntityId);
		if (now >= state.targetUntil || !(entity instanceof LivingEntity living)
				|| !living.isAlive() || CombatTargeting.isGolem(living)
				|| !living.getUuid().equals(state.targetUuid)) {
			clearBackpackTarget(state, now);
			return null;
		}
		return living;
	}

	/** 当前仍在高亮窗口内、由该玩家战斗背包标记的实体。 */
	public static LivingEntity backpackHighlightedTarget(ServerPlayerEntity owner) {
		BackpackCharge state = BACKPACK_STATES.get(owner.getUuid());
		if (state == null) {
			return null;
		}
		return activeBackpackTarget(owner, state, owner.getWorld().getTime());
	}

	private static void clearBackpackTarget(BackpackCharge state, long now) {
		state.targetEntityId = -1;
		state.targetUuid = null;
		state.targetUntil = 0;
		if (state.cooldownUntil <= now) {
			state.nextChargeAt = now + backpackChargeInterval(state.launcherType);
		}
	}

	private static int backpackPhase(BackpackCharge state, long now) {
		if (state.launcherType
				== com.tk.gun.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			return summonAntennaPhase(state, now);
		}
		if (now < state.cooldownUntil) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_COOLDOWN;
		}
		if (state.autoActive) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE;
		}
		if (state.autoHoldAim) {
			return com.tk.gun.net.CombatBackpackStatusPayload.PHASE_AUTO_PAUSED;
		}
		return state.targetEntityId >= 0 && now < state.targetUntil
				? com.tk.gun.net.CombatBackpackStatusPayload.PHASE_MARKED
				: com.tk.gun.net.CombatBackpackStatusPayload.PHASE_READY;
	}

	private static int maxBackpackCharges(
			com.tk.gun.item.BackpackUpgradeData.LauncherType type) {
		return type == com.tk.gun.item.BackpackUpgradeData.LauncherType.AUTO
				? AUTO_BACKPACK_MAX_CHARGES : BACKPACK_MAX_CHARGES;
	}

	private static int backpackChargeInterval(
			com.tk.gun.item.BackpackUpgradeData.LauncherType type) {
		return type == com.tk.gun.item.BackpackUpgradeData.LauncherType.AUTO
				? AUTO_BACKPACK_CHARGE_INTERVAL_TICKS : BACKPACK_CHARGE_INTERVAL_TICKS;
	}

	private static void syncBackpackStatus(ServerPlayerEntity owner,
										   BackpackCharge state, long now) {
		ServerWorld world = owner.getServerWorld();
		int phase = backpackPhase(state, now);
		int remaining = phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_COOLDOWN
				? (int) Math.max(0, state.cooldownUntil - now)
				: phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_READY
						? (int) Math.max(0, state.summonReadyUntil - now)
				: phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_CAST
						? (int) Math.max(0, state.summonCastUntil - now)
				: phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_MARKED
						|| phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE
				? (int) Math.max(0, state.targetUntil - now) : 0;
		boolean targetPhase =
				phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_MARKED
						|| phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE
						|| phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_MARKED
						|| phase == com.tk.gun.net.CombatBackpackStatusPayload.PHASE_SUMMON_CAST;
		com.tk.gun.net.CombatBackpackStatusPayload payload =
				new com.tk.gun.net.CombatBackpackStatusPayload(owner.getId(),
						targetPhase
								? state.targetEntityId : -1,
						phase, state.charges, remaining);
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			ServerPlayNetworking.send(viewer, payload);
		}
		state.lastSyncAt = now;
	}

	private static void tickBackpackStates(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!com.tk.gun.item.BackpackUpgradeData.canUseLauncher(
					com.tk.gun.item.CombatBackpackItem.equippedStack(player))) {
				clearBackpackState(player);
				continue;
			}
			long now = player.getWorld().getTime();
			BackpackCharge state = backpackState(player, now);
			int previousPhase = backpackPhase(state, now);
			int previousCharges = state.charges;
			if (state.launcherType
					== com.tk.gun.item.BackpackUpgradeData.LauncherType.AUTO) {
				tickAutoBackpack(player, state, now);
			} else if (state.launcherType
					== com.tk.gun.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
				tickSummonAntenna(player, state, now);
			} else {
				tickPortableBackpack(player, state, now);
			}
			if (previousPhase != backpackPhase(state, now)
					|| previousCharges != state.charges
					|| state.lastSyncAt < 0 || now - state.lastSyncAt >= 5) {
				syncBackpackStatus(player, state, now);
			}
		}
	}

	private static void tickPortableBackpack(ServerPlayerEntity player,
			BackpackCharge state, long now) {
		if (state.cooldownUntil > 0) {
			if (now >= state.cooldownUntil) {
				state.cooldownUntil = 0;
				state.charges = BACKPACK_MAX_CHARGES;
				state.nextChargeAt = 0;
			}
		} else if (activeBackpackTarget(player, state, now) == null) {
			if (state.nextChargeAt <= 0) {
				state.nextChargeAt = now + BACKPACK_CHARGE_INTERVAL_TICKS;
			}
			if (now >= state.nextChargeAt && state.charges < BACKPACK_MAX_CHARGES) {
				state.charges++;
				state.nextChargeAt = state.charges >= BACKPACK_MAX_CHARGES
						? 0 : now + BACKPACK_CHARGE_INTERVAL_TICKS;
			}
		}
	}

	private static void tickAutoBackpack(ServerPlayerEntity player,
			BackpackCharge state, long now) {
		if (state.cooldownUntil > 0) {
			if (now >= state.cooldownUntil) {
				state.cooldownUntil = 0;
				state.charges = AUTO_BACKPACK_MAX_CHARGES;
				state.nextChargeAt = 0;
				state.autoHoldAim = false;
			}
			return;
		}
		if (!state.autoActive) {
			if (state.charges < AUTO_BACKPACK_MAX_CHARGES) {
				if (state.nextChargeAt <= 0) {
					state.nextChargeAt = now + AUTO_BACKPACK_CHARGE_INTERVAL_TICKS;
				}
				if (now >= state.nextChargeAt) {
					state.charges++;
					state.nextChargeAt = state.charges >= AUTO_BACKPACK_MAX_CHARGES
							? 0 : now + AUTO_BACKPACK_CHARGE_INTERVAL_TICKS;
				}
			}
			return;
		}
		if (!player.getStackInHand(Hand.OFF_HAND).isOf(Items.FIREWORK_ROCKET)) {
			stopAutoBackpack(player, state, now, false, true);
			sendBackpackFireResult(player);
			return;
		}

		LivingEntity target = activeBackpackTarget(player, state, now);
		if (target == null || !autoTargetVisible(player, target)) {
			int oldTarget = state.targetEntityId;
			target = findAutoTarget(player, -1);
			if (oldTarget != targetId(target)
					&& player.getServerWorld().getEntityById(oldTarget)
					instanceof LivingEntity oldLiving) {
				oldLiving.removeStatusEffect(StatusEffects.GLOWING);
			}
			if (target == null) {
				state.targetEntityId = -1;
				state.targetUuid = null;
				state.targetUntil = 0;
			} else {
				state.targetEntityId = target.getId();
				state.targetUuid = target.getUuid();
				state.targetUntil = now + 20;
				state.autoShotsOnTarget = 0;
			}
		}
		if (target != null) {
			state.targetUntil = now + 20;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
					20, 0, false, false, true), player);
			if (now >= state.nextAutoAttackAt) {
				if (fireAutoBackpack(player, state, target, now)) {
					state.nextAutoAttackAt =
							now + AUTO_BACKPACK_ATTACK_INTERVAL_TICKS;
				}
			}
		}
	}

	private static int targetId(LivingEntity target) {
		return target == null ? -1 : target.getId();
	}

	private static boolean autoTargetVisible(ServerPlayerEntity player,
			LivingEntity target) {
		Vec3d eye = player.getEyePos();
		Vec3d playerCenter = player.getBoundingBox().getCenter();
		Vec3d center = target.getBoundingBox().getCenter();
		Vec3d fromPlayer = center.subtract(playerCenter);
		if (fromPlayer.lengthSquared()
				> AUTO_BACKPACK_TARGET_RADIUS * AUTO_BACKPACK_TARGET_RADIUS
				|| fromPlayer.lengthSquared() < 1.0E-8D) {
			return false;
		}
		Vec3d offset = center.subtract(eye);
		Vec3d direction = player.getRotationVector();
		return offset.normalize().dotProduct(direction) >= 0.0D
				&& player.canSee(target);
	}

	private static LivingEntity findAutoTarget(ServerPlayerEntity player, int excludedEntityId) {
		ServerWorld world = player.getServerWorld();
		net.minecraft.util.math.Box search =
				player.getBoundingBox().expand(AUTO_BACKPACK_TARGET_RADIUS);
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, search,
				entity -> entity != player && entity.getId() != excludedEntityId
						&& entity.isAlive() && !entity.isSpectator()
						&& entity.canHit() && !CombatTargeting.isGolem(entity)
						&& autoTargetVisible(player, entity))) {
			double distance = candidate.squaredDistanceTo(player);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static void toggleAutoBackpack(ServerPlayerEntity player,
			BackpackCharge state, com.tk.gun.net.CombatBackpackFirePayload payload,
			long now) {
		if (state.autoActive) {
			stopAutoBackpack(player, state, now, false, true);
			sendBackpackFireResult(player);
			return;
		}
		if (state.charges <= 0
				|| !player.getStackInHand(Hand.OFF_HAND).isOf(Items.FIREWORK_ROCKET)) {
			syncBackpackStatus(player, state, now);
			sendBackpackFireResult(player);
			return;
		}
		LivingEntity target = findAutoTarget(player, -1);
		if (target == null) {
			syncBackpackStatus(player, state, now);
			sendBackpackFireResult(player);
			return;
		}
		captureAutoSpawnOffset(player, state, payload);
		state.autoActive = true;
		state.autoHoldAim = false;
		state.nextAutoAttackAt = now;
		state.autoShotsOnTarget = 0;
		state.targetEntityId = target.getId();
		state.targetUuid = target.getUuid();
		state.targetUntil = now + 20;
		fireAutoBackpack(player, state, target, now);
		if (state.charges > 0) {
			state.nextAutoAttackAt = now + AUTO_BACKPACK_ATTACK_INTERVAL_TICKS;
		}
		syncBackpackStatus(player, state, now);
	}

	private static void captureAutoSpawnOffset(ServerPlayerEntity player,
			BackpackCharge state, com.tk.gun.net.CombatBackpackFirePayload payload) {
		Vec3d requested = new Vec3d(payload.x(), payload.y(), payload.z());
		Vec3d spawn = Double.isFinite(requested.x) && Double.isFinite(requested.y)
				&& Double.isFinite(requested.z) && player.squaredDistanceTo(requested) <= 64.0D
				? requested
				: player.getPos().add(0.0D, player.getStandingEyeHeight() - 0.15D, 0.0D);
		Vec3d offset = spawn.subtract(player.getPos());
		float yaw = -player.getBodyYaw();
		double radians = Math.toRadians(yaw);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		state.frontOffsetX = offset.x * cos - offset.z * sin;
		state.frontOffsetY = offset.y;
		state.frontOffsetZ = offset.x * sin + offset.z * cos;
		state.frontOffsetYaw = yaw;
	}

	private static Vec3d autoSpawnPosition(ServerPlayerEntity player,
			BackpackCharge state) {
		double radians = Math.toRadians(player.getBodyYaw());
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return player.getPos().add(
				state.frontOffsetX * cos - state.frontOffsetZ * sin,
				state.frontOffsetY,
				state.frontOffsetX * sin + state.frontOffsetZ * cos);
	}

	private static boolean fireAutoBackpack(ServerPlayerEntity player,
			BackpackCharge state, LivingEntity target, long now) {
		ItemStack stored = player.getStackInHand(Hand.OFF_HAND);
		if (!stored.isOf(Items.FIREWORK_ROCKET) || state.charges <= 0) {
			stopAutoBackpack(player, state, now, false, true);
			sendBackpackFireResult(player);
			return false;
		}
		Vec3d spawn = autoSpawnPosition(player, state);
		Vec3d targetCenter = target.getBoundingBox().getCenter();
		Vec3d direction = targetCenter.subtract(spawn).normalize();
		if (direction.lengthSquared() < 1.0E-8D) {
			return false;
		}
		ItemStack launched = stored.copyWithCount(1);
		com.tk.gun.item.BackpackFireworkData.markNoSelfDamage(launched);
		Entity projectile = createBackpackProjectile(player, launched, spawn, direction);
		if (projectile == null) {
			return false;
		}
		projectile.addCommandTag(CombatTargeting.BACKPACK_PROJECTILE_TAG);
		projectile.setPosition(spawn.x, spawn.y, spawn.z);
		BACKPACK_PROJECTILES.put(projectile.getUuid(),
				now + BACKPACK_PROJECTILE_TRACK_TICKS);
		if (!player.getServerWorld().spawnEntity(projectile)) {
			BACKPACK_PROJECTILES.remove(projectile.getUuid());
			return false;
		}
		stored.decrement(1);
		player.getInventory().markDirty();
		state.charges--;
		playSound(player, TkGun.SND_GUNSHOT, 0.9F, 1.0F);
		sendBackpackFireResult(player);
		if (state.charges <= 0) {
			stopAutoBackpack(player, state, now, true, false);
		} else {
			state.autoShotsOnTarget++;
			if (state.autoShotsOnTarget >= 2) {
				LivingEntity next = findAutoTarget(player, target.getId());
				if (next != null) {
					target.removeStatusEffect(StatusEffects.GLOWING);
					state.targetEntityId = next.getId();
					state.targetUuid = next.getUuid();
					state.targetUntil = now + 20;
					state.autoShotsOnTarget = 0;
				}
			}
		}
		return true;
	}

	private static void stopAutoBackpack(ServerPlayerEntity player,
			BackpackCharge state, long now, boolean depleted, boolean holdAim) {
		state.autoActive = false;
		state.autoHoldAim = holdAim;
		state.nextAutoAttackAt = 0;
		state.autoShotsOnTarget = 0;
		int targetId = state.targetEntityId;
		state.targetEntityId = -1;
		state.targetUuid = null;
		state.targetUntil = 0;
		if (player.getServerWorld().getEntityById(targetId)
				instanceof LivingEntity living) {
			living.removeStatusEffect(StatusEffects.GLOWING);
		}
		if (depleted && state.charges <= 0) {
			state.cooldownUntil = now + AUTO_BACKPACK_COOLDOWN_TICKS;
			state.nextChargeAt = 0;
			state.autoHoldAim = false;
		} else if (state.charges < AUTO_BACKPACK_MAX_CHARGES) {
			state.nextChargeAt = now + AUTO_BACKPACK_CHARGE_INTERVAL_TICKS;
		}
		syncBackpackStatus(player, state, now);
	}

	private static void clearBackpackState(ServerPlayerEntity player) {
		BackpackCharge state = BACKPACK_STATES.remove(player.getUuid());
		if (state == null) {
			return;
		}
		if (state.targetEntityId < 0) {
			return;
		}
		if (player.getServerWorld().getEntityById(state.targetEntityId)
				instanceof LivingEntity living) {
			living.removeStatusEffect(StatusEffects.GLOWING);
		}
	}

	private static void launchBackpackProjectile(ServerPlayerEntity player,
			com.tk.gun.net.CombatBackpackFirePayload payload, LivingEntity target,
			BackpackCharge state) {
		long now = player.getWorld().getTime();

		ItemStack stored = player.getStackInHand(Hand.OFF_HAND);
		if (!com.tk.gun.item.BackpackLauncherRules.isLaunchable(stored)) {
			sendBackpackFireResult(player);
			return;
		}

		Vec3d requested = new Vec3d(payload.x(), payload.y(), payload.z());
		Vec3d fallback = player.getPos().add(0.0D, player.getStandingEyeHeight() - 0.15D, 0.0D);
		Vec3d spawn = Double.isFinite(requested.x) && Double.isFinite(requested.y)
				&& Double.isFinite(requested.z) && player.squaredDistanceTo(requested) <= 64.0D
				? requested : fallback;
		Vec3d targetCenter = target == null
				? crosshairAimPoint(player, payload)
				: target.getBoundingBox().getCenter();
		Vec3d direction = targetCenter.subtract(spawn).normalize();
		if (direction.lengthSquared() < 1.0E-8D) {
			direction = player.getRotationVector();
		}
		ItemStack launchedStack = stored.copyWithCount(1);
		Entity projectile = createBackpackProjectile(player, launchedStack, spawn, direction);
		if (projectile == null) {
			sendBackpackFireResult(player);
			return;
		}
		projectile.addCommandTag(CombatTargeting.BACKPACK_PROJECTILE_TAG);
		projectile.setPosition(spawn.x, spawn.y, spawn.z);
		BACKPACK_PROJECTILES.put(projectile.getUuid(),
				now + BACKPACK_PROJECTILE_TRACK_TICKS);
		if (!player.getServerWorld().spawnEntity(projectile)) {
			BACKPACK_PROJECTILES.remove(projectile.getUuid());
			sendBackpackFireResult(player);
			return;
		}
		playBackpackLaunchSound(player.getServerWorld(), spawn, launchedStack);
		stored.decrement(1);
		player.getInventory().markDirty();
		state.charges = Math.max(0, state.charges - 1);
		if (state.charges == 0) {
			int targetId = state.targetEntityId;
			clearBackpackTarget(state, now);
			if (player.getServerWorld().getEntityById(targetId) instanceof LivingEntity living) {
				living.removeStatusEffect(StatusEffects.GLOWING);
			}
			state.cooldownUntil = now + BACKPACK_FIREWORK_COOLDOWN_TICKS;
			state.nextChargeAt = 0;
		}
		syncBackpackStatus(player, state, now);
		sendBackpackFireResult(player);
		TkGun.LOGGER.info("[tkgun] 战斗背包烟花：从({}, {}, {})向({}, {}, {})以弩速发射 玩家={}",
				round3(spawn.x), round3(spawn.y), round3(spawn.z),
				round3(direction.x), round3(direction.y), round3(direction.z),
				player.getName().getString());
	}

	private static Entity createBackpackProjectile(ServerPlayerEntity player, ItemStack stack,
			Vec3d spawn, Vec3d direction) {
		ServerWorld world = player.getServerWorld();
		if (stack.isOf(Items.FIREWORK_ROCKET)) {
			com.tk.gun.item.BackpackFireworkData.markNoSelfDamage(stack);
			var rocket = new net.minecraft.entity.projectile.FireworkRocketEntity(
					world, stack, player, spawn.x, spawn.y, spawn.z, true);
			rocket.setVelocity(direction.multiply(1.6D));
			return rocket;
		}
		if (stack.isOf(Items.TIPPED_ARROW)) {
			var arrow = new ArrowEntity(world, player, stack, stack.copy());
			arrow.setVelocity(direction.multiply(2.5D));
			return arrow;
		}
		if (stack.isOf(Items.WIND_CHARGE)) {
			var windCharge = new net.minecraft.entity.projectile.WindChargeEntity(
					player, world, spawn.x, spawn.y, spawn.z);
			windCharge.setVelocity(direction.multiply(1.6D));
			return windCharge;
		}
		if (stack.getItem() instanceof TridentItem) {
			TridentEntity trident;
			if (BOLT != null && stack.isOf(BOLT)) {
				var molten = new com.tk.gun.entity.MoltenTridentEntity(
						MOLTEN_TRIDENT_ENTITY, world, player, stack);
				molten.setGunFired(false);
				trident = molten;
			} else {
				trident = new TridentEntity(world, player, stack);
			}
			trident.setVelocity(direction.multiply(2.5D));
			return trident;
		}
		return null;
	}

	private static void playBackpackLaunchSound(ServerWorld world, Vec3d position,
			ItemStack stack) {
		SoundEvent sound;
		if (stack.isOf(Items.FIREWORK_ROCKET)) {
			sound = SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH;
		} else if (stack.isOf(Items.TIPPED_ARROW)) {
			sound = SoundEvents.ENTITY_ARROW_SHOOT;
		} else if (stack.isOf(Items.WIND_CHARGE)) {
			sound = SoundEvents.ENTITY_WIND_CHARGE_THROW;
		} else if (stack.getItem() instanceof TridentItem) {
			sound = SoundEvents.ITEM_TRIDENT_THROW.value();
		} else {
			return;
		}
		world.playSound(null, position.x, position.y, position.z, sound,
				SoundCategory.PLAYERS, 1.0F, 1.0F);
	}

	private static Vec3d crosshairAimPoint(ServerPlayerEntity player,
			com.tk.gun.net.CombatBackpackFirePayload payload) {
		Vec3d camera = new Vec3d(payload.cameraX(), payload.cameraY(), payload.cameraZ());
		Vec3d forward = Vec3d.fromPolar(
				(float) payload.cameraPitch(), (float) payload.cameraYaw());
		if (!Double.isFinite(camera.x) || !Double.isFinite(camera.y)
				|| !Double.isFinite(camera.z)
				|| camera.squaredDistanceTo(player.getEyePos()) > 4096.0D
				|| forward.lengthSquared() < 1.0E-8D) {
			camera = player.getEyePos();
			forward = player.getRotationVector();
		}
		return camera.add(forward.multiply(128.0D));
	}

	private static void clearBackpackMarker(ServerPlayerEntity player,
			BackpackCharge state, long now) {
		int targetId = state.targetEntityId;
		clearBackpackTarget(state, now);
		if (player.getServerWorld().getEntityById(targetId) instanceof LivingEntity living) {
			living.removeStatusEffect(StatusEffects.GLOWING);
		}
	}

	public static void restoreBackpackChargeAfterLoyalty(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		BackpackCharge state = backpackState(player, now);
		if (state.cooldownUntil > 0) {
			state.cooldownUntil = 0;
			state.charges = BACKPACK_MAX_CHARGES;
			state.nextChargeAt = 0;
		} else if (state.charges < BACKPACK_MAX_CHARGES) {
			state.charges++;
			state.nextChargeAt = state.charges >= BACKPACK_MAX_CHARGES
					? 0 : now + BACKPACK_CHARGE_INTERVAL_TICKS;
		}
		syncBackpackStatus(player, state, now);
	}

	public static boolean isBackpackProjectile(Entity entity) {
		return entity != null && BACKPACK_PROJECTILES.containsKey(entity.getUuid());
	}

	private static void tickBackpackProjectiles(MinecraftServer server) {
		int now = server.getTicks();
		Iterator<Map.Entry<UUID, Long>> iterator = BACKPACK_PROJECTILES.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue() < now) {
				iterator.remove();
			}
		}
	}

	private static void enforceLauncherOffhandStorage(ServerPlayerEntity player) {
		if (!com.tk.gun.item.BackpackLauncherRules.hasActiveLauncher(player)) {
			return;
		}
		net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
		ItemStack offhand = inventory.getStack(
				net.minecraft.entity.player.PlayerInventory.OFF_HAND_SLOT);
		if (offhand.isEmpty()
				|| com.tk.gun.item.BackpackLauncherRules.isLaunchable(offhand)) {
			return;
		}
		ItemStack displaced = offhand.copy();
		offhand.setCount(0);
		inventory.offerOrDrop(displaced);
		inventory.markDirty();
		player.playerScreenHandler.syncState();
		if (player.currentScreenHandler != player.playerScreenHandler) {
			player.currentScreenHandler.syncState();
		}
		TkGun.LOGGER.info("[tkgun] launcher removed non-launchable offhand item: player={} item={}",
				player.getName().getString(), displaced.getItem());
	}

	private static void sendBackpackFireResult(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player,
				new com.tk.gun.net.CombatBackpackFireResultPayload());
	}

	public static boolean isOverheated(ServerPlayerEntity player) {
		PState st = state(player);
		return player.getWorld().getTime() < st.overheatUntil;
	}

	// ---------------------------------------------------------------- 开火
	/** @param charged true=右键蓄满，false=左键腰射（腰射要额外等拉栓） */
	/**
	 * 举枪状态三档：AIM（24 tick）、AIM_FAST（14 tick）、AIM_SLIDE（7 tick）。
	 * 客户端按状态选择对应动画和播放速度。
	 *
	 * <p>普通右键只建立 item-use 瞄准状态；专注由 Z 切换。本方法供调试命令和快速举枪路径使用。
	 */
	public static void startAim(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PState st = state(player);
		// 已经架着枪了（例如刚打完一发、或者又点了一次右键）→ **保持**当前姿势，
		// 别重播 Focus_ready：那段开头是收枪姿势，用户 2026-10-13 明确说"打完不要变成收枪动作"。
		if (st.deployed && st.shotQueue.isEmpty() && st.state != TkGun.State.FIRE) {
			setState(server, player, TkGun.State.AIM_HOLD, 600);
			return;
		}
		long now = player.getWorld().getTime();
		boolean fast = now < st.fastAimUntil || player.isSneaking();
		clearDodgeCooldownsForFocus(st);
		st.deployed = true;
		// **进蹲射 = 重置翻滚/滑铲的循环**（用户 2026-10-17："在蹲射后会重置此循环"）：
		// 交替状态归零、那把 1 秒锁也清掉 —— 蹲射之后的第一次闪避一定是翻滚。
		st.lastDodgeKind = 0;
		st.dodgeBlockUntil = 0;
		setState(server, player, fast ? TkGun.State.AIM_FAST : TkGun.State.AIM,
				600);   // 举枪状态保持足够长，由对应动画播放完成并停在尾帧。
	}

	/**
	 * **滑铲里按右键**：立刻结束滑铲、进"架枪（最快档）"。
	 *
	 * <p>用户 2026-10-13 原话："可以滑铲期间检测到右键，立刻加速播放架枪动画进入架枪状态"。
	 * 所以这里不给 sliding_ready 收势，直接切 Focus_ready 3 倍速（0.35s 架好），
	 * 滑铲剩下的冲量还在（不打速度，让原版摩擦自然收）。
	 */
	public static void startSlideBrace(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PState st = state(player);
		if (ensureMagazineLoaded(player, st)) {
			rebroadcast(player);
		}
		long now = player.getWorld().getTime();
		// 记录滑铲剩余时间，随后立即结束滑行并进入 AIM_SLIDE。
		boolean wasSliding = now < st.slideUntil;
		long slideLeft = Math.max(0, st.slideUntil - now);
		st.slideUntil = 0;                     // 滑铲立刻收
		st.slideMeasuring = false;
		st.fastAimUntil = now + 60;            // 之后这一小段里右键都算"快"
		clearDodgeCooldownsForFocus(st);
		st.deployed = true;
		setState(server, player, TkGun.State.AIM_SLIDE, 600);
		TkGun.LOGGER.info("[tkgun] 滑铲中右键 -> 加速架枪（AIM_SLIDE 3 倍速，滑铲剩余={} tick）玩家={}",
				slideLeft, player.getName().getString());
		if (!wasSliding) {
			TkGun.LOGGER.info("[tkgun] （注意：这次右键进来时其实不在滑铲里，状态被强行设成 AIM_SLIDE）");
		}
	}

	/** 松开右键（或没架完就松手）→ 回到待机姿势。 */
	public static void stopAim(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PState st = state(player);
		if (TkGun.State.isAiming(st.state)) {
			st.deployed = false;
			st.focusRamp = 0;             // 解除专注：射速/伤害的递增曲线归零
			st.state = TkGun.State.IDLE;
			st.stateUntil = 0;
			broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
		}
	}

	/** 这次部署要几 tick（右键至少按住这么久再松手才会开火）。 */
	public static int aimTicks(ServerPlayerEntity player) {
		PState st = state(player);
		long now = player.getWorld().getTime();
		// 冲刺加成窗口里也算"快档"：那条路进专注用的是 `fast_ready`（见 startFocus）。
		return (now < st.fastAimUntil || now < st.dashUntil || player.isSneaking())
				? TkGun.AIM_TICKS_FAST : TkGun.AIM_TICKS_NORMAL;
	}

	public static void tryFire(ServerPlayerEntity player, boolean charged) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PState st = state(player);
		if (ensureMagazineLoaded(player, st)) {
			rebroadcast(player);
		}
		long now = player.getWorld().getTime();
		if (now == st.lastFireTick) {                 // 同一 tick 里右键和左键都触发，只认一次
			return;
		}
		st.lastFireTick = now;
		if (now < st.overheatUntil) {
			playSound(player, TkGun.SND_VENT, 0.7F, 1.1F);
			TkGunTexts.say(player, TkGunTexts.OVERHEAT_NO_FIRE, true);
			return;
		}
		if (now < st.reloadUntil) {                   // 装填中（弹夹还没插回去）
			playSound(player, TkGun.SND_BOLT, 0.45F, 1.7F);
			TkGunTexts.say(player, TkGunTexts.RELOADING, true);
			return;
		}
		// ⚠️ 2026-10-15 用户："**正常按左键是不能开火的** —— 行走射击也得先瞄准，然后播行走射击的动画，
		//    然后才能开火"：扣扳机的前提是**举着枪**（右键按住 = `isUsingItem()`）。
		//    客户端那边已经先拦了一道（右键没按就不发开火包），这里是权威兜底（老客户端 / 改包的）。
		if (!player.isUsingItem() && !st.deployed) {
			playSound(player, TkGun.SND_DRY, 0.45F, 1.6F);
			TkGunTexts.say(player, TkGunTexts.needAimText(), true);
			// 打一行日志：验收/排查时"这次没出膛到底是被谁拦的"要能一眼看出来
			TkGun.LOGGER.info("[tkgun] 扣扳机被拒（没举枪：isUsingItem=false）玩家={}",
					player.getName().getString());
			return;
		}
		// 开火资格由 GunStateMachine.canFire 统一决定。
		if (!GunStateMachine.canFire(st.state)) {
			playSound(player, TkGun.SND_DRY, 0.45F, 1.5F);
			TkGunTexts.say(player, TkGunTexts.CANT_FIRE, true);
			TkGun.LOGGER.info("[tkgun] 扣扳机被拒（状态 {} 不允许开火）玩家={}", st.state,
					player.getName().getString());
			return;
		}
		// 专注每个射击间隔最多预输入 1 发；腰射不接受预输入。
		int queueLimit = st.deployed ? 1 : TkGun.SHOT_QUEUE_MAX;
		if (st.shotQueue.size() >= queueLimit) {
			return;
		}
		// **腰射取消预输入**（2026-10-18 用户）：当前这一发仍在播放 / 仍在队列里 /
		// 本 tick 已经消费过一次点击 → 后续点击直接忽略。快速点三下只会打出一发，
		// 不会在切物品或松手之后继续补射。专注模式不受这一段影响。
		// `now <= lastShotAt` 同时挡住同一服务端 tick 内连续到达的多个开火包。
		if (!st.deployed && (st.shotAnimating || !st.shotQueue.isEmpty()
				|| now <= st.lastShotAt || now < st.shotCycleAt)) {
			TkGun.LOGGER.info("[tkgun] 腰射无预输入：当前射击未结束，这次点击忽略 玩家={}",
					player.getName().getString());
			return;
		}
		enqueueShot(player, st, now);
	}

	private static boolean isMoltenTridentAmmo(String ammoId) {
		try {
			return "bolt".equals(net.minecraft.util.Identifier.tryParse(ammoId).getPath());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean isGrapeshotAmmo(String ammoId) {
		try {
			return "armor_piercing_bolt".equals(net.minecraft.util.Identifier.tryParse(ammoId).getPath());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean isRoseQuartzAmmo(String ammoId) {
		try {
			return "rose_quartz_bolt".equals(net.minecraft.util.Identifier.tryParse(ammoId).getPath());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	/** 从当前枪械 ItemStack 的 NBT 载入腰射弹匣；新枪默认 0/6 空弹匣。 */
	private static void loadMagazineFromGun(ServerPlayerEntity player, PState st) {
		ItemStack gun = com.tk.gun.item.GunAmmoData.heldGun(player);
		if (gun.isEmpty()) {
			return;
		}
		int hipCapacity = TkGun.magazineCapacity(gun, false);
		st.lastHeldGun = gun;
		int mag;
		String ammo;
		int version = com.tk.gun.item.GunAmmoData.savedMagazineVersion(gun);
		if (!com.tk.gun.item.GunAmmoData.hasSavedMagazine(gun)) {
			// 新枪不预装任何弹种：0/6、空弹匣；弹仓也保持空。
			ammo = "";
			mag = hipCapacity;
			com.tk.gun.item.GunAmmoData.saveMagazine(gun, mag, ammo);
			player.getInventory().markDirty();
			TkGun.LOGGER.info("[tkgun] 新枪初始化空弹匣: 0/{} 玩家={}",
					hipCapacity, player.getName().getString());
		} else {
			mag = com.tk.gun.item.GunAmmoData.savedMagazineCount(gun);
			ammo = com.tk.gun.item.GunAmmoData.savedMagazineAmmo(gun);
			if (version < 2 && mag == 0 && "tkgun:bolt".equals(ammo)
					&& !tkgun$ringHasAmmo(gun, ammo, player)) {
				// r163 自动填入的默认满弹匣：弹仓里并没有对应弹药，清掉。
				mag = hipCapacity;
				ammo = "";
				com.tk.gun.item.GunAmmoData.saveMagazine(gun, mag, ammo);
				player.getInventory().markDirty();
				TkGun.LOGGER.info("[tkgun] 清除旧版自动弹匣（弹仓无对应弹药）玩家={}",
						player.getName().getString());
			} else if (version < 2) {
				// 合法的旧弹匣：保留状态，只补写版本号，避免以后重复迁移。
				com.tk.gun.item.GunAmmoData.saveMagazine(gun, mag, ammo);
				player.getInventory().markDirty();
			}
		}
		if (ammo == null || ammo.isEmpty()) {
			// 无弹种的“满弹匣”是旧版默认状态；统一修复成真正的 0/6 空弹匣。
			if (mag != hipCapacity) {
				mag = hipCapacity;
				com.tk.gun.item.GunAmmoData.saveMagazine(gun, mag, "");
				player.getInventory().markDirty();
				TkGun.LOGGER.info("[tkgun] 修复无弹种满弹匣 -> 空弹匣 玩家={}",
						player.getName().getString());
			}
		}
		st.mag = Math.max(0, Math.min(hipCapacity, mag));
		st.magAmmo = ammo == null ? "" : ammo;
		st.magAmmoStack = ItemStack.EMPTY;
		if (!st.magAmmo.isEmpty()) {
			for (ItemStack candidate : com.tk.gun.item.GunAmmoData.read(
					gun, player.getWorld().getRegistryManager())) {
				if (!candidate.isEmpty()
						&& Registries.ITEM.getId(candidate.getItem()).toString().equals(st.magAmmo)) {
					st.magAmmoStack = candidate.copyWithCount(1);
					break;
				}
			}
			if (st.magAmmoStack.isEmpty()) {
				st.magAmmoStack = com.tk.gun.item.GunAmmoData.stackForId(st.magAmmo);
			}
		}
		st.lastSavedMag = st.mag;
		st.lastSavedAmmo = st.magAmmo;
		TkGun.LOGGER.info("[tkgun] 弹匣载入: {}/{} 弹种={} 版本={} 玩家={}",
				hipCapacity - st.mag, hipCapacity,
				st.magAmmo.isEmpty() ? "空" : st.magAmmo, version,
				player.getName().getString());
	}

	private static boolean ensureMagazineLoaded(ServerPlayerEntity player, PState st) {
		ItemStack gun = com.tk.gun.item.GunAmmoData.heldGun(player);
		if (gun.isEmpty()) {
			return false;
		}
		if (st.magLoaded && st.lastHeldGun == gun) {
			return false;
		}
		st.lastHeldGun = gun;
		loadMagazineFromGun(player, st);
		st.magLoaded = true;
		return true;
	}

	private static boolean tkgun$ringHasAmmo(ItemStack gun, String ammoId, ServerPlayerEntity player) {
		for (ItemStack stack : com.tk.gun.item.GunAmmoData.read(
				gun, player.getWorld().getRegistryManager())) {
			if (!stack.isEmpty()
					&& Registries.ITEM.getId(stack.getItem()).toString().equals(ammoId)) {
				return true;
			}
		}
		return false;
	}

	/** 腰射弹匣变化时写回枪械 NBT；专注弹匣不覆盖腰射状态。 */
	private static void persistHipMagazine(ServerPlayerEntity player, PState st) {
		persistHipMagazine(player, st, false);
	}

	private static void persistHipMagazine(ServerPlayerEntity player, PState st, boolean force) {
		if (!st.magLoaded || (st.deployed && !force)) {
			return;
		}
		ItemStack gun = com.tk.gun.item.GunAmmoData.heldGun(player);
		if (gun.isEmpty()) {
			gun = st.lastHeldGun;
		}
		if (gun.isEmpty()) {
			return;
		}
		if (st.mag == st.lastSavedMag
				&& java.util.Objects.equals(st.magAmmo, st.lastSavedAmmo)) {
			return;
		}
		com.tk.gun.item.GunAmmoData.saveMagazine(gun, st.mag, st.magAmmo);
		st.lastSavedMag = st.mag;
		st.lastSavedAmmo = st.magAmmo;
		player.getInventory().markDirty();
	}

	/** 清空尚未开火的预输入：腰射回退弹匣计数，专注退还已从弹仓扣除的弹药。 */
	private static void clearPendingShots(ServerPlayerEntity player, PState st, String reason) {
		int cleared = st.shotQueue.size();
		if (cleared == 0 && !st.shotAnimating) {
			return;
		}
		int hip = 0;
		int focus = 0;
		int focusQueued = 0;
		for (Pending p : st.shotQueue) {
			if (p.focus) {
				focusQueued++;
				ItemStack template = p.ammoStack;
				if (template.isEmpty() && !p.ammo.isEmpty()) {
					template = com.tk.gun.item.GunAmmoData.stackForId(p.ammo);
				}
				if (!template.isEmpty()
						&& com.tk.gun.item.GunAmmoData.refund(
						player, st.lastHeldGun, template, 1)) {
					focus++;
				}
			} else {
				hip++;
				st.mag = Math.max(0, st.mag - 1);
			}
		}
		st.focusShots = Math.max(0, st.focusShots - focusQueued);
		st.shotQueue.clear();
		st.shotAnimating = false;
		st.shotCycleAt = 0;
		st.boltAt = 0;
		persistHipMagazine(player, st);
		TkGun.LOGGER.info("[tkgun] 清空预输入（{}）：{} 发，腰射回退 {}，专注退还 {} 玩家={}",
				reason, cleared, hip, focus, player.getName().getString());
	}

	/**
	 * **入队一发**（真正的"扣扳机"动作）：弹夹计数 +1、排进射击队列、按 ramp 算这一发的节奏。
	 *
	 * <p>两条路都走它：单次扣扳机（{@link #tryFire}）和**专注里的按住左键全自动**（{@code tickAll}）。
	 *
	 * @return true = 真的入队了；false = 弹夹是空的（已经转成装填）
	 */
	private static boolean enqueueShot(ServerPlayerEntity player, PState st, long now) {
		GunDefinition definition = TkGun.definition(player);
		int capacity = TkGun.magazineCapacity(definition, st.deployed);
		if (activeMag(st) >= capacity) {
			// 当前模式的弹匣已经空了。
			// ⚠️ 2026-10-17 用户："在任何状态下空弹匣不会自动装填" —— 所以**扣扳机不再触发装填**，
			//    只响一声空仓 + 提示按 F。
			playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
			TkGunTexts.say(player, TkGunTexts.MAG_EMPTY, true);
			return false;
		}
		String ammoId = st.deployed
				? com.tk.gun.item.GunAmmoData.selectedAmmoId(player)
				: st.magAmmo;
		if (st.deployed) {
			st.lastFocusAmmo = ammoId;
		}
		ItemStack ammoStack = st.deployed
				? com.tk.gun.item.GunAmmoData.selectedAmmo(player).copyWithCount(1)
				: st.magAmmoStack.copyWithCount(1);
		if (st.deployed && !com.tk.gun.item.GunAmmoData.consumeExpected(player, ammoId, 1)) {
			playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
			return false;
		}
		int shotIndex;
		if (st.deployed) {
			st.focusShots++;
			shotIndex = st.focusShots;
		} else {
			st.mag++;
			shotIndex = st.mag;
			persistHipMagazine(player, st);
		}
		st.shotQueue.add(Pending.shot(shotIndex, ammoId, ammoStack, st.deployed));
		st.shotSeq++;
		// 节奏：专注里**每发递减 0.25 秒**（用户 2026-10-16），下限 FOCUS_CYCLE_MIN；腰射固定 12 tick
		st.shotCycle = st.deployed
				? definition.focusCycleTicks(st.focusRamp)
				: definition.hipShotCycleTicks();
		if (st.deployed && isMoltenTridentAmmo(ammoId)) {
			st.shotCycle *= 2;                     // 蹲射连射间隔减慢一倍
		}
		if (st.deployed && isGrapeshotAmmo(ammoId)) {
			st.shotCycle = TkGun.GRAPESHOT_FOCUS_CYCLE_TICKS;
		}
		if (st.deployed && isRoseQuartzAmmo(ammoId)) {
			st.shotCycle = TkGun.roseQuartzFocusCycleTicks(st.focusRamp);
		}
		TkGun.LOGGER.info("[tkgun] 入队弹夹第 {} 发（动画 {}，队列 {} 件，节奏 {} tick/发，专注={} ramp={}）玩家={}",
				shotIndex, TkGun.shotAnimation(shotIndex), st.shotQueue.size(), st.shotCycle, st.deployed,
				st.focusRamp, player.getName().getString());
		st.lastShotAt = now;
		if (PROBE_NEXT_SHOT) {
			st.probeShot = true;
			PROBE_NEXT_SHOT = false;
		}
		return true;
	}

	/** 主动装填：从当前弹仓消耗对应弹药并播放 reload；空弹匣不会自动触发。 */
	/** Focus reload also consumes and loads one complete hip-fire magazine. */
	private static boolean reloadHipMagazineDuringFocus(ServerPlayerEntity player, PState st,
													   String focusAmmoId) {
		int capacity = TkGun.magazineCapacity(player, false);
		boolean sameAmmo = focusAmmoId.equals(st.magAmmo);
		int needed = sameAmmo ? st.mag : capacity;
		if (needed <= 0) {
			return true;
		}
		ItemStack selectedStack =
				com.tk.gun.item.GunAmmoData.selectedAmmo(player).copyWithCount(1);
		if (selectedStack.isEmpty()
				|| !com.tk.gun.item.GunAmmoData.consumeExpected(player, focusAmmoId, needed)) {
			return false;
		}
		if (!sameAmmo) {
			int refundAmount = Math.max(0, capacity - st.mag);
			ItemStack oldTemplate = st.magAmmoStack;
			if (oldTemplate.isEmpty() && !st.magAmmo.isEmpty()) {
				oldTemplate = com.tk.gun.item.GunAmmoData.stackForId(st.magAmmo);
			}
			if (refundAmount > 0
					&& (oldTemplate.isEmpty()
					|| !com.tk.gun.item.GunAmmoData.refund(player, oldTemplate, refundAmount))) {
				com.tk.gun.item.GunAmmoData.refund(player, selectedStack, needed);
				return false;
			}
			st.magExplosive = com.tk.gun.item.GunAmmoData.selectedIsExplosive(player);
			st.magAmmo = focusAmmoId;
		}
		st.mag = 0;
		st.magAmmoStack = selectedStack;
		persistHipMagazine(player, st, true);
		return true;
	}

	private static void restoreLastFocusAmmoSelection(ServerPlayerEntity player, PState st) {
		if (st.lastFocusAmmo.isEmpty()) {
			return;
		}
		if (com.tk.gun.item.GunAmmoData.selectAmmoType(player, st.lastFocusAmmo)) {
			TkGun.LOGGER.info("[tkgun] 退出蹲射：弹仓切回最后使用弹种 {} 玩家={}",
					st.lastFocusAmmo, player.getName().getString());
		}
	}

	public static void reload(ServerPlayerEntity player) {
		PState st = state(player);
		if (ensureMagazineLoaded(player, st)) {
			rebroadcast(player);
		}
		long now = player.getWorld().getTime();
		if (now < st.reloadUntil) {
			return;                                    // 已经在装填了
		}
		if (!st.deployed) {
			int hipCapacity = TkGun.magazineCapacity(player, false);
			int loadedRemaining = Math.max(0, hipCapacity - st.mag);
			String selectedId = com.tk.gun.item.GunAmmoData.selectedAmmoId(player);
			ItemStack selectedStack =
					com.tk.gun.item.GunAmmoData.selectedAmmo(player).copyWithCount(1);
			if (selectedId.isEmpty()) {
				playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
				return;
			}
			boolean sameAmmo = !st.magAmmo.isEmpty() && st.magAmmo.equals(selectedId);
			if (sameAmmo && loadedRemaining >= hipCapacity) {
				return;                                // full magazine, same ammo: no-op
			}
			int needed = sameAmmo ? st.mag : hipCapacity;
			if (needed <= 0
					|| !com.tk.gun.item.GunAmmoData.consumeExpected(player, selectedId, needed)) {
				playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
				return;
			}
			if (!sameAmmo) {
				int refundAmount = st.magAmmo.isEmpty() ? 0 : loadedRemaining;
				ItemStack oldTemplate = st.magAmmoStack;
				if (oldTemplate.isEmpty() && !st.magAmmo.isEmpty()) {
					oldTemplate = com.tk.gun.item.GunAmmoData.stackForId(st.magAmmo);
				}
				if (refundAmount > 0
						&& (oldTemplate.isEmpty()
						|| !com.tk.gun.item.GunAmmoData.refund(player, oldTemplate, refundAmount))) {
					// Roll back the new rounds if the old rounds cannot be returned.
					com.tk.gun.item.GunAmmoData.refund(player, selectedStack, needed);
					playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
					TkGun.LOGGER.warn("[tkgun] 换弹取消：弹仓没有空间返还旧弹 {} 发 玩家={}",
							refundAmount, player.getName().getString());
					return;
				}
				st.magExplosive = com.tk.gun.item.GunAmmoData.selectedIsExplosive(player);
				st.magAmmo = selectedId;
			}
			st.magAmmoStack = selectedStack;
		} else {
			String focusAmmoId = com.tk.gun.item.GunAmmoData.selectedAmmoId(player);
			if (focusAmmoId.isEmpty()
					|| !reloadHipMagazineDuringFocus(player, st, focusAmmoId)) {
				playSound(player, TkGun.SND_DRY, 0.5F, 1.3F);
				return;
			}
			st.lastFocusAmmo = focusAmmoId;
			st.focusShots = 0;
			st.shotIndex = 1;
		}
		st.mag = 0;
		st.reloadUntil = now + TkGun.RELOAD_TICKS;
		st.lastShotAt = now;
		st.boltAt = now + TkGun.RELOAD_TICKS;          // 装填完才允许开火（boltAt 到点还会补一声"上膛"）
		ServerWorld w = player.getServerWorld();
		// v4 素材（2026-10-16）给了两段真·换弹声：`换弹.ogg`（插弹夹）+ `换弹2.ogg`（弹夹到位）。
		// 作者在 `reload` 动画里把这两声埋在 0.111s / 0.833s，这里按同样的节奏放（1 tick = 0.05s）——
		// 服务端放的附近所有人都听得见，客户端那边就不重复放了（见 BowgunRig#onSoundKeyframe）。
		// 原来那两声（铁活板门 + 弩上膛）是"没有真素材时的凑合"，现在撤掉。
		playSound(player, TkGun.SND_RELOAD, 0.9F, 1.0F);
		st.reloadClickAt = now + 17;
		MinecraftServer server = player.getServer();
		if (server != null) {
			// 装填动作 = 作者的换弹动画 `reload`（1.125 秒）—— 2026-10-14 新素材才有的，
			// 老素材没换弹动画，是拿 `Focus_ready` 的头半段（弹夹从枪身里滑到位）顶的。
			// 架着枪时播完接着走"保持尾针"（AIM_HOLD，见 tickAll 的状态到期分支）；
			// 没架枪时播完回 IDLE。
			setState(server, player, TkGun.State.RELOAD, TkGun.RELOAD_TICKS);
		}
		persistHipMagazine(player, st);
		TkGun.LOGGER.info("[tkgun] 装填 {} tick（弹夹回满 {} 发，deployed={}）玩家={}",
				TkGun.RELOAD_TICKS, TkGun.magazineCapacity(player, st.deployed), st.deployed,
				player.getName().getString());
	}

	/**
	 * 连发节奏机：队列里还有事就逐件来 —— 每一件都重新广播 FIRE（带它自己的 {@code shotIndex}，
	 * 客户端据此把**那一段**射击动画从头播一遍）：
	 * 隔 {@link TkGun#ATTACK_FRAME_TICKS} tick 到**出伤帧**才出膛，再隔
	 * {@link TkGun#SHOT_CYCLE_TICKS} 处理下一件。
	 */
	private static void tickShots(ServerPlayerEntity player, PState st, long now) {
		// **枪已离手 → 这一 tick 一发都不许出膛**（用户 2026-10-17："切换物品后仍然有子弹射出"）：
		// 腰射的预输入队列在 `tickAll` 里会被清掉，这里再加一道，保证它不会赶在清队列之前补射出去。
		if (!TkGun.holdingGun(player)) {
			return;
		}
		if (st.shotAnimating) {
			if (now >= st.shotReleaseAt) {
				st.shotAnimating = false;
				Pending p = st.shotQueue.poll();
				if (p != null) {
					releaseShot(player.getServer(), player, st, p);
					// Cadence is measured from shot start. Anchoring it to the release
					// tick would add ATTACK_FRAME_TICKS to every requested interval.
					st.shotCycleAt = st.shootStartTick + st.shotCycle;
				}
			}
			return;
		}
		if (!st.shotQueue.isEmpty() && now >= st.shotCycleAt && now >= st.boltAt
				&& st.state != TkGun.State.OVERHEAT) {
			Pending p = st.shotQueue.peek();
			if (p == null) {
				return;
			}
			st.shotAnimating = true;
			st.shootStartTick = now;
			st.shotIndex = p.shotIndex;
			// 当前模式的最后一发：播放卸匣声并进入对应收尾逻辑。
			if (p.shotIndex >= TkGun.magazineCapacity(player, true)) {
				playSound(player, TkGun.SND_MAG, 0.85F, 1.0F);
			}
			st.shotReleaseAt = now + TkGun.ATTACK_FRAME_TICKS;
			if (st.probeShot) {
				TkGun.LOGGER.info("[tkgun] 开火起手 tick={} 动画={}（队列 {} 件，弹药等出伤帧 +{} tick）",
						now, TkGun.shotAnimation(p.shotIndex), st.shotQueue.size(), TkGun.ATTACK_FRAME_TICKS);
			}
			setState(player.getServer(), player, TkGun.State.FIRE,
					TkGun.definition(player).fireTicks());
		}
	}

	/**
	 * 调试/验收用：一次把 n 发塞进连发队列。
	 * 正常左键每 tick 只认一次（防连点重复触发），所以"排队"这条得靠这个命令来验：
	 * 入队 n 发后，日志里应该是 n 组「开火起手 → 攻击帧出膛」，彼此间隔 {@link TkGun#SHOT_CYCLE_TICKS} tick。
	 *
	 * <p>该命令会在当前弹匣容量用完后继续填充队列，以便压测连射节奏和过热；正常游戏流程仍要求主动装填。
	 */
	public static int queueShots(ServerPlayerEntity player, int n) {
		PState st = state(player);
		long now = player.getWorld().getTime();
		int added = 0;
		for (int i = 0; i < n && st.shotQueue.size() < TkGun.SHOT_QUEUE_MAX; i++) {
			if (now < st.overheatUntil) {
				break;
			}
			int capacity = TkGun.magazineCapacity(player, st.deployed);
			if (activeMag(st) >= capacity) {
				if (st.deployed) {
					st.focusShots = 0;
				} else {
					st.mag = 0;
				}
			}
			int shotIndex;
			if (st.deployed) {
				st.focusShots++;
				shotIndex = st.focusShots;
			} else {
				st.mag++;
				shotIndex = st.mag;
			}
			st.shotQueue.add(Pending.shot(shotIndex,
					com.tk.gun.item.GunAmmoData.selectedAmmoId(player),
					com.tk.gun.item.GunAmmoData.selectedAmmo(player).copyWithCount(1),
					st.deployed));
			st.shotSeq++;
			added++;
		}
		st.lastShotAt = now;
		if (added > 0) {
			st.probeShot = true;
			TkGun.LOGGER.info("[tkgun] 一次入队 {} 件（队列 {} 件，节奏 {} tick/发，弹夹 {}/{}）",
					added, st.shotQueue.size(), TkGun.definition(player).hipShotCycleTicks(),
					activeMag(st), TkGun.magazineCapacity(player, st.deployed));
		}
		return added;
	}

	/** 出伤帧到点：弹药真正出膛 + 枪口特效 + 炮声 + 热量/连射结算。 */
	private static void releaseShot(MinecraftServer server, ServerPlayerEntity player, PState st, Pending p) {
		if (p == null) {
			return;
		}
		long now = player.getWorld().getTime();
		if (st.probeShot) {
			st.probeShot = false;
			TkGun.LOGGER.info("[tkgun] 出伤帧到点 tick={} → 弹夹第 {} 发射出（动画 {}，起手 tick={}，差 {} tick）",
					now, p.shotIndex, TkGun.shotAnimation(p.shotIndex), st.shootStartTick,
					now - st.shootStartTick);
		}

		// —— 专注里的"越打越快/越打越疼"（2026-10-15 用户要的）——
		// ⚠️ **腰射（没进专注）完全不涨热、也不会过热**（用户："相当于站立射击的时候是不会过热的"）：
		//    那种情况下 ramp 归零、伤害倍率 1.0、节奏固定 SHOT_CYCLE_TICKS。
		boolean focus = st.deployed;
		float damageMult = 1.0F;
		if (focus) {
			st.focusRamp = (now <= st.focusWindowUntil) ? st.focusRamp + 1 : 1;
			st.focusWindowUntil = now + TkGun.COMBO_WINDOW;
			damageMult = Math.min(TkGun.FOCUS_DAMAGE_MAX,
					TkGun.FOCUS_DAMAGE_START + (st.focusRamp - 1) * TkGun.FOCUS_DAMAGE_STEP);
		} else {
			st.focusRamp = 0;
		}
		fireOnce(player, st, damageMult, p.ammo, p.ammoStack);

		// 拉栓时长：专注里若当前节奏比拉栓还快（射速递减到 0.25s 一发），拉栓不能成为瓶颈，
		// 所以专注里取 min(拉栓, 当前节奏) —— 否则"每发间隔 -0.25s"会被 8 tick 的拉栓卡住（2026-10-16）。
		st.boltAt = now + (focus ? Math.min(TkGun.BOLT_TICKS, st.shotCycle) : TkGun.BOLT_TICKS);
		playShotSound(player, st.focusRamp, p.ammo);
		// 专注模式打出弹匣最后一发时进入过热。
		// 进过热会清掉连发队列、把 deployed 复位，并按规则挂上"缓慢 10"。
		if (focus && p.shotIndex >= TkGun.magazineCapacity(player, true)) {
			TkGun.LOGGER.info("[tkgun] 专注里打出最后一颗（第 {} 发）-> 进过热 玩家={}",
					p.shotIndex, player.getName().getString());
			startOverheat(server, player);
			return;
		}
		if (!focus) {
			return;                       // 腰射：不涨热、不算连射，打完就完事
		}
		st.heat = Math.min(TkGun.HEAT_MAX, st.heat + TkGun.HEAT_PER_SHOT);
		if (st.heat >= TkGun.HEAT_MAX * 0.75F && st.heat < TkGun.HEAT_MAX) {
			TkGunTexts.say(player, TkGunTexts.OVERHEAT_WARN, true);
		}
		rebroadcast(player);
		// 专注里连到第 N 发（或热量满）= **就在这一发的出伤帧当 tick 进过热动画**
		// （用户 2026-10-13 原话："连续射击 10 发以上，射击出伤帧会立刻触发 overheat 动画"）
		// 过热只由"专注弹匣最后一颗"触发；连射 ramp / 热量不再决定过热时机。
		int nextCycle = TkGun.definition(player).focusCycleTicks(st.focusRamp);
		if (isMoltenTridentAmmo(p.ammo)) {
			nextCycle *= 2;
		}
		if (isGrapeshotAmmo(p.ammo)) {
			nextCycle = TkGun.GRAPESHOT_FOCUS_CYCLE_TICKS;
		}
		if (isRoseQuartzAmmo(p.ammo)) {
			nextCycle = TkGun.roseQuartzFocusCycleTicks(st.focusRamp);
		}
		st.shotCycle = nextCycle;
		TkGun.LOGGER.info("[tkgun] 专注第 {} 发：伤害 x{}，下一发节奏 {} tick（热 {}）玩家={}",
				st.focusRamp, Math.round(damageMult * 100) / 100.0F,
				nextCycle,
				st.heat, player.getName().getString());
	}

	/** 枪击的伤害类型（tkgun:bolt：死亡信息"被 xxx 的熔铁弩矢打穿"）。 */
	/** Runs on the tick the visible bullet reaches its impact point. */
	private static void applyBulletImpact(ServerWorld world, ServerPlayerEntity player,
										  net.minecraft.entity.Entity target, Vec3d hitPos,
										  boolean hitSomething, boolean explosive, boolean cutting,
										  boolean sculk, boolean roseQuartz, float damageMult, Vec3d dir) {
		if (!hitSomething) {
			return;
		}
		if (CombatTargeting.isGolem(target)) {
			return;
		}
		if (sculk) {
			// 幽匿弹：直击与范围共用 6 点无视护甲伤害，避免直击目标被结算两次。
			applySonicBurst(world, player, hitPos, target, 7.2F);
		} else if (target != null && target.isAlive()) {
			float directDamage = roseQuartz
					? TkGun.ROSE_QUARTZ_DAMAGE : rangedDamage(player) * damageMult;
			if (target instanceof net.minecraft.entity.LivingEntity living) {
				if (cutting) {
					applyCuttingDamage(living, gunDamageSource(player));
				} else {
					living.damage(gunDamageSource(player), directDamage);
				}
				if (!cutting && !roseQuartz) {
					living.takeKnockback(0.5D, dir.x, dir.z);
				}
			} else {
				target.damage(gunDamageSource(player), directDamage);
			}
		}
		if (sculk) {
			world.spawnParticles(TkGunParticles.SONIC_BOOM,
					hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
		} else if (explosive) {
			applyExplosionDamage(world, player, hitPos, rangedDamage(player) * damageMult, true);
			world.spawnParticles(TkGunParticles.EXPLOSIVE_BOLT_IMPACT,
					hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.FLAME,
					hitPos.x, hitPos.y, hitPos.z, 14, 0.16, 0.16, 0.16, 0.08);
			world.spawnParticles(ParticleTypes.SMALL_FLAME,
					hitPos.x, hitPos.y, hitPos.z, 12, 0.2, 0.2, 0.2, 0.06);
			world.spawnParticles(ParticleTypes.CRIT,
					hitPos.x, hitPos.y, hitPos.z, 16, 0.2, 0.2, 0.2, 0.25);
			world.spawnParticles(ParticleTypes.SMOKE,
					hitPos.x, hitPos.y, hitPos.z, 10, 0.18, 0.18, 0.18, 0.05);
		} else if (cutting) {
			// 切割弹同样带 3×3×3 范围伤害；数值与爆破弹一致（直伤 8 的 75% = 6 点）。
			applyExplosionDamage(world, player, hitPos, rangedDamage(player) * damageMult, false);
			world.spawnParticles(TkGunParticles.CUTTING_EXPLO,
					hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
		} else if (roseQuartz) {
			world.spawnParticles(TkGunParticles.ROSE_QUARTZ_IMPACT,
					hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (roseQuartz) {
			net.minecraft.util.math.BlockPos feedbackPos = target == null
					? net.minecraft.util.math.BlockPos.ofFloored(hitPos) : player.getBlockPos();
			world.playSound(null, feedbackPos,
					net.minecraft.sound.SoundEvent.of(TkGun.SND_ROSE_QUARTZ_HIT),
					SoundCategory.MASTER, 1.0F, 1.0F);
		} else if (target != null) {
			// Play at the shooter so long-range hits always produce a confirmation.
			world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT,
					SoundCategory.MASTER, 1.0F, 2.0F);
		} else {
			Identifier impactSound = sculk ? TkGun.SND_SONIC_BOOM
					: (explosive ? TkGun.SND_EXPLOSION1
					: (cutting ? TkGun.SND_CUTTING1 : TkGun.SND_IMPACT));
			world.playSound(null,
					net.minecraft.util.math.BlockPos.ofFloored(hitPos.x, hitPos.y, hitPos.z),
					net.minecraft.sound.SoundEvent.of(impactSound),
					SoundCategory.MASTER, 1.0F, 1.0F);
		}
		ServerPlayNetworking.send(player, new com.tk.gun.net.GunHitMarkerPayload());
		// TODO: Add a non-positional shooter-only impact confirmation for very long shots.
	}

	/** Cutting rounds deal three independent hits: iron sword (6), then 3, then 1.5. */
	private static void applyCuttingDamage(net.minecraft.entity.LivingEntity target,
										   net.minecraft.entity.damage.DamageSource source) {
		float[] hits = {7.2F, 3.6F, 1.8F};
		for (float amount : hits) {
			if (!target.isAlive()) {
				break;
			}
			target.timeUntilRegen = 0;
			target.damage(source, amount);
		}
	}

	/** Explosive and cutting rounds damage surrounding living entities in a 3x3x3 area. */
	private static void applyExplosionDamage(ServerWorld world, ServerPlayerEntity player,
											 Vec3d center, float damage, boolean knockback) {
		net.minecraft.util.math.Box area = new net.minecraft.util.math.Box(
				center.add(-1.5D, -1.5D, -1.5D),
				center.add(1.5D, 1.5D, 1.5D));
		net.minecraft.entity.damage.DamageSource source = gunDamageSource(player);
		float splashDamage = damage * 0.75F;
		for (net.minecraft.entity.LivingEntity living : world.getEntitiesByClass(
				net.minecraft.entity.LivingEntity.class, area,
				e -> e != player && e.isAlive() && !e.isSpectator() && e.canHit()
						&& !CombatTargeting.isGolem(e))) {
			Vec3d away = living.getPos().subtract(center);
			living.damage(source, splashDamage);
			if (knockback) {
				living.takeKnockback(0.45D, away.x, away.z);
			}
		}
	}

	/**
	 * 幽匿弹音爆：以命中点为中心，3 格直径球体内所有生物受到 6 点无视护甲伤害。
	 * 所有受影响目标只受到伤害，不再附加击退或上挑。
	 */
	private static void applySonicBurst(ServerWorld world, ServerPlayerEntity player,
										Vec3d center, net.minecraft.entity.Entity direct, float damage) {
		net.minecraft.util.math.Box area = new net.minecraft.util.math.Box(
				center.add(-1.5D, -1.5D, -1.5D), center.add(1.5D, 1.5D, 1.5D));
		net.minecraft.entity.damage.DamageSource source = sonicDamageSource(player);
		java.util.Set<net.minecraft.entity.LivingEntity> affected =
				java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		if (direct instanceof net.minecraft.entity.LivingEntity living && living.isAlive()
				&& !CombatTargeting.isGolem(living)) {
			affected.add(living);
		}
		for (net.minecraft.entity.LivingEntity living : world.getEntitiesByClass(
				net.minecraft.entity.LivingEntity.class, area,
				e -> e != player && e.isAlive() && !e.isSpectator() && e.canHit()
						&& !CombatTargeting.isGolem(e))) {
			Vec3d bodyCenter = living.getBoundingBox().getCenter();
			if (bodyCenter.squaredDistanceTo(center) <= 2.25D) {
				affected.add(living);
			}
		}
		TkGun.LOGGER.info("[tkgun] 幽匿弹音爆：中心=({}, {}, {}) 直击生物={} 影响生物={}",
				round3(center.x), round3(center.y), round3(center.z),
				direct instanceof net.minecraft.entity.LivingEntity, affected.size());
		for (net.minecraft.entity.LivingEntity living : affected) {
			living.timeUntilRegen = 0;
			living.damage(source, damage);
			if (!living.isAlive()) {
				continue;
			}
		}
	}

	/** 幽匿弹的伤害类型：tkgun:sonic 已加入 bypasses_armor / bypasses_enchantments。 */
	private static net.minecraft.entity.damage.DamageSource sonicDamageSource(ServerPlayerEntity player) {
		net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> key =
				net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
						TkGun.id("sonic"));
		RegistryEntry<net.minecraft.entity.damage.DamageType> entry = player.getServerWorld()
				.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getEntry(key)
				.map(e -> (RegistryEntry<net.minecraft.entity.damage.DamageType>) e).orElse(null);
		if (entry == null) {
			return player.getDamageSources().sonicBoom(player);
		}
		return new net.minecraft.entity.damage.DamageSource(entry, player, player);
	}

	private static net.minecraft.entity.damage.DamageSource gunDamageSource(ServerPlayerEntity player) {
		net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> key =
				net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
						TkGun.id("bolt"));
		RegistryEntry<net.minecraft.entity.damage.DamageType> entry = player.getServerWorld()
				.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getEntry(key)
				.map(e -> (RegistryEntry<net.minecraft.entity.damage.DamageType>) e).orElse(null);
		if (entry == null) {
			return player.getDamageSources().playerAttack(player);
		}
		return new net.minecraft.entity.damage.DamageSource(entry, player, player);
	}

	/**
	 * **开炮声 = 素材里那一声 `枪击3.ogg`，就这一声，别的都不要。**
	 *
	 * <p>沿革：2026-10-13 我自己合成过 cannon_boom/clank/tail 三层（音色发尖发空，已删）；
	 * 2026-10-14 改回"素材枪声 + 三层原版音效（龙息火球爆 / 通用爆炸 / 铁砧落地）压音高拼的炮感"；
	 * **用户要求完全删除现有射击音效并只使用素材枪声** —— 所以那三层原版叠层、
	 * 连同旧的 `枪击.ogg`（`tkgun:gunshot`：文件 + sounds.json 条目）**全部下线**，这里只剩素材那一声。
	 * 素材的 `枪击3.ogg` 与本包里的 `gunshot3.ogg` sha1 逐字节相同
	 * （`CBB76D33916656AD77ECD04AF30D2D9F6CBD5437`），没有做任何转码。
	 *
	 * <p>{@code ramp} 参数留着是为了不改进队侧那几处调用点；现在已经不分单发/连射了。
	 */
	private static void playShotSound(ServerPlayerEntity player, int ramp, String ammoId) {
		// 无论单发还是专注连射，统一播放素材 `枪击3.ogg`。
		String path = "";
		try {
			path = net.minecraft.util.Identifier.tryParse(ammoId).getPath();
		} catch (RuntimeException ignored) {
		}
		Identifier shot = "bolt".equals(path) ? TkGun.SND_CROSSBOW_FIRE
				: ("cutting_bolt".equals(path) ? TkGun.SND_SHOT2
				: ("sculk_bolt".equals(path) ? TkGun.SND_SONIC_SHOT
				: ("armor_piercing_bolt".equals(path) ? TkGun.SND_GRAPESHOT_FIRE
				: ("rose_quartz_bolt".equals(path) ? TkGun.SND_GUNSHOT : TkGun.SND_SHOT3))));
		playSound(player, shot, 0.9F, 1.0F);
	}

	private static void startOverheat(MinecraftServer server, ServerPlayerEntity player) {
		PState st = state(player);
		// 清掉尚未出膛的连发队列并归还对应弹药。
		clearPendingShots(player, st, "过热");
		// 过热会退出专注；清除 deployed 可避免 Overheat 结束后错误回到 AIM_HOLD。
		st.deployed = false;
		st.focusShots = 0;
		st.focusRamp = 0;              // 过热/收枪：递增曲线归零
		restoreLastFocusAmmoSelection(player, st);
		st.overheatUntil = player.getWorld().getTime() + TkGun.OVERHEAT_TICKS;
		st.heat = TkGun.HEAT_MAX;
		// 音效：**交给 `Overheat` 自己的音效关键帧** —— 作者在那段 0.4167s 处配了 `散热.ogg`
		// （用户 2026-10-17："将专注相关的音效替换为…模型所引用的音频"）。原来这里那声即时 `vent`
		// 和一层原版灭火声（`BLOCK_FIRE_EXTINGUISH`）已删：动画到点响的那一声才对得上画面。
		TkGunTexts.say(player, TkGunTexts.OVERHEAT_START, true);
		// **过热期间也挂"缓慢 10"**（用户 2026-10-17："将缓慢效果绑定退出专注状态以及过热两种状态"）：
		// 时长 = **过热动画本身的时长**（`Overheat` 1.5s = 30 tick，见 TkGun#OVERHEAT_ANIM_TICKS）。
		// 原来错写成 100 tick（散热窗口）：动画 1.5s 就停了，人还被缓慢按在原地 3.5s。
		// 翻滚会把动画和这个效果一起打断（见 onRollStarted）。
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
				net.minecraft.entity.effect.StatusEffects.SLOWNESS,
				TkGun.OVERHEAT_ANIM_TICKS, TkGun.SLOW_ON_EXIT_AMPLIFIER, false, false, true));
		setState(server, player, TkGun.State.OVERHEAT, TkGun.OVERHEAT_ANIM_TICKS);
	}

	public static void startInspect(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		setState(server, player, TkGun.State.INSPECT, TkGun.INSPECT_TICKS);
		playSound(player, TkGun.SND_BOLT, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- 专注（架枪）状态
	/** 熔铁矢：以满蓄力三叉戟的 2.5 格/tick 从枪口发射，附魔走真实 TridentEntity。 */
	private static void spawnMoltenTrident(ServerWorld world, ServerPlayerEntity player,
										   Vec3d muzzle, Vec3d dir, ItemStack ammoStack) {
		ItemStack stack = ammoStack == null || ammoStack.isEmpty()
				? new ItemStack(BOLT) : ammoStack.copyWithCount(1);
		com.tk.gun.entity.MoltenTridentEntity trident =
				new com.tk.gun.entity.MoltenTridentEntity(MOLTEN_TRIDENT_ENTITY, world, player, stack);
		trident.setGunFired(true);
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		float pitch = (float) Math.toDegrees(Math.atan2(-dir.y,
				Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
		trident.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, yaw, pitch);
		trident.prevYaw = yaw;
		trident.prevPitch = pitch;
		trident.setVelocity(dir.multiply(5.0D));
		if (player.isInCreativeMode()) {
			trident.pickupType =
					net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
		}
		boolean spawned = world.spawnEntity(trident);
		TkGun.LOGGER.info("[tkgun] 熔铁矢：spawn={} id={} 枪口=({}, {}, {}) 方向=({}, {}, {}) 附魔={}",
				spawned, trident.getId(),
				round3(muzzle.x), round3(muzzle.y), round3(muzzle.z),
				round3(dir.x), round3(dir.y), round3(dir.z), stack.hasGlint());
	}

	/**
	 * 放一颗**看得见的子弹**（2026-10-15 用户："用这个去代表那个弹丸"）：从枪口沿弹道飞，
	 * 飞够 {@code distance} 格就自己消失。伤害/爆裂粒子在 {@link #fireOnce} 里已经瞬时结算完了，
	 * 它只是"画面上飞出去的那颗"。
	 */
	private static void spawnGrapeshot(ServerWorld world, ServerPlayerEntity player,
									   Vec3d muzzle, Vec3d dir, float damageMult) {
		Vec3d forward = dir.normalize();
		Vec3d reference = Math.abs(forward.y) > 0.98D
				? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
		Vec3d right = forward.crossProduct(reference).normalize();
		Vec3d up = right.crossProduct(forward).normalize();
		double spread = TkGun.GRAPESHOT_CONE_RADIUS / TkGun.GRAPESHOT_RANGE;
		applyGrapeshotDamage(world, player, muzzle, forward, damageMult);
		com.tk.gun.entity.GrapeshotVolley volley =
				new com.tk.gun.entity.GrapeshotVolley(player.getId(), muzzle, forward);

		int spawned = 0;
		double sectorAngle = Math.PI * 2.0D / TkGun.GRAPESHOT_PELLETS;
		double patternRotation = player.getRandom().nextDouble() * Math.PI * 2.0D;
		for (int i = 0; i < TkGun.GRAPESHOT_PELLETS; i++) {
			// Stratified disk sampling: one pellet per angular sector and one
			// area-balanced radial band. A random rotation and small jitter keep
			// consecutive shots varied without allowing all pellets to clump.
			double angle = patternRotation
					+ (i + 0.5D) * sectorAngle
					+ (player.getRandom().nextDouble() - 0.5D) * sectorAngle * 0.35D;
			int radialBand = Math.floorMod(i * 3, TkGun.GRAPESHOT_PELLETS);
			double areaFraction = (radialBand + player.getRandom().nextDouble())
					/ TkGun.GRAPESHOT_PELLETS;
			double radius = Math.sqrt(areaFraction) * spread;
			Vec3d pelletDir = forward
					.add(right.multiply(Math.cos(angle) * radius))
					.add(up.multiply(Math.sin(angle) * radius))
					.normalize();
			double pelletRange = Math.min(TkGun.GRAPESHOT_RANGE,
					grapeshotBlockDistance(world, player, muzzle, pelletDir));
			com.tk.gun.entity.BulletEntity pellet = spawnBullet(
					world, player, muzzle, pelletDir, pelletRange, "tkgun:armor_piercing_bolt");
			if (pellet != null) {
				pellet.setGrapeshotVolley(volley);
				spawned++;
			}
		}
		TkGun.LOGGER.info("[tkgun] 霰弹：从枪口({}, {}, {}) 发射 {} 颗弹丸，锥角上限 {}°，飞行 {} 格 / {} tick",
				round3(muzzle.x), round3(muzzle.y), round3(muzzle.z), spawned,
				Math.round(Math.toDegrees(Math.atan(spread))), TkGun.GRAPESHOT_RANGE,
				TkGun.GRAPESHOT_FLIGHT_TICKS);
	}

	private static double grapeshotBlockDistance(ServerWorld world, ServerPlayerEntity player,
												 Vec3d muzzle, Vec3d dir) {
		Vec3d end = muzzle.add(dir.multiply(TkGun.GRAPESHOT_RANGE));
		net.minecraft.util.hit.BlockHitResult hit = world.raycast(
				new RaycastContext(muzzle, end, RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS
				? TkGun.GRAPESHOT_RANGE
				: Math.min(TkGun.GRAPESHOT_RANGE, muzzle.distanceTo(hit.getPos()));
	}

	private static void applyGrapeshotDamage(ServerWorld world, ServerPlayerEntity player,
											 Vec3d muzzle, Vec3d forward, float damageMult) {
		Vec3d end = muzzle.add(forward.multiply(TkGun.GRAPESHOT_RANGE));
		double radius = TkGun.GRAPESHOT_DAMAGE_RADIUS;
		net.minecraft.util.math.Box search = new net.minecraft.util.math.Box(
				Math.min(muzzle.x, end.x) - radius, Math.min(muzzle.y, end.y) - radius,
				Math.min(muzzle.z, end.z) - radius, Math.max(muzzle.x, end.x) + radius,
				Math.max(muzzle.y, end.y) + radius, Math.max(muzzle.z, end.z) + radius);
		float nearDamage = rangedDamage(player) * damageMult;
		float farDamage = nearDamage * 0.5F;
		double radiusSq = radius * radius;
		int hitCount = 0;
		for (net.minecraft.entity.LivingEntity living : world.getEntitiesByClass(
				net.minecraft.entity.LivingEntity.class, search,
				e -> e != player && e.isAlive() && !e.isSpectator() && e.canHit()
						&& !CombatTargeting.isGolem(e))) {
			Vec3d center = living.getBoundingBox().getCenter();
			Vec3d relative = center.subtract(muzzle);
			double along = relative.dotProduct(forward);
			if (along < 0.0D || along > TkGun.GRAPESHOT_RANGE) {
				continue;
			}
			Vec3d axisPoint = muzzle.add(forward.multiply(along));
			if (center.squaredDistanceTo(axisPoint) > radiusSq) {
				continue;
			}
			living.damage(gunDamageSource(player),
					along <= 6.0D ? nearDamage : farDamage);
			Vec3d away = relative.subtract(forward.multiply(along));
			if (away.horizontalLengthSquared() > 1.0E-6D) {
				living.takeKnockback(0.35D, away.x, away.z);
			}
			hitCount++;
		}
		TkGun.LOGGER.info("[tkgun] 霰弹范围结算：{} 个实体，半径 {} 格，长度 {} 格",
				hitCount, radius, TkGun.GRAPESHOT_RANGE);
	}

	private static void spawnRoseQuartz(ServerWorld world, ServerPlayerEntity player,
										Vec3d muzzle, Vec3d dir, float damageMult) {
		com.tk.gun.entity.BulletEntity projectile = spawnBullet(
				world, player, muzzle, dir, TkGun.HITSCAN_RANGE, "tkgun:rose_quartz_bolt");
		if (projectile == null) {
			TkGun.LOGGER.warn("[tkgun] 玫瑰石英弹体注册不可用，本次发射取消。玩家={}",
					player.getName().getString());
			return;
		}
		projectile.configureGuidance(player, TkGun.ROSE_QUARTZ_GUIDANCE_RADIUS);
		projectile.setGuidedImpactAction((target, hitPos, impactDir) -> {
			if (player.isRemoved()) {
				return;
			}
			applyBulletImpact(world, player, target, hitPos, true,
					false, false, false, true, damageMult, impactDir);
		});
		TkGun.LOGGER.info("[tkgun] 玫瑰石英：制导半径={}格，实际碰撞结算，id={}，玩家={}",
				TkGun.ROSE_QUARTZ_GUIDANCE_RADIUS, projectile.getId(),
				player.getName().getString());
	}

	private static com.tk.gun.entity.BulletEntity spawnBullet(
			ServerWorld world, ServerPlayerEntity player, Vec3d muzzle, Vec3d dir,
			double distance, String ammoId) {
		if (BULLET_ENTITY == null) {
			return null;
		}
		com.tk.gun.entity.BulletEntity bullet = new com.tk.gun.entity.BulletEntity(BULLET_ENTITY, world);
		String path = "";
		try {
			path = net.minecraft.util.Identifier.tryParse(ammoId).getPath();
		} catch (RuntimeException ignored) {
		}
		int style = ("bolt".equals(path) || "explosive_bolt".equals(path))
				? com.tk.gun.entity.BulletEntity.STYLE_EXPLOSIVE
				: ("cutting_bolt".equals(path) ? com.tk.gun.entity.BulletEntity.STYLE_CUTTING
				: ("sculk_bolt".equals(path) ? com.tk.gun.entity.BulletEntity.STYLE_SCULK
				: ("armor_piercing_bolt".equals(path) ? com.tk.gun.entity.BulletEntity.STYLE_GRAPESHOT
				: ("rose_quartz_bolt".equals(path) ? com.tk.gun.entity.BulletEntity.STYLE_ROSE_QUARTZ
				: com.tk.gun.entity.BulletEntity.STYLE_TRAIL))));
		bullet.setBulletStyle(style);
		bullet.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, player.getYaw(), player.getPitch());
		double speed = "armor_piercing_bolt".equals(path)
				? TkGun.GRAPESHOT_SPEED : TkGun.BULLET_SPEED;
		bullet.launch(dir, distance, speed);
		world.spawnEntity(bullet);
		if (style != com.tk.gun.entity.BulletEntity.STYLE_GRAPESHOT) {
		TkGun.LOGGER.info("[tkgun] 子弹实体：从({}, {}, {}) 朝({}, {}, {}) 飞 {} 格（{} tick）",
				round3(muzzle.x), round3(muzzle.y), round3(muzzle.z),
				round3(dir.x), round3(dir.y), round3(dir.z), round3(distance),
				Math.max(TkGun.BULLET_MIN_TICKS, (int) Math.ceil(distance / speed)));
		}
		return bullet;
	}

	/**
	 * **这位玩家现在在专注（架枪）里吗**（服务端权威；客户端靠状态包的 `focus` 位镜像）。
	 */
	public static boolean focused(ServerPlayerEntity player) {
		PState st = state(player);
		return st.deployed && TkGun.holdingGun(player);
	}

	/**
	 * 切换专注模式。按 Z 或调用本方法：未专注时进入对应的 AIM 状态，已专注时播放 Focus_over 退出。
	 * 专注期间移动、跳跃及大部分热键被屏蔽；过热时无法进入。
	 *
	 * @return true = 本次进入专注；false = 本次退出或被拒绝
	 */
	private static void clearDodgeCooldownsForFocus(PState st) {
		st.fallbackRollReadyAt = 0;
		st.dodgeLockUntil = 0;
		st.dodgeBlockUntil = 0;
		st.slideCooldownUntil = 0;
		st.lastDodgeAt = 0;
		st.lastDodgeKind = 0;
		st.rollFallbackPending = false;
		st.rollFallbackAt = 0;
		st.rollInputAccepted = false;
	}

	public static boolean startFocus(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		PState st = state(player);
		if (ensureMagazineLoaded(player, st)) {
			rebroadcast(player);
		}
		long now = player.getWorld().getTime();
		if (!st.shotQueue.isEmpty() || st.shotAnimating) {
			clearPendingShots(player, st, "进入专注");
		}
		// 滑铲+Z 的组合输入可能同时走“滑铲开始时消费缓冲”和“随后到达的焦点包”两条路。
		// AIM_SLIDE 只有 7 tick，重复进入会把 `sliding_fast_ready` 从 Focus 保持中重新拉回开头，
		// 表现就是快速蹲射动作播两遍。这里直接吸收重复包。
		if (st.deployed && st.state == TkGun.State.AIM_SLIDE && now < st.stateUntil
				&& now <= st.lastSlideFastFocusAt + 4) {
			TkGun.LOGGER.info("[tkgun] AIM_SLIDE 已进入，忽略重复专注包 玩家={}",
					player.getName().getString());
			return true;
		}
		/**
		 * 这次 Z 是"翻滚过 2/3 后打断"或"翻滚刚结束的宽限窗"→ 用快档
		 * （`AIM_ROLL` → `rolling_fast_ready`）。
		 */
		boolean fromRoll = false;
		boolean rolling = st.state == TkGun.State.ROLL;
		// **翻滚中按 Z = 快速蹲射**（用户 2026-10-17："让翻滚时可以进行快速蹲射并打断翻滚动画并进入蹲射状态"）：
		// 先把 ROLL 收掉（这样能绕过 `setState` 里"翻滚没播完不许换状态"那道闸），再照常进专注 ——
		// 下面用专门的 `AIM_ROLL`，客户端播 `rolling_fast_ready`。
		if (rolling) {
			// **2026-10-18 用户定档：翻滚动画 0.38s 处开始执行快速蹲射**。
			// 0.38s × 20 向上取整 = 第 8 tick；在此之前输入的 Z 不丢，先缓存（预输入）。
			long fastAt = st.rollFocusAt > 0
					? st.rollFocusAt
					: st.stateStarted + com.tk.gun.CombatRollHook.ROLL_FAST_FOCUS_TICKS;
			if (now < fastAt) {
				st.rollFocusRequested = true;
				TkGun.LOGGER.info("[tkgun] 翻滚快速蹲射预输入：等待 0.38s 执行（剩 {} tick）玩家={}",
						fastAt - now, player.getName().getString());
				return false;
			}
			if (!st.rollFocusRequested) {
				TkGun.LOGGER.info("[tkgun] 翻滚快速蹲射窗口已结束（第 {} tick，仅接受 0.38s 前预输入）玩家={}",
						now - st.stateStarted, player.getName().getString());
				return false;
			}
			st.rollFocusRequested = false;
			st.rollFocusAt = 0;
			TkGun.LOGGER.info("[tkgun] 翻滚已达 0.38s（第 {} tick）-> 打断翻滚、快速蹲射 玩家={}",
					now - st.stateStarted, player.getName().getString());
			st.state = TkGun.State.IDLE;
			st.stateUntil = 0;
			// **动作意图优先**：即使上一帧还残留着 `deployed=true`，这一次 Z 也必须是"进入快速专注"，
			// 不能被下面的"已架着 → 解除专注"分支吃掉。
			st.deployed = false;
			st.focusRamp = 0;
			fromRoll = true;
		}
		// **滑铲中点 Z = `sliding_fast_ready` 中断滑铲、进蹲射**（用户 2026-10-17）：收掉滑铲 → 下面按
		// `AIM_SLIDE` 进（客户端这一段播作者新加的 `sliding_fast_ready`）。
		boolean fromSlide = st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY;
		if (fromSlide) {
			lockAimAfterDodge(st, now);
			st.slideUntil = 0;
			st.slideMeasuring = false;
			st.lastSlideFastFocusAt = now;
			st.state = TkGun.State.IDLE;
			st.stateUntil = 0;
		}
		if (!TkGun.holdingGun(player)) {
			TkGunTexts.say(player, TkGunTexts.FOCUS_NO_GUN, true);
			return false;
		}
		if (now < st.overheatUntil) {
			playSound(player, TkGun.SND_VENT, 0.7F, 1.1F);
			TkGunTexts.say(player, TkGunTexts.FOCUS_OVERHEATED, true);
			return false;
		}
		if (st.deployed) {                 // 已经架着 → 再按一次 = 解除（省得"按了没反应"）
			stopFocus(player);
			return false;
		}
		clearDodgeCooldownsForFocus(st);
		st.deployed = true;
		st.focusShots = 0;
		st.shotIndex = 1;
		st.lastFocusAmmo = com.tk.gun.item.GunAmmoData.selectedAmmoId(player);
		// 快速蹲射意图已经兑现，关掉宽限窗，避免之后一段普通按 Z 还被误判成翻滚快档。
		st.rollFocusUntil = 0;
		st.rollFocusRequested = false;
		st.rollFocusAt = 0;
		// **进蹲射 = 立即装填弹匣**（用户 2026-10-17："将进入蹲射状态添加立即装填弹匣的效果"）：
		// `mag` 是"弹夹里已经打出去几发"，清零 = 满弹夹；顺手清掉装填窗口（不然刚进蹲射还会被
		// "装填中…" 拦着开火）。下面 `setState` 广播的包里带着新的 `mag`，客户端的弹夹计数/弹链
		// 会跟着一起刷新。
		st.heat = 0.0F;
		st.reloadUntil = 0;
		// **进蹲射会重置"翻滚/滑铲交替"循环**（用户 2026-10-17）：下一次 Alt 重新从翻滚开始，
		// 顺便把上一次循环留下的 1 秒屏蔽也清掉。
		st.lastDodgeKind = 0;
		st.dodgeBlockUntil = 0;
		// 进入动画 = 作者的 `Focus_ready`（起枪 → 落到架枪定格），播完 tickAll 接到 AIM_HOLD 保持。
		// ⚠️ 2026-10-16 用户把两段过门动画的用途定死了：**进入 = Focus_ready、退出 = Focus_over**
		//    （"按下进入专注模式，再次输入则退出并使用 Focus_over 动画"）—— 所以这里不再是 OVER。
		// **冲刺加成窗口里按 Z → 准备动作换成 `fast_ready`**（用户 2026-10-17："在这段加成时间内如果
		// 输入专注按键则使用 fast_ready 作为专注准备动画，此动画准备时间要快于通常准备动作，详情见
		// 资源文件夹中的动画时间线"）。作者的时间线：`fast_ready` 0.6667s / `Focus_ready` 1.0s ——
		// 所以状态时长 14 tick vs 24 tick（TkGun.AIM_TICKS_FAST / _NORMAL）；客户端 `pick` 里
		// AIM_FAST 播的就是 `fast_ready`，蹲射准备那声栓声也会按它 0.0833s 那一拍排（+2 tick）。
		// ⚠️ 2026-10-17 用户："取消加速状态下的快速专注功能" —— 冲刺窗口里按 Z 也走**普通**过门
		// （`Focus_ready`，`AIM_TICKS_NORMAL`），不再切 `AIM_FAST`/`fast_ready`。（滑铲中按 Z 仍走
		// `AIM_SLIDE` → `sliding_fast_ready`，那是另一条需求。）
		if (now < st.dashUntil) {
			TkGun.LOGGER.info("[tkgun] 进专注 -> 清空冲刺加成（原剩余 {} tick）玩家={}",
					st.dashUntil - now, player.getName().getString());
		}
		st.dashUntil = 0;
		GunStateMachine.clearDash(player);
		// 0.38 秒预输入兑现后直接进入 AIM_ROLL，播放新版 rolling_fast_ready。
		TkGun.State aimState = fromSlide ? TkGun.State.AIM_SLIDE
				: (fromRoll ? TkGun.State.AIM_ROLL : TkGun.State.AIM);
		int aimStateTicks = fromSlide ? TkGun.AIM_TICKS_SLIDE
				: (fromRoll ? TkGun.AIM_TICKS_FAST : TkGun.AIM_TICKS_NORMAL);
		if (fromRoll) {
			lockAimAfterDodge(st, now);
		}
		st.quickFocusGuardUntil = (fromRoll || fromSlide) ? now + 4 : 0;
		setState(server, player, aimState, aimStateTicks);
		// 音效：**蹲射准备 = 素材 `栓声.ogg`**（用户 2026-10-17："将蹲射准备动作的音频替换为…栓声"）。
		// 作者把它埋在 `Focus_ready` 的 0.25s（24 tick 的状态 / 1.0s 动画 → 0.25×24 ≈ 6 tick），
		// 这里由**服务端**排这一声（`aimSoundAt`）：时间点还是作者的，但附近所有人都听得见，
		// 也不依赖客户端那条关键帧上报（BowgunRig 里这一段会跳过 `bolt`，免得旁观者听到两声）。
		ServerWorld world = player.getServerWorld();
		// 表现：起手一簇火星（⚠️ 2026-10-15 用户："不要使用那个傀儡的、它的那个攻击特效" ——
		// 原来这里放的是 `energy_converge`，那是从**傀儡**那包 粒子.zip 里切出来的，已撤掉）
		world.spawnParticles(ParticleTypes.CRIT,
				player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.25, 0.3, 0.25, 0.06);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
				player.getX(), player.getY() + 1.05, player.getZ(), 12, 0.3, 0.35, 0.3, 0.12);
		TkGunTexts.say(player, TkGunTexts.focusEnterText(), true);
		TkGun.LOGGER.info("[tkgun] 进入专注状态（{}，过门 {} tick，此后按住左键全自动、射速/伤害递增）玩家={}",
				aimState, aimStateTicks, player.getName().getString());
		return true;
	}

	/** 解除专注；由 Z 切换、过热或收枪触发，并按 Focus_over 过渡收尾。 */
	public static void stopFocus(ServerPlayerEntity player) {
		PState st = state(player);
		if (!st.deployed) {
			return;
		}
		clearPendingShots(player, st, "退出专注");
		restoreLastFocusAmmoSelection(player, st);
		st.deployed = false;
		st.focusShots = 0;
		st.heat = 0.0F;
		st.focusRamp = 0;
		st.rollFocusUntil = 0;          // 退出专注会消费掉所有快速蹲射意图
		st.rollFocusRequested = false;
		st.rollFocusAt = 0;
		// **退出专注 = 挂"缓慢 10"**（用户 2026-10-17："将缓慢效果绑定退出专注状态以及过热两种状态"）。
		// 时长 = 退出过门动画（`Focus_over`）那么长；翻滚会立刻清掉它（见 CombatRollHook）。
		// "缓慢"等级 = 放大器 + 1（原版 HUD 显示规则）：放大器 9 → 显示"缓慢 10"。
		// 隐藏粒子、保留图标，免得玩家以为中了别的东西。
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
				net.minecraft.entity.effect.StatusEffects.SLOWNESS,
				TkGun.SLOW_ON_EXIT_TICKS, TkGun.SLOW_ON_EXIT_AMPLIFIER, false, false, true));
		MinecraftServer server = player.getServer();
		if (server != null && TkGun.holdingGun(player) && st.state != TkGun.State.OVERHEAT) {
			// 退出动画 = 作者的 `Focus_over`（用户 2026-10-16："再次输入则退出并使用 Focus_over 动画"）。
			// 客户端把 OVER 播成 `Focus_over`；播完 `tickAll` 的状态到期分支发现 deployed=false → 回 IDLE。
			setState(server, player, TkGun.State.OVER, TkGun.FOCUS_EXIT_TICKS);
		} else if (server != null) {
			st.state = TkGun.State.IDLE;
			st.stateUntil = 0;
			broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
		}
		TkGunTexts.say(player, TkGunTexts.FOCUS_EXIT, true);
		TkGun.LOGGER.info("[tkgun] 专注解除 玩家={}", player.getName().getString());
	}

	/** 调试/运维：强制进入某个状态（/tkgun state）。 */
	public static void forceState(ServerPlayerEntity player, TkGun.State state, int ticks) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			setState(server, player, state, ticks);
		}
	}

	private static float rangedDamage(ServerPlayerEntity player) {
		return TkGun.definition(player).baseDamage();
	}

	/** 服务端看玩家这一 tick 是否还有移动输入（翻滚收尾选 idle/run 过渡用）。 */
	private static boolean wantsMove(ServerPlayerEntity player) {
		return Math.abs(player.forwardSpeed) > 0.01F || Math.abs(player.sidewaysSpeed) > 0.01F;
	}

	/** 动作结束：从这一 tick 起再屏蔽右键瞄准 1 秒。只延长，不缩短已有窗口。 */
	private static void lockAimAfterDodge(PState st, long now) {
		st.aimLockUntil = Math.max(st.aimLockUntil, now + AIM_LOCK_AFTER_DODGE_TICKS);
	}

	/**
	 * 滑铲真正开始时消费 Alt+Z / 潜行+Z 的组合专注意图。
	 * 与翻滚不同，Z 在滑铲里没有 0.38s 门槛：直接进入 `AIM_SLIDE`（sliding_fast_ready）。
	 */
	private static void consumeSlideFocus(ServerPlayerEntity player, PState st, long now) {
		if (!st.dodgeFocusQueued) {
			return;
		}
		boolean valid = now <= st.dodgeFocusQueuedAt + DODGE_COMBO_BUFFER_TICKS;
		st.dodgeFocusQueued = false;
		st.dodgeFocusQueuedAt = 0;
		if (valid && st.state == TkGun.State.SLIDE && TkGun.holdingGun(player)) {
			TkGun.LOGGER.info("[tkgun] SLIDE 开始，接收 Z+闪避组合预输入 -> sliding_fast_ready 玩家={}",
					player.getName().getString());
			startFocus(player);
		}
	}

	// ---------------------------------------------------------------- 每 tick
	private static void tickAll(MinecraftServer server) {
		tickBolts();
		tickBackpackProjectiles(server);
		tickBackpackStates(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			enforceLauncherOffhandStorage(player);
			removeGunFromOffhand(player);
			PState st = state(player);
			long now = player.getWorld().getTime();
			tickRollFallback(player, st, now);
			boolean holdingGunNow = TkGun.holdingGun(player);
			if (holdingGunNow) {
				ItemStack held = com.tk.gun.item.GunAmmoData.heldGun(player);
				if (!held.isEmpty()) {
					GunDefinition heldDefinition = TkGun.definition(held);
					if (st.gunDefinitionId != null
							&& !st.gunDefinitionId.equals(heldDefinition.id())) {
						st.deployed = false;
						st.focusShots = 0;
						st.shotIndex = 1;
						player.stopUsingItem();
						clearPendingShots(player, st, "切换枪种");
						if (st.state != TkGun.State.ROLL && st.state != TkGun.State.SLIDE) {
							setState(server, player, TkGun.State.IDLE, 1);
						}
						TkGun.LOGGER.info("[tkgun] 枪种 {} -> {}，已重置专注状态 玩家={}",
								st.gunDefinitionId, heldDefinition.id(), player.getName().getString());
					}
					st.gunDefinitionId = heldDefinition.id();
					if (!st.magLoaded || st.lastHeldGun != held) {
						st.lastHeldGun = held;
						loadMagazineFromGun(player, st);
						st.magLoaded = true;
						rebroadcast(player);
					} else {
						st.lastHeldGun = held;
					}
				}
			} else {
				st.magLoaded = false;
			}
			st.heldGunLastTick = holdingGunNow;
			if (holdingGunNow && st.magLoaded && !st.deployed) {
				persistHipMagazine(player, st);
			}

			// 翻滚/滑铲及结束后 1 秒：右键是完全屏蔽的。玩家可能在这之前已经按住右键，
			// 仅让 `BowgunItem#use` 返回 fail 不会结束已经开始的 use，所以这里每 tick 权威兜底。
			if (aimInputLocked(player) && player.isUsingItem()
					&& player.getActiveHand() == Hand.MAIN_HAND
					&& TkGun.isGun(player.getActiveItem())) {
				player.stopUsingItem();
			}

			// 过热冷却
			if (st.overheatUntil > 0 && now >= st.overheatUntil) {
				st.overheatUntil = 0;
				st.heat = TkGun.HEAT_MAX * 0.4F;
				rebroadcast(player);
			}
			if (!st.deployed && now >= st.overheatUntil) {
				st.heat = Math.max(0.0F, st.heat - TkGun.HEAT_COOL);
			}
			// 连射窗口过期就把计数清掉：`/tkgun status` 里看到的"连射"才不是过期数字
			if (st.combo > 0 && now > st.comboWindowUntil) {
				st.combo = 0;
			}
			// **蹲射准备那一声栓声**（用户 2026-10-17："将蹲射准备动作的音频替换为…栓声"）：
			// 时间点 = 作者埋在 `Focus_ready` 0.25s / `fast_ready` 0.0833s 的那一拍（进状态时已换算成 tick）。
			// 被翻滚 / 过热打断就不放了（那时候人已经不在架枪）。
			if (st.aimSoundAt > 0 && now >= st.aimSoundAt) {
				st.aimSoundAt = 0;
				if (TkGun.State.isAiming(st.state) && TkGun.holdingGun(player)) {
					playSound(player, TkGun.SND_BOLT, 1.0F, 1.0F);
					TkGun.LOGGER.info("[tkgun] 蹲射准备 -> 栓声（素材 栓声.ogg）玩家={}",
							player.getName().getString());
				}
			}
			// 拉栓计时（**不出声**）：用户 2026-10-17："修正蹲射（专注状态）每次射击后都会播放栓声的问题，
			// 使此声音只在进入蹲射状态时播放" —— 每发之后那声 `SND_BOLT` 删掉；栓声只由 `setState`
			// 进架枪时排的那一拍（`aimSoundAt`，见 startFocus/startAim）放。
			// ⚠️ `boltAt` 本身还在用：它是"上一发的拉栓没走完之前不许出下一发"的节奏闸（见 tickShots），
			// 只把音效去掉，计时别删。
			if (st.boltAt > 0 && now >= st.boltAt) {
				st.boltAt = 0;
			}
			// 换弹第二声：弹夹到位（v4 素材 `换弹2.ogg`，动画里埋在 0.833s 处）
			if (st.reloadClickAt > 0 && now >= st.reloadClickAt) {
				st.reloadClickAt = 0;
				playSound(player, TkGun.SND_RELOAD2, 0.9F, 1.0F);
			}
			// 连发节奏（攻击帧出膛 + 逐发排队，见 tickShots）
			tickShots(player, st, now);
			// —— 专注（架枪）：把枪收起来就自动解除，并**立刻重播一包**（客户端靠 focus 位挑动画）——
			if (st.deployed && !TkGun.holdingGun(player)) {
				st.deployed = false;
				TkGun.LOGGER.info("[tkgun] 收枪了 -> 专注自动解除 玩家={}", player.getName().getString());
				rebroadcast(player);
			}
			// 枪离手时立即清理射击队列、动画与拉栓计时，禁止切换物品后继续补射。
			if (!TkGun.holdingGun(player)
					&& (!st.shotQueue.isEmpty() || st.shotAnimating || st.boltAt > 0)) {
				TkGun.LOGGER.info("[tkgun] 枪离手 -> 清掉排队的 {} 发（不再补射）玩家={}",
						st.shotQueue.size(), player.getName().getString());
				clearPendingShots(player, st, "枪离手");
			}
			// 空弹匣不会自动装填；装填入口仅为 GunReloadPayload。专注移动锁见 FocusMovementLockMixin，
			// 缓慢效果仅由退出专注和过热施加。
			// **翻滚的兜底检测**（2026-10-17）：用户报"翻滚（alt键）中断效果未能生效" —— 如果
			// combat_roll 的事件没到（按键被别的 mod 抢了、或它自己没触发），这里用"水平速度突然很大"
			// 来认这次翻滚。（事件正常触发时这条不会重复执行 —— 那时状态已经变成 ROLL 了。）
			// ⚠️ 阈值不能再用 0.35：冲刺加成 +50% 之后疾跑约 0.42 格/tick，会把"加速疾跑"误判成翻滚
			// （表现：过热 / Focus_over 动画被突然打断）。见 GunStateMachine#ROLL_DETECT_SPEED。
			// 冲刺加成窗口（`dashUntil`）里的高速也**不算**翻滚 —— 那本来就是我们自己给的奖励。
			if ((st.state == TkGun.State.OVERHEAT || st.state == TkGun.State.OVER)
					&& now >= st.dashUntil
					&& player.getVelocity().horizontalLength() > GunStateMachine.ROLL_DETECT_SPEED) {
				interruptForRoll(player, st, "高速位移兜底");
			}
			//
			// ⚠️ 2026-10-17：服务端这条"按住左键自动连发"（靠扳机状态包）**删掉了** —— 用户报
			//    "按压一次打出两发"：那条包只要晚到/丢释放包，服务端就以为扳机还按着，于是自己又补一发；
			//    而客户端在专注里本来就会（按住超过 6 tick 后）每 5 tick 补一个**开火包**。
			//    现在连发只有一个来源（客户端补包），单击 = 一发，不会再出现"一下两发"。
			// 过热"不可移动" + 翻滚后的 3 秒加速冲刺（规则都在 GunStateMachine）
			GunStateMachine.tickImmovable(player, st, now);
			if (GunStateMachine.tickDash(player, st, now)) {
				rebroadcast(player);
			}
			GunStateMachine.tickNoJump(player, st, now);      // 散热期间屏蔽跳跃（属性修饰符）
			GunStateMachine.tickGunStepHeight(player);        // 持枪移动时自动跨 1 格
			// Alt 没有真正滚起来（被闪避锁/其它状态拒绝）时，别把 Z 永远吞掉：
			// 缓冲过期后退回普通专注切换。
			if (st.dodgeFocusQueued
					&& now > st.dodgeFocusQueuedAt + DODGE_COMBO_BUFFER_TICKS
					&& st.state != TkGun.State.ROLL
					&& st.state != TkGun.State.SLIDE
					&& st.state != TkGun.State.SLIDE_READY) {
				st.dodgeFocusQueued = false;
				st.dodgeFocusQueuedAt = 0;
				TkGun.LOGGER.info("[tkgun] Z+闪避组合缓冲过期（动作未开始）-> 按普通 Z 处理 玩家={}",
						player.getName().getString());
				startFocus(player);
			}
			// **翻滚快速蹲射预输入到点**：0.38s 前按下的 Z 在这里兑现。
			// 必须放在"状态自然结束"之前，否则 10 tick 的 ROLL 会先收成 IDLE，
			// 预输入就变成了普通举枪。
			if (st.state == TkGun.State.ROLL && st.rollFocusRequested
					&& st.rollFocusAt > 0 && now >= st.rollFocusAt) {
				TkGun.LOGGER.info("[tkgun] 翻滚快速蹲射预输入到点 -> 执行（第 {} tick）玩家={}",
						now - st.stateStarted, player.getName().getString());
				startFocus(player);
			}
			// Movement can begin after the single roll-end check. Exit the
			// recovery transition immediately instead of forcing a 4-tick
			// roll_to_idle segment before walking/running can take over.
			if (st.state == TkGun.State.ROLL_TO_IDLE
					&& TkGun.holdingGun(player) && wantsMove(player)) {
				st.stateUntil = 0;
				st.state = TkGun.State.IDLE;
				lockAimAfterDodge(st, now);
				broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
				TkGun.LOGGER.info("[tkgun] 翻滚收势期间出现移动输入 -> 立即切回行走/奔跑 玩家={}",
						player.getName().getString());
			}
			st.lastX = player.getX();
			st.lastZ = player.getZ();
			// 状态自然结束
			if (st.stateUntil > 0 && now >= st.stateUntil) {
				TkGun.State ended = st.state;
				st.stateUntil = 0;
				st.state = TkGun.State.IDLE;
				boolean holding = TkGun.holdingGun(player);
				// 快速蹲射过门结束：立即解除翻滚/滑铲留下的右键瞄准锁。
				if (ended == TkGun.State.AIM_ROLL || ended == TkGun.State.AIM_SLIDE) {
					st.aimLockUntil = 0;
				}
				// 翻滚收尾：站定播放 `roll_to_idle`；已在移动则直接交给 IDLE/run。
				// 快速专注只能由翻滚 0.38 秒处的组合输入进入，不再经过额外过渡动作。
				if (ended == TkGun.State.ROLL) {
					if (holding && wantsMove(player)) {
						lockAimAfterDodge(st, now);
						broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
					} else {
						TkGun.LOGGER.info("[tkgun] 翻滚结束（站定）-> roll_to_idle 玩家={}",
								player.getName().getString());
						setState(server, player, TkGun.State.ROLL_TO_IDLE,
								CombatRollHook.ROLL_TO_IDLE_TICKS);
					}
				} else if (ended == TkGun.State.ROLL_TO_IDLE) {
					lockAimAfterDodge(st, now);
					broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
				} else if (ended == TkGun.State.SLIDE || ended == TkGun.State.SLIDE_READY) {
					lockAimAfterDodge(st, now);
					broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
				} else if (st.deployed && holding && st.shotQueue.isEmpty()) {
					if (ended == TkGun.State.RELOAD || ended == TkGun.State.OVER) {
						// ⚠️ 2026-10-15 用户报的"换完弹之后不会恢复到架枪状态，直接离开架枪状态"就是这里：
						//    换弹动画（`reload`）的尾帧是"人侧身、枪端在胸前"，**不是架枪姿势** ——
						//    以前这里跟打完一发一样直接发 AIM_HOLD（= 客户端 CONTINUE 保持尾帧），
						//    保持住的自然是那个换弹尾帧。所以换完弹要**显式再举一次枪**：
						//    播作者的快速起枪 `fast_ready`（0.667 秒），它的尾帧就是架枪定格。
						//
						// 退出专注后必须显式重播举枪动画，不能保持 Focus_over 的站姿尾帧。
						//    进入专注的过门动画 `Focus_over` 的**尾帧也是站姿**（实测 `body.position` 末帧
						//    = [0,0,0]，而架枪姿势 `fast_ready`/`Focus_ready` 的末帧是 `[0,-8,0]` 蹲着）
						//    → OVER 结束也必须**显式再举一次枪**，不能直接 AIM_HOLD 保持 Focus_over 的尾针。
						setState(server, player, TkGun.State.AIM_FAST, TkGun.AIM_TICKS_FAST);
					} else {
						// 打完了还架着枪 → **保持**在这一发动画的尾帧（别重播举枪动作）
						setState(server, player, TkGun.State.AIM_HOLD, 600);
					}
				} else {
					broadcast(server, player, TkGun.State.IDLE, 0, st.heat);
				}
			}
			// （检视的定时音效/粒子点随旧素材的 `simple` 一起作废：新素材里没有 simple 了，
			//   INSPECT 现在播作者的 Focus_over_shooting 样品，它自己没有埋音效事件。）

			tickSlide(player, st, now);
			tickSlideReady(player, st, now);
			tickAimSlow(player, st);
		}
	}

	/** 推进已经由 Alt 循环启动的滑铲；不再把潜行键（Z）本身当成滑铲触发键。 */
	private static void tickSlide(ServerPlayerEntity player, PState st, long now) {
		MinecraftServer server = player.getServer();
		if (now < st.slideUntil) {
			// 线性减速：v = v0 × (1 - 已滑比例)。整段滑多远由 TkGun.SLIDE_DISTANCE 说好，
			// 每 tick 直接"摆好速度"，所以湿地/冰面也滑一样远（原版摩擦只会叠在下一 tick 上，被这里覆盖）。
			double t = (now - st.slideStart) / (double) TkGun.SLIDE_TICKS;
			double remain = Math.max(0.0D, 1.0D - t);
			double v0 = 2.0D * TkGun.SLIDE_DISTANCE / (double) TkGun.SLIDE_TICKS;
			// **按距离封顶**：玩家自己按着 W 会给滑铲额外加速（实测 3.8 → 无输入 3.55 / 按着 W 4.73），
			// 所以这里盯住"离起点已经滑了多远"，剩下的距离才是这一 tick 允许的速度 ——
			// 结果就是无论按不按键，位移都落在 SLIDE_DISTANCE 上（用户要的 3~4 格）。
			double dx = player.getX() - st.slideFromX;
			double dz = player.getZ() - st.slideFromZ;
			double left = TkGun.SLIDE_DISTANCE - Math.sqrt(dx * dx + dz * dz);
			if (left <= 0.05D) {
				st.slideUntil = now;            // 到量就收，后面走收势/结束逻辑
			} else {
				double speed = Math.min(v0 * remain, left);
				Vec3d v = player.getVelocity();
				player.setVelocity(st.slideDirX * speed, v.y, st.slideDirZ * speed);
				player.velocityModified = true;
			}
			// 把玩家自己的 WASD 输入按住滑铲这段清零：不然"按着前进键滑"会比说好的距离多滑一格多
			// （实测 3.5 理论 → 不按键 3.2 格、按着键 4.7 格）。清零之后滑多远就是滑多远，可验收。
			player.forwardSpeed = 0.0F;
			player.sidewaysSpeed = 0.0F;
			player.upwardSpeed = 0.0F;
			if (player.getWorld().getTime() % 2 == 0) {
				player.getServerWorld().spawnParticles(ParticleTypes.CLOUD,
						player.getX(), player.getY() + 0.15, player.getZ(), 2, 0.2, 0.05, 0.2, 0.02);
			}
		} else if (st.slideMeasuring) {
			// 滑完把**实测位移**打进日志：用户要 3~4 格，这条数字就是验收标准，别靠看
			st.slideMeasuring = false;
			// 滑铲结束后再锁 1 秒；从起铲时刻算会让最后一段滑行吃掉冷却。
			st.dodgeBlockUntil = Math.max(st.dodgeBlockUntil, now + 20);
			double dx = player.getX() - st.slideFromX;
			double dz = player.getZ() - st.slideFromZ;
			TkGun.LOGGER.info("[tkgun] 滑铲结束：实测位移 {}(水平 {}) 格，理论 {} 格，时长 {} tick",
					round3(Math.sqrt(dx * dx + dz * dz)),
					round3(dx * st.slideDirX + dz * st.slideDirZ),
					TkGun.SLIDE_DISTANCE, TkGun.SLIDE_TICKS);
			// 滑到终点时右键仍按着则播放 sliding_ready 收势；滑行中右键走 startSlideBrace。
			if (TkGun.holdingGun(player) && player.isUsingItem() && !TkGun.State.isAiming(st.state)) {
				TkGun.LOGGER.info("[tkgun] 滑铲自然结束且右键仍按着 -> 播收势 sliding_ready 玩家={}",
						player.getName().getString());
				startSlideReady(player);
			}
		}
	}

	/** 玩家现在是不是在滑铲里（给 BowgunItem 判断"滑到一半按右键 = 收势接举枪"）。 */
	/** 调试/验收用：跳过"疾跑+蹲+落地"的输入判定直接起一次滑铲（/tkgun slide）。 */
	public static void forceSlide(ServerPlayerEntity player) {
		PState st = state(player);
		long now = player.getWorld().getTime();
		st.lastDodgeKind = 2;
		st.slideUntil = 0;                 // 允许立刻起铲
		st.slideCooldownUntil = 0;
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d flat = new Vec3d(look.x, 0.0, look.z).normalize();
		st.slideUntil = now + TkGun.SLIDE_TICKS;
		st.slideDirX = flat.x;
		st.slideDirZ = flat.z;
		st.slideStart = now;
		st.slideFromX = player.getX();
		st.slideFromZ = player.getZ();
		st.slideMeasuring = true;
		double v0 = 2.0D * TkGun.SLIDE_DISTANCE / (double) TkGun.SLIDE_TICKS;
		player.setVelocity(flat.x * v0, player.getVelocity().y, flat.z * v0);
		player.velocityModified = true;
		setState(player.getServer(), player, TkGun.State.SLIDE, TkGun.SLIDE_TICKS);
		consumeSlideFocus(player, st, now);
		playSound(player, TkGun.SND_VENT, 0.5F, 1.6F);
	}

	public static boolean isSliding(ServerPlayerEntity player) {
		PState st = state(player);
		return player.getWorld().getTime() < st.slideUntil;
	}

	/**
	 * 滑铲收势：播放 {@code sliding_ready}，结束后回到 IDLE 或重新举枪。
	 *
	 * <p>触发条件是"**滑铲自然滑到终点时右键还按着**"（见 {@code tickSlide} 里滑完那一下）。
	 * 滑铲中按右键走更快的 {@link #startSlideBrace} 路径。
	 */
	public static void startSlideReady(ServerPlayerEntity player) {
		PState st = state(player);
		st.slideUntil = 0;                      // 立刻结束滑行
		// 收势之后的一小段时间内右键 = 快速部署（AIM_FAST）——"滑铲里按右键更快进架枪"
		st.fastAimUntil = player.getWorld().getTime() + 60;
		setState(player.getServer(), player, TkGun.State.SLIDE_READY, TkGun.SLIDE_READY_TICKS);
		playSound(player, TkGun.SND_BOLT, 0.5F, 1.3F);
	}

	/** 收势结束后恢复 IDLE；若仍处于专注则重新进入快速举枪。 */
	private static void tickSlideReady(ServerPlayerEntity player, PState st, long now) {
		if (st.state != TkGun.State.SLIDE_READY || now < st.stateUntil) {
			return;
		}
		st.state = TkGun.State.IDLE;
		st.stateUntil = 0;
		// 仅当服务端仍标记为专注时重新举枪；普通滑铲结束直接回 IDLE。
		if (st.deployed && TkGun.holdingGun(player)) {
			st.fastAimUntil = now + 60;
			startAim(player);
		}
	}

	/** 举枪时减速（原版只对 BOW/CROSSBOW 这类 useAction 减速，我们自己的物品要手写）。 */
	private static void tickAimSlow(ServerPlayerEntity player, PState st) {
		if (!TkGun.holdingGun(player)) {
			return;
		}
		if (aimInputLocked(player)) {
			return;
		}
		boolean aiming = player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND;
		if (!aiming || player.getWorld().getTime() < st.slideUntil) {
			return;
		}
		// **蹲射预备 / 翻滚 / 滑铲 / 翻滚加速期间，长按右键都不减速**（用户 2026-10-17）：
		// 这些状态下右手按着右键只是"顺手"，不该把速度和动作按住；HUD/准星那套不受影响。
		if (TkGun.State.isAiming(st.state) || st.state == TkGun.State.ROLL
				|| st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY) {
			return;
		}
		// **翻滚后的加速冲刺期间不算瞄准**（用户 2026-10-17 保留的那半）：
		// 疾跑不再挡瞄准 —— 用户："撤销原版疾跑不允许瞄准的设定，在原版疾跑状态下瞄准会终止疾跑并
		// 进入站立射击状态"；终止疾跑由下面的 `player.setSprinting(false)` 负责（客户端也同步压一次）。
		if (player.getWorld().getTime() < st.dashUntil) {
			return;
		}
		// **瞄着就取消疾跑**（用户 2026-10-17："将原版疾跑状态下按瞄准按键调整为取消疾跑"）。
		// 原版自己就不允许"用着物品还疾跑"（拉弓/吃东西都是），我们这把枪是自定义物品，得手写这一条。
		// ⚠️ 必须放在下面那个"速度 > 0.03 才处理"的判断**外面**：站着不动时按瞄准也得把疾跑清掉，
		//    否则一走动又直接是疾跑（客户端那边同步也做了一份，见 TkGunClient#trackMovement）。
		player.setSprinting(false);
		Vec3d v = player.getVelocity();
		Vec3d flat = new Vec3d(v.x, 0.0, v.z);
		if (flat.length() > 0.03D) {
			Vec3d slowed = flat.multiply(0.45D);
			player.setVelocity(slowed.x, v.y, slowed.z);
			player.velocityModified = true;
		}
	}

	/** 给别的 mod（比如战利品/成就）用：玩家附近掉落的子弹壳。 */
	public static void scatterShell(ServerPlayerEntity player, ItemStack shell) {
		ItemEntity e = new ItemEntity(player.getServerWorld(), player.getX(), player.getY() + 1.0, player.getZ(), shell);
		player.getServerWorld().spawnEntity(e);
	}

	/**
	 * **翻滚打断"过热 / Focus_over"**：清掉缓慢、复位专注标记、过热顺带清掉过热计时，
	 * 然后广播 ROLL（动画就被翻滚接管了）。
	 *
	 * <p>两条路径共用：① combat_roll 的翻滚事件（{@link #onRollStarted}）；
	 * ② `tickAll` 里的"高速位移兜底"（事件万一没到，Alt 翻滚也得能打断）。
	 *
	 * <p>用户 2026-10-17："Overheat 和 Focus_over 状态下输入翻滚将会中断动画并清空缓慢效果"。
	 */
	private static void interruptForRoll(ServerPlayerEntity player, PState st, String reason) {
		boolean wasOverheat = st.state == TkGun.State.OVERHEAT;
		clearPendingShots(player, st, "翻滚离开专注");
		player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
		restoreLastFocusAmmoSelection(player, st);
		st.deployed = false;
		st.focusShots = 0;
		st.focusRamp = 0;
		st.stateUntil = 0;
		if (wasOverheat) {
			st.overheatUntil = 0;                      // 翻滚把过热也打断（不然凉下来还会自己接着连发）
			st.heat = TkGun.HEAT_MAX * 0.4F;
		}
		broadcastRoll(player, com.tk.gun.CombatRollHook.ROLL_DURATION);
		TkGun.LOGGER.info("[tkgun] {} 里翻滚（{}）-> 打断动画 + 清掉缓慢 玩家={}",
				wasOverheat ? "过热" : "Focus_over 过门", reason, player.getName().getString());
	}

	/**
	 * **防滚一次后的"强制位移滑铲"**（用户 2026-10-17："添加一段强制位移并使用 sliding 动画…
	 * 翻滚一次后不允许使用模组翻滚并执行 sliding 状态"）。
	 *
	 * <p>和 `tickSlide` 的起手完全同一套数：沿水平视线给一次初速（`2×SLIDE_DISTANCE/SLIDE_TICKS`），
	 * 状态切 `SLIDE`（客户端播 `sliding`），`slideUntil` 管减速与收尾。
	 */
	private static void startModSlide(ServerPlayerEntity player, PState st, long now) {
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
		if (flat.lengthSquared() < 1.0E-6D || player.getServer() == null) {
			return;
		}
		flat = flat.normalize();
		double v0 = 2.0D * TkGun.SLIDE_DISTANCE / (double) TkGun.SLIDE_TICKS;
		player.setVelocity(flat.x * v0, player.getVelocity().y, flat.z * v0);
		player.velocityModified = true;
		// 和 tickSlide 的自然滑铲保持同一套完整状态；否则后续 tick 会读到默认 0 方向，
		// 第一帧就把速度压成 0，表现成“刚滑一下就停”。
		clearPendingShots(player, st, "滑铲离开专注");
		st.deployed = false;
		st.focusShots = 0;
		st.focusRamp = 0;
		st.slideDirX = flat.x;
		st.slideDirZ = flat.z;
		st.slideFromX = player.getX();
		st.slideFromZ = player.getZ();
		st.slideStart = now;
		st.slideUntil = now + TkGun.SLIDE_TICKS;
		st.slideCooldownUntil = now + TkGun.SLIDE_COOLDOWN;
		st.slideMeasuring = true;
		setState(player.getServer(), player, TkGun.State.SLIDE, TkGun.SLIDE_TICKS);
		consumeSlideFocus(player, st, now);
		playSound(player, TkGun.SND_VENT, 0.5F, 1.6F);   // 铲地摩擦（和原滑铲同一套音）
		TkGun.LOGGER.info("[tkgun] 翻滚后再按 Alt -> 强制位移滑铲（sliding 动画）+ 屏蔽翻滚/滑铲 20 tick（1 秒）玩家={}",
				player.getName().getString());
	}

	private static void startFallbackRoll(ServerPlayerEntity player, PState st, long now) {
		Vec3d direction = new Vec3d(st.rollRequestX, 0.0D, st.rollRequestZ);
		if (direction.lengthSquared() < 1.0E-4D) {
			Vec3d velocity = player.getVelocity();
			direction = new Vec3d(velocity.x, 0.0D, velocity.z);
		}
		if (direction.lengthSquared() < 1.0E-4D) {
			direction = new Vec3d(player.sidewaysSpeed, 0.0D, player.forwardSpeed)
					.rotateY(-(float) Math.toRadians(player.getYaw()));
		}
		if (direction.lengthSquared() < 1.0E-4D) {
			Vec3d look = player.getRotationVec(1.0F);
			direction = new Vec3d(look.x, 0.0D, look.z);
		}
		if (direction.lengthSquared() < 1.0E-6D) {
			return;
		}
		direction = direction.normalize();
		double dashSpeed = rollDashSpeed(direction);
		st.rollRequestX = 0.0D;
		st.rollRequestZ = 0.0D;
		player.setVelocity(direction.x * dashSpeed,
				player.getVelocity().y, direction.z * dashSpeed);
		player.velocityModified = true;
		st.fallbackRollReadyAt = now + FALLBACK_ROLL_COOLDOWN_TICKS;
		spawnNativeRollVisuals(player);
		broadcastRoll(player, CombatRollHook.ROLL_DURATION);
		TkGun.LOGGER.info("[tkgun] 无 Combat Roll：执行模组内置翻滚，速度={} 冷却 {} tick 玩家={}",
				dashSpeed, FALLBACK_ROLL_COOLDOWN_TICKS, player.getName().getString());
	}

	private static void spawnNativeRollVisuals(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI / 5.0D;
			world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
					player.getX() + Math.cos(angle) * 0.8D,
					player.getY() + 0.9D,
					player.getZ() + Math.sin(angle) * 0.8D,
					1, 0.0D, 0.0D, 0.0D, 0.02D);
		}
	}

	/** Falls back only when Combat Roll did not deliver its own physical-roll event in time. */
	private static void tickRollFallback(ServerPlayerEntity player, PState st, long now) {
		if (!st.rollFallbackPending || now < st.rollFallbackAt) {
			return;
		}
		st.rollFallbackPending = false;
		st.rollFallbackAt = 0;
		if (st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE
				|| st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY
				|| player.getVelocity().horizontalLength() >= 0.15D) {
			return;
		}
		Vec3d flat = new Vec3d(st.rollRequestX, 0.0D, st.rollRequestZ);
		if (flat.lengthSquared() < 1.0E-4D) {
			Vec3d look = player.getRotationVec(1.0F);
			flat = new Vec3d(look.x, 0.0D, look.z);
		}
		st.rollRequestX = 0.0D;
		st.rollRequestZ = 0.0D;
		if (flat.lengthSquared() > 1.0E-6D) {
			flat = flat.normalize();
			flat = flat.multiply(rollDashSpeed(flat));
			player.setVelocity(flat.x, player.getVelocity().y, flat.z);
			player.velocityModified = true;
		}
		broadcastRoll(player, CombatRollHook.ROLL_DURATION);
		TkGun.LOGGER.info("[tkgun] Combat Roll 未启动物理翻滚 -> 延迟 {} tick 后执行兜底冲刺 玩家={}",
				2, player.getName().getString());
	}

	private static double rollDashSpeed(Vec3d direction) {
		double lateralWeight = Math.min(1.0D, Math.abs(direction.x));
		return TkGun.ROLL_DASH_SPEED
				* (1.0D + lateralWeight * (TkGun.ROLL_SIDE_SPEED_MULTIPLIER - 1.0D));
	}

	public static void broadcastRoll(ServerPlayerEntity player, int ticks) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			PState st = state(player);
			long now = player.getWorld().getTime();
			int gameplayTicks = Math.max(1, ticks);
			// 每次真正开始翻滚都重开预输入。Alt+Z 的组合输入可能比 ROLL 状态先到，
			// 先把队列里的专注意图转成“0.38s 预输入”；没有组合输入才清空。
			boolean queuedFocus = st.dodgeFocusQueued
					&& now <= st.dodgeFocusQueuedAt + DODGE_COMBO_BUFFER_TICKS;
			st.rollFocusRequested = queuedFocus;
			st.dodgeFocusQueued = false;
			st.dodgeFocusQueuedAt = 0;
			st.rollInputAccepted = false;
			st.rollFocusAt = now + com.tk.gun.CombatRollHook.ROLL_FAST_FOCUS_TICKS;
			// 快速蹲射宽限从"翻滚玩法窗口结束"再往后算。多次路径（combat_roll 事件 / 按键兜底 /
			// 高速位移兜底）在同一 tick 重复进来也只延长窗口，不会缩短或重复扣奖励。
			st.rollFocusUntil = Math.max(st.rollFocusUntil,
					now + gameplayTicks + ROLL_FAST_FOCUS_GRACE_TICKS);
			if (queuedFocus) {
				TkGun.LOGGER.info("[tkgun] ROLL 开始，接收 Alt+Z 组合预输入 -> 0.38s 快速蹲射 玩家={}",
						player.getName().getString());
			}
			setState(server, player, TkGun.State.ROLL, ticks);
		}
	}

	/**
	 * **翻滚开始**（`CombatRollHook` 调）：走状态机那一关 ——
	 * 连锁闪避（翻滚↔滑铲 连着来）会被锁；每次翻滚给 3 秒加速冲刺；翻滚 = 离开专注射击。
	 * 翻滚本身的位移/动画归 combat_roll，我们只管枪这边。
	 */
	public static void onRollStarted(ServerPlayerEntity player) {
		PState st = state(player);
		long now = player.getWorld().getTime();
		// 滑铲/收势不能被 combat_roll 的翻滚事件半路顶掉；滑铲结束后的 1 秒锁由 dodgeBlockUntil 管。
		if (st.state == TkGun.State.SLIDE || st.state == TkGun.State.SLIDE_READY) {
			TkGun.LOGGER.info("[tkgun] 滑铲/收势中 -> 忽略翻滚事件 玩家={}", player.getName().getString());
			return;
		}
		if (st.state == TkGun.State.AIM || st.state == TkGun.State.AIM_FAST
				|| st.state == TkGun.State.AIM_ROLL || st.state == TkGun.State.AIM_SLIDE) {
			TkGun.LOGGER.info("[tkgun] 专注准备/快速专注中 -> 忽略迟到翻滚事件（当前 {}）玩家={}",
					st.state, player.getName().getString());
			return;
		}
		// 同一次翻滚的重复事件不重新广播/刷新状态。
		if (st.state == TkGun.State.ROLL || st.state == TkGun.State.ROLL_TO_IDLE) {
			return;
		}
		// 非专注状态下，翻滚结束后的下一次 Alt 必须走滑铲。combat_roll 事件通常先于
		// GunRollPayload 到达；这里不能抢先广播 ROLL，否则会把“滑铲”覆盖成第二次翻滚。
		if (st.lastDodgeKind == 1 && !st.deployed && !st.rollInputAccepted
				&& now < st.dashUntil && st.lastDodgeAt != now) {
			TkGun.LOGGER.info("[tkgun] 翻滚事件：下一次 Alt 应由滑铲接管，事件先忽略 玩家={}",
					player.getName().getString());
			return;
		}
		if (st.lastDodgeKind == 1 && !st.deployed && now >= st.dashUntil) {
			st.lastDodgeKind = 0;
			TkGun.LOGGER.info("[tkgun] roll event: slide window expired -> restart roll cycle player={}",
					player.getName().getString());
		}
		// 滑铲结束后的 1 秒锁同样约束 combat_roll 自己触发的事件。
		// 过热 / Focus_over 仍允许翻滚打断，避免恢复流程被锁死。
		if (now < st.dodgeBlockUntil && st.state != TkGun.State.OVERHEAT
				&& st.state != TkGun.State.OVER) {
			TkGun.LOGGER.info("[tkgun] 翻滚事件：滑铲后冷却中，忽略 玩家={}", player.getName().getString());
			return;
		}
		// **先按"一次闪避"结算**：3 秒 +30% 加速冲刺。
		// 用户 2026-10-17："将专注状态下进行翻滚和过热状态下的翻滚后给予上文提过的翻滚后获得的相同效果"
		// —— 也就是三种翻滚（普通 / 专注里 / 过热里）都要拿到同一套冲刺奖励。
		// 以前这里把过热分支放在前面**直接 return**，那两种翻滚就少了这个奖励。
		// （连锁闪避冷却里 `onDodgeStarted` 自己会拒绝并给提示，返回 true = 锁着）
		boolean rewardAlreadyGranted = st.rollFallbackPending || st.rollInputAccepted;
		st.rollFallbackPending = false;
		st.rollFallbackAt = 0;
		boolean locked = rewardAlreadyGranted
				? false : GunStateMachine.onDodgeStarted(player, st, now, true);
		// **过热 / Focus_over 里翻滚 = 打断动画 + 清掉缓慢**
		// （用户 2026-10-17："同时 Overheat 和 Focus_over 状态下输入翻滚将会中断动画并清空缓慢效果"）。
		// 这两段过门/结算动画本来就不该挡住闪避，所以这里一定打断（即使闪避键在冷却）。
		if (st.state == TkGun.State.OVERHEAT || st.state == TkGun.State.OVER) {
			interruptForRoll(player, st, "翻滚事件");
			return;
		}
		if (locked) {
			TkGun.LOGGER.info("[tkgun] 闪避被锁（连锁冷却中）-> 这次翻滚不给冲刺/不切枪状态 玩家={}",
					player.getName().getString());
			return;
		}
		clearPendingShots(player, st, "翻滚离开专注");
		st.deployed = false;                 // 翻滚 = 离开专注射击
		st.focusShots = 0;
		st.focusRamp = 0;
		broadcastRoll(player, com.tk.gun.CombatRollHook.ROLL_DURATION);
	}

	/** 让别的系统也能查到玩家当前状态（客户端只读镜像）。 */
	public static Map<UUID, PState> all() {
		return STATES;
	}

	/**
	 * Hitscan entity detection with a horizontal radius.
	 *
	 * <p>原版 {@code ProjectileUtil.raycast} 只检测实体自身碰撞箱；这里把实体碰撞箱
	 * 向外扩 {@code radius} 格，形成"射线 + 水平判定半径"的判定。仍要求到实体碰撞箱中心
	 * 的视线不被方块挡住，避免隔墙命中。
	 */
	private static net.minecraft.util.hit.EntityHitResult raycastEntityWithinRadius(
			ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d end, double radius) {
		Vec3d delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0E-6D) {
			return null;
		}
		Vec3d direction = delta.multiply(1.0D / length);
		net.minecraft.util.math.Box search = net.minecraft.util.math.Box.from(start)
				.stretch(delta).expand(radius);
		net.minecraft.util.hit.EntityHitResult best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : world.getOtherEntities(player, search,
				e -> e.isAlive() && !e.isSpectator() && e != player && e.canHit()
						&& !CombatTargeting.isGolem(e))) {
			net.minecraft.util.math.Box bounds = entity.getBoundingBox();
			Vec3d center = bounds.getCenter();
			double along = center.subtract(start).dotProduct(direction);
			if (along < 0.0D) {
				continue;                       // 只判定枪口前方，避免背后 1 格误伤
			}
			java.util.Optional<Vec3d> direct = bounds.raycast(start, end);
			along = Math.min(length, along);
			Vec3d closest = start.add(direction.multiply(along));
			double dx = Math.max(Math.max(bounds.minX - closest.x, 0.0D), closest.x - bounds.maxX);
			double dz = Math.max(Math.max(bounds.minZ - closest.z, 0.0D), closest.z - bounds.maxZ);
			double gap = Math.sqrt(dx * dx + dz * dz);
			if (gap > radius) {
				continue;
			}
			Vec3d point;
			double distance;
			if (direct.isPresent()) {
				point = direct.get();
				distance = start.distanceTo(point);
			} else {
				// 半径命中时把落点放在目标中心，避免粒子停在目标前方 1 格。
				point = center;
				distance = Math.min(length, along);
			}
			if (distance >= bestDistance) {
				continue;
			}
			net.minecraft.util.hit.BlockHitResult los = world.raycast(new RaycastContext(start, center,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
			if (los.getType() != net.minecraft.util.hit.HitResult.Type.MISS
					&& start.distanceTo(los.getPos()) < start.distanceTo(center) - 0.05D) {
				continue;
			}
			bestDistance = distance;
			best = new net.minecraft.util.hit.EntityHitResult(entity, point);
		}
		return best;
	}

	/**
	 * **一次真实出膛**：量到的枪口 + 朝向 → 射线命中（hitscan）→ 伤害 / 落点爆裂 / 弹道粒子 / 枪口粒子。
	 *
	 * <p>2026-10-14 从 {@link #releaseShot} 里抽出来，因为**强化的过载连射**要复用同一份：
	 * 普通单发是"服务端排队 + 出伤帧出膛"；强化连射是"客户端按 `Focus_over_shooting` 里枪口焰的
	 * 节奏连发 7 个开火包"，服务端每收到一个包就当场调这里一次（见 {@link #tryOverFire}）。
	 *
	 * @param damageMult 伤害倍率（现在只有普通单发 1.0 这一个调用方；留参数是为了以后加"蓄力/过载"类效果）
	 */
	private static void fireOnce(ServerPlayerEntity player, PState st, float damageMult, String ammoId,
								 ItemStack ammoStack) {
		String ammoPath = "";
		try {
			ammoPath = net.minecraft.util.Identifier.tryParse(ammoId).getPath();
		} catch (RuntimeException ignored) {
		}
		boolean explosive = "bolt".equals(ammoPath) || "explosive_bolt".equals(ammoPath);
		boolean cutting = "cutting_bolt".equals(ammoPath);
		boolean sculk = "sculk_bolt".equals(ammoPath);
		boolean roseQuartz = "rose_quartz_bolt".equals(ammoPath);
		// —— 枪口 / 枪管朝向 ——
		// 优先用**客户端渲染 locator 骨骼时量出来的**（真正看得见的枪口/枪管）；
		// 没有或不合理（离玩家太远 = 乱报）就退回服务端按 bodyYaw 算的老办法。
		Vec3d muzzle;
		Vec3d dir;
		boolean usedClientMuzzle = st.fireMuzzleOk;
		if (usedClientMuzzle) {
			muzzle = new Vec3d(st.fireMx, st.fireMy, st.fireMz);
			dir = new Vec3d(st.fireDx, st.fireDy, st.fireDz);
		} else {
			muzzle = muzzlePos(player);
			dir = muzzleDir(player);
		}
		// ⚠️ 这里**不要**清 `fireMuzzleOk`！（2026-10-14 用户："第一发是对的，后面几发不在枪口"）
		// 客户端发枪口数据是"扣扳机那一下"发的，而第 2..N 发是排队十几 tick 后才出膛的 ——
		// 每发清一次标志的话，后面几发就会退回用服务端**固定推算**的枪口（MUZZLE_SIDE=-0.25 → 偏到枪口左边）。
		// 保留标志 = 整梭子都用客户端量出来的真实枪口（下一发扣扳机时客户端会再刷新一次）。
		dir = dir.normalize();

		ServerWorld world = player.getServerWorld();
		// —— v4 素材（2026-10-16）新加的 `二级加速弹壳` 粒子 ——
		// 作者给的那 6 帧弹壳，配的动画是"整梭连射（射速越打越快）"，在我们的状态机里对应
		// **专注里 ramp 上来之后**的那几发：从枪身右侧（抛壳口大概位置 = 枪口往回收 0.6 格）甩出去。
		if (st.focusRamp >= TkGun.FOCUS_SHOT2_RAMP) {
			double yaw = Math.toRadians(-player.getYaw());
			Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
			world.spawnParticles(TkGunParticles.SHELL_BOOST,
					muzzle.x - dir.x * 0.6D + right.x * 0.2D, muzzle.y + 0.15D,
					muzzle.z - dir.z * 0.6D + right.z * 0.2D, 1,
					right.x * 0.35D, 0.2D, right.z * 0.35D, 0.7D);
		}
		// ⚠️ 枪口在**模型自己的高度**上（作者把炮扛在腰/胯位置，实测离脚底只有 0.1~0.6 格），
		//    贴着斜坡/台阶时这个点可能正好在一个方块里面 → 射线 0 距离就"撞"停，像卡壳。
		//    这里沿枪管方向一点点往前挪，直到离开方块（最多 1.5 格），观感上还是从枪口出来。
		int nudges = 0;
		while (nudges < 15 && !world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(muzzle)).isAir()) {
			muzzle = muzzle.add(dir.multiply(0.1D));
			nudges++;
		}
		// 枪口自发光：与模型 fire1..4 的 0.2083s 枪口焰同寿命，5 tick 内由亮到暗。
		// 位置稍微沿枪管前移，避免辉光中心被枪管挡住。
		Vec3d glowPos = muzzle.add(dir.multiply(0.06D));
		world.spawnParticles(TkGunParticles.MUZZLE_GLOW,
				glowPos.x, glowPos.y, glowPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		// 熔铁矢：从枪口定位器发射满蓄力三叉戟，走真实弹丸/附魔/回收。
		if ("bolt".equals(ammoPath)) {
			spawnMoltenTrident(world, player, muzzle, dir, ammoStack);
			return;
		}
		if ("armor_piercing_bolt".equals(ammoPath)) {
			spawnGrapeshot(world, player, muzzle, dir, damageMult);
			return;
		}
		if (roseQuartz) {
			spawnRoseQuartz(world, player, muzzle, dir, damageMult);
			return;
		}
		// —— 射线（hitscan）：先看方块、再看实体，取更近的那个 ——
		Vec3d end = muzzle.add(dir.multiply(TkGun.HITSCAN_RANGE));
		net.minecraft.util.hit.BlockHitResult blockHit = world.raycast(new RaycastContext(muzzle, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		double blockDist = blockHit.getType() == net.minecraft.util.hit.HitResult.Type.MISS
				? TkGun.HITSCAN_RANGE : muzzle.distanceTo(blockHit.getPos());
		// These three rounds use a 1.5-block horizontal cylinder around the shot ray.
		double entityHitRadius = explosive || cutting || sculk ? 1.5D : 1.0D;
		net.minecraft.util.hit.EntityHitResult entHit =
				raycastEntityWithinRadius(world, player, muzzle, end, entityHitRadius);
		double entDist = entHit == null ? Double.MAX_VALUE : muzzle.distanceTo(entHit.getPos());

		Vec3d hitPos = blockHit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? end : blockHit.getPos();
		boolean hitSomething = blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS;
		if (entHit != null && entDist <= blockDist) {
			hitPos = entHit.getPos();
			hitSomething = true;
		}
		net.minecraft.entity.Entity hitEntity =
				entHit != null && entDist <= blockDist ? entHit.getEntity() : null;
		Vec3d impactPos = hitPos;
		boolean didHit = hitSomething;
		Vec3d impactDir = dir;
		if (PROBE_NEXT_SHOT) {
			PROBE_NEXT_SHOT = false;
			TkGun.LOGGER.info("[tkgun] 枪口实测: 枪口=({}, {}, {}) 玩家=({}, {}, {}) 相对高度={} 朝向=({}, {}, {}) 客户端量到={}",
					round3(muzzle.x), round3(muzzle.y), round3(muzzle.z),
					round3(player.getX()), round3(player.getY()), round3(player.getZ()),
					round3(muzzle.y - player.getY()),
					round3(dir.x), round3(dir.y), round3(dir.z), st.fireMuzzleOk);
		}

		// —— 落点：爆裂粒子 + 命中声 ——
		// ⚠️ 2026-10-17 用户："删除弹头击中后的 minecraft 原版爆炸特效及其音效，并将声音替换为…imp1"：
		//   * `world.createExplosion(...)`（非破坏性，但会**自动**放原版爆炸粒子 + 原版爆炸声）整段删掉；
		//     那条爆炸带的**溅射伤害**也随之没有了（直伤 + 击退仍在上面的 hitscan 里）——
		//     以后要溅射再单独写一份不带原版特效的版本。
		//   * `ParticleTypes.EXPLOSION`（原版那团灰白爆炸云）删掉；素材自己的 `energy_xplo` + 火/烟/火花保留。
		//   * 命中声 = 素材 `imp1.ogg`（`tkgun:imp1`），**服务端**在命中点放 → 分发后附近所有人都听得见。
		// —— 弹道 ——
		// ⚠️ 2026-10-15 用户："里面给了那个 bullet 的那个子弹模型，你用这个去代表那个弹丸" ——
		//    所以那条 Flame/Smoke 铺出来的曳光粒子线**撤了**，改成真正飞出去的**子弹实体**
		//    （`tkgun:bullet`，贴图 = 素材里的 `粒子/bullet.png`，3 帧循环）。
		//    伤害依旧是上面这条 hitscan（瞬时、服务端权威），子弹只是画面上看得见的那颗。
		double trail = muzzle.distanceTo(hitPos);
		com.tk.gun.entity.BulletEntity visual =
				spawnBullet(world, player, muzzle, dir, trail, ammoId);
		Runnable impact = () -> applyBulletImpact(world, player, hitEntity, impactPos, didHit,
				explosive, cutting, sculk, roseQuartz, damageMult, impactDir);
		if (visual != null) {
			visual.setImpactAction(impact);
		} else {
			impact.run();
		}
		TkGun.LOGGER.info("[tkgun] hitscan: 枪口=({}, {}, {}) 落点=({}, {}, {}) 距离={} 命中实体={} 客户端量到枪口={}",
				round3(muzzle.x), round3(muzzle.y), round3(muzzle.z),
				round3(hitPos.x), round3(hitPos.y), round3(hitPos.z), round3(trail), entHit != null,
				usedClientMuzzle);

		// —— 枪口特效 ——
		// ⚠️ 2026-10-15 用户："**枪口，不要使用那个傀儡的、它的那个攻击特效**，因为枪口它本来就有了" ——
		//    枪口焰本体（fire1..fire4 四段平面 + dash 速度线 + 弹壳 shell）是**作者模型自带**的，
		//    客户端按动画里的 scale 关键帧翻出来。之前服务端在这上面又叠了一层
		//    `energy_converge`（那是从**傀儡**那包 粒子.zip 里切出来的）+ 火焰/烟，
		//    现在**全部撤掉**，枪口只留模型自己的表现。
		// 抛壳口：只冒一小股烟（弹壳本体是模型自带的 shell 骨骼，跟着动画飞出来；
		// 用户明确不要"打完爆一地铁粒"，那种掉落物既难看又要清理）
		Vec3d right = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D)).normalize();
		player.getServerWorld().spawnParticles(ParticleTypes.SMOKE,
				player.getX() + right.x * 0.45 - dir.x * 0.3,
				player.getEyeY() - 0.3,
				player.getZ() + right.z * 0.45 - dir.z * 0.3,
				3, 0.03, 0.03, 0.03, 0.015);
	}

}
