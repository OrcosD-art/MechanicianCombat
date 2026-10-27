package com.tk.gun.client;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 手持弩炮时的越肩视角配套（用户 2026-10-14）。
 *
 * <p>分工：相机 / 解耦 / 准星 都是整合包里 ShoulderSurfing 的活（decoupled_camera = true，
 * 我们从它的 API 切视角，见 {@link ShoulderSurfBridge}），正中那个 - . - 准星是它
 * crosshair_type = STATIC 画的。别自己再写相机 mixin（会互顶）。
 *
 * <p>我们只负责：
 * <ol>
 *   <li>瞄准放大：长按右键把 FOV 收到 {@link #AIM_FOV}，按帧平滑（{@link #advanceZoom}）。</li>
 *   <li>长按右键时身体转向鼠标方向（20 度/tick）。</li>
	 *   <li>瞄准落点（{@link #impactPoint}，= HUD 那个圈的圆心）：**和枪管直线解耦**。
	 *       长按右键时先量"准星（屏幕正中）射线打到的那个点"，再让落点方向
	 *       （{@link #aimDir}）**按固定角速度慢慢转过去**（{@link #AIM_SLEW_DEG_PER_TICK}），
	 *       子弹就从 locator 出来、打到这个"转过去的"落点上（用户 2026-10-14 的原话）。</li>
	 *   <li>长按右键时**只**把整个模型左右转，让枪尽可能面向那个落点；上下倾角一动不动。</li>
 * </ol>
 */
public final class GunCamera {
	/** 视角（= 相机朝向）：鼠标方向。解耦之后它和身体 yaw 不是一个东西。 */
	private static float lookYaw;
	private static float lookPitch;
	/**
	 * **这一 tick 算出来的目标身体 yaw** —— 渲染期 {@code BowgunRigClient} 会把它再写一次到玩家身上。
	 *
	 * <p>为什么要在渲染期再写一遍：ShoulderSurfing 在 `decoupled_camera = true` 时按它自己的 turning
	 * 逻辑（测试端配置 `turning_speed_multiplier = 0.25`、`player_y_rot_follows_camera = false`）
	 * 改玩家 yaw —— 我们 tick 里 `p.setYaw(...)` 的值会被它盖掉（测试端日志里 `身体残余` 一直 40°
	 * 上下不收敛就是它）。渲染帧是这一帧**最后**写 yaw 的地方，这里再写一次，画面上的身体/枪管
	 * 就一定是朝目标方向的。
	 */
	private static float aimYawTarget;
	private static boolean aimYawValid;

	/** 本 tick 的"枪口 → 准星点"目标 yaw 有效吗（拿着枪 + 非第一人称 + 本 tick 算过）。 */
	public static boolean aimYawValid() {
		return aimYawValid;
	}

	/** 本 tick 的"枪口 → 准星点"目标 yaw（度）；渲染期由 {@code AimAlignment} 用它对齐根骨。 */
	public static float aimYawTarget() {
		return aimYawTarget;
	}

	/** 长按右键瞄准时的 FOV 系数（小于 1 = 视角放大；0.62 约等于 1.6 倍望远镜感）。 */
	public static final float AIM_FOV = 0.62F;
	/** FOV 平滑的时间常数（秒）：越小越快。0.09 秒左右跟手但不闪。 */
	private static final double ZOOM_TAU = 0.09D;
	private static float zoomBlend;
	/** Last FOV returned by GameRenderer, including aim zoom and vanilla effects. */
	private static double renderedFovDegrees = 70.0D;

	// ------------------------------------------------ 瞄准落点：一门"慢慢转过去"的炮塔
	/**
	 * **准星点**：屏幕正中那条射线（相机眼睛 + 相机朝向）打出去的第一个碰撞点
	 * —— 就是"玩家瞄的那个坐标"（用户 2026-10-14："去检测那个玩家他那准星瞄准的那个坐标"）。
	 * 没瞄准时 = null。
	 */
	private static Vec3d crosshairPoint;
	/**
	 * **炮口当前朝向**（单位向量，世界空间）。它**不等于**枪管方向：瞄准时它从枪管方向起步，
	 * 按 {@link #AIM_SLEW_DEG_PER_TICK} 的角速度**慢慢转向"枪口 → 准星点"**。
	 * 子弹方向、HUD 那个圈的落点、模型要面向的方向，全都用它 —— 这就是"落点和枪管直线解耦"。
	 */
	private static Vec3d aimDir;
	/**
	 * **弹着点** = 从 locator 沿 {@link #aimDir} 打出去的第一个碰撞点（什么都没撞到就取 96 格处）。
	 * 它就是"这一枪会打在哪"、也是 HUD 那个圈的**圆心**（用户："实际落点是那个圆心"）。
	 */
	private static Vec3d aimTarget;
	/** 进入瞄准那一刻的枪口水平中心线（世界 yaw，度）。 */
	private static float aimConeBaseYaw;
	private static boolean aimConeBaseValid;
	/** 右键刚按下，等下一帧真正渲染出当前模型枪口后再取中心线。 */
	private static boolean aimConeBasePending;
	/** 当前准星是否超出 ±25° 瞄准扇区；side = -1 左 / +1 右。 */
	private static boolean aimOutsideCone;
	private static int aimOutsideSide;
	/** 上一次 tick 是否在右键瞄准，用来在按下右键时重置中心线。 */
	private static boolean wasAiming;
	/** 枪口水平瞄准扇区的半角。 */
	public static final float AIM_CONE_HALF_DEG = 25.0F;
	/** 视觉枪口 yaw − 身体 yaw 的平滑补偿（度）。 */
	private static double barrelOffsetSmoothed;
	private static boolean barrelOffsetValid;
	/** 射线最长打多远（格）。 */
	private static final double AIM_RANGE = 96.0D;
	/** 长按右键时身体转向落点的速度（度/tick）：20 约等于 0.15 秒转 90 度。 */
	/**
	 * 每 tick 把身体往目标方向转多少度。
	 *
	 * <p>沿革：20°/tick（400°/秒）→ 30°/tick（600°/秒，用户 2026-10-17："站立瞄准时始终面朝准星"）
	 * → **60°/tick（1200°/秒）**（用户 2026-10-17 又报"视角水平移动时身体并未跟随，子弹从枪口横向飞出"：
	 * 600°/秒追不上甩枪，枪口停在旧朝向，子弹却已经按新方向飞了）。
	 * 1200°/秒 已经接近"甩一下就到"，但仍是逐 tick 转、不会瞬移，所以不会出现"身体比视角先到"的抖动。
	 */
	private static final float TURN_AIM = 60.0F;
	/**
	 * **落点的转头速度**（度/tick，20 tick = 1 秒）。
	 *
	 * <p>用户 2026-10-14 原话："那个实际落点是可以缓慢过去的，就是有一定的速度移过去……有一个
	 * 准心去瞄准它的那个过程"。所以落点**不是**瞬间贴到准星上，而是匀速转过去。
	 *
	 * <p>⚠️ 2026-10-17 默认值从 5°/tick（100°/秒）提到 **25°/tick（500°/秒）**：100°/秒 完全追不上
	 * 甩视角（1000+°/秒），于是子弹方向 `aimDir` 严重滞后 —— 用户报的"子弹从枪口横向飞出"就是这个。
	 * 500°/秒 还留着"炮口扫过去"的观感，但不会和视角脱节。想调手感不用改代码：
	 * `-Dtkgun.aimslew=<度/秒>`（默认 500）。
	 */
	private static final double AIM_SLEW_DEG_PER_TICK = slewFromProperty();

	private static double slewFromProperty() {
		String s = System.getProperty("tkgun.aimslew");
		if (s != null) {
			try {
				return Math.max(0.2D, Double.parseDouble(s) / 20.0D);
			} catch (NumberFormatException ignored) {
				// 参数写坏了就用默认值，别让客户端起不来
			}
		}
		return 25.0D;
	}

	private static int ticks;
	private static boolean lastLoggedAiming;
	/**
	 * Right-click was already in use when a decoupled state began. Ignore the held
	 * button until it is physically released, otherwise vanilla restarts item use
	 * every tick and re-applies the aim coupling.
	 */
	private static boolean releaseAimUntilKeyUp;
	/** Decoupled state in which the current right-click aim began. */
	private static int aimDecoupledReason;
	// ---- 枪骨左右修正（把实测枪管方向驱到相机视线方向）：带自标定的阻尼闭环 ----
	/** 演示模式（-Dtkgun.demo）才打诊断日志，别污染玩家平时的客户端日志。 */
	private static final boolean VERBOSE = System.getProperty("tkgun.demo") != null;
	/** 只打"瞄准朝向"那几行（验收用：`-Dtkgun.aimlog=1`，比整包 demo 轻） */
	private static final boolean AIM_LOG = System.getProperty("tkgun.aimlog") != null;

	public static void resetVanillaState() {
		zoomBlend = 0.0F;
		crosshairPoint = null;
		aimDir = null;
		aimTarget = null;
		aimYawValid = false;
		aimYawTarget = 0.0F;
		aimConeBaseValid = false;
		aimConeBasePending = false;
		barrelOffsetValid = false;
		barrelOffsetSmoothed = 0.0D;
		aimOutsideCone = false;
		aimOutsideSide = 0;
		wasAiming = false;
		releaseAimUntilKeyUp = false;
		aimDecoupledReason = 0;
	}

	// ---------------------------------------------------------------- 每 tick
	public static void tickEnd(MinecraftClient client) {
		PlayerEntity p = client.player;
		if (p == null || !active(client)) {
			zoomBlend = 0.0F;
			crosshairPoint = null;
			aimDir = null;
			aimTarget = null;
			aimYawValid = false;
			aimConeBaseValid = false;
			aimConeBasePending = false;
			barrelOffsetValid = false;
			barrelOffsetSmoothed = 0.0D;
			aimOutsideCone = false;
			wasAiming = false;
			releaseAimUntilKeyUp = false;
			aimDecoupledReason = 0;
			return;
		}
		int currentDecoupledReason = ShoulderCouplingState.decoupledReason(client);
		if (!client.options.useKey.isPressed()) {
			releaseAimUntilKeyUp = false;
			aimDecoupledReason = 0;
		} else if (wasAiming && currentDecoupledReason != 0
				&& currentDecoupledReason != aimDecoupledReason) {
			releaseAimUntilKeyUp = true;
			if (p.isUsingItem() && p.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND
					&& com.tk.gun.TkGun.isGun(p.getActiveItem())) {
				p.stopUsingItem();
			}
		}
		ticks++;
		// 翻滚 / 滑铲及其结束后的 1 秒：右键是**完全屏蔽**，不只是不画准星。
		// 原版在 `useKey` 仍按着时会继续维持 `isUsingItem()`（进而触发本模组的举枪减速），
		// 所以这里主动停掉已经开始的物品使用；`BowgunItem#use` 同时会在入口拒绝新的右键。
		if (!aimAllowed(client)) {
			zoomBlend = 0.0F;
			if (p.isUsingItem() && p.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND
					&& com.tk.gun.TkGun.isGun(p.getActiveItem())) {
				p.stopUsingItem();
			}
		}
		Camera camera = client.gameRenderer.getCamera();
		lookYaw = camera.getYaw();
		lookPitch = camera.getPitch();
		if (FallbackShoulderCamera.isFallbackActive(client)) {
			p.setPitch(lookPitch);
			p.prevPitch = lookPitch;
		}
		boolean aiming = aiming(client);
		// 腰射：玩家 yaw 与相机准星方向耦合。
		// 正常路径由 ShoulderSurfing 的 camera coupling callback 完成（它连 pitch 一起同步）；
		// 该扩展点不可用时，至少在这里把 yaw 直接对齐，保证“身体正对屏幕中心准星”。
		// 腰射时直接对齐相机 yaw；ShoulderSurfing 耦合成功时两者本来就相同，
		// 这里再写一次可保证回调失效时仍然耦合。
		if (!aiming && ShoulderCouplingState.shouldCoupleCamera(client)) {
			float want = MathHelper.wrapDegrees(lookYaw);
			p.setYaw(want);
			p.setHeadYaw(want);
			p.setBodyYaw(want);
		}
		if (aiming && !wasAiming) {
			aimDecoupledReason = currentDecoupledReason;
			// 不要在 tick 头拿上一帧的 muzzledir：右键刚按下时模型还在
			// stand_shooting / Focus_ready 的过渡姿势，那样中心线会偏。
			// 等 TkGunClient.captureMuzzle 捕获当前帧模型枪口后再锁定。
			aimConeBaseValid = false;
			aimConeBasePending = true;
			aimOutsideCone = false;
			aimOutsideSide = 0;
			// 进入瞄准时从当前身体 yaw 起步，避免第一 tick 直接跳到计算目标。
			aimYawTarget = p.getYaw();
			aimYawValid = true;
			barrelOffsetValid = false;
			barrelOffsetSmoothed = 0.0D;
		} else if (!aiming) {
			aimConeBaseValid = false;
			aimConeBasePending = false;
			barrelOffsetValid = false;
			barrelOffsetSmoothed = 0.0D;
			aimOutsideCone = false;
			aimOutsideSide = 0;
		}
		wasAiming = aiming;
		// **疾跑中瞄准 = 终止疾跑、进入站立射击**（用户 2026-10-17："在原版疾跑状态下瞄准会终止疾跑并
		// 进入站立射击状态"）。每 tick 压一次：客户端 `tickMovement` 只要还按着疾跑键就会自己再开回来
		// （和当初"服务端清不掉疾跑"是同一个坑）。清掉之后腿动画会自己从 run 切回 walk_*/idle。
		if (aiming && p.isSprinting()) {
			p.setSprinting(false);
			if (ticks % 40 == 0) {
				TkGun.LOGGER.info("[tkgun] 瞄准 -> 终止疾跑（进入站立射击）");
			}
		}
		// 落点：没瞄准 = 枪口沿枪管直线打出去的那个点；瞄准 = 按炮塔速度转过去的那个点
		//
		// ⚠️ 2026-10-17 用户："当视角水平移动时玩家身体并未跟随，导致子弹从枪口横向飞出" ——
		//    以前的规则是"只有长按右键 / 专注时才让身体跟视角转"，而且身体追的是**滞后的落点**、
		//    子弹方向 `aimDir` 更只有 100°/秒 → 甩视角时身体和枪口留在旧朝向，子弹却按新方向飞，
		//    看着就是"从枪口横向飞出去"。
		//    现在改成：**手持弩炮期间，身体一直跟着视角的水平方向转**（跟随时以准星点为目标），
		//    速率见 TURN_AIM / AIM_SLEW_DEG_PER_TICK，两者都提到能跟住甩枪的量级。
		//    （`updateAim` 这里传 true = 每 tick 都算准星点，身体才有目标可追；着弹点圈仍然只在
		//      长按右键时才画，见 GunReticle；腰射的子弹方向仍取实测枪管方向，见 shotDirection。）
		updateAim(client, camera, true);
		if (aimDir != null) {
			// 长按右键：**整个模型转过去，让枪口真的对着落点**（用户 2026-10-15："长按右键的时候
			// 这个模型的正面朝向必须对着着弹点……不然会出现枪口朝着一个方向、子弹打向另外一个方向"）。
			//
			// ⚠️ 转的是"**枪口 → 落点**"这条连线（而不是 aimDir 自己的 yaw）：枪是 chest 的子骨，
			// 作者在走路/端枪的姿势里给 chest 拧了（比如 walk_* 的 45°、stand_shooting 的 14.9°），
			// 所以"身体正面朝哪"和"枪口指哪"差着十几度。用**量出来的枪口点**算，这个差就自动被补掉。
			//
			// 这是个**收敛**的比例闭环：我们按"残余角度"转多少，枪口方向就跟着转多少（枪焊在身体上），
			// 所以一 tick 之内残差就掉到 0 附近；上下倾角一律不动（抬枪角度是素材里的 Molang 在算）。
			Vec3d muzzle = TkGunClient.muzzleWorldPos();
			Vec3d aimPoint = TkGunClient.aimWorldPos();
			// **实测枪管方向**：水平瞄准要按它算，不能假设"枪管 yaw = 身体 yaw"（见下）。
			Vec3d barrel = TkGunClient.muzzleWorldDir();
			// 目标点**优先取准星点**（屏幕正中那条射线打到的点），这样作者给 chest 拧的那些角度
			// （walk_* 的 45°、stand_shooting 的 14.9°）会被"枪口 → 目标"这条线自动补掉。
			Vec3d target = crosshairPoint != null ? crosshairPoint : aimTarget;
			double residual = Double.NaN;
			double wantYaw = Double.NaN;
			double lineYaw = Double.NaN;
			double lineError = Double.NaN;
			if (muzzle != null && target != null) {
				double hx = target.x - muzzle.x;
				double hz = target.z - muzzle.z;
				Vec3d look = Vec3d.fromPolar(lookPitch, lookYaw);
				// 目标太近或在枪口/相机后方时，改用视线前方 32 格的稳定点；
				// 否则第三人身枪口的视差会让目标向量翻转，左右表现不对称。
				if (hx * hx + hz * hz >= 4.0D
						&& hx * look.x + hz * look.z >= 0.0D) {
					wantYaw = Math.toDegrees(Math.atan2(-hx, hz));
				} else {
					double fx = look.x * 32.0D;
					double fz = look.z * 32.0D;
					if (fx * fx + fz * fz > 1.0E-6D) {
						wantYaw = Math.toDegrees(Math.atan2(-fx, fz));
					}
				}
			}
			if (Double.isNaN(wantYaw)) {
				// 目标太近/缺失：**直接用相机 yaw**（最稳、左右对称），别再拿近点向量算
				wantYaw = lookYaw;
			}
			// 真正的中心线：aim 定位点 → locator 枪口定位点。
			if (aiming && aimPoint != null && muzzle != null) {
				Vec3d line = muzzle.subtract(aimPoint);
				if (line.x * line.x + line.z * line.z > 1.0E-6D) {
					lineYaw = Math.toDegrees(Math.atan2(-line.x, line.z));
					lineError = MathHelper.wrapDegrees(wantYaw - lineYaw);
					double rawOffset = MathHelper.wrapDegrees(lineYaw - p.getYaw());
					if (!barrelOffsetValid) {
						barrelOffsetSmoothed = rawOffset;
						barrelOffsetValid = true;
					} else {
						// 中心线相对身体的动画偏角也会随姿态变化；慢速平滑避免把关键帧抖动喂进 yaw。
						barrelOffsetSmoothed += MathHelper.wrapDegrees(
								rawOffset - barrelOffsetSmoothed) * 0.25D;
					}
				}
			}
			// ⚠️ 2026-10-17 **撤销第四十三轮那个"实测枪管偏角"补偿**（用户："撤销 42 与 43 轮…"）：
			//    枪管方向是**上一帧渲染时**捕捉的（`muzzleWorldDir()`），60°/tick 转身时它整整落后一 tick
			//    —— 测试端日志里 `枪管-身体` 在转身瞬间跳到 ±40~92°、静止时才 −1~2°，就是它。
			//    把这种旧值加进控制律只会把 yaw 环带飞（`身体残余` 一直在 40° 上下不收敛）。
			//    所以控制律回到"身体追枪口 → 准星点"，枪管 yaw 只拿来**诊断**。
			double barrelOffset = Double.NaN;      // 仅诊断：枪管 yaw − 身体 yaw
			if (barrel != null && barrel.lengthSquared() > 0.5D) {
				double barrelYaw = Math.toDegrees(Math.atan2(-barrel.x, barrel.z));
				barrelOffset = MathHelper.wrapDegrees(barrelYaw - p.getYaw());
				if (aiming) {
					if (!barrelOffsetValid) {
						barrelOffsetSmoothed = barrelOffset;
						barrelOffsetValid = true;
					} else {
						barrelOffsetSmoothed += MathHelper.wrapDegrees(
								barrelOffset - barrelOffsetSmoothed) * 0.35D;
					}
				}
			}
			// 右键瞄准：目标身体 yaw = 准星 yaw − 中心线相对身体的平滑偏角。
			// 这是“目标位置”而不是“每 tick 按误差再转一次”，不会形成衰减振荡。
			double bodyWantYaw = aiming && barrelOffsetValid
					? MathHelper.wrapDegrees(lookYaw - barrelOffsetSmoothed)
					: (aiming ? lookYaw : wantYaw);
			residual = MathHelper.wrapDegrees(bodyWantYaw - p.getYaw());
			// 把这一 tick 的**目标 yaw** 记下来：渲染期由 `AimAlignment` 把差值加到根骨 `body` 上
			// （ShoulderSurfing 解耦模式下会按它自己的 turning 逻辑改玩家 yaw，改 entity.yaw 追不上）。
			// ⚠️ 2026-10-17 用户："瞄准状态下玩家身体依旧会旋转抽搐" —— 目标改成**相机 yaw**（稳定值），
			//    不再用"枪口 → 准星点"（那一串依赖上一帧量到的枪口位置，转身/换目标时会抖：
			//    身体一转 → 枪口动 → 目标 yaw 又变 → 一帧一跳 = 抽搐）。
			//    枪口仍然对着准星方向：相机 yaw 就是准星方向，姿势里那点 chest/gun 偏角由下面的骨骼补角吃掉。
			if (aiming) {
				double delta = MathHelper.wrapDegrees(bodyWantYaw - aimYawTarget);
				aimYawTarget = MathHelper.wrapDegrees((float) (
						aimYawTarget + delta * 0.35D));
				// 残差很小时直接吸附，避免长时间停在目标两侧。
				if (Math.abs(delta) < 0.08D) {
					aimYawTarget = MathHelper.wrapDegrees((float) bodyWantYaw);
				}
			} else {
				aimYawTarget = lookYaw;
			}
			aimYawValid = true;
			// 诊断（瞄准时每 2 秒一行，验收"水平真的对上了"）：枪管 yaw 应该追到 `目标 yaw`。
			if (aiming && ticks % 40 == 0) {
				TkGun.LOGGER.info("[tkgun] 瞄准水平：中心线yaw={} 准星yaw={} 身体yaw={} 枪管-身体={}°"
								+ " 线差={}° 身体残余={}° 视觉偏角补偿={}°",
						Double.isNaN(lineYaw) ? "-" : Math.round(lineYaw * 10.0) / 10.0,
						Math.round(wantYaw * 10.0) / 10.0,
						Math.round(p.getYaw() * 10.0) / 10.0,
						Double.isNaN(barrelOffset) ? "-" : Math.round(barrelOffset * 10.0) / 10.0,
						Double.isNaN(lineError) ? "-" : Math.round(lineError * 10.0) / 10.0,
						Math.round(residual * 10.0) / 10.0,
						barrelOffsetValid ? Math.round(barrelOffsetSmoothed * 10.0) / 10.0 : "-");
			}
			// 2026-10-17 用户："撤销所有关于视角修改的内容，我将重新使用 ShoulderSurfing" ——
			// 回到原来的分工：**相机/解耦交给 ShoulderSurfing**，本模组只负责把**身体**往"枪口 → 准星点"转
			// （瞄准时；非瞄准时不动朝向）。渲染期补角、自带越肩相机、自建鼠标层全部撤掉了。
			// **翻滚 / 滑铲期间不转身体**（用户 2026-10-17："长按右键与翻滚状态同时进行时会出现
			// 翻滚动画疑似倒放并进入腰射状态"）：这两段是整套全身动画，再一边滚一边追准星拧身体，
			// 画面就像在倒放/抽搐。翻滚结束后（IDLE）再恢复跟准星。
			GunClientState.Entry actState = GunClientState.get(p.getId());
			boolean action = actState != null && (actState.state == TkGun.State.ROLL
					|| actState.state == TkGun.State.ROLL_TO_IDLE
					|| actState.state == TkGun.State.SLIDE || actState.state == TkGun.State.SLIDE_READY);
			// 翻滚 / 滑铲期间把渲染插值的上一帧 yaw 钉住当前值：不然战斗翻滚那边把 yaw 改一下，
			// 这一帧就会从旧朝向**插值甩过去**（用户报的"翻滚后进入 idle 仍然倒转"就是这种甩）。
			if (action) {
				p.prevYaw = p.getYaw();
			}
			if (aiming && !action) {
				float applied = aimYawTarget;
				p.setYaw(applied);
				p.setBodyYaw(applied);
				p.setHeadYaw(applied);
			}
			aimYawResidual = residual;
		} else {
			aimYawResidual = Double.NaN;
		}
		if (VERBOSE && (aiming != lastLoggedAiming || ticks % 100 == 0)) {
			lastLoggedAiming = aiming;
			Vec3d barrel = TkGunClient.muzzleWorldDir();
			// 枪管方向和视线方向的夹角：瞄准过程中这个角应该收敛到 0（弹着点收到准星上）
			Vec3d look = Vec3d.fromPolar(lookPitch, lookYaw);
			// 水平夹角（枪管 vs 视线）——枪骨修正补的就是它，应该收敛到 ~0
			double hori = -1.0D;
			if (barrel != null) {
				Vec3d b = new Vec3d(barrel.x, 0.0D, barrel.z).normalize();
				Vec3d l = new Vec3d(look.x, 0.0D, look.z).normalize();
				hori = Math.toDegrees(Math.acos(MathHelper.clamp(b.dotProduct(l), -1.0D, 1.0D)));
			}
			TkGun.LOGGER.info("[tkgun] 越肩：视角 yaw={}（相机）身体 yaw={}（实体）差 {}° 瞄准={}"
							+ " 弹着点={} 枪管水平夹角={}° 枪骨修正={}rad FOV系数={} 视角模式={} ShoulderSurfing={}",
					MathHelper.wrapDegrees(lookYaw), MathHelper.wrapDegrees(p.getYaw()),
					MathHelper.wrapDegrees(lookYaw - p.getYaw()), aiming,
					aimTarget == null ? "-" : vec(aimTarget),
					Math.round(hori * 10) / 10.0,
					Math.round(fovScale() * 1000) / 1000.0, client.options.getPerspective(),
					ShoulderSurfBridge.describe());
		}
		// 瞄准追踪（-Dtkgun.demo 才打，每 2 tick 一行）：验收"落点按固定角速度转向准星"——
		// 残差 = 当前落点方向 与 "枪口→准星点" 的夹角，每 tick 应该掉 AIM_SLEW_DEG_PER_TICK 度左右。
		if (VERBOSE && aiming && aimDir != null && (ticks & 1) == 0) {
			Vec3d muzzle = TkGunClient.muzzleWorldPos();
			double residual = -1.0D;
			if (muzzle != null && crosshairPoint != null) {
				Vec3d want = crosshairPoint.subtract(muzzle);
				if (want.lengthSquared() > 1.0E-6D) {
					residual = Math.toDegrees(Math.acos(MathHelper.clamp(
							aimDir.dotProduct(want.normalize()), -1.0D, 1.0D)));
				}
			}
			TkGun.LOGGER.info("[tkgun] 瞄准追踪：准星点={} 落点={} 残差={}° 落点距离={} 走的方向=({}, {}, {})"
							+ " 转头速度={}°/tick 身体yaw={}",
					vec(crosshairPoint), vec(aimTarget), Math.round(residual * 10) / 10.0,
					muzzle == null || aimTarget == null ? -1.0D
							: Math.round(muzzle.distanceTo(aimTarget) * 10) / 10.0,
					round(aimDir.x), round(aimDir.y), round(aimDir.z),
					Math.round(AIM_SLEW_DEG_PER_TICK * 10) / 10.0,
					MathHelper.wrapDegrees(p.getYaw()));
		}
		// 瞄准朝向验收（-Dtkgun.aimlog=1）：把"身体 yaw / 枪口 → 落点的 yaw / 残差"打出来 ——
		// 残差收敛到 0 就说明"模型正面（枪口）真的对着着弹点"。
		if (AIM_LOG && aiming && (ticks % 10) == 0) {
			Vec3d muzzle = TkGunClient.muzzleWorldPos();
			Vec3d target = aimTarget != null ? aimTarget : crosshairPoint;
			double want = Double.NaN;
			if (muzzle != null && target != null) {
				want = Math.toDegrees(Math.atan2(-(target.x - muzzle.x), target.z - muzzle.z));
			}
			TkGun.LOGGER.info("[tkgun] 瞄准朝向：身体yaw={} 目标yaw={} 残差={}° 落点={} 枪口={} 专注={}",
					Math.round(p.getYaw() * 10) / 10.0,
					Double.isNaN(want) ? "-" : Math.round(want * 10) / 10.0,
					Double.isNaN(aimYawResidual) ? "-" : Math.round(aimYawResidual * 10) / 10.0,
					vec(target), vec(muzzle), GunClientState.focused(p.getId()));
		}
	}

	/**
	 * 推进"这一枪打哪"：
	 *
	 * <ul>
	 *   <li><b>没瞄准</b>：落点 = 枪口沿**枪管方向**直线打出去的第一个碰撞点（老行为，不动模型姿态）。</li>
	 *   <li><b>瞄准中</b>：先算 {@link #crosshairPoint}（准星射线打到的那个点），目标方向 = 枪口 → 准星点；
	 *       再让 {@link #aimDir} **按 {@link #AIM_SLEW_DEG_PER_TICK} 的角速度转过去**（炮塔）。
	 *       落点 = 枪口沿 aimDir 打出去的第一个碰撞点 —— 它就是子弹实际会打到的地方。</li>
	 * </ul>
	 *
	 * <p>枪口位置从头到尾不变（子弹永远从 locator 出来），变的只有方向 —— 这就是"落点和枪管直线解耦"。
	 */
	private static void updateAim(MinecraftClient client, Camera camera, boolean aiming) {
		if (client.world == null) {
			crosshairPoint = null;
			aimDir = null;
			aimTarget = null;
			return;
		}
		Vec3d muzzle = TkGunClient.muzzleWorldPos();
		Vec3d barrel = TkGunClient.muzzleWorldDir();
		if (muzzle == null || barrel == null || barrel.lengthSquared() < 0.5D) {
			crosshairPoint = null;
			aimDir = null;
			aimTarget = null;
			return;
		}
		if (!aiming) {
			crosshairPoint = null;
			aimDir = null;              // 松开右键 = 炮塔复位，下次瞄准从"枪管方向"起步（不跳变）
			aimTarget = cast(client, muzzle, barrel);
			return;
		}
		// 准星点 = 屏幕正中那条射线打到的第一个东西（方块或生物）
		crosshairPoint = cast(client, camera.getPos(),
				Vec3d.fromPolar(camera.getPitch(), camera.getYaw()));
		Vec3d want = crosshairPoint.subtract(muzzle);
		Vec3d cameraLook = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		double hx = want.x;
		double hz = want.z;
		if (hx * hx + hz * hz < 4.0D
				|| hx * cameraLook.x + hz * cameraLook.z < 0.0D) {
			// 目标过近/在枪口或视线后方：用相机前方方向，避免第一 tick 朝反方向校准。
			want = cameraLook;
		} else {
			want = want.normalize();
		}
		// 扇区中心跟着**当前** aim→locator 中心线走，而不是锁死在刚按右键那一帧。
		// 否则模型转向准星后，射击范围仍然卡在旧方向的 ±25° 边界，必须松开重按才更新。
		aimConeBaseYaw = yawOf(barrel);
		aimConeBaseValid = true;
		boolean firstAimTick = aimDir == null;
		if (!firstAimTick) {
			want = clampAimCone(want);
		} else {
			aimOutsideCone = false;
			aimOutsideSide = 0;
		}
		// 第一次按下右键：射击方向立刻取准星目标，保证第一发和对准范围同步；
		// 之后的移动仍按固定角速度追踪准星。
		aimDir = firstAimTick
				? want
				: slew(aimDir, want, Math.toRadians(AIM_SLEW_DEG_PER_TICK));
		aimTarget = cast(client, muzzle, aimDir);
	}

	/**
	 * 把目标方向限制在进入瞄准时枪口中心线的左右 {@link #AIM_CONE_HALF_DEG}°。
	 * 只改水平 yaw，保留目标的上下分量；越界时记录方向供 HUD 画箭头。
	 */
	private static Vec3d clampAimCone(Vec3d want) {
		double wantYaw = yawOf(want);
		double delta = MathHelper.wrapDegrees(wantYaw - aimConeBaseYaw);
		aimOutsideCone = Math.abs(delta) > AIM_CONE_HALF_DEG;
		if (!aimOutsideCone) {
			aimOutsideSide = 0;
			return want;
		}
		// MC 的正 yaw 朝左，所以 delta > 0 是左边。
		aimOutsideSide = delta > 0.0D ? -1 : 1;
		double clampedYaw = aimConeBaseYaw
				+ Math.copySign(AIM_CONE_HALF_DEG, delta);
		double horizontal = Math.sqrt(want.x * want.x + want.z * want.z);
		if (horizontal < 1.0E-6D) {
			return want;
		}
		return new Vec3d(
				-Math.sin(Math.toRadians(clampedYaw)) * horizontal,
				want.y,
				Math.cos(Math.toRadians(clampedYaw)) * horizontal).normalize();
	}

	/**
	 * 把单位向量 {@code cur} 朝 {@code want} 转 {@code maxRad} 弧度（转不够就停在中途）。
	 * 用球面插值 = **角速度恒定**（直线插值在大角度时会越走越快，看着不像"转炮塔"）。
	 */
	private static Vec3d slew(Vec3d cur, Vec3d want, double maxRad) {
		if (cur == null || maxRad <= 0.0D) {
			return want;
		}
		double dot = MathHelper.clamp(cur.dotProduct(want), -1.0D, 1.0D);
		double ang = Math.acos(dot);
		if (ang <= maxRad || ang < 1.0E-4D) {
			return want;
		}
		double sin = Math.sqrt(Math.max(0.0D, 1.0D - dot * dot));
		if (sin < 1.0E-7D) {
			return want;            // 正好反方向：没有唯一解，直接给目标
		}
		double t = maxRad / ang;
		Vec3d a = cur.multiply(Math.sin((1.0D - t) * ang) / sin);
		Vec3d b = want.multiply(Math.sin(t * ang) / sin);
		return a.add(b).normalize();
	}

	/** 从 {@code from} 沿 {@code dir} 打一条射线（方块 + 生物，取更近的那个），返回命中点。 */
	private static Vec3d cast(MinecraftClient client, Vec3d from, Vec3d dir) {
		Vec3d end = from.add(dir.multiply(AIM_RANGE));
		BlockHitResult blockHit = client.world.raycast(new RaycastContext(from, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
		double blockDist = blockHit.getType() == HitResult.Type.MISS
				? AIM_RANGE : from.distanceTo(blockHit.getPos());
		// 生物：和服务端 hitscan 同一套（ProjectileUtil.raycast）
		EntityHitResult entHit = ProjectileUtil.raycast(client.player, from, end,
				new Box(from, end).expand(1.0D),
				e -> e.isAlive() && !e.isSpectator() && e != client.player && e.canHit()
						&& !CombatTargeting.isGolem(e),
				AIM_RANGE * AIM_RANGE);
		double entDist = entHit == null ? Double.MAX_VALUE : from.distanceTo(entHit.getPos());
		if (entHit != null && entDist <= blockDist) {
			return entHit.getPos();
		}
		return blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();
	}

	/** 当前算出来的**弹着点**（世界坐标）；null = 还没量到。HUD 那个圈画的就是它。 */
	public static Vec3d impactPoint() {
		return aimTarget;
	}

	/**
	 * **开火方向**：瞄准时 = {@link #aimDir}（从枪口指向"慢慢转过去"的那个落点，
	 * 用户 2026-10-14："从 loc 的位置出来，然后去击中那个修改过的准星位置"）；
	 * 没瞄准时 = 实测的枪管方向。
	 *
	 * <p>子弹依旧从 locator 出来（{@link TkGunClient} 发的枪口位置不变），HUD 那个圈画的是同一方向的落点，
	 * 所以"圈画在哪 = 子弹会打哪"。
	 */
	public static Vec3d shotDirection(MinecraftClient client) {
		if (aiming(client) && aimDir != null) {
			return aimDir;
		}
		return TkGunClient.muzzleWorldDir();
	}

	/** 准星超出枪口左右 25° 扇区时，HUD 隐藏落点圈并显示方向箭头。 */
	public static boolean aimOutsideCone() {
		return aimOutsideCone;
	}

	/** 越界方向：-1 = 左，+1 = 右。 */
	public static int aimOutsideSide() {
		return aimOutsideSide;
	}

	/**
	 * 渲染器量到本地玩家当前帧的真实 `locator` 朝向时调用。
	 *
	 * <p>右键瞄准刚按下时，tick 阶段拿到的是上一帧的枪口；等这一帧模型真正渲染出来，
	 * 用视觉上的枪口方向作为 ±25° 中心线，避免中心线偏向待机姿势或相机 yaw。
	 */
	public static void noteVisualMuzzle(Vec3d dir) {
		if (!aimConeBasePending || dir == null || dir.lengthSquared() < 1.0E-6D) {
			return;
		}
		aimConeBaseYaw = yawOf(dir);
		aimConeBaseValid = true;
		aimConeBasePending = false;
		aimOutsideCone = false;
		aimOutsideSide = 0;
	}

	/** 准星（屏幕正中）瞄到的那个世界坐标；没在瞄准 = null。诊断用。 */
	public static Vec3d crosshairPoint() {
		return crosshairPoint;
	}

	/** 上一 tick "枪口 → 落点"和身体 yaw 之间的残差（度；没瞄准 = NaN）。诊断用。 */
	public static double aimYawResidual() {
		return aimYawResidual;
	}

	private static double aimYawResidual = Double.NaN;

	// ⚠️ 2026-10-14 用户明确：**不要微调枪的角度**（上下偏转枪不好做，而且容易出问题）。
	// 左右瞄准靠"整个模型转"来保证 —— 见 tickEnd 里按 aimDir 走的那句 setYaw。

	/**
	 * 按帧推进瞄准放大（{@code GameRendererZoomMixin} 每帧调一次）。
	 * 用真实帧时长做指数平滑，所以 60/144 Hz 都顺；按 tick（20 Hz）推进就是一卡一卡。
	 */
	public static void advanceZoom(MinecraftClient client, float frameTicks) {
		if (!aimAllowed(client)) {
			zoomBlend = 0.0F;
			return;
		}
		float want = aiming(client) ? 1.0F : 0.0F;
		double dt = Math.max(1.0E-4D, frameTicks / 20.0D);       // tick -> 秒
		float k = (float) (1.0D - Math.exp(-dt / ZOOM_TAU));
		zoomBlend += (want - zoomBlend) * k;
		if (want == 0.0F && zoomBlend < 1.0E-3F) {
			zoomBlend = 0.0F;
		}
	}

	/** 当前 FOV 系数（1 = 不放大）。HUD 投影枪口时也读它，才和实际画面一致。 */
	public static float fovScale() {
		return 1.0F + (AIM_FOV - 1.0F) * zoomBlend;
	}

	public static void recordRenderedFov(double degrees) {
		if (Double.isFinite(degrees) && degrees > 0.0D) {
			renderedFovDegrees = degrees;
		}
	}

	public static double renderedFovDegrees() {
		return renderedFovDegrees;
	}

	/** 长按右键 = 瞄准（在界面里不算）。 */
	public static boolean aiming(MinecraftClient client) {
		return client.currentScreen == null && client.options.useKey.isPressed() && aimAllowed(client);
	}

	/**
	 * **现在允不允许右键瞄准**（不看按键有没有按着）。
	 *
	 * <p>用户 2026-10-17："撤销原版疾跑不允许瞄准的设定"（疾跑中瞄准只终止疾跑）；翻滚后的**加速冲刺**
	 * 窗口仍然不允许——各处要"屏蔽右键输入"时统一用它，别各写一份条件。
	 */
	public static boolean aimAllowed(MinecraftClient client) {
		return active(client) && !releaseAimUntilKeyUp
				&& !GunClientState.aimLocked(client.player.getId());
	}

	/** 本地玩家现在在**专注（架枪）**里吗 —— 服务端通过状态包下发的位，客户端只读镜像。 */
	public static boolean focused(MinecraftClient client) {
		return client.player != null && GunClientState.focusish(client.player.getId());
	}

	/** 这套操控生效中吗：手持弩炮 + 不是第一人称。 */
	public static boolean active(MinecraftClient client) {
		return client.player != null && TkGun.holdingGun(client.player)
				&& !client.options.getPerspective().isFirstPerson()
				&& client.options.getPerspective()
						!= net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT;
	}

	public static float lookYaw() {
		return lookYaw;
	}

	public static float lookPitch() {
		return lookPitch;
	}

	private static String vec(Vec3d v) {
		return "(" + round(v.x) + ", " + round(v.y) + ", " + round(v.z) + ")";
	}

	/** 世界方向 → 水平 yaw（度，和 MC 的相机 yaw 同一套符号）。 */
	private static float yawOf(Vec3d v) {
		return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
	}

	private static double round(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}

	private GunCamera() {
	}
}
