package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.option.Perspective;
import net.minecraft.screen.slot.Slot;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端自动演示 + 截图（只有 -Dtkgun.demo=1 时才跑）。
 *
 * 用途：验证第三人称持枪动作是否真的播出来（原版键位是真的按，所以服务端链路也一起验了）。
 * 每进入一个新动画就截一张 assistant 目录下的 PNG，日志里也会打 STEP。
 */
public final class GunDebugHarness {
	/**
	 * ⚠️ 别用 {@code Boolean.getBoolean("tkgun.demo")} —— 它只认字面量 "true"，
	 * 而启动脚本传的是 {@code -Dtkgun.demo=1}，那样开关永远关着（2026-10-12 踩过：
	 * 演示一直没跑，之前能出截图是因为用 RCON 打开了远程演示）。
	 */
	private static final boolean ENABLED = truthy(System.getProperty("tkgun.demo"));

	private static boolean truthy(String value) {
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
	}
	private static boolean remoteEnabled;

	public static void setRemoteEnabled(boolean on) {
		remoteEnabled = on;
		if (!on) {
			releaseKeys(MinecraftClient.getInstance());
			started = false;
			waitTicks = 0;
			tick = 0;
		}
		TkGun.LOGGER.info("[tkgun-demo] 远程演示 {}", on ? "开启" : "关闭");
	}
	private static final Set<String> SHOT = new HashSet<>();
	private static int tick;
	private static boolean started;
	private static int waitTicks;
	private static int bootTicks;
	private static String lastAnim = "";
	private static int shotDelay = -1;
	private static String shotName = "";
	/** 翻滚键按下去之后要记得松手，否则 combat_roll 会每冷却好一次就翻一次（2026-10-12 踩过）。 */
	private static KeyBinding heldRollKey;
	private static int releaseRollAt = -1;

