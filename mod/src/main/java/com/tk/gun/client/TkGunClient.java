package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.CombatRollHook;
import com.tk.gun.TkGunParticles;
import com.tk.gun.net.GunFocusPayload;
import com.tk.gun.net.GunFirePayload;
import com.tk.gun.net.GunJumpPayload;
import com.tk.gun.net.GunReloadPayload;
import com.tk.gun.net.GunRollPayload;
import com.tk.gun.net.GunStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端：
 *  1. 玩家身体和持枪动画由 GeckoLib rig 渲染；
 *  2. 处理瞄准、开火、专注、装填和闪避输入；
 *  3. 左键"攻击" -> 给服务端发开火请求。
 */
public class TkGunClient implements ClientModInitializer {
	/** 专注由潜行键切换，测试端绑定为 Z。 */
	/** F 键主动装填；空弹匣不会自动装填。 */
	public static final KeyBinding RELOAD_KEY = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.tkgun.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.tkgun"));
	public static final KeyBinding AMMO_UI_KEY = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.tkgun.ammo_ui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.tkgun"));
	public static final KeyBinding BACKPACK_ACTION_KEY = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.tkgun.backpack_action", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.tkgun"));
	private static boolean attackWasDown = false;
	private static boolean meleeAttackDown = false;
	private static boolean backpackActionDown = false;
	/** 左键已经连着按了多少 tick（用来区分"轻点"和"按住连发"）。 */
	private static int attackHeldTicks = 0;
	/** Z（或改键后的那个键）上一 tick 是否按着 —— 自己做边沿检测，不依赖原版 KeyBinding。 */
	private static boolean focusKeyDown = false;
	private static boolean jumpKeyDown = false;
	/** 连发补包是否已经打过日志（一次按住只打一行，别刷屏）。 */
	private static boolean autoFireLogged = false;
	/** F 键上一 tick 是否按着（自己做边沿检测）。 */
	private static boolean reloadKeyDown = false;
	private static boolean ammoUiKeyDown = false;
	/** combat_roll 的翻滚键（按它的翻译键 `keybinds.combat_roll.roll` 找）+ 上一 tick 是否按着。 */
	private static KeyBinding rollKey;
	private static KeyBinding fallbackRollKey;
	private static boolean rollKeyDown = false;

	/** 在 `options.allKeys` 里找 combat_roll 的翻滚键；找不到就返回 null（不影响别的功能）。 */
	private static KeyBinding findCombatRollKey(MinecraftClient client) {
		if (client.options == null || client.options.allKeys == null) {
			return null;
		}
		for (KeyBinding kb : client.options.allKeys) {
			String tk = kb.getTranslationKey();
			if (tk == null) {
				continue;
			}
			String lower = tk.toLowerCase(java.util.Locale.ROOT);
			if (lower.contains("combat_roll")) {
				TkGun.LOGGER.info("[tkgun] 找到 combat_roll 键位：{}（翻译键 {}）",
						kb.getBoundKeyLocalizedText().getString(), tk);
				return kb;
			}
		}
		return null;
	}

	private static boolean readRollKeyDown(MinecraftClient client) {
		if (!CombatRollHook.isInstalled()) {
			return fallbackRollKey != null && client.currentScreen == null
					&& fallbackRollKey.isPressed();
		}
		// 组合输入必须在本 tick 就认出 Alt：不能等“每 100 tick 一次”的懒加载，
		// 否则刚好在初始化前按下 Alt+Z 会被当成普通 Z。
		if (rollKey == null) {
			rollKey = findCombatRollKey(client);
		}
		if (rollKey == null) {
			return false;
		}
		try {
			int code = InputUtil.fromTranslationKey(rollKey.getBoundKeyTranslationKey()).getCode();
			return code > 0 && client.currentScreen == null
					&& InputUtil.isKeyPressed(client.getWindow().getHandle(), code);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Key-press frame used to let Combat Roll read the intended direction before focus locks input. */
	public static boolean isRollTriggerTick(MinecraftClient client) {
		return client.player != null && readRollKeyDown(client) && !rollKeyDown
				&& GunClientState.canTriggerDodge(client.player.getId());
	}

	private static boolean isInputDown(MinecraftClient client, InputUtil.Key key) {
		if (key == null || key.getCode() < 0 || client.getWindow() == null) {
			return false;
		}
		long handle = client.getWindow().getHandle();
		return key.getCategory() == InputUtil.Type.MOUSE
				? GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS
				: InputUtil.isKeyPressed(handle, key.getCode());
	}

	/**
	 * 把 KeyBinding 的当前绑定解析成可直接轮询的键。
	 *
	 * <p>不能用 {@link KeyBinding#wasPressed()}：默认 F 同时是原版“副手交换”，
	 * 原版会先把这个按压计数消费掉，轮到本模组时就永远是 false。
	 */
	private static InputUtil.Key bindingKey(KeyBinding binding, int fallback) {
		try {
			String translationKey = binding.getBoundKeyTranslationKey();
			if (translationKey == null || translationKey.endsWith(".unknown")) {
				// 玩家主动清空绑定时保持“无按键”，不要偷偷退回 F/K。
				return InputUtil.UNKNOWN_KEY;
			}
			InputUtil.Key key = InputUtil.fromTranslationKey(translationKey);
			if (key.getCategory() == InputUtil.Type.KEYSYM || key.getCategory() == InputUtil.Type.MOUSE) {
				return key;
			}
		} catch (Throwable ignored) {
			// 无法解析时退回默认键
		}
		return InputUtil.Type.KEYSYM.createFromCode(fallback);
	}

	/** 某个按键绑定当前是否指向指定鼠标键（供中键兼容层判断优先级）。 */
	public static boolean isBoundToMouse(KeyBinding binding, int button) {
		if (binding == null) {
			return false;
		}
		try {
			InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
			return key.getCategory() == InputUtil.Type.MOUSE && key.getCode() == button;
		} catch (Throwable ignored) {
			return false;
		}
	}
	private static boolean selfTested = false;
	private static int ticks;
	/**
	 * 每个玩家的"在动"跟踪：上一 tick 的水平位置 + 这个 tick 之前都算"在动"的截止 tick。
	 *
	 * <p>⚠️ 2026-10-13 用户实测报的 bug：跑一段停下来**不回 idle**（一直卡在 run）。
	 * 根因是原来用 `Entity.horizontalSpeed` 判"在动"，而 MC 源码里这个字段
	 * **只在 `Entity.move()` 里 `+=`，没有任何衰减/归零**（`Entity.java` 里唯一的写入点），
	 * 跑起来之后就永久大于阈值 —— 判定永远是"在动"。
	 * 现在改成**自己算位置增量**：对本地玩家和别的玩家都成立（远处玩家的 velocity 不一定同步，
	 * 但位置一定同步），停下 {@link #MOVING_HOLD_TICKS} tick 后就落回 idle。
	 */
	private static final class Move {
		double x;
		double z;
		int until;
		/** 最近一次动起来的位移分量（世界坐标；取"还在动的这段时间里最后一次真实位移"）。 */
		double dx;
		double dz;
		/** 位移发生时玩家的朝向（度）：用来把位移投到"前/右"基上挑四方向走动动画。 */
		float yaw;
		/**
		 * 那一下**是不是疾跑**（用户 2026-10-16："非冲刺状态的玩家需要进行 walk_front/back/left/right 动画"）。
		 *
		 * <p>为什么要单独记：以前靠速度阈值 0.18 格/tick 判"跑"，而**普通走路就是 0.216 格/tick** ——
		 * 走路一直被当成跑（永远播 `run`），四方向走动动画根本用不上。现在以原版 {@code isSprinting()}
		 * 为准，速度阈值只当兜底。
		 */
		boolean sprinting;
		/**
		 * 采样那一下**是不是正拿着枪瞄着**（右键按住）—— 用户 2026-10-17："将原版疾跑状态下按瞄准按键
		 * 调整为取消疾跑"。瞄着的时候一律按 `walk_*` 走（就算位移量到了疾跑量级也不给 `run`）。
		 */
		boolean aiming;
		/** walk_* 动画的归一化相位（0..1，1 秒一轮），用于同步 chest 的 Y 起伏。 */
		float walkPhase;
		/** walk_* 动画倍速 = 实际水平速度 / 标准步行速度。 */
		float walkAnimationSpeed = 1.0F;
	}

	private static final Map<Integer, Move> MOVES = new HashMap<>();
	private static final Identifier GUN_STEP_MODIFIER = TkGun.id("gun_step_height");
	private static boolean heldGunLastTick;
	/** 停下之后还当作"在动"几 tick，免得临界速度来回抖。 */
	private static final int MOVING_HOLD_TICKS = 4;
	/** 每 tick 水平位移超过 0.02 格（= 0.4 格/秒）才算走（潜行 0.065、疾跑 0.28 都远超它）。 */
	private static final double MOVING_EPS2 = 0.02 * 0.02;

	/** 某个玩家现在算不算"在走/在跑"（走路和疾跑共用 run 动画，见 BowgunRig.pick）。 */
	public static boolean isMoving(int entityId) {
		Move m = MOVES.get(entityId);
		return m != null && ticks < m.until;
	}

	/** walk_* 循环相位（0..1）；未移动时归零。 */
	public static float walkPhase(int entityId) {
		Move m = MOVES.get(entityId);
		return m == null ? 0.0F : m.walkPhase;
	}

	/** walk_* 动画应按玩家实际行走速度播放的倍速。 */
	public static float walkAnimationSpeed(int entityId) {
		Move m = MOVES.get(entityId);
		return m == null ? 1.0F : m.walkAnimationSpeed;
	}

	/**
	 * 清掉某玩家“刚移动过”的保持窗。
	 *
	 * <p>翻滚本身会给位移，`MOVING_HOLD_TICKS=4` 会把这股位移记成“还在跑”。
	 * 翻滚结束播 `roll_to_idle` 时如果不清理，过渡末帧刚对齐 idle 就会立刻被
	 * `walkAnimation()` 判成 run，出现一跳；清掉后只有真实按键产生的位移会重新点亮。
	 */
	public static void clearMovingHold(int entityId) {
		Move m = MOVES.get(entityId);
		if (m != null) {
			m.until = 0;
			m.dx = 0.0D;
			m.dz = 0.0D;
		}
	}

	/** 玩家这一 tick 是否有真实的移动输入（用于翻滚过渡期区分“惯性”和“玩家在走”）。 */
	public static boolean hasMovementInput(int entityId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return false;
		}
		net.minecraft.entity.Entity entity = client.world.getEntityById(entityId);
		if (!(entity instanceof AbstractClientPlayerEntity player)) {
			return false;
		}
		if (player == client.player) {
			return client.currentScreen == null
					&& (client.options.forwardKey.isPressed()
					|| client.options.backKey.isPressed()
					|| client.options.leftKey.isPressed()
					|| client.options.rightKey.isPressed());
		}
		return Math.abs(player.forwardSpeed) > 0.01F || Math.abs(player.sidewaysSpeed) > 0.01F;
	}

	/**
	 * Seeds the movement sampler from the current local input. This bridges the
	 * tick between a roll recovery packet and the next position-delta sample.
	 */
	static void primeMovementFromInput(int entityId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null
				|| client.player.getId() != entityId || client.currentScreen != null) {
			return;
		}
		boolean forward = client.options.forwardKey.isPressed();
		boolean back = client.options.backKey.isPressed();
		boolean left = client.options.leftKey.isPressed();
		boolean right = client.options.rightKey.isPressed();
		double forwardInput = (forward ? 1.0D : 0.0D) - (back ? 1.0D : 0.0D);
		double sideInput = (right ? 1.0D : 0.0D) - (left ? 1.0D : 0.0D);
		if (forwardInput == 0.0D && sideInput == 0.0D) {
			return;
		}
		// Roll/sprint correction has already turned the body toward the physical
		// movement vector and will re-express these keys as forward input.
		boolean correctedDirection = GunClientState.rolling(entityId)
				|| GunClientState.dashing(entityId);
		if (correctedDirection) {
			forwardInput = 1.0D;
			sideInput = 0.0D;
		}
		Move move = MOVES.computeIfAbsent(entityId, ignored -> new Move());
		float yaw = client.player.getYaw();
		double radians = Math.toRadians(yaw);
		double fx = -Math.sin(radians);
		double fz = Math.cos(radians);
		double rx = -Math.cos(radians);
		double rz = -Math.sin(radians);
		boolean sprinting = client.player.isSprinting() || GunClientState.dashing(entityId);
		double speed = sprinting ? 0.28D : 0.216D;
		move.dx = (fx * forwardInput + rx * sideInput) * speed;
		move.dz = (fz * forwardInput + rz * sideInput) * speed;
		move.x = client.player.getX();
		move.z = client.player.getZ();
		move.yaw = yaw;
		move.until = ticks + MOVING_HOLD_TICKS;
		move.sprinting = sprinting;
		move.aiming = GunCamera.aiming(client);
	}

	/** 客户端 tick 计数（连射节拍/日志用）。 */
	public static int ticks() {
		return ticks;
	}

	/**
	 * **这位玩家的"走动方向"动画名**（作者这次给的四方向走动：`walk_front/back/left/right`）。
	 *
	 * <p>怎么算：把"最近一次真实位移"投到玩家自己的**前/右**基上（模型坐标系，
	 * 和枪口那套一致：模型 -z = 面朝方向、模型 +x = 角色的右手边），谁分量大用谁；
	 * 疾跑（速度快）直接用 `run` —— 用户 2026-10-14："包含了四个方向的走动动画"。
	 *
	 * <p>⚠️ 走动方向**不能**用"鼠标视角"：ShoulderSurfing 解耦模式下视角和身体是分开转的，
	 * 动画跟着**身体**（实体 yaw）和真实位移走才对得上画面。
	 */
	public static String walkAnimation(int entityId) {
		Move m = MOVES.get(entityId);
		if (m == null || ticks >= m.until) {
			return null;
		}
		double speed = Math.sqrt(m.dx * m.dx + m.dz * m.dz);
		// **疾跑 = run，其余一律四方向走动**（用户 2026-10-16："非冲刺状态的玩家需要进行
		// walk_front/back/left/right 动画"）。速度阈值只当兜底：有些情况 isSprinting 会被原版打断
		// （例如按蹲那一 tick），但位移明显是疾跑量级时仍然给 run。
		if (speed < 1.0E-6D) {
			return null;
		}
		double yaw = Math.toRadians(m.yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double fwd = m.dx * fx + m.dz * fz;
		double right = m.dx * rx + m.dz * rz;
		// run has no directional variants. Use it only for real forward movement;
		// backpedaling and strafing must use their corresponding walk_* animation.
		if (!m.aiming && (m.sprinting || speed > RUN_SPEED_MIN)
				&& fwd > 0.0D && Math.abs(fwd) >= Math.abs(right)) {
			return "run";
		}
		if (Math.abs(fwd) >= Math.abs(right)) {
			return fwd >= 0.0D ? "walk_front" : "walk_back";
		}
		return right >= 0.0D ? "walk_right" : "walk_left";
	}

	/**
	 * 走 / 跑的**兜底**分界（格每 tick 的水平位移）。
	 * 疾跑约 0.28、普通走约 0.216 —— 阈值取 0.25 正好把两者分开（主要判定看 {@code isSprinting}）。
	 */
	private static final double RUN_SPEED_MIN = 0.25D;

	private static void tickClientStepHeight(MinecraftClient client, boolean holdingGun) {
		if (client.player == null) {
			return;
		}
		var attr = client.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (attr == null) {
			return;
		}
		boolean has = attr.getModifier(GUN_STEP_MODIFIER) != null;
		if (holdingGun && !has) {
			attr.addTemporaryModifier(new EntityAttributeModifier(GUN_STEP_MODIFIER, 0.5D,
					EntityAttributeModifier.Operation.ADD_VALUE));
		} else if (!holdingGun && has) {
			attr.removeModifier(GUN_STEP_MODIFIER);
		}
	}

	private static void trackMovement(MinecraftClient client) {
		// 走掉的玩家把记录删掉，别让这张表一直涨
		MOVES.keySet().removeIf(id -> client.world.getEntityById(id) == null);
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			Move m = MOVES.computeIfAbsent(player.getId(), k -> {
				Move fresh = new Move();
				fresh.x = player.getX();
				fresh.z = player.getZ();
				return fresh;
			});
			double dx = player.getX() - m.x;
			double dz = player.getZ() - m.z;
			// 翻滚、滑铲和快速蹲射产生的位移是技能惯性，不是玩家移动输入。
			// 特别是滑铲转 AIM_HOLD 时，残余速度会被 MOVING_HOLD_TICKS 续成 walk_front，
			// 让蹲射保持先切成走路、再切回 Focus_hold / sliding_fast_ready，形成动画重播。
			// 只在没有真实方向键输入时屏蔽；玩家主动移动时仍按正常采样进入 walk_*。
			GunClientState.Entry ge = GunClientState.get(player.getId());
			boolean skillMotion = ge != null
					&& (ge.state == TkGun.State.ROLL
					|| ge.state == TkGun.State.ROLL_TO_IDLE
					|| ge.state == TkGun.State.SLIDE
					|| ge.state == TkGun.State.SLIDE_READY
					|| ge.state == TkGun.State.AIM_SLIDE
					|| (ge.state == TkGun.State.AIM_HOLD
						&& GunClientState.focusish(player.getId())));
			if (skillMotion && !hasMovementInput(player.getId())) {
				m.until = 0;
				m.dx = 0.0D;
				m.dz = 0.0D;
				m.x = player.getX();
				m.z = player.getZ();
				continue;
			}
			// **瞄着就取消疾跑**（用户 2026-10-17："将原版疾跑状态下按瞄准按键调整为取消疾跑"）。
			// 原版的疾跑是**客户端说了算**：服务端 `tickAimSlow` 每 tick `setSprinting(false)`，
			// 可客户端下一 tick 又按"疾跑键还按着"自己开回来 —— 所以服务端那一下按不住。
			// 这里两端都做：① 真把客户端的疾跑标志清掉（原版疾跑 FOV / 粒子 / 消耗跟着停）；
			// ② 采样记下 `aiming`，`walkAnimation` 不再按 `run` 挑（瞄着走就得是 walk_*）。
			// 必须走 `GunCamera.aiming`：翻滚/滑铲后的右键锁期间，右键即使按着也不算瞄准，
			// 不能因此取消疾跑或把 run 采样成 walk。
			boolean aiming = player == client.player && GunCamera.aiming(client);
			if (aiming && player.isSprinting()) {
				player.setSprinting(false);
			}
			// The post-roll acceleration window now uses vanilla sprint rather
			// than a separate dash movement/rotation mode. Vanilla and
			// ShoulderSurfing remain responsible for sprint turning.
			if (player == client.player && !aiming
					&& GunClientState.dashing(player.getId())
					&& dx * dx + dz * dz > MOVING_EPS2
					&& !player.isSneaking() && !player.isUsingItem()) {
				player.setSprinting(true);
			}
			if (dx * dx + dz * dz > MOVING_EPS2) {
				m.until = ticks + MOVING_HOLD_TICKS;
				m.dx = dx;
				m.dz = dz;
				m.yaw = player.getYaw();
				m.sprinting = player.isSprinting() && !aiming;
				m.aiming = aiming;
				float horizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz);
				m.walkAnimationSpeed = net.minecraft.util.math.MathHelper.clamp(
						horizontalSpeed / 0.216F, 0.55F, 1.35F);
				m.walkPhase += 0.05F * m.walkAnimationSpeed;
				if (m.walkPhase >= 1.0F) {
					m.walkPhase -= 1.0F;
				}
			} else if (ticks >= m.until) {
				m.walkPhase = 0.0F;
				m.walkAnimationSpeed = 1.0F;
			}
			m.x = player.getX();
			m.z = player.getZ();
		}
	}
	/** 手持弩炮强制**越肩视角**：记住进枪之前是什么视角，枪一离手就还原。 */
	private static boolean thirdPersonForced = false;
	/**
	 * 进枪之前玩家自己是什么视角（枪离手时还原 —— 别硬改成第一人称：
	 * 这个整合包装了 ShoulderSurfing，玩家的默认视角是它自己的 `SHOULDER_SURFING`，
	 * 强行还原成第一人称等于把人家设置改了）。
	 */
	private static Perspective perspectiveBeforeGun;
	/** ShoulderSurfing 在不在（不在就退回原版第三人称；装了就交给它）。 */
	private static boolean surfAvailable;
	/**
	 * 本地玩家枪口的世界坐标 + 枪管朝向 —— **渲染时从模型里量出来的**（枪口那根骨，
	 * 见 {@code BowgunRigRenderer.MUZZLE_BONE}）。开火时随 {@link GunFirePayload} 发给服务端，
	 * 子弹/特效就真的从看得见的枪口出去。
	 *
	 * <p>⚠️ 别改成"按玩家模型固定推算"（2026-10-14 试过、被用户否了：算出来的点在枪口**左边**）。
	 * 要改就改 `MUZZLE_BONE`（现在绑的是 `fire1`）。
	 */
	private static double muzzleX;
	private static double muzzleY;
	private static double muzzleZ;
	private static double aimX;
	private static double aimY;
	private static double aimZ;
	private static double muzzleDx;
	private static double muzzleDy = 0.0D;
	private static double muzzleDz = -1.0D;
	private static boolean muzzleValid;

	public static void captureMuzzle(net.minecraft.util.math.Vec3d aimPos,
									 net.minecraft.util.math.Vec3d muzzlePos,
									 net.minecraft.util.math.Vec3d dir) {
		aimX = aimPos.x;
		aimY = aimPos.y;
		aimZ = aimPos.z;
		muzzleX = muzzlePos.x;
		muzzleY = muzzlePos.y;
		muzzleZ = muzzlePos.z;
		if (dir.lengthSquared() > 1.0E-6D) {
			net.minecraft.util.math.Vec3d n = dir.normalize();
			muzzleDx = n.x;
			muzzleDy = n.y;
			muzzleDz = n.z;
			// 右键瞄准刚开始时，tick 头拿到的是上一帧；等这帧真实枪口渲染出来再给相机锁中心线。
			GunCamera.noteVisualMuzzle(n);
		}
		muzzleValid = true;
	}

	/** 枪口的世界坐标（还没量到 = null）。HUD 的弹着点射线拿它当起点。 */
	public static net.minecraft.util.math.Vec3d muzzleWorldPos() {
		return muzzleValid ? new net.minecraft.util.math.Vec3d(muzzleX, muzzleY, muzzleZ) : null;
	}

	/** 近端 `aim` 定位点的世界坐标（与枪口同一渲染帧捕获）。 */
	public static net.minecraft.util.math.Vec3d aimWorldPos() {
		return muzzleValid ? new net.minecraft.util.math.Vec3d(aimX, aimY, aimZ) : null;
	}

	/** 枪管当前朝向（世界空间；还没量到 = null）。 */
	public static net.minecraft.util.math.Vec3d muzzleWorldDir() {
		return muzzleValid
				? new net.minecraft.util.math.Vec3d(muzzleDx, muzzleDy, muzzleDz) : null;
	}

	/**
	 * 服务端那套**固定推算**的枪口（`TkGun.MUZZLE_*` 常量 + 玩家 yaw）—— **只用来做检测对比**
	 * （蓝色粒子），子弹不走这条路。它就是之前偏到枪口左边 0.25 格的那个值。
	 */
	private static net.minecraft.util.math.Vec3d approxMuzzle(MinecraftClient client) {
		double yaw = Math.toRadians(client.player.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double xx = -Math.cos(yaw);
		double xz = -Math.sin(yaw);
		return new net.minecraft.util.math.Vec3d(
				client.player.getX() + fx * TkGun.MUZZLE_FORWARD + xx * TkGun.MUZZLE_SIDE,
				client.player.getY() + TkGun.MUZZLE_HEIGHT,
				client.player.getZ() + fz * TkGun.MUZZLE_FORWARD + xz * TkGun.MUZZLE_SIDE);
	}

	private static String fmt(net.minecraft.util.math.Vec3d v) {
		return "(" + Math.round(v.x * 100) / 100.0 + ", " + Math.round(v.y * 100) / 100.0
				+ ", " + Math.round(v.z * 100) / 100.0 + ")";
	}
	/**
	 * 第一次进 tick 时自检：**GeckoLib rig 那份动画文件**在不在、有几段。
	 *
	 * <p>⚠️ 2026-10-13：以前这里还查 playeranimator 的注册表（`pal.animation.json`），
	 * 但那套已经被 GeckoLib rig 顶掉（见 {@link com.tk.gun.client.rig.BowgunRig}），
	 * 留着只会在日志里报"找不到动画 tkgun:idle"这种误导性警告 —— 一起退休了。
	 */
	private static void selfTest() {
		selfTested = true;
		rigAnimations();
	}

	/**
	 * 核对 **GeckoLib rig 那份动画文件**（`animations/entity/bowgun_rig.animation.json`）：
	 * 它才是驱动玩家模型/枪/枪口焰的那一份。把里面的动画名全打出来 ——
	 * 机械师新素材应该是 15 段（idle / Focus_ready / Focus_shooting[2..7] / Overheat / run /
	 * roll / sliding / sliding_ready / Focus_over_shooting）。
	 */
	private static void rigAnimations() {
		Identifier id = Identifier.of(TkGun.MOD_ID, "animations/entity/bowgun_rig.animation.json");
		var res = MinecraftClient.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			TkGun.LOGGER.warn("[tkgun] rig 动画文件不在资源里: {}", id);
			return;
		}
		try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
				res.get().getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
			var doc = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
			var anims = doc.getAsJsonObject("animations");
			StringBuilder sb = new StringBuilder();
			for (String key : anims.keySet()) {
				sb.append(key).append(' ');
			}
			TkGun.LOGGER.info("[tkgun] rig 动画 {} 段: {}", anims.size(), sb.toString().trim());
		} catch (Exception e) {
			TkGun.LOGGER.warn("[tkgun] rig 动画文件解析失败", e);
		}
	}

	/** 某个玩家此刻在播我们哪个动画（渲染 props 时要读）。 */
	public static String currentAnimation(UUID uuid) {
		return com.tk.gun.client.rig.BowgunRig.currentAnimation();
	}

	/**
	 * **把一个动画音效关键帧的名字报给服务端**（{@link com.tk.gun.net.GunSoundPayload}）——
	 * 服务端查 {@code TkGun#KEYFRAME_SOUNDS} 后在玩家位置放，**附近所有人都听得见**
	 * （用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见"）。
	 *
	 * <p>为什么不自己 `playSound`：那样只有本地听得见。服务端放的那一声会连**自己**一起收到
	 * （原版 `ServerWorld#playSound` 广播给范围内所有玩家），所以这里**不要再补一声**，不然就是双份。
	 */
	/** 本地玩家现在是不是在翻滚（客户端镜像；翻滚期间 Z / F 都不发包，先把动画播完）。 */
	private static boolean isRolling(MinecraftClient client) {
		if (client.player == null) {
			return false;
		}
		GunClientState.Entry e = GunClientState.get(client.player.getId());
		return e != null && e.active() && e.state == TkGun.State.ROLL;
	}

	private static boolean canUseStandingMelee(MinecraftClient client) {
		if (client.player == null || client.currentScreen != null
				|| client.player.isUsingItem()
				|| !TkGun.isGun(client.player.getMainHandStack())
				|| !TkGun.definition(client.player.getMainHandStack()).id()
						.equals(TkGun.id("veritas_et_justitia"))
				|| GunClientState.focusish(client.player.getId())) {
			return false;
		}
		// Server-authoritative state validation avoids falling back to a fire packet
		// merely because a state packet has not arrived in the same client tick.
		return true;
	}

	public static void sendSoundKeyframe(String key) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			return;                     // 还没连上（单机"正在打开世界"那一瞬）：丢掉这一声
		}
		ClientPlayNetworking.send(new com.tk.gun.net.GunSoundPayload(key));
	}

	/**
	 * **发一个开火包**（普通单发和强化的过载连射共用）。
	 *
	 * <p>枪口 = 渲染时量到的 `locator` 世界坐标；朝向 = 瞄准时的 `GunCamera.aimDir`
	 * （从 locator 指向慢慢转过去的落点），没瞄准就是实测的枪管方向。枪口位置永远不变。
	 */
	public static void sendFirePacket(MinecraftClient client, String why) {
		if (client.player == null) {
			return;
		}
		double mx = muzzleX;
		double my = muzzleY;
		double mz = muzzleZ;
		double dx = muzzleDx;
		double dy = muzzleDy;
		double dz = muzzleDz;
		net.minecraft.util.math.Vec3d aimDir = GunCamera.shotDirection(client);
		if (aimDir != null) {
			dx = aimDir.x;
			dy = aimDir.y;
			dz = aimDir.z;
		}
		if (!muzzleValid) {
			// 骨骼还没渲染过（极少见）：用眼睛位置 + 视角方向兜底，服务端还会再判一次
			net.minecraft.util.math.Vec3d eye = client.player.getEyePos();
			net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0F);
			mx = eye.x;
			my = eye.y;
			mz = eye.z;
			dx = look.x;
			dy = look.y;
			dz = look.z;
		}
		// why == null = 连发补包（每 3 tick 一个，不想刷屏）；只给第一个补包打一行
		if (why != null) {
			TkGun.LOGGER.info("[tkgun] {} -> 发开火包（主手={}，枪口=({}, {}, {})，朝向=({}, {}, {})）",
					why, client.player.getMainHandStack().getItem(),
					Math.round(mx * 100) / 100.0, Math.round(my * 100) / 100.0, Math.round(mz * 100) / 100.0,
					Math.round(dx * 100) / 100.0, Math.round(dy * 100) / 100.0, Math.round(dz * 100) / 100.0);
		}
		ClientPlayNetworking.send(new GunFirePayload(mx, my, mz, dx, dy, dz));
	}

	@Override
	public void onInitializeClient() {
		com.tk.gun.client.rig.BowgunPlayerAnimatorPose.register();
		if (!CombatRollHook.isInstalled()) {
			fallbackRollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					"key.tkgun.roll", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
					"category.tkgun"));
			TkGun.LOGGER.info("[tkgun] 未安装 Combat Roll：启用模组内置闪避键（默认左 Alt）");
		}
		// 让作者写在动画里的"跟随视角" Molang 真的生效（GeckoLib 对 GeoReplacedEntity 会返回 0，
		// 这里用 public 的 setActorVariable 把 4 个 query 按"被替换的那个玩家"重新实现，见 TkGunMolang）。
		TkGunMolang.install();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shouldersurfing")) {
			ShoulderSurfBridge.registerHipFireCoupling();
		}
		TkGun.LOGGER.info("[tkgun] 蹲射/专注与下蹲共用原版 sneak 键");
		// 弩矢弹丸的渲染器：什么都不画（飞行表现 = 服务端铺的曳光粒子）
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.tk.gun.TkGunServer.BOLT_ENTITY,
				com.tk.gun.client.entity.BoltProjectileRenderer::new);
		// 看得见的子弹（素材 粒子/bullet.png，3 帧循环，面朝相机）
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.tk.gun.TkGunServer.BULLET_ENTITY,
				com.tk.gun.client.entity.BulletRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.tk.gun.TkGunServer.MOLTEN_TRIDENT_ENTITY,
				com.tk.gun.client.entity.MoltenTridentEntityRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.tk.gun.TkGunServer.MOLTEN_GOLEM,
				com.tk.gun.client.entity.MoltenGolemEntityRenderer::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.HEAT_VENT, HeatVentParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.ENERGY_CONVERGE, EnergyParticle.MuzzleFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.ENERGY_XPLO, EnergyParticle.HitFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.EXPLOSIVE_BOLT_IMPACT,
						EnergyParticle.HitFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.SHELL_BOOST, ShellBoostParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.MUZZLE_GLOW, MuzzleGlowParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.CUTTING_EXPLO, EnergyParticle.CuttingFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.SONIC_BOOM, EnergyParticle.SonicBoomFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.GRAPESHOT_IMPACT, EnergyParticle.GrapeshotImpactFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.ROSE_QUARTZ_IMPACT, EnergyParticle.RoseQuartzImpactFactory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.ROSE_QUARTZ_TRAIL, GlowingRoseTrailParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.THUNDER, ThunderParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.GOLEM_ENERGY_CONVERGE,
						GolemEnergyParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.tk.gun.TkGunParticles.GOLEM_ENERGY_CONVERGE_2,
						GolemEnergyParticle.Factory::new);
		// 右下角弹夹计数（自带 HUD，不动 hud mod）
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
				(context, tickCounter) -> GunHud.render(context));
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
				(context, tickCounter) -> CombatBackpackHud.render(context));
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
				(context, tickCounter) -> OffhandStorageHud.render(context));
		// 越肩视角的准星：中间 `- . -` + 枪口那个圈圈
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
				(context, tickCounter) -> GunReticle.render(context));
		ClientPlayNetworking.registerGlobalReceiver(GunStatePayload.ID,
				(payload, context) -> context.client().execute(() -> GunClientState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(
				com.tk.gun.net.MeleeImpactPayload.ID,
				(payload, context) -> context.client().execute(() ->
						MeleeImpactClientState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunHitMarkerPayload.ID,
				(payload, context) -> context.client().execute(GunReticle::triggerHitMarker));
		ClientPlayNetworking.registerGlobalReceiver(com.tk.gun.net.GunDemoPayload.ID,
				(payload, context) -> context.client().execute(
						() -> GunDebugHarness.setRemoteEnabled(payload.on())));
		ClientPlayNetworking.registerGlobalReceiver(
				com.tk.gun.net.CombatBackpackFireResultPayload.ID,
				(payload, context) -> context.client().execute(() -> {
					if (context.client().player != null) {
						CombatBackpackClientState.completeFire(
								context.client().player.getId());
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(
				com.tk.gun.net.CombatBackpackStatusPayload.ID,
				(payload, context) -> context.client().execute(() ->
						CombatBackpackClientState.applyStatus(
								payload.ownerEntityId(),
								payload.targetEntityId(),
								payload.phase(),
								payload.charges(),
								payload.remainingTicks())));
		ClientPlayNetworking.registerGlobalReceiver(
				com.tk.gun.net.CombatBackpackParticlePayload.ID,
				(payload, context) -> context.client().execute(() ->
						LocatorShriekBurst.start(
								payload.playerId(),
								LocatorShriekBurst.Source.SUMMON_ANTENNA,
								new net.minecraft.util.math.Vec3d(
										payload.x(), payload.y(), payload.z()))));


		com.tk.gun.client.screen.AmmoStorageScreen.registerClientScreen();
		com.tk.gun.client.screen.BackpackModificationTableScreen.registerClientScreen();
		for (com.tk.gun.item.CombatBackpackItem backpack :
				com.tk.gun.TkGunServer.COMBAT_BACKPACKS) {
			io.wispforest.accessories.api.client.AccessoriesRendererRegistry.registerRenderer(
					backpack, com.tk.gun.client.CombatBackpackAccessoryRenderer::new);
		}
		io.wispforest.accessories.api.client.AccessoriesRendererRegistry.onReload();
		TkGun.LOGGER.info("[tkgun] 战斗背包 Accessories 渲染器={}",
				io.wispforest.accessories.api.client.AccessoriesRendererRegistry
						.getRenderer(com.tk.gun.TkGunServer.COMBAT_BACKPACK).getClass().getSimpleName());

		ClientTickEvents.END_CLIENT_TICK.register(TkGunClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(
				client -> MeleeImpactClientState.tick());
		TkGun.LOGGER.info("[tkgun] 客户端就绪: GeckoLib 玩家动画 rig");
	}

	private static void tick(MinecraftClient client) {
		GunClientState.tickAll();
		CombatBackpackClientState.tick(client);
		LocatorShriekBurst.tick(client);
		AmmoSwitchHud.tick(client);
		ticks++;
		// 第一次自检可能正好撞在资源重载中间（会误报 0/8），所以隔几秒再打两次
		if ((ticks == 100 || ticks == 400 || ticks == 1200) && client.getResourceManager() != null) {
			selfTest();
		}
		GunDebugHarness.tick(client);
		if (client.world == null || client.player == null) {
			thirdPersonForced = false;
			heldGunLastTick = false;
			jumpKeyDown = false;
			MOVES.clear();
			backpackActionDown = false;
			com.tk.gun.client.rig.BowgunPlayerAnimatorPose.clear();
			return;
		}
		// Backpack X is intentionally handled before focus, roll, reload and other
		// state-specific input so those input locks cannot consume or suppress it.
		handleBackpackActionKey(client);
		boolean holdingGunNow = TkGun.holdingGun(client.player);
		boolean jumpDown = client.currentScreen == null
				&& client.options.jumpKey.isPressed();
		boolean silentStorm = holdingGunNow
				&& TkGun.definition(client.player).id()
						.equals(TkGun.id("abyssal_ancient_cannon"));
		if (jumpDown && !jumpKeyDown && silentStorm) {
			ClientPlayNetworking.send(new GunJumpPayload());
			TkGun.LOGGER.info("[tkgun] jump key -> request Silent Storm skill jump");
		}
		jumpKeyDown = jumpDown;
		if (holdingGunNow && client.player.isSneaking()) {
			client.player.setSneaking(false);
		}
		if (!holdingGunNow && heldGunLastTick) {
			// Leaving the gun must discard every stale client-side gun state so
			// vanilla first-person camera/action logic takes over immediately.
			// 弹匣/热量保留；重新持枪时服务端会补发权威状态。
			GunClientState.onGunReleased(client.player.getId());
		}
		heldGunLastTick = holdingGunNow;
		tickClientStepHeight(client, holdingGunNow);
		trackMovement(client);
		forceOverShoulder(client);
		FallbackShoulderCamera.tick(client);
		// 越肩视角 + "身体/视角解耦"的状态机（鼠标增量、身体转向、FOV 放大系数都在里面推进）
		GunCamera.tickEnd(client);
		// —— Z 键 = 切换专注（架枪）——
		// ⚠️ 2026-10-17：**不再用 `FOCUS_KEY.wasPressed()`**（用户报"按 Z 没反应"）。那条路要经过
		// Fabric 的按键注册 + 原版 `KeyBinding` 的按下处理，任何一环在这个整合包里出问题（别的 mod
		// 抢同一个键、注册时机不对）都会让这次按键**静默丢掉**，而且看不出来。
		// 现在直接读**当前绑定的键码**做边沿检测：绑在哪个键上就认哪个键（默认 Z），不经过原版那套。
		// KeyBinding 仍然注册（照旧出现在"选项 → 按键设置"里、能改键、能看冲突）。
		boolean sneakDown = client.options.sneakKey.isPressed();
		boolean focusDown = sneakDown && client.currentScreen == null;
		// **Z = 切换专注**（用户 2026-10-17："将 z 的长按专注回调为切换"）：**只在按下那一下发一包**，
		// 服务端按当前是否已专注决定进（`Focus_ready`）/ 出（`Focus_over`）。
		if (focusDown != focusKeyDown) {
			focusKeyDown = focusDown;
			if (focusDown && TkGun.holdingGun(client.player)) {
				// **翻滚中按 Z = 快速蹲射**（用户 2026-10-17）：不再屏蔽 —— 服务端会打断翻滚动画、
				// 按快档（`fast_ready`）直接进架枪，见 `TkGunServer#startFocus` 里的 ROLL 分支。
				// 翻滚中按 Z 一定是"进入快速蹲射"意图：此时客户端镜像可能还残留着翻滚前的
				// `focus=true`（状态包尚未到达），若按普通 toggle 判断会算成"退出"，本地就不会
				// 清冲刺/校正朝向。服务端以 `deployed` 为准，这里只是把表现提前对齐。
				boolean entering = isRolling(client)
						|| !GunClientState.focusish(client.player.getId());
				// 只认正在翻滚或本次 Alt 新按下的组合输入。翻滚结束后普通 Z 不得继续命中旧缓冲，
				// 否则会被服务端误兑现成滑铲快速蹲射。
				// 服务端会先把它缓存为“翻滚后再快速蹲射”，避免 Z 包先到而先进入普通 Focus_ready。
				GunClientState.Entry focusState = GunClientState.get(client.player.getId());
				boolean sliding = focusState != null && (focusState.state == TkGun.State.SLIDE
						|| focusState.state == TkGun.State.SLIDE_READY);
				boolean dodgeCombo = !sliding && (isRolling(client)
						|| (readRollKeyDown(client) && !rollKeyDown));
				ClientPlayNetworking.send(new GunFocusPayload(dodgeCombo));
				if (entering) {
					// **进专注 = 立刻清掉冲刺加成**（用户 2026-10-17；上一版只清服务端，玩家反馈"未能生效"）。
					// 服务端 `startFocus` 也会清 `dashUntil` + 摘修饰符；这里在**按下那一 tick** 先把本地
					// 玩家身上的那份摘掉，不用等属性包往返 —— 否则按了 Z 还会带着加速度滑一下。
					com.tk.gun.GunStateMachine.clearDash(client.player);
					// **按下蹲射键 → 强制把朝向拧到指针（准星）所指方向**（用户 2026-10-17）。
					client.player.setYaw(GunCamera.lookYaw());
					client.player.setHeadYaw(GunCamera.lookYaw());
				}
				TkGun.LOGGER.info("[tkgun] 下蹲/专注键 -> 切换专注（当前{}，闪避组合={}）",
						entering ? "未专注 → 进" : "已专注 → 出（Focus_over）", dodgeCombo);
			} else if (focusDown) {
				TkGun.LOGGER.info("[tkgun] 下蹲/专注键：手上不是破垒重炮，忽略");
			}
		}
		// F 键主动装填。
		InputUtil.Key reloadKey = bindingKey(RELOAD_KEY, GLFW.GLFW_KEY_F);
		int reloadKeyCode = reloadKey.getCode();
		boolean reloadRaw = isInputDown(client, reloadKey);
		boolean reloadDown = client.currentScreen == null && reloadRaw;
		if (reloadDown && !reloadKeyDown && TkGun.holdingGun(client.player)) {
			// 专注里 F 也属于"被屏蔽的按键"（用户 2026-10-17："屏蔽除射击和翻滚以外的所有按键输入"）
			// —— 要装填就松开 Z 退出专注再按。
			if (GunClientState.focusish(client.player.getId())) {
				TkGun.LOGGER.info("[tkgun] F：专注期间屏蔽装填（松开 Z 再按）");
			} else if (isRolling(client)
					|| (GunClientState.get(client.player.getId()) != null
					&& GunClientState.get(client.player.getId()).state == TkGun.State.MELEE)) {
				// **翻滚期间屏蔽装填**（用户 2026-10-17）：F 会把 ROLL 顶成 RELOAD，翻滚动画只播一半。
				TkGun.LOGGER.info("[tkgun] F：翻滚中，等翻滚动画播完再装填");
			} else {
				// 过热期间的装填**不在客户端拦**（用户 2026-10-17）：过热里翻滚后的冲刺窗口内允许
				// 立刻装弹，客户端看不到 `dashUntil`，所以交给服务端权威判定。
				ClientPlayNetworking.send(new GunReloadPayload());
				TkGun.LOGGER.info("[tkgun] 装填键 {}（码 {}）-> 请求装填",
						reloadKey.getLocalizedText().getString(), reloadKeyCode);
			}
		}
		// 记录物理按下状态（而不是“界面关闭时的按下”），
		// 这样在界面里按住/松开按键后关闭界面，不会又补触发一次。
		reloadKeyDown = reloadRaw;
		InputUtil.Key ammoUiKey = bindingKey(AMMO_UI_KEY, GLFW.GLFW_KEY_K);
		int ammoUiKeyCode = ammoUiKey.getCode();
		boolean ammoUiRaw = isInputDown(client, ammoUiKey);
		boolean ammoUiDown = client.currentScreen == null && ammoUiRaw;
		if (ammoUiDown && !ammoUiKeyDown && TkGun.holdingGun(client.player)) {
			ClientPlayNetworking.send(new com.tk.gun.net.OpenAmmoStoragePayload());
			TkGun.LOGGER.info("[tkgun] 弹药环键 {}（码 {}）-> 打开弹药环",
					ammoUiKey.getLocalizedText().getString(), ammoUiKeyCode);
		}
		ammoUiKeyDown = ammoUiRaw;
		// （原来这里有一条"§e[tkgun] 专注没生效 …"的聊天栏诊断 —— 2026-10-17 用户要求清除，已整段删掉。
		//   现在"是不是旧 jar"的判断看进服日志里的 `构建=` 那一行就够了。）
		// —— 左键 ——
		// 单击：上面那条"按下边沿"已经发过一个开火包（= 一发）。
		// 按住：**专注里**每 5 tick 补一个开火包 —— 这就是连发（服务端会按 12→7→5 tick 的节奏出弹）。
		// ⚠️ 2026-10-17 用户报"按一下打出两发"：根因是**服务端那条靠扳机状态包的自动连发** ——
		//    那个位只要晚到/丢了"松开"包，服务端就以为扳机还按着，于是自己再补一发。
		//    现在**服务端那条整段删掉**，连发只有这一个来源，并且**按住超过 6 tick 才起连发**，
		//    所以轻点永远只有一发。
		boolean trig = client.currentScreen == null && TkGun.holdingGun(client.player)
				&& client.options.attackKey.isPressed();
		if (trig) {
			attackHeldTicks++;
		} else {
			attackHeldTicks = 0;
			autoFireLogged = false;
		}
		if (trig && attackHeldTicks > 6 && attackHeldTicks % 5 == 0
				&& GunClientState.focusish(client.player.getId())) {
			sendFirePacket(client, autoFireLogged ? null : "左键(专注·连发)");
			autoFireLogged = true;
		}
		// —— **翻滚键（combat_roll 的）**：按下就通知服务端 ——
		// 用户 2026-10-17："尝试将翻滚改为检测到输入翻滚按键时回到 idle 状态并瞬间触发翻滚"。
		// 为什么要在按键那一刻就报：combat_roll 的可用性判定要求 `GENERIC_MOVEMENT_SPEED > 0`，
		// 而"缓慢"会把移速属性压小，强到一定程度（−150%）变负数 → 它直接拒绝翻滚
		// （用户报的"翻滚未生效"就是这个根因）。服务端收到后：若是过热/退门 → 打断动画 +
		// 清掉缓慢 + 状态切成 ROLL；其余情况什么都不做，翻滚照旧由 combat_roll 自己走。
		boolean rollDown = readRollKeyDown(client);
		if (rollDown && !rollKeyDown && TkGun.holdingGun(client.player)) {
			GunClientState.Entry rollState = GunClientState.get(client.player.getId());
			boolean prep = rollState != null && (rollState.state == TkGun.State.AIM
					|| rollState.state == TkGun.State.AIM_FAST
					|| rollState.state == TkGun.State.AIM_ROLL
					|| rollState.state == TkGun.State.AIM_SLIDE);
			if (prep) {
				// **蹲射准备期间（AIM/AIM_FAST/AIM_SLIDE 过门）屏蔽闪避键**（用户 2026-10-17）。
				TkGun.LOGGER.info("[tkgun] 翻滚键：蹲射准备中，忽略");
			} else {
				GunRollPayload rollPayload = rollDirectionPayload(client);
				SprintDirectionCorrection.alignForRoll(
						client, rollPayload.directionX(), rollPayload.directionZ());
				ClientPlayNetworking.send(rollPayload);
				// 状态包要下一 tick 才到；先把本地 rig 锁到 ROLL，避免闪一帧 run。
				GunClientState.beginLocalRoll(client.player.getId());
				TkGun.LOGGER.info("[tkgun] 翻滚键按下 -> 通知服务端 方向=({}, {})（过热/退门时打断并清缓慢）",
						rollPayload.directionX(), rollPayload.directionZ());
			}
		}
		rollKeyDown = rollDown;
		// ⚠️ 2026-10-17：**专注期间屏蔽移动 / 跳跃 / 潜行不在这里做了**。
		// 这里（tick 末尾）清键位"晚了一 tick"：按下 W 的那一 tick 位移已经产生（实测 0.098 格），
		// 服务端那条"专注里按移动键 → 退专注 + 缓慢"当场触发，看着就是"根本没屏蔽"。
		// 现在改在 `FocusMovementLockMixin` 里、**输入算出来的同一 tick** 把 `movementForward/Sideways/
		// jumping/sneaking` 清零；热键类由 `FocusInputLockMixin` 取消 `handleInputEvents` 整段。
		// —— locator 定位检测：**已撤**（用户 2026-10-15："现在可以隐藏掉枪口的粒子特效了，
		//    因为之前是为了测试你到底能不能定位到 locator"）。枪口点/枪管方向照旧每帧从
		//    `locator` 骨骼上量（TkGunClient.captureMuzzle），只是不再在那儿放白色亮点、也不再刷日志。
		boolean down = client.options.attackKey.isPressed();
		if (!down && attackWasDown && meleeAttackDown) {
			ClientPlayNetworking.send(new com.tk.gun.net.GunMeleePayload(false, false));
			meleeAttackDown = false;
		}
		if (down && !attackWasDown) {
			boolean holding = TkGun.holdingGun(client.player);
			boolean noScreen = client.currentScreen == null;
			if (holding && noScreen) {
				if (canUseStandingMelee(client)) {
					boolean movingForward = client.options.forwardKey.isPressed()
							&& (TkGunClient.isMoving(client.player.getId())
							|| Math.abs(client.player.forwardSpeed) > 0.01F);
					ClientPlayNetworking.send(new com.tk.gun.net.GunMeleePayload(
							true, movingForward));
					meleeAttackDown = true;
					TkGun.LOGGER.info("[tkgun] 左键 -> Veritas close combat");
					attackWasDown = down;
					return;
				}
				// ⚠️ 2026-10-15 用户："**正常按左键是不能开火的** —— 行走射击也得先瞄准，然后播
				//    行走射击的动画，然后才能开火"：扣扳机的**前提是右键举着枪**（瞄准）。
				//    所以这里先拦一道（不发包），服务端还会用 `isUsingItem()` 再权威判一次。
				// ⚠️ 2026-10-17：**客户端不再自己当门**。原来这里是"没按右键就不发包"，
				// 但专注（架枪）里本来就不需要右键，而且 focus 位万一没对上就会把这一发吞掉
				// （用户 2026-10-16/17 两次报"专注状态不能射击"）。现在只要手持弩炮 + 没开界面就发，
				// **行不行由服务端权威判定**（腰射没举枪时服务端会回一句"先按住右键举枪瞄准"）。
				sendFirePacket(client,
						GunClientState.focusish(client.player.getId()) ? "左键(专注)" : "左键");
			} else {
				// 诊断用：按了左键但没发包，说清楚是哪一个条件挡的（2026-10-13 用户报"按左键没反应"）
				TkGun.LOGGER.info("[tkgun] 左键按下但没发包：手持弩炮={} 界面为空={}（主手={}）",
						holding, noScreen, client.player.getMainHandStack().getItem());
			}
		}
		attackWasDown = down;
	}

	/** Captures raw WASD before focus input locks zero the KeyboardInput fields. */
	private static GunRollPayload rollDirectionPayload(MinecraftClient client) {
		float forward = (keyDown(client, client.options.forwardKey) ? 1.0F : 0.0F)
				- (keyDown(client, client.options.backKey) ? 1.0F : 0.0F);
		float sideways = (keyDown(client, client.options.leftKey) ? 1.0F : 0.0F)
				- (keyDown(client, client.options.rightKey) ? 1.0F : 0.0F);
		if (Math.abs(forward) < 0.01F && Math.abs(sideways) < 0.01F) {
			return new GunRollPayload(0.0F, 0.0F);
		}
		double yaw = Math.toRadians(client.gameRenderer.getCamera().getYaw());
		Vec3d look = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
		// Minecraft yaw 0 faces +Z. Its left-hand direction is +X at yaw 0.
		Vec3d left = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
		Vec3d direction = look.multiply(forward).add(left.multiply(sideways)).normalize();
		return new GunRollPayload((float) direction.x, (float) direction.z);
	}

	private static boolean keyDown(MinecraftClient client, KeyBinding binding) {
		try {
			InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
			return isInputDown(client, key);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * 手持破垒重炮 → 强制**越肩视角**（用户 2026-10-14："手持枪械默认进入越肩视角"）。
	 *
	 * <p>整合包里装了 ShoulderSurfing（玩家默认视角就是它的 `SHOULDER_SURFING`），
	 * 所以优先切到那个值；它不在（没装/改了）就退回原版 `THIRD_PERSON_BACK`。
	 * ⚠️ 2026-10-14 之前这里写死 `THIRD_PERSON_BACK` —— 那正好把玩家的越肩视角顶成
	 * "远处正后方"的老第三人称，这就是用户看到的"没有越肩"。
	 *
	 * <p>每 tick 顶一次（按 F5 也会被拉回来）；枪离手时还原成**玩家原来那个视角**。
	 */
	/**
	 * Polls the backpack action with its own edge detector. This is public so the
	 * focus input mixin can forward X before it cancels vanilla hotkey handling.
	 */
	public static void handleBackpackActionKey(MinecraftClient client) {
		InputUtil.Key backpackActionKey = bindingKey(BACKPACK_ACTION_KEY, GLFW.GLFW_KEY_X);
		boolean backpackActionRaw = isInputDown(client, backpackActionKey);
		if (backpackActionRaw && !backpackActionDown && client.currentScreen == null
				&& client.player != null
				&& com.tk.gun.item.BackpackUpgradeData.canUseLauncher(
						com.tk.gun.item.CombatBackpackItem.equippedStack(client.player))) {
			CombatBackpackClientState.trigger(client.player);
		}
		backpackActionDown = backpackActionRaw;
	}

	private static void forceOverShoulder(MinecraftClient client) {
		if (TkGun.holdingGun(client.player)) {
			if (!thirdPersonForced) {
				thirdPersonForced = true;
				perspectiveBeforeGun = client.options.getPerspective();
				// 2026-10-17 用户："撤销所有关于视角修改的内容，我将重新使用 ShoulderSurfing" ——
				// 回到原来的分工：**视角交给 ShoulderSurfing**（它接管时用它的 `SHOULDER_SURFING`），
				// 没装/没接管才退回原版第三人称。本模组不再自带越肩相机。
				surfAvailable = applyShoulderSurf(true);
				if (!surfAvailable) {
					client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				}
				TkGun.LOGGER.info("[tkgun] 手持弩炮 -> 越肩视角（ShoulderSurfing {}）",
						surfAvailable ? "接管" : "没装，退回原版第三人称");
			}
		} else if (thirdPersonForced) {
			thirdPersonForced = false;
			FallbackShoulderCamera.leave(client);
			applyShoulderSurf(false);
			// Always restore the vanilla perspective that was active before the gun.
			// ShoulderSurfing can otherwise leave its decoupled state behind after
			// switching items and re-entering first person.
			if (perspectiveBeforeGun != null
					&& client.options.getPerspective() != perspectiveBeforeGun) {
				client.options.setPerspective(perspectiveBeforeGun);
			}
			GunCamera.resetVanillaState();
			surfAvailable = false;
			perspectiveBeforeGun = null;
		}
	}

	/**
	 * 走 {@link ShoulderSurfBridge}（里面才会 import ShoulderSurfing 的 API）：
	 * 没装那个 mod 时这里会抛 NoClassDefFoundError，被吞掉 → 返回 false，退回原版第三人称。
	 */
	private static boolean applyShoulderSurf(boolean enter) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shouldersurfing")) {
			return false;
		}
		try {
			if (enter) {
				return ShoulderSurfBridge.goOverShoulder();
			}
			ShoulderSurfBridge.leaveOverShoulder();
			return false;
		} catch (Throwable t) {
			return false;
		}
	}

}