	public static void tick(MinecraftClient client) {
		if (!ENABLED && !remoteEnabled) {
			return;
		}
		if (client.player == null || client.world == null) {
			autoConfirm(client);
			autoLogin(client);
			return;
		}
		if (!started) {
			// 拿着"布景清理棒"时走另一条线：定期点一下左键，用来验证"左键点 NPC 才清掉"
			if (holdingCleanupWand(client)) {
				wandTick++;
				if (wandTick % 40 == 1) {
					TkGun.LOGGER.info("[tkgun-demo] 清理棒：点一下左键（tick {}）", wandTick);
					client.options.attackKey.setPressed(true);
				} else if (wandTick % 40 == 3) {
					client.options.attackKey.setPressed(false);
				}
				return;
			}
			// 等拿到枪再开始（单机自己发，联机由 RCON /item replace 发好）
			if (TkGun.holdingGun(client.player)) {
				started = true;
				tick = 0;
				releasedKeys = false;
				client.player.getInventory().selectedSlot = 0;
				TkGun.LOGGER.info("[tkgun-demo] 拿到武器，开始自动演示（第三人称）");
			} else {
				waitTicks++;
				// ⚠️ 2026-10-13：`/tkgun kit` 把炮塞在快捷栏 0，但玩家**当前选中的格子**可能是别的
				// （实测是熟牛排）——那样服务端看到的"主手"不是枪：右键会去啃牛排、左键的开火包被忽略，
				// 演示白跑（早先靠"副手也塞一把"绕过去，但那只验到 holdingGun，验不到主手那条链路）。
				// 这里按一下原版快捷栏 1 键（走 UpdateSelectedSlotC2SPacket，服务端才认）。
				if (waitTicks % 20 == 1) {
					client.options.hotbarKeys[0].setPressed(true);
				} else if (waitTicks % 20 == 3) {
					client.options.hotbarKeys[0].setPressed(false);
				}
				if (waitTicks % 100 == 0) {
					TkGun.LOGGER.info("[tkgun-demo] 等待手持破垒重炮（服务端用 /item replace 发到快捷栏 0）…");
				}
				return;
			}
		}
		tick++;
		if (tick == 5) {
			client.player.getInventory().selectedSlot = 0;
			client.options.hotbarKeys[0].setPressed(true);   // 让**服务端**也知道切到了槽 0
			// 抬头看天：不然右键会被准星指着的方块吃掉（村庄里到处是交互方块），
			// 物品 use 根本不会触发，看起来像"举枪没反应"。
			client.player.setPitch(-55.0F);
		}
		if (tick == 8) {
			client.options.hotbarKeys[0].setPressed(false);
		}
		// 背包界面（检查物品在 GUI 里长什么样）+ 定时截图
		if (tick == 26) {
			client.setScreen(new InventoryScreen(client.player));
		}
		// 把鼠标挪到快捷栏第 1 格的破垒重炮上 —— 这样工具提示会弹出来（LegendaryTooltips 的
		// 3D 模型预览就挂在提示框左上角，用户 2026-10-14 说的"左边预览的模型"就是它）。
		if (tick == 28 || tick == 30) {
			hoverSlot(client, 36);
		}
		if (tick == 32) {
			shot(client, "tkgun_gui_inventory.png");
		}
		if (tick == 34) {
			shot(client, "tkgun_gui_tooltip.png");
			client.setScreen(null);
		}
		// 截图工具/人手会抢焦点 → MC 弹暂停菜单，演示里直接关掉它，别让画面停在菜单上
		if (client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
			client.setScreen(null);
		}
		if (tick % 25 == 0 && (tick <= 350 || tick >= 400)) {
			shot(client, "tkgun_t" + tick + "_" + sanitize(TkGunClient.currentAnimation(client.player.getUuid())) + ".png");
		}
		// 可视化验收脚本：右键举枪，Z 切换专注；专注期间移动输入被屏蔽，Alt 进入翻滚/滑铲循环。
		// 实际状态转移以服务端状态机和 /tkgun status 为准。
		boolean use = (tick > 40 && tick <= 75) || (tick >= 293 && tick <= 320);
		boolean attack = (tick >= 125 && tick <= 127) || (tick >= 140 && tick <= 142) || (tick >= 150 && tick <= 152);
		boolean move = (tick > 200 && tick <= 230) || (tick > 260 && tick <= 300);
		//
		// ⚠️ 按键脚本只在 tick <= 330 这一段运行，跑完**必须把按过的键全部松开并停止接管**：
		// 以前这段是无条件每 tick 执行的，脚本结束后仍会把 W/疾跑/潜行/右键强制设成"没按"，
		// 于是玩家自己怎么按 W 都没反应 —— 用户原话："老是禁用我的 w 按键"（2026-10-12）。
		if (tick <= 330) {
			client.options.useKey.setPressed(use);
			client.options.attackKey.setPressed(attack);
			client.options.forwardKey.setPressed(move);
			// 疾跑/滑铲都要"人在动"才成立：光按疾跑键、不按前进键，原版不会进疾跑
			client.options.sprintKey.setPressed(tick > 270 && tick <= 300);
			client.options.sneakKey.setPressed(tick > 290 && tick <= 300);
		} else if (tick >= 400 && tick <= 520) {
			// —— 相机阶段（2026-10-14 用户要求：越肩视角 + 人物/摄像头解耦 + 右键转向 + 瞄准放大）——
			// 键位照旧的语义按（长按右键 = 瞄准），鼠标用 changeLookDirection 模拟（端到端走同一条路）。
			client.options.useKey.setPressed(tick >= 415 && tick <= 445);
			// 瞄准里也开两枪：验"指哪打哪"——服务端日志的 hitscan 方向应该带俯仰、落点≈准星落点
			client.options.attackKey.setPressed(tick >= 428 && tick <= 430 || tick >= 436 && tick <= 438);
			// 瞄准里插一段**前进**（425~443）：瞄准时身体和相机有个夹角，这时候走出来的方向
			// 才区分得开"相机相对"还是"身体相对"（都贴相机 = 解耦对了）；465~500 再来一段普通走动。
			client.options.forwardKey.setPressed((tick >= 425 && tick <= 443) || (tick >= 465 && tick <= 500));
			client.options.sprintKey.setPressed(false);
			client.options.sneakKey.setPressed(false);
			if (tick == 424) {
				lastCamMoveX = client.player.getX();
				lastCamMoveZ = client.player.getZ();
			}
			if (tick == 444) {
				logCamera(client, "⑤ 瞄准中按住 W（位移方向该贴**相机**，不贴身体）");
				assertCameraMove(client);
			}
			if (tick >= 400 && tick <= 410) {
				client.player.changeLookDirection(3.0D, 0.0D);      // 模拟"鼠标往右推"
			}
			if (tick == 412) {
				logCamera(client, "① 没瞄准：自由环视 30°（身体应该纹丝不动）");
				shot(client, "tkgun_cam1_free.png");
			}
			// ⑥ 瞄准落点的"转头过程"（用户 2026-10-14："那个实际落点是可以缓慢过去的"）：
			//    按住右键的同时猛甩一下鼠标 —— 落点（圈圈）**不会**瞬间贴到准星上，
			//    而是按 GunCamera 的转头速度慢慢转过去。这几张截图就该看到圈圈从枪管方向滑向准星。
			if (tick >= 415 && tick <= 421) {
				client.player.changeLookDirection(8.0D, 0.0D);      // 8°/tick × 7 tick ≈ 56° 的急甩
			}
			if (tick == 416 || tick == 419 || tick == 422 || tick == 426 || tick == 431 || tick == 445) {
				shot(client, "tkgun_aim_slew" + tick + ".png");
			}
			if (tick == 430) {
				logCamera(client, "② 长按右键中（身体应该正在追视角、FOV 应该收到 " + GunCamera.AIM_FOV + "）");
			}
			if (tick == 445) {
				logCamera(client, "③ 瞄准保持（落点应该已经转到位：圈圈和准星重合）");
				shot(client, "tkgun_cam2_aim.png");
			}
			if (tick >= 455 && tick <= 468) {
				client.player.changeLookDirection(-3.0D, 0.0D);     // 松开之后再转回来 42°
			}
			if (tick == 462) {
				lastCamMoveX = client.player.getX();
				lastCamMoveZ = client.player.getZ();
			}
			if (tick == 495) {
				assertCameraMove(client);
			}
			if (tick == 501) {
				logCamera(client, "④ 走动中（W 应该按**相机**方向走，身体朝移动方向）");
				shot(client, "tkgun_cam3_walk.png");
			}
		} else if (tick >= 560 && tick <= 1500) {
			// —— 四方向走动 + 移动开火 + 强化阶段（2026-10-14 第二版素材）——
			//
			// 要验的东西 / 对应的键位安排：
			//   620~660 按住 A（左移）+ 630 扣扳机 → 期望 rig=walk_left，**上半身叠 stand_shooting**（移动开火）
			//   720~760 按住 D（右移）+ 730 扣扳机 → 期望 rig=walk_right，同上
			//   840~880 按住 S（后退）+ 850 扣扳机 → 期望 rig=walk_back，同上
			//   1010    站定扣扳机 → 期望 **stand_shooting 叠层**（2026-10-15：没进专注时是行走射击）
			//   1050    通过 HUD 技能桥或 Z 切换专注 → 期望进入 AIM 系专注状态
			//   1085    站定扣扳机 → 期望 Focus_shooting[N]（架着炮精瞄，按落点高度取 _22.5/_45）
			//   1130    专注中按侧移并扣扳机 → 期望移动输入被屏蔽，只有射击逻辑执行
			// ⚠️ 2026-10-15：**扣扳机必须先举枪**（右键按住才算瞄准，见 TkGunServer#tryFire），
			//    所以这一段里把右键按住（以前这里是一律松开）。
			client.options.useKey.setPressed(
					(tick >= 600 && tick <= 1180) || (tick >= 1380 && tick <= 1490));
			client.options.sprintKey.setPressed(false);
			client.options.sneakKey.setPressed(false);
			client.options.forwardKey.setPressed(false);
			client.options.attackKey.setPressed(
					(tick >= 630 && tick <= 632) || (tick >= 730 && tick <= 732) || (tick >= 850 && tick <= 852)
							|| (tick >= 1010 && tick <= 1012) || (tick >= 1085 && tick <= 1087)
							|| (tick >= 1130 && tick <= 1132)
							// 仰角三档（1410 抬头 / 1440 半抬头 / 1470 平视）
							|| (tick >= 1410 && tick <= 1412) || (tick >= 1440 && tick <= 1442)
							|| (tick >= 1470 && tick <= 1472));
			client.options.leftKey.setPressed((tick >= 620 && tick <= 660) || (tick >= 1120 && tick <= 1160));
			client.options.rightKey.setPressed(tick >= 720 && tick <= 760);
			client.options.backKey.setPressed(tick >= 840 && tick <= 880);
			if (tick == 630) {
				TkGun.LOGGER.info("[tkgun-demo] ① 左移中开火（期望 walk_left 的腿 + stand_shooting 的上半身）");
			}
			if (tick == 730) {
				TkGun.LOGGER.info("[tkgun-demo] ② 右移中开火（期望 walk_right + stand_shooting）");
			}
			if (tick == 850) {
				TkGun.LOGGER.info("[tkgun-demo] ③ 后退中开火（期望 walk_back + stand_shooting）");
			}
			if (tick == 1010) {
				TkGun.LOGGER.info("[tkgun-demo] ④ 站定开火（期望腰射/行走射击：rig=idle|walk_* + 上半身 stand_shooting）");
			}
			if (tick == 1050) {
				// ⑤ 通过 HUD 技能桥进入专注；正常游戏也可直接按 Z 切换。
				boolean found = pressHudSkillKey(client, 1);
				TkGun.LOGGER.info("[tkgun-demo] ⑤ 按 hud 技能键进专注（找到 key.hud.skill1={}）；"
						+ "期望 rig=Focus_over + HUD『专注』 + 服务端『进入专注状态』", found);
			}
			if (tick == 1052) {
				releaseHudSkillKeys(client);
			}
			if (tick == 1085) {
				TkGun.LOGGER.info("[tkgun-demo] ⑥ 专注里开火（期望 Focus_shooting[N] + 仰角档，不再叠 stand_shooting）");
			}
			if (tick == 1130) {
				TkGun.LOGGER.info("[tkgun-demo] ⑦ 专注中侧移开火（期望移动输入被清零，专注射击继续）");
			}
			// —— 仰角变体（作者给每段射击都做了 基础/_22.5/_45）：抬头打的时候该切到 _45 ——
			// `changeLookDirection` 走的是原版鼠标那条路（解耦相机下 setPitch 会被 ShoulderSurfing 顶掉）。
			// ⚠️ 这里**必须用 `setPitch`**：实测 `changeLookDirection(0, dy)` 在这套输入环境下
			//    改不动俯仰角（2026-10-14 试过 -3 / -150 / +140 三档，实体俯仰纹丝不动），
			//    而 `setPitch` 一写就生效（-75 能稳定保持到开火那一 tick）。
			if (tick == 1400) {
				client.player.setPitch(-75.0F);                          // 抬头 75° → _45
			}
			if (tick == 1430) {
				client.player.setPitch(-25.0F);                          // 抬头 25° → _22.5
			}
			if (tick == 1460) {
				client.player.setPitch(0.0F);                            // 平视 → 不带后缀
			}
			if (tick >= 1400 && tick <= 1403) {
				client.player.changeLookDirection(0.0D, -150.0D);        // 大幅抬头（≈ -22°/tick）
			}
			if (tick == 1410) {
				TkGun.LOGGER.info("[tkgun-demo] ⑧ 抬头 {}° 开火（期望 Focus_shooting[N]_45）",
						Math.round(-client.player.getPitch()));
			}
			if (tick >= 1430 && tick <= 1433) {
				client.player.changeLookDirection(0.0D, 140.0D);         // 收回到 ~25°
			}
			if (tick == 1440) {
				TkGun.LOGGER.info("[tkgun-demo] ⑨ 抬头 {}° 开火（期望 _22.5）",
						Math.round(-client.player.getPitch()));
			}
			if (tick >= 1460 && tick <= 1463) {
				client.player.changeLookDirection(0.0D, 60.0D);          // 接近平视
			}
			if (tick == 1470) {
				TkGun.LOGGER.info("[tkgun-demo] ⑩ 抬头 {}° 开火（期望不带后缀）",
						Math.round(-client.player.getPitch()));
			}
		} else if (tick > 1500 && !releasedKeys) {
			// ⚠️ 收尾要等**相机阶段 + 走动/强化阶段都跑完**（tick 1200）：早先这里一过 330 就把键全松开、
			//    远程演示也关掉了，结果新加的相机阶段（400~520）根本轮不到跑。
			releaseKeys(client);
			releasedKeys = true;
			// 远程演示（RCON 打开的那种）跑完就自己关掉：免得留着一个一直在按键盘的脚本
			// 把玩家的 W / 疾跑 / 右键占住（2026-10-12 用户反馈过）。
			if (remoteEnabled) {
				remoteEnabled = false;
			}
			TkGun.LOGGER.info("[tkgun-demo] 脚本结束，已把自动按键全部松开（控制权还给玩家）");
		}
		// 连点（同一段里左键连按三下）= 验证"排队不覆盖"，已经并进上面的 attack 脚本
		if (tick == 340) {
			pressCombatRoll(client);
		}
		if (heldRollKey != null && tick >= releaseRollAt) {
			heldRollKey.setPressed(false);
			heldRollKey = null;
		}

		// 观察动画切换 -> 截图。
		// Prefer the authoritative GeckoLib rig state for animation-step screenshots.
		String anim = GunClientState.animationName(client.player.getId());
		if (anim == null) {
			anim = TkGunClient.currentAnimation(client.player.getUuid());
		}
		if (anim == null) {
			anim = TkGun.holdingGun(client.player) ? "(vanilla)" : "(none)";
		}
		if (!anim.equals(lastAnim)) {
			TkGun.LOGGER.info("[tkgun-demo] STEP t={} 动画 -> {}", tick, anim);
			lastAnim = anim;
			shotDelay = 6;
			shotName = "tkgun_" + tick + "_" + anim.replace(":", "_") + ".png";
		}
		if (shotDelay > 0 && --shotDelay == 0) {
			shot(client, shotName);
		}
	}

	private static String sanitize(String anim) {
		return anim == null ? "none" : anim.replace(":", "_");
	}

	private static int wandTick;

	/**
	 * 按一下 **hud mod 的技能键**（`key.hud.skill<N>`）。
	 *
	 * <p>为什么要这样：技能键是 hud mod 注册的 KeyBinding，tkgun 这边拿不到那个对象，
	 * 但 `client.options.allKeys` 里**所有**注册过的键位都在 —— 按翻译键找出来设成按下就行，
	 * hud 的客户端 tick 会照常走它那条"按键 → 查蓝/冷却 → 发包"的路。
	 */
	private static boolean pressHudSkillKey(MinecraftClient client, int slot) {
		String want = "key.hud.skill" + slot;
		boolean found = false;
		for (KeyBinding kb : client.options.allKeys) {
			if (want.equals(kb.getTranslationKey())) {
				kb.setPressed(true);
				found = true;
			}
		}
		return found;
	}

	/** 松开所有 hud 技能键（按一下就得放，不然 hud 那边会一直当按住）。 */
	private static void releaseHudSkillKeys(MinecraftClient client) {
		for (KeyBinding kb : client.options.allKeys) {
			String key = kb.getTranslationKey();
			if (key != null && key.startsWith("key.hud.skill")) {
				kb.setPressed(false);
			}
		}
	}

	/** 自动脚本按过 / 压过的键；脚本结束或远程关闭时全部松开，控制权还给玩家。 */
	private static boolean releasedKeys = true;

	private static void releaseKeys(MinecraftClient client) {
		if (client == null) {
			return;
		}
		client.options.useKey.setPressed(false);
		client.options.forwardKey.setPressed(false);
		client.options.sprintKey.setPressed(false);
		client.options.sneakKey.setPressed(false);
		client.options.attackKey.setPressed(false);
		releaseHudSkillKeys(client);
		if (heldRollKey != null) {
			heldRollKey.setPressed(false);
			heldRollKey = null;
		}
	}

	/** 主手是不是布景清理棒（按物品 id 判，不引 tkm 的类）。 */
	private static boolean holdingCleanupWand(MinecraftClient client) {
		if (client.player == null) {
			return false;
		}
		var stack = client.player.getMainHandStack();
		if (stack.isEmpty()) {
			return false;
		}
		return String.valueOf(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()))
				.equals("tkm:cleanup_wand");
	}

	/**
	 * 把真实光标挪到某个 GUI 槽位上（走反射：`Mouse.x/y` 是 private，`HandledScreen.x/y` 是 protected）。
	 * `HandledScreen.render` 每帧用真实鼠标坐标算 `focusedSlot`，所以挪了光标工具提示就会弹。
	 * 槽位用**玩家背包的槽号**（36 = 快捷栏第 1 格），坐标 = 屏幕原点 + 槽位自身坐标 + 8（槽心）。
	 */
	private static void hoverSlot(MinecraftClient client, int handlerIndex) {
		if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
			return;
		}
		try {
			Slot slot = screen.getScreenHandler().getSlot(handlerIndex);
			int ox = intField(screen, "x");
			int oy = intField(screen, "y");
			double lx = ox + slot.x + 8.0;
			double ly = oy + slot.y + 8.0;
			// Mouse.x/y 存的是窗口原始像素 -> 按 GUI 缩放折算回去
			double fx = (double) client.getWindow().getWidth() / client.getWindow().getScaledWidth();
			double fy = (double) client.getWindow().getHeight() / client.getWindow().getScaledHeight();
			setDouble(client.mouse, "x", lx * fx);
			setDouble(client.mouse, "y", ly * fy);
			TkGun.LOGGER.info("[tkgun-demo] 光标悬停槽 {} 逻辑=({}, {}) 设备=({}, {}) 物品={}",
					handlerIndex, lx, ly, lx * fx, ly * fy, slot.getStack().getItem());
		} catch (Throwable t) {
			TkGun.LOGGER.warn("[tkgun-demo] 悬停槽 {} 失败: {}", handlerIndex, t.toString());
		}
	}

	private static int intField(Object o, String name) throws Exception {
		return field(o, name, INT_ALIASES).getInt(o);
	}

	private static void setDouble(Object o, String name, double value) throws Exception {
		field(o, name, DOUBLE_ALIASES).setDouble(o, value);
	}

	// ⚠️ **线上跑的是 intermediary 名**：反射写 yarn 名（x / y）会抛 NoSuchFieldException
	// （2026-10-14 实测："悬停槽 36 失败: java.lang.NoSuchFieldException: x"）。
	// 名字来自 `moddev\tools\yarn-1.21.1.tiny`：Mouse.x=field_1795, y=field_1794,
	// HandledScreen.x=field_2776, y=field_2800。两个名字都试，开发/线上都能跑。
	private static final String[][] INT_ALIASES = {{"x", "field_2776"}, {"y", "field_2800"}};
	private static final String[][] DOUBLE_ALIASES = {{"x", "field_1795"}, {"y", "field_1794"}};

	private static Field field(Object o, String name, String[][] aliases) throws Exception {
		String[] names = {name};
		for (String[] pair : aliases) {
			if (pair[0].equals(name)) {
				names = pair;
			}
		}
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			for (String n : names) {
				try {
					Field f = c.getDeclaredField(n);
					f.setAccessible(true);
					return f;
				} catch (NoSuchFieldException ignored) {
					// 换下一个名字 / 往父类找
				}
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void shot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
				msg -> TkGun.LOGGER.info("[tkgun-demo] 截图 {} ({})", name, msg.getString()));
	}

	// ---------------------------------------------------------------- 相机阶段的小工具
	/** 相机阶段上一次记的位置（验"W 是按相机方向走"）。 */
	private static double lastCamMoveX;
	private static double lastCamMoveZ;

	/** 打一行"越肩/解耦"的实测数据（视角、身体、差、FOV 系数），日志里一眼能看出对不对。 */
	private static void logCamera(MinecraftClient client, String what) {
		TkGun.LOGGER.info("[tkgun-demo] {}：相机 yaw={} 身体 yaw={} 差={}° 瞄准={} FOV系数={} 视角={} 朝向={}",
				what, round(GunCamera.lookYaw()), round(client.player.getYaw()),
				round(net.minecraft.util.math.MathHelper.wrapDegrees(
						GunCamera.lookYaw() - client.player.getYaw())),
				GunCamera.aiming(client), round(GunCamera.fovScale()),
				client.options.getPerspective(), dirOf(client));
	}

	/**
	 * 验"走动 = 相机相对"：这一段按的是 W，所以世界位移方向应该 ≈ **视角 yaw**
	 * （如果输入没被扭回身体坐标系，就会是**身体 yaw** —— 那说明解耦那条数学错了）。
	 */
	private static void assertCameraMove(MinecraftClient client) {
		double dx = client.player.getX() - lastCamMoveX;
		double dz = client.player.getZ() - lastCamMoveZ;
		double moved = Math.sqrt(dx * dx + dz * dz);
		double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
		TkGun.LOGGER.info("[tkgun-demo] 走动验算：实际位移={} 格 位移方向 yaw={}° / 相机 yaw={}° / 身体 yaw={}°"
						+ "（位移方向该贴相机；贴身体就说明输入没扭对）",
				round(moved), round(moveYaw), round(GunCamera.lookYaw()),
				round(client.player.getYaw()));
	}

	private static String dirOf(MinecraftClient client) {
		var v = client.player.getRotationVec(1.0F);
		return "(" + round(v.x) + ", " + round(v.z) + ")";
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	/**
	 * 自动点「登 录」（只在 -Dtkgun.demo=1 的开发演示里跑）。
	 *
	 * TkAuth 的登录页按用户要求是"填好账号密码、但让人自己点登录"，所以自动化演示必须
	 * 补这一下，否则客户端会一直停在登录页。这里不引 TkAuth 的类（不是依赖），只按按钮
	 * 文案找。同一个 Screen 实例只点一次，避免反复触发。
	 */
	private static Object lastLoginScreen;
	/**
	 * ⚠️ 登录只自动点，但要**限流**：同一个 Screen 实例只点一次，两次点击至少隔 5 秒，最多 6 次。
	 * （早先那版是"整局只点一次"，结果 quickplay 被 tkauth 踢回来后偶发点不中 → 客户端一直卡在登录页，
	 *  2026-10-12 因此白等过好几轮；无限重试又会变成一秒十几个连接把服务端刷成
	 *  `lost connection: Disconnected`，两头都不能要。）
	 */
	private static int loginAttempts;
	private static long lastLoginClickAt;

	private static boolean autoLogin(MinecraftClient client) {
		Screen screen = client.currentScreen;
		// 同一个屏幕实例也允许**重试**（点不中是很常见的：quickplay 被踢回来那一瞬间按钮还没就绪），
		// 靠上面的 5 秒节流 + 6 次上限兜住，不会变成刷连接。
		if (screen == null || loginAttempts >= 6) {
			return false;
		}
		// ⚠️ 2026-10-14：带着 `-Dtk.dev.autologin` 起的时候（launch_client.py --dev）
		//    TkAuth 自己会登录进服，这里再点一次 = **同一个账号两条连接** → 服务端把先到的那条踢掉
		//    （实测 `lost connection: Disconnected`，然后卡在登录页）。有那个参数就交给它，别插手。
		if (System.getProperty("tk.dev.autologin") != null) {
			return false;
		}
		// ⚠️ 2026-10-13 实测：5 秒节流太短 —— 连接要 8 秒左右才进世界，第二次点击会
		//    又发一条登录（同一个账号第二个连接），服务端把前一个踢掉，客户端拿到
		//    "你已在异地登录"直接掉线。拉到 20 秒后就不会点了重复的。
		if (System.currentTimeMillis() - lastLoginClickAt < 20000L) {
			return false;
		}
		for (Element e : screen.children()) {
			if (e instanceof ButtonWidget b) {
				String label = b.getMessage().getString().replace(" ", "");
				if (label.contains("登录") || label.toLowerCase().contains("login")) {
					lastLoginScreen = screen;
					loginAttempts++;
					lastLoginClickAt = System.currentTimeMillis();
					TkGun.LOGGER.info("[tkgun-demo] 自动点「{}」进服", b.getMessage().getString());
					b.onPress();
					return true;
				}
			}
		}
		// TkAuth 的登录页是**即时模式**手绘按钮（不是 ButtonWidget），只能按它的布局算坐标点。
		// 布局：PANEL_W=320，panelX=(w-320)/2，panelY=max(28, h/2-121-12)；
		//       登录按钮在 (panelX+34, panelY+190)，尺寸 118x24。
		String cls = screen.getClass().getName();
		if (cls.contains("LoginScreen")) {
			int panelX = (screen.width - 320) / 2;
			int panelY = Math.max(28, screen.height / 2 - 121 - 12);
			int x = panelX + 34 + 59;
			int y = panelY + 190 + 12;
			lastLoginScreen = screen;
			loginAttempts++;
			lastLoginClickAt = System.currentTimeMillis();
			TkGun.LOGGER.info("[tkgun-demo] 自动点登录按钮 @({},{}) [{}]", x, y, cls);
			screen.mouseClicked(x, y, 0);
			return true;
		}
		return false;
	}

	private static void pressCombatRoll(MinecraftClient client) {
		// 先找 combat_roll 自己的键位（bettercombat 的 feint 之类也带 "combat"，别点错）
		for (KeyBinding key : client.options.allKeys) {
			String t = key.getTranslationKey();
			if (t.contains("combat_roll") || t.contains("combatroll") || t.contains("combat.roll")) {
				key.setPressed(true);
				heldRollKey = key;
				releaseRollAt = tick + 6;
				TkGun.LOGGER.info("[tkgun-demo] 按下翻滚键 {}", t);
				return;
			}
		}
		for (KeyBinding key : client.options.allKeys) {
			if (key.getTranslationKey().contains("combat")) {
				key.setPressed(true);
				heldRollKey = key;
				releaseRollAt = tick + 6;
				TkGun.LOGGER.info("[tkgun-demo] 按下翻滚键 {}", key.getTranslationKey());
				return;
			}
		}
		TkGun.LOGGER.warn("[tkgun-demo] 没找到 combat_roll 的键位");
	}

	/** 测试世界带"实验性世界设置"确认框，自动点第一个按钮（= 继续加载）。 */
	private static boolean autoConfirm(MinecraftClient client) {
		if (client.currentScreen instanceof ConfirmScreen screen) {
			for (Element e : screen.children()) {
				if (e instanceof ButtonWidget b) {
					TkGun.LOGGER.info("[tkgun-demo] 自动确认对话框：「{}」", b.getMessage().getString());
					b.onPress();
					return true;
				}
			}
		}
		return false;
	}

	private GunDebugHarness() {
	}
}
