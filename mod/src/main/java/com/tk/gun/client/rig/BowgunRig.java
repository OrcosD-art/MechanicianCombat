package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunParticles;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.GunCamera;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用 GeckoLib 播 711 那套**作者原始**动画的玩家 rig（不再走 playeranimator）。
 *
 * <p>为什么换：playeranimator 只能做旋转 + bend，**不支持骨骼位移/缩放**，而作者这套动画
 * 大量依赖 position（下蹲 -8、架枪的位移）和 scale（枪口焰/速度线的显隐）——
 * 表现就是用户说的"玩家高度不变化、没有坐下架枪、关节不对"。GeckoLib 这些全支持，
 * 而且是拿 bbmodel 里作者原封不动的动画（骨名 = geo 骨名），不用做任何重定向。
 *
 * <p>动画名与 {@code assets/tkgun/animations/entity/bowgun_rig.animation.json} 里的键一一对应。
 */
public final class BowgunRig implements GeoReplacedEntity {
	public static final BowgunRig INSTANCE = new BowgunRig();
	/** 无操作后继续保留腰射上半身姿势的 tick 数（3 秒）。 */
	/** playerId → 最近一次持枪/移动/开火操作的 world tick。 */

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
		registrar.add(new AnimationController<>(this, "rig", 1, this::pick)
				.setAnimationSpeedHandler(rig -> {
					double speed = animationSpeed();
					logSwitch(currentAnimationName, speed);
					return speed;
				})
				.setSoundKeyframeHandler(BowgunRig::onSoundKeyframe)
				.setCustomInstructionKeyframeHandler(BowgunRig::onCustomKeyframe)
				.setParticleKeyframeHandler(BowgunRig::onParticleKeyframe));
		// **第二条控制器：只做"移动开火"的上半身**（作者的 `stand_shooting` 里**没有**腿和躯干的通道，
		// 就是给"腿在走路、上半身在开火"这种叠层用的 —— 2026-10-14 用户点名要的"移动开火"）。
		// GeckoLib 4.8 的多控制器语义：所有控制器都往同一批骨骼上写，后注册的覆盖先注册的；
		// 最后统一把"这一帧没有任何动画碰过的骨骼"拉回绑定姿势（见 AnimationProcessor#tickAnimation）。
		// 所以上层只写上半身、下层照旧写全身，合并出来的就是"边走边打"。
		registrar.add(new AnimationController<>(this, "upper", 1, this::pickUpper)
				.setAnimationSpeedHandler(rig -> {
					AbstractClientPlayerEntity player = CURRENT_PLAYER;
					return player != null
							&& com.tk.gun.client.MeleeImpactClientState
									.freezesAnimation(player.getId()) ? 0.0D : 1.0D;
				})
				// 上半身动画是从完整动画派生的，仍带有原动画的声音/timeline/粒子标记。
				// 这些事件由主 rig 控制器或服务端处理；这里显式吸收，避免重复播放和日志刷屏。
				.setSoundKeyframeHandler(BowgunRig::onUpperSoundKeyframe)
				.setCustomInstructionKeyframeHandler(event -> { })
				.setParticleKeyframeHandler(BowgunRig::onParticleKeyframe));
		// New guns may only author deploy/stow clips for their own gun subtree. This
		// overlay keeps the gun at the correct terminal pose while inherited body,
		// locomotion and shooting animations continue to play.
		registrar.add(new AnimationController<>(this, "gun_pose", 0, this::pickGunPose)
				.setSoundKeyframeHandler(event -> { })
				.setCustomInstructionKeyframeHandler(event -> { })
				.setParticleKeyframeHandler(event -> { }));
		// ⚠️ 2026-10-17 **"hands" 控制器已删除**（用户："撤销关于双手 / 身体切分 + 多段旋转的效果"）。
		// 原因见 `pickUpper` 里的注释：那条控制器在换弹时会 STOP，把 `reload_upper` 的手臂动作复位掉。
		// **第三组：每根弹壳各一个控制器**（用户 2026-10-17："将资源文件夹中的 shells 组（原名 bone2）
		// 下所有动画单独渲染，否则会出现连射是弹壳动画未落地被下一个射击动画覆盖的问题"）。
		//
		// 为什么必须分开：作者的每发动画只写**同一根** `shell` 骨，抛壳全程 0.875 秒，而连射最快
		// 0.25 秒一发 —— 下一发一重播，"这一段没有通道的骨骼"就被拉回初始位，弹壳"啪"地回弹夹。
		// 现在每根弹壳（shell..shell7）**各自一个控制器**、各自播自己的 `shell_eject_<k>`：
		// 后注册的控制器按骨骼覆盖 rig 那条（所以主射击动画里那根 `shell` 通道不再说话），
		// 而 7 个控制器之间互不干扰 → 每根弹壳的抛出都能跑完自己那 0.875 秒。
		// 导出脚本（`derive_shell_animations`）已经给每根弹壳生成了 `shell_eject_<k>` / `shell_hold_<k>`。
		for (int i = 0; i < SHELL_COUNT; i++) {
			final int idx = i;
			registrar.add(new AnimationController<>(this, "shell" + (i + 1), 0,
					state -> pickShell(state, idx))
					// 弹壳只负责骨骼运动；音效与 timeline 仍由主射击动画处理。
					.setSoundKeyframeHandler(event -> { })
					.setCustomInstructionKeyframeHandler(event -> { })
					.setParticleKeyframeHandler(event -> { }));
		}
	}

	/**
	 * Spawns the authored particle effect at the model's locator. The current source
	 * places `particles` on `Overheat` at 0.25 seconds.
	 */
	private static void onParticleKeyframe(
			software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent<BowgunRig> event) {
		String effect = event.getKeyframeData().getEffect();
		AbstractClientPlayerEntity player = CURRENT_PLAYER;
		if (effect == null || player == null || player.getWorld() == null) {
			return;
		}
		if ("energy_converge".equals(effect)) {
			String locatorName = event.getKeyframeData().getLocator();
			String attachedLocator = locatorName == null ? "aim" : locatorName;
			net.minecraft.util.math.Vec3d locator = "aim".equals(attachedLocator)
					? BowgunRigRenderer.aimPosition(player.getId())
					: BowgunRigRenderer.locatorPosition(player.getId());
			if (locator != null) {
				if (!com.tk.gun.client.EnergyParticle.spawnAttached(
						player, locator, attachedLocator)) {
					player.getWorld().addParticle(TkGunParticles.ENERGY_CONVERGE,
							locator.x, locator.y, locator.z,
							0.0D, 0.0D, 0.0D);
				}
			}
			return;
		}
		if ("energy_xplo".equals(effect)) {
			String locatorName = event.getKeyframeData().getLocator();
			net.minecraft.util.math.Vec3d locator = "aim".equals(locatorName)
					? BowgunRigRenderer.aimPosition(player.getId())
					: BowgunRigRenderer.locatorPosition(player.getId());
			if (locator != null) {
				player.getWorld().addParticle(TkGunParticles.ENERGY_XPLO,
						locator.x, locator.y, locator.z, 0.0D, 0.0D, 0.0D);
			}
			return;
		}
		if ("thunder".equals(effect)) {
			String locatorName = event.getKeyframeData().getLocator();
			net.minecraft.util.math.Vec3d locator = "aim".equals(locatorName)
					? BowgunRigRenderer.aimPosition(player.getId())
					: BowgunRigRenderer.locatorPosition(player.getId());
			if (locator != null) {
				player.getWorld().addParticle(TkGunParticles.THUNDER,
						locator.x, locator.y, locator.z, 0.0D, 0.0D, 0.0D);
				TkGun.LOGGER.info("[tkgun] ATK_3 thunder particle -> locator（玩家 {}）",
						player.getName().getString());
			}
			return;
		}
		if ("particles".equals(effect)) {
			net.minecraft.util.math.Vec3d locator =
					BowgunRigRenderer.locatorPosition(player.getId());
			if (locator != null) {
				boolean quietStorm = TkGun.definition(player).id()
						.equals(TkGun.id("abyssal_ancient_cannon"));
				if (quietStorm) {
					com.tk.gun.client.LocatorShriekBurst.start(player.getId(),
							com.tk.gun.client.LocatorShriekBurst.Source.GUN, locator);
				} else if (TkGun.definition(player).id()
						.equals(TkGun.id("veritas_et_justitia"))) {
					// Drive Veritas heat audio from the same event that starts
					// the authored heat animation, rather than a parallel sound key.
					reportServerSoundKeyframe("vent");
				} else {
					if (!com.tk.gun.client.HeatVentParticle.spawnAttached(player, locator)) {
						player.getWorld().addParticle(TkGunParticles.HEAT_VENT,
								locator.x, locator.y, locator.z, 0.0D, 0.02D, 0.0D);
					}
				}
				TkGun.LOGGER.info("[tkgun] 动画粒子关键帧 -> locator（玩家 {}）",
						player.getName().getString());
			}
		}
	}

	/**
	 * 素材里作者在动画上埋了音效关键帧（`Focus_shooting*` 的 0.0417s = `ding`、`roll` = `roll`、
	 * `sliding` 的 0.25s = `steam`）。GeckoLib 不注册 handler 就只打一句 WARN 什么都不放。
	 *
	 * <p>2026-10-16 素材 v4 起，这些关键帧在导出时被**改名成我们认识的音效名**
	 * （`tools/gen_geckolib_mech.py` 的 `postprocess_animations`，按作者配的 ogg 文件对）：
	 * `shot` / `shot2` / `mag` / `reload` / `reload2` / `empty` / `vent` / `bolt` / `roll` / `steam`。
	 *
	 * <p>这里按我们自己的时机安排（**避免和"服务端已经放过的音"重复**）：
	 * <ul>
	 *   <li>`shot` / `shot2` / `empty` —— **跳过**：每发的炮声由服务端在**出伤帧**放
	 *       （`TkGunServer.playShotSound()` 还要叠原版低频/金属层），客户端再放一遍就是双份。</li>
	 *   <li>`reload` / `reload2` / `mag` / `bolt` —— **跳过**：这几声全在服务端的状态机时机上放
	 *       （`TkGunServer.reload` / `ejectMag` / `boltAt`），服务端放**附近所有人都能听见**，
	 *       客户端放只有自己听得见 —— 两份一起响还会互相打架。</li>
	 *   <li>`roll` / `steam` —— 这俩服务端没放，照作者意图由客户端补上（滑铲/翻滚是纯动作表现）。</li>
	 * </ul>
	 */
	private static void onSoundKeyframe(
			software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent<BowgunRig> event) {
		String key = event.getKeyframeData().getSound();
		if (key == null) {
			return;
		}
		// ⚠️ 只报**本地玩家自己**的关键帧：这套 rig 是所有手持弩炮的玩家共用的渲染器/控制器，
		// 别人的关键帧会被服务端当成"**我们**放了这一声"放到我们身上。别人那一声应该由**他们的客户端**
		// 报给服务端、服务端再广播给我们 —— 这正是本轮（关键帧音改走服务端）要的机制。
		AbstractClientPlayerEntity who = CURRENT_PLAYER;
		if (who == null || who != net.minecraft.client.MinecraftClient.getInstance().player) {
			return;
		}
		// ★ **专注那几段：照模型引用的音频放**（用户 2026-10-17："将专注相关的音效替换为…模型所引用的
		//   音频"），而且改**由服务端广播**（"…使旁观者也能听见"）——时间仍是作者埋的关键帧时间。
		if (reportFocusSoundKeyframe(key)) {
			return;
		}
		if (SKILL_SOUND_ANIMS.contains(currentAnimationName)) {
			reportServerSoundKeyframe(key);
			return;
		}
		switch (key) {
			case "roll" -> TkGunClient.sendSoundKeyframe(key);
			case "steam" -> TkGunClient.sendSoundKeyframe(key);
			default -> {
				if (VERBOSE_DEMO) {
					TkGun.LOGGER.info("[tkgun] 动画音效关键帧 {}（服务端那边会放同名音，客户端不重复）", key);
				}
			}
		}
	}

	/** Normal melee clips run on the upper controller, so forward only their authored cues. */
	private static void onUpperSoundKeyframe(
			software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent<BowgunRig> event) {
		String key = event.getKeyframeData().getSound();
		if (key == null || upperName == null) {
			return;
		}
		String upperBase = upperName.endsWith("_upper")
				? upperName.substring(0, upperName.length() - "_upper".length())
				: upperName;
		if (SKILL_SOUND_ANIMS.contains(upperBase)) {
			if (CURRENT_PLAYER == net.minecraft.client.MinecraftClient
					.getInstance().player) {
				reportServerSoundKeyframe(key);
			}
			return;
		}
		if (!MELEE_SOUND_ANIMS.contains(upperName)) {
			return;
		}
		// ATK_hold_over audio is driven by the authoritative server timeline.
		if ("ATK_hold_over".equals(upperName)) {
			return;
		}
		AbstractClientPlayerEntity who = CURRENT_PLAYER;
		if (who == net.minecraft.client.MinecraftClient.getInstance().player) {
			reportServerSoundKeyframe(key);
		}
	}

	private static final Set<String> MELEE_SOUND_ANIMS =
			Set.of("ATK_1", "ATK_2", "ATK_3", "ATK_3_hold", "ATK_4", "ATK_hold_over");
	private static final Set<String> SKILL_SOUND_ANIMS =
			Set.of("skill_load", "gather", "ready_hit", "hit");

	/** **专注那几段**（"照模型引用的音频放"的动画名，见 {@link #reportFocusSoundKeyframe}）。 */
	private static final Set<String> FOCUS_SOUND_ANIMS = Set.of(
			"Focus_ready", "fast_ready", "Focus_over",
			"Focus_shooting1", "Focus_shooting4", "Focus_shooting6", "Focus_shooting7",
			"Focus_shooting_extra",
			"Focus_over_shooting", "Overheat");

	/**
	 * **专注相关的音效 = 作者在模型里配的那几个音频**（用户 2026-10-17："将专注相关的音效替换为
	 * …模型所引用的音频"）。
	 *
	 * <p>做法是**信关键帧**：这几段动画在 bbmodel 里就配着音效关键帧（导出时按 `SOUND_NAMES` 换成
	 * 我们认的名字，见 `tools/gen_geckolib_mech.py#postprocess_animations`），GeckoLib 到点就叫这里 ——
	 * **时间就是作者埋的那个时间**，动画倍速（进专注 0.83x / 快档 2x / 滑铲抢枪 3x）会自动跟着缩放，
	 * 不用在 Java 里手写一堆偏移。
	 *
	 * <p>模型引用的全部（`Focus_*` / `Overheat`）：
	 * <pre>
	 * Focus_ready            0.25s   栓声（bolt）
	 * fast_ready             0.0833s 栓声
	 * Focus_over             0.25s 换弹 + 0.375s 换弹2      ← 以前这里**一声都没有**
	 * Focus_shooting[2..7]   0.0417s 开火关键帧（**跳过**：服务端在出伤帧统一放枪击3）
	 * Focus_shooting7        0.1667s 空弹仓（专注弹匣最后一发的卸匣动作）
	 * Focus_over_shooting    0.0417s…2.5s 开火关键帧 ×10（跳过）+ 2.5833s 弹匣 + 3.0s 散热
	 * Overheat               0.4167s 散热
	 * </pre>
	 *
	 * <p>服务端那边对应的"原版 / 合成"音已经删掉（`startFocus` 的原版铁砧 + 压低音高的栓声、
	 * `startSlideBrace` 的栓声、`startOverheat` 的原版灭火声与那声即时 `vent`），所以不会打架。
	 * `shot2` 照旧跳过 —— 每发的炮声由服务端在**出伤帧**统一放。
	 *
	 * <p>**放音交给服务端**（用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见"）：这里只把
	 * 关键帧的**名字**报上去（{@link com.tk.gun.net.GunSoundPayload}），服务端查
	 * {@code TkGun#KEYFRAME_SOUNDS} 在玩家位置放 —— `ServerWorld#playSound` 会广播给范围内所有玩家，
	 * **自己也会收到那一份**，所以客户端这边不再本地补一声。音量/音高随表走（素材原样 1.0/1.0）。
	 *
	 * @return true = 这一声归这里管（调用方别再走下面的 switch）
	 */
	private static boolean reportFocusSoundKeyframe(String key) {
		if (!FOCUS_SOUND_ANIMS.contains(currentAnimationName)) {
			return false;
		}
		// 只认表里那几个名字（`TkGun#KEYFRAME_SOUNDS`，服务端用同一张表查）——`shot`/`shot2` 不在表里：
		// 那两声由服务端在**出伤帧**放，客户端不报。
		if (!TkGun.KEYFRAME_SOUNDS.containsKey(key)) {
			return false;
		}
		// ⚠️ `bolt` 在 `Focus_ready` / `fast_ready` 里**由服务端排**（`TkGunServer` 的 `aimSoundAt`，
		//    时间点就是作者埋在 0.25s / 0.0833s 的那一拍）—— 这里跳过，不然旁观者会听到两声。
		if ("bolt".equals(key) && ("Focus_ready".equals(currentAnimationName)
				|| "fast_ready".equals(currentAnimationName))) {
			return false;
		}
		// **报给服务端去放**（用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见"）：
		// 时间仍是这里的关键帧时间，但 `ServerWorld#playSound` 会广播给范围内所有玩家 ——
		// **连自己也会收到那一份**，所以客户端这边不要再补一声（补了就是双份）。
		return reportServerSoundKeyframe(key);
	}

	private static boolean reportServerSoundKeyframe(String key) {
		if (!TkGun.KEYFRAME_SOUNDS.containsKey(key)) {
			return false;
		}
		TkGunClient.sendSoundKeyframe(key);
		return true;
	}

	/** 最近一次 pick 选的动画名 + 打日志用（验收"待机=idle / 部署快慢 / 开火"看客户端日志即可）。 */
	private static String currentAnimationName = "-";

	/** 当前 rig 控制器在播哪一段（渲染器判"这段要不要跟随视角"要用）。 */
	public static String currentAnimation() {
		return currentAnimationName;
	}

	/** Animation layers affecting the current player's body in this GeckoLib render pass. */
	public static java.util.Set<String> currentPlayerPoseAnimations() {
		java.util.Set<String> names = new java.util.LinkedHashSet<>();
		if (currentAnimationName != null && !currentAnimationName.isBlank()) {
			names.add(currentAnimationName);
		}
		if (upperName != null && !upperName.isBlank()) {
			names.add(upperName);
		}
		return names;
	}

	public static String currentUpperAnimation() {
		return upperName;
	}
	private static String lastLogged = "";
	/** 演示模式（`-Dtkgun.demo=1`）才打这类诊断日志。 */
	private static final boolean VERBOSE_DEMO =
			System.getProperty("tkgun.demo") != null || System.getProperty("tkgun.verbose") != null;

	private static void logSwitch(String name, double speed) {
		String line = name + "（倍速 " + speed + "）";
		if (!line.equals(lastLogged)) {
			lastLogged = line;
			TkGun.LOGGER.info("[tkgun] rig -> {}", line);
		}
	}

	/** 播动画的统一入口（顺便记下名字给日志）。 */
	private static PlayState play(AnimationState<BowgunRig> state, String name) {
		currentAnimationName = name;
		// ⚠️ 这里必须是 setAndContinue，别写成 play(...)：2026-10-12 批量替换把这一行也替换掉了，
		// 结果 play() 自己调自己 → 客户端一进服 StackOverflow 崩服（20:06~20:26 所有"进不去"都是它）。
		return state.setAndContinue(rawFor(name));
	}

	/**
	 * 动画倍速：**部署**（Focus_ready）按状态变速 —— 普通右键慢（0.83 倍 ≈ 1.2s），
	 * 滑铲后/蹲着右键快（2 倍 ≈ 0.5s）；其余动画 1 倍。
	 * 倍速和 {@link com.tk.gun.TkGun#AIM_TICKS_NORMAL}/{@link com.tk.gun.TkGun#AIM_TICKS_FAST} 是同一套数。
	 *
	 * <p>⚠️ 这里读的是**当前正在渲染的玩家**（{@link #CURRENT_PLAYER}，由 BowgunRigClient 每帧设置）——
	 * 因为 GeoReplacedEntity 是单例，拿不到"这是谁"。用静态字段够了：同一帧内只有一个玩家在画。
	 */
	private static double animationSpeed() {
		AbstractClientPlayerEntity player = CURRENT_PLAYER;
		if (player == null) {
			return 1.0D;
		}
		if (com.tk.gun.client.MeleeImpactClientState
				.freezesAnimation(player.getId())) {
			return 0.0D;
		}
		if (currentAnimationName != null && currentAnimationName.startsWith("walk_")) {
			return TkGunClient.walkAnimationSpeed(player.getId());
		}
		// 架枪三档（慢/快/滑铲抢枪）都写在 GunClientState.speedFor 里，和 TkGun.AIM_TICKS_* 一套数
		return GunClientState.speedFor(player.getId());
	}

	/** 当前正在渲染的玩家（BowgunRigClient 每帧设；动画倍速要用）。 */
	public static AbstractClientPlayerEntity CURRENT_PLAYER;

	/**
	 * 作者还在射击动画里埋了 **timeline（自定义指令）关键帧**（`Focus_shooting*` 0.0417s 的 `script: "shoot"`）。
	 * 不注册 handler 时 GeckoLib 会打 `Custom Instruction Keyframe found ... but no keyframe handler registered`。
	 *
	 * <p>它标的就是**出伤帧**（0.04167s = 1 tick），但开火这件事**必须由服务端/客户端那条链路做**
	 * （枪口坐标是渲染时量的、伤害是服务端结算的），这里注册只是为了消警告 + 演示模式留一条证据行。
	 */
	private static void onCustomKeyframe(
			software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent<BowgunRig> event) {
		String script = event.getKeyframeData().getInstructions();
		if (VERBOSE_DEMO) {
			TkGun.LOGGER.info("[tkgun] 动画指令关键帧 -> {}（出膛时刻，实际开火由帧包/服务端管）", script);
		}
	}

	/**
	 * **上半身叠层**（第二条控制器）：**没进专注（架枪）时，只要举着枪（右键按住）就接管上半身**。
	 *
	 * <p>播作者的 `stand_shooting`：它只写 chest / head / arm_left / arm_right / gun / 枪口焰 /
	 * 弹壳 / 速度线 —— 腿和躯干一根都不碰（`body`/`leg_*` 完全没有通道），正好盖在"走路那条动画"上面。
	 *
	 * <p>两件事都用它（2026-10-15 用户："非架枪情况下长按右键就进入持枪行走的动画；按住右键再按左键
	 * 就播那个站立射击的动画、**只用它的上半身**，因为上下是分开的"）：
	 * <ol>
	 *   <li>**举枪（右键按住，没进专注）**：播一遍 `stand_shooting` 并**停在最后一帧** ——
	 *       那就是作者给的"端枪"上半身姿势，腿还在走 walk_*（= 持枪行走）；</li>
	 *   <li>**开火**：每发把它**从头重播**（枪口焰/后坐/弹壳都在里面），所以还是同一个动画。</li>
	 * </ol>
	 * 区别只在于"这一遍是不是真的开火"：不是开火那几帧里枪口焰/速度线/弹壳由渲染器压掉
	 * （见 {@code BowgunRigRenderer} 的 READY 处理），不然举枪那一下会"空放一发"。
	 *
	 * <p>每发都要"重来一遍"（后坐/枪口焰/弹壳）：服务端每一发都会重播一次 FIRE 状态，
	 * 这里跟 {@code e.elapsed <= 1} 对齐给一个新实例（同一份 RawAnimation 实例 GeckoLib 不会重播）。
	 */
	private PlayState pickUpper(AnimationState<BowgunRig> state) {
		Entity entity = entityOf(state);
		String name = entity == null ? null : upperAnimationFor(entity);
		state.setControllerSpeed(1.0F);
		// 诊断（用户 2026-10-17："蹲射结束 / 冲刺窗口 + 疾跑 + 装填时上半身换弹动作不完整"）：
		// 换弹还没结束时上层就离开 `reload_upper` = 动画被切断。正常这段里名字应该一直是它。
		if (entity != null && "reload_upper".equals(upperName) && !"reload_upper".equals(name)) {
			GunClientState.Entry now = GunClientState.get(entity.getId());
			if (now != null && now.state == TkGun.State.RELOAD) {
				TkGun.LOGGER.info("[tkgun] 换弹中途上层离开 reload_upper -> {}（elapsed={}/{}）玩家={}",
						name, now.elapsed, now.total, entity.getName().getString());
			}
		}
		if (name == null) {
			upperName = null;             // 收枪/进专注/站着不动：下一次要重新起手
			// ⚠️ 顺手把"上一次打过的 tag"也清掉：不然下一段叠层恰好和它同名时，那一行日志会被
			//    "变化才打"的条件吞掉 —— 2026-10-17 排查"换弹动画不完整"时就被这个坑过一次
			//    （日志里看不到 `upper -> reload_upper`，误以为叠层没起来）。
			lastLoggedUpper = "-";
			return PlayState.STOP;
		}
		// ⚠️ 2026-10-17 **切分已撤销**（用户："撤销关于双手 / 身体切分 + 多段旋转的效果"）：
		//    这里恢复成播作者整段 `stand_shooting` / `stand_shooting_hold`。
		//    撤销的直接原因：切分后多出来的 "hands" 控制器会在换弹等情况下 STOP，而它之前正在写
		//    `arm_left`/`arm_right`/`gun` —— 一 STOP，GeckoLib 就把这几根骨头复位，把 `reload_upper`
		//    的手臂动作当场取消掉（用户 2026-10-17 报的"idle 状态下装填动画未能生效"）。
		String play = name;
		GunClientState.Entry e = GunClientState.get(entity.getId());
		boolean firing = e != null && e.active() && e.state == TkGun.State.FIRE;
		boolean meleeing = e != null && e.active() && e.state == TkGun.State.MELEE;
		// 打一行日志（变化时才打）：验收"举枪/开火时上半身叠 stand_shooting"看这一行
		String tag = play + (firing ? "(开火)" : "(保持)");
		if (!tag.equals(lastLoggedUpper)) {
			lastLoggedUpper = tag;
			TkGun.LOGGER.info("[tkgun] upper -> {}（腿在 walk_*/run/idle 上，上下分层）", tag);
		}
		if (e != null && GunClientState.meleeGraceActive(entity.getId())
				&& play.equals(e.lastMeleeAnimation)) {
			// Hold the exact attack pose across the client/server packet boundary.
			return PlayState.CONTINUE;
		}
		// 换段 / 开火那一发：给一份新实例从头播（GeckoLib 认"同一份 RawAnimation"就会不重播）
		// ⚠️ 2026-10-17：重播信号从"包刚到（elapsed <= 1）"改成**射击序号变化**（`shotSeq`）——
		// 点按快的时候两包挨在一起，旧判据会漏掉一次重播（用户报"点按过快射击动画丢失"）。
		boolean newShot = (firing || meleeing) && e != null
				&& e.shotSeq != lastUpperShotSeq;
		if (newShot) {
			lastUpperShotSeq = e.shotSeq;
		}
		if (!play.equals(upperName) || newShot) {
			upperName = play;
			// ⚠️⚠️ 2026-10-17 **这一步不能省**：GeckoLib 4.8 的 `AnimationController#setAnimation`
			// 是**按 `RawAnimation.equals`（值比较）**判"还是不是同一段"的 —— 相等就直接 return，
			// **既不重播、也不复位时间轴**（javap 逐条核对过：`if (currentRawAnimation.equals(raw)) return;`）。
			// 后果：两次换弹之间上层没换过别的动画时，第二次的 `reload_upper` 被当成"同一段" →
			// 直接停在**上一轮的最后一帧**（那帧正是"侧身、枪端在胸前"的倾斜姿势）。
			// 用户 2026-10-17 的原话就是这么描述的："上半身仅倾向并停留在某一关键帧持续至换弹结束"。
			// 所以凡是我们**决定从头播**（换段 / 新的一发）都先显式复位一次。
			// ⚠️ 只能在"换段"这里调，不能每帧调 —— 每帧复位就真的永远停在第 0 帧了。
			state.getController().forceAnimationReset();
			return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(play));
		}
		return state.setAndContinue(rawFor(play));
	}

	private PlayState pickGunPose(AnimationState<BowgunRig> state) {
		Entity entity = entityOf(state);
		if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player)) {
			return PlayState.STOP;
		}
		net.minecraft.util.Identifier gunId = TkGun.definition(player).id();
		if (!gunId.equals(TkGun.id("abyssal_ancient_cannon"))
				&& !gunId.equals(TkGun.id("veritas_et_justitia"))) {
			return PlayState.STOP;
		}
		GunClientState.Entry entry = GunClientState.get(entity.getId());
		if (entry == null) {
			return PlayState.STOP;
		}
		if (entry.state == TkGun.State.FIRE && entry.focus) {
			return state.setAndContinue(rawFor("gun_open_hold"));
		}
		if (entry.state == TkGun.State.AIM_HOLD && entry.focus) {
			return state.setAndContinue(rawFor("open"));
		}
		if (entry.state == TkGun.State.IDLE
				&& TkGunClient.walkAnimation(entity.getId()) == null
				&& upperAnimationFor(entity) == null) {
			return state.setAndContinue(rawFor("close"));
		}
		// Locomotion, fire, reload, draw and holster transitions keep their
		// inherited animation channels. Only focused hold uses the deployed tail.
		return PlayState.STOP;
	}

	/** upper 控制器当前在播哪一段（换段才给新实例）。 */
	private static String upperName;
	/** 本次装填用不用"只叠上半身"（**装填开始那一刻**定死，避免中途切换导致动画重播）。 */
	/** 上面这个值本轮装填定过没有（装填结束时清掉，下一次重新判定）。 */



	// ---------------------------------------------------------------- 弹壳（每根一个控制器）
	/** 弹壳骨的数量（`shell` + `shell2..shell7`）—— 和导出的 `shell_eject_<k>` 一一对应。 */
	private static final int SHELL_COUNT = 7;
	/** 每根弹壳"已经抛出"了吗（true = 播抛出/停在消失帧；false = 在弹夹里）。 */
	private static final boolean[] shellEjected = new boolean[SHELL_COUNT];
	/** 每根弹壳上次响应过的**射击序号**（变了才重播抛出，和 rig 那条同一个信号源）。 */
	private static final int[] shellSeenSeq = new int[SHELL_COUNT];

	static {
		java.util.Arrays.fill(shellSeenSeq, -1);
	}

	/**
	 * **单根弹壳**该播什么（每根弹壳一个控制器，见 {@link #registerControllers}）。
	 *
	 * <p>规则（2026-10-17 用户："shells 组下所有动画单独渲染，否则连射时弹壳动画未落地就被下一发覆盖"）：
	 * <ol>
	 *   <li>这一发正好是这根弹壳（`shotIndex` 对应）→ 播 `shell_eject_<k>`，**给新实例从头播**
	 *       （序号变了才算新的一发，和 rig 那条同一个判据）；</li>
	 *   <li>装填中（`RELOAD`）→ 放回弹夹：播 `shell_hold_<k>`（单关键帧静姿）；</li>
	 *   <li>其它时候 → 保持当前段：抛出过的停在"消失帧"（scale 0，看不见），没抛过的停在静姿。
	 *       **绝不 STOP**（没有通道的骨骼会被 GeckoLib 拉回 geo 初始位，弹壳会当场弹回弹夹）。</li>
	 * </ol>
	 */
	private static PlayState pickShell(AnimationState<BowgunRig> state, int idx) {
		int k = idx + 1;
		Entity entity = entityOf(state);
		GunClientState.Entry e = entity == null ? null : GunClientState.get(entity.getId());
		int seq = e == null ? -1 : e.shotSeq;
		// ① 装填 → 回弹夹（每根只切一次，靠 shellEjected 判重）
		if (e != null && e.active() && e.state == TkGun.State.RELOAD && shellEjected[idx]) {
			shellEjected[idx] = false;
			shellSeenSeq[idx] = seq;
			// ⚠️ 这里**不要**用 `play()`：`currentAnimationName` 是 rig 那条控制器私有的缓存，
			//    弹壳控制器也去写它会把 rig 的日志/`stillAndWasWalking` 判据污染（实测日志里
			//    `rig -> shell_hold_7` 就是这么来的）。
			return state.setAndContinue(rawFor("shell_hold_" + k));
		}
		// ② 这一发的弹壳 = 这根 → 从头播抛出
		boolean thisShot = e != null && e.active() && e.state == TkGun.State.FIRE
				&& Math.min(e.shotIndex, SHELL_COUNT) == k;
		if (thisShot && seq != shellSeenSeq[idx]) {
			shellSeenSeq[idx] = seq;
			shellEjected[idx] = true;
			String name = "shell_eject_" + k;
			return state.setAndContinue(
					software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold(name));
		}
		// ③ 保持（抛出过 → 停在消失帧；没抛出过 → 静姿）
		return state.setAndContinue(
				rawFor(shellEjected[idx] ? "shell_eject_" + k : "shell_hold_" + k));
	}
	/** 上一次处理过的**射击序号**（rig 那条控制器：变了就重播这一段射击动画）。 */
	private static int lastShotSeq = -1;
	/** 同上，upper 控制器用（腰射时的 `stand_shooting`）。 */
	private static int lastUpperShotSeq = -1;

	/**
	 * **上半身该叠哪一段**（返回 null = 不叠层，走全身动画）。
	 *
	 * <p>2026-10-16 用户把这套讲清楚了："换弹和那个持枪动作，这两个动作是只用他们的上半身，下半身继续用
	 * 那个四向走动 —— 他不可能每时刻装弹或者持枪的时候都是站立原地不动的" + "架枪打架按了右键就变回站立，
	 * 你没维持住蹲下状态"。所以规则改成：
	 *
	 * <table>
	 *   <tr><th>情形</th><th>上半身叠</th><th>腿</th></tr>
	 *   <tr><td>腰射（没进专注）举着枪</td><td>{@code stand_shooting_hold}（端枪**静姿**，不空放）</td><td>walk_xx / run</td></tr>
	 *   <tr><td>腰射开火</td><td>{@code stand_shooting}（整段重播：后坐/枪口焰/弹壳）</td><td>walk_xx / run</td></tr>
	 *   <tr><td>**架枪（专注）**</td><td>不叠层，使用完整 Focus 动画</td><td>—</td></tr>
	 *   <tr><td>架枪且**站着不动**</td><td>不叠层 —— 直接播作者整段（蹲姿最准）</td><td>—</td></tr>
	 *   <tr><td>换弹且**在移动**</td><td>{@code reload_upper}</td><td>walk_xx / run</td></tr>
	 *   <tr><td>换弹且站着不动</td><td>不叠层 —— 播作者整段 {@code reload}</td><td>—</td></tr>
	 * </table>
	 *
	 * <p>`*_upper` 是导出脚本（`derive_upper_variants`）从作者原动画里**去掉两条腿的通道**复制出来的
	 * （`body` 保留：架枪的"蹲下去"就是 `body.position.y = -8`，去掉就等于没维持蹲姿）。
	 */
	public static String upperAnimationFor(Entity entity) {
		if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
			return null;
		}
		GunClientState.Entry e = GunClientState.get(entity.getId());
		if (e == null) {
			return null;
		}
		if (e.state == TkGun.State.SKILL_JUMP) {
			return null;
		}
		if (e.airborneIdle) {
			return null;
		}
		if (e.active() && isFortressUpperLayeredSkillState(e.state)) {
			return fortressSkillUpperAnimation(e.state);
		}
		boolean firing = e.active() && e.state == TkGun.State.FIRE;
		if (e.state == TkGun.State.MELEE) {
			if (e.shotIndex == 2 && e.total == TkGun.MELEE_DASH_TOTAL_TICKS) {
				// ATK_run owns the complete dash attack and hard-stop. The main
				// controller also owns the direct cut to recovery.
				e.lastMeleeAnimation = e.meleeReturning
						? (e.dashMeleeReturnRun
								? "ATK_2_dash_to_run" : "ATK_2_dash_to_idle")
						: "ATK_run";
				return null;
			}
			if (!e.active()) {
				return e.lastMeleeAnimation == null ? "ATK_2_end" : e.lastMeleeAnimation;
			}
			String meleeAnimation;
			if (e.meleeReturning) {
				meleeAnimation = TkGun.meleeReturnAnimation(e.shotIndex);
			} else if (e.meleeHoldOver) {
				meleeAnimation = "ATK_hold_over";
			} else if (e.shotIndex == 3
					&& e.elapsed >= TkGun.MELEE_ATK3_MAIN_END_TICK) {
				meleeAnimation = "ATK_3_hold";
			} else {
				meleeAnimation = TkGun.meleeAnimation(e.shotIndex);
			}
			e.lastMeleeAnimation = meleeAnimation;
			return meleeAnimation;
		}
		if (GunClientState.meleeGraceActive(entity.getId())) {
			return e.lastMeleeAnimation;
		}
		// 翻滚回待机时如果人已经在移动，收势只占上半身；腿继续走 `*_lower`，不跟着
		// `roll_to_idle` 站定。静止时仍用作者的完整 `roll_to_idle`。
		if (e.active() && e.state == TkGun.State.ROLL_TO_IDLE
				&& TkGunClient.hasMovementInput(entity.getId())) {
			return null;
		}
		// **腰射开火一旦起手，就播完整段**（用户 2026-10-18："瞄准腰射时松开右键射击动画
		// 会被强制结束"）。旧逻辑把 `holdingGunUp()` 当成 FIRE 层是否存在的条件；右键一松，
		// 这里返回 null → `pickUpper` 给 STOP，`stand_shooting` 当场被 GeckoLib 复位。
		// 同时第二次按住右键时，FIRE 状态还没结束、上层已被 STOP，下一次同名的
		// `stand_shooting` 又会被当成"新一段"从头播 —— 看起来就是重复播放上一次开火动画。
		// 因此 FIRE 不依赖右键是否仍按着，等状态自然结束再切 `stand_shooting_hold`/STOP。
		if (firing && !GunClientState.focusish(entity.getId())) {
			return "stand_shooting";
		}
		if (GunClientState.focusish(entity.getId())) {
			// **专注射击不叠层**：专注期间移动键在客户端就被屏蔽（`FocusMovementLockMixin`），
			// 人不会走，所以直接用作者的整段全身动画（`Focus_ready` / `Focus` / `Focus_shooting[2..7]`）最准。
			//
			// ⚠️ 2026-10-17：判断从"只看 focus 位"改成 `focusish`（focus 位 **或** 状态在 AIM* 里）——
			// 只看布尔时，只要那一包没到/语义对不上，`stand_shooting_hold`（端枪静姿）就会叠上来，
			// 用户看到的就是"专注动画被站立盖掉"。
			return null;
		}
		// **全身动作期间不许叠层**（2026-10-17 用户："在散热状态下按住瞄准（右键）会出现玩家站立的问题"）：
		// 过热 / 翻滚 / 滑铲（含收势）/ 退出过门 `Focus_over` / 检视 都是作者整段全身动画，
		// 只要按住右键，这里就会叠 `stand_shooting_hold`（端枪**站姿**），把过热动画的
		// 抬枪排汽整段盖掉 —— 表现正是"散热状态下按住右键，人就站起来了"。
		// 名单与 {@code pick()} 里那些"全身状态"一致；上半身叠层只对"走路 / 待机 / 腰射"这几种情况生效。
		if (e.active()) {
			switch (e.state) {
				case OVERHEAT, ROLL, ROLL_TO_IDLE,
						SLIDE, SLIDE_READY, OVER, INSPECT, SKILL_JUMP,
						FORTRESS_SKILL_LOAD, FORTRESS_SKILL_GATHER,
						FORTRESS_SKILL_READY_HIT, FORTRESS_SKILL_HIT -> {
					return null;
				}
				default -> { }
			}
		}
		// **换弹：用哪一套在"装填开始那一刻"定死，之后不再切换。**
		//
		// 这条规则被反馈改了三轮，取舍记在这儿：
		//   * 2026-10-16："站立瞄准时装弹动画未覆盖" → 换弹判断提到举枪静姿前面；
		//   * 2026-10-17 第一次："站立换弹时输入行走键会重新播放装填动画" —— 因为站着播**整段** `reload`、
		//     一走动切 `reload_upper`、站定又切回整段，切回时缓存实例**从头重播**；
		//   * 2026-10-17 第二次：统一成"永远只叠 `reload_upper`" → 站着/idle（含满弹匣）看到的是
		//     **只有上半身**的换弹，作者那段**全身** `reload`（含腿部站姿）再也不播 →
		//     用户报"idle 状态下满弹匣依旧不能播放换弹动画"。
		//
		// 现在：**装填刚开始那一瞬间**在动 → 只叠 `reload_upper`（腿继续走）；没在动 → **不叠层**，
		// `pick()` 会播作者整段 `reload`（腿也归那段）。之后起步/停下都不再换套 → 不会中途重播。
		// ⚠️ 这里**不看 `e.active()`**：客户端镜像的 `elapsed` 和状态包可能差一拍（`elapsed` 先到
		//    `total`，服务端那一 tick 还是 RELOAD）。之前用 `active()` 卡着，那一拍上层会掉到
		//    `stand_shooting_hold` —— 表现就是"换弹动画断在半截"。状态包一到就会改写 state，所以只认它。
		if (e.state == TkGun.State.RELOAD) {
			return "reload_upper";
		}
		boolean holding = holdingGunUp(entity);
		String moveAnim = TkGunClient.walkAnimation(entity.getId());
		boolean moving = TkGunClient.isMoving(entity.getId());
		if (!holding && moving && moveAnim != null
				&& moveAnim.startsWith("walk_")) {
			return "idle_upper";
		}
		// 奔跑时让作者完整 run 的上半身动作播放，不被 stand_shooting_hold 覆盖。
		if ("run".equals(moveAnim) || ((net.minecraft.entity.player.PlayerEntity) entity).isSprinting()) {
			return null;
		}
		if (holding) {
			// 腰射：开火那一遍整段重播；只是举着枪 → 端枪静姿（别空放一发后坐）
			return firing ? "stand_shooting" : "stand_shooting_hold";
		}
		// 停止操作后先保持上半身端枪姿势 5 秒，再让完整 idle 的上半身动作接管。
		return null;
	}

	/** 上半身叠层出场了吗（`pick` 那条只管腿；渲染器也用它决定特效骨显隐）。 */
	private static boolean isFortressUpperLayeredSkillState(TkGun.State state) {
		return state == TkGun.State.FORTRESS_SKILL_LOAD
				|| state == TkGun.State.FORTRESS_SKILL_GATHER
				|| state == TkGun.State.FORTRESS_SKILL_READY_HIT
				|| state == TkGun.State.FORTRESS_SKILL_HIT;
	}

	private static String fortressSkillUpperAnimation(TkGun.State state) {
		return switch (state) {
			case FORTRESS_SKILL_LOAD -> "skill_load_upper";
			case FORTRESS_SKILL_GATHER -> "gather_upper";
			case FORTRESS_SKILL_READY_HIT -> "ready_hit_upper";
			case FORTRESS_SKILL_HIT -> "hit_upper";
			default -> null;
		};
	}

	public static boolean upperLayerActive(Entity entity) {
		return upperAnimationFor(entity) != null;
	}

	/** 这位玩家举着枪吗（本地玩家看右键有没有按；别的玩家看"他正在用这把枪"）。 */
	private static boolean holdingGunUp(Entity entity) {
		if (entity == net.minecraft.client.MinecraftClient.getInstance().player) {
			// **不可瞄准的状态（翻滚加速窗口）连"举枪静姿"也不叠**（用户 2026-10-17：不可右键瞄准的状态
			// 同步屏蔽右键输入）—— 否则右键还按着时上半身照样摆端枪姿势。
			return net.minecraft.client.MinecraftClient.getInstance().options.useKey.isPressed()
					&& net.minecraft.client.MinecraftClient.getInstance().currentScreen == null
					&& com.tk.gun.client.GunCamera.aimAllowed(
							net.minecraft.client.MinecraftClient.getInstance());
		}
		return entity instanceof net.minecraft.entity.LivingEntity living && living.isUsingItem();
	}

	/** {@link #pickUpper} 的"变化才打日志"用。 */
	private static String lastLoggedUpper = "-";

	/**
	 * **腰射 / 行走射击**：正在开枪，而且**没进专注**（没架枪）。
	 *
	 * <p>这种时候腿交给走路/待机那条动画，上半身由 {@link #pickUpper} 叠 `stand_shooting`；
	 * 进了专注（架枪）就换成全身的 `Focus_shooting[2..7]`（抬着炮精瞄 + 仰角档），不叠层。
	 */
	public static boolean hipFire(Entity entity) {
		if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
			return false;
		}
		GunClientState.Entry e = GunClientState.get(entity.getId());
		return e != null && e.active() && e.state == TkGun.State.FIRE && !e.focus;
	}

	private static Entity entityOf(AnimationState<BowgunRig> state) {
		Object raw = state.getExtraData().get(DataTickets.ENTITY);
		return raw instanceof Entity entity ? entity : null;
	}

	// 2026-10-15 material v3: aim elevation is NOT picked by code any more --
	// the author removed the _22.5/_45 variants and drives the chest rotation with Molang
	// (chest.rotation.x = -query.body_x_rotation / 3, GeckoLib evaluates it).
	// The old "pick a tier + lift the chest bone" block was deleted here.

	/** 走路时该播哪一段（四方向走动 / 跑）；没在动就给待机。 */
	private static String walkOrIdle(Entity entity) {
		String walk = TkGunClient.walkAnimation(entity.getId());
		if (walk != null) {
			return walk;
		}
		// **静止时：专注里播作者新加的 `Focus` 循环动画**（用户 2026-10-17："资源文件夹中添加了 Focus
		// 循环动画，此动画用来防止专注射击被 idle 等其他动画覆盖"）；其余情况才是待机。
		return GunClientState.focusish(entity.getId()) ? "Focus_hold" : "idle";
	}

	/**
	 * 同上，但只取**下半身版**（`idle_lower` / `run_lower` / `walk_front_lower`…）。
	 *
	 * <p>用户 2026-10-17："在奔跑状态下满弹匣装填动画未生效" —— 作者的位移动画都是全身的，
	 * 会把上半身叠层（换弹 / 端枪）的手臂、枪、胸一起盖掉。上半身叠层在场时腿这条只播 `*_lower`，
	 * 两条控制器各管各的骨头，叠层的动作必然可见。`*_lower` 由导出脚本 `derive_lower_variants()` 生成。
	 */
	private static String walkOrIdleLower(Entity entity) {
		String walk = TkGunClient.walkAnimation(entity.getId());
		return (walk == null ? "idle" : walk) + "_lower";
	}

	/** 走路那几段（循环播放，静止时必须被 idle 顶掉）。 */
	private static final Set<String> WALK_ANIMS = Set.of(
			"walk_front", "walk_back", "walk_left", "walk_right", "run");

	/**
	 * **"人已经停下、但当前还在播走路段"** 吗（用户 2026-10-16："静止时 idle 动画未能覆盖 walk 系列动画"）。
	 *
	 * <p>为什么需要它：走路段是 {@code loop} 动画，而 {@code pick()} 里有几条路径是
	 * {@code PlayState.CONTINUE}（"保持上一段"：打完一发的 AIM_HOLD、射击余韵…）—— 一旦上一段是走路，
	 * CONTINUE 就把它一直循环下去，表现就是"站着原地踏步"，idle 上不来。
	 *
	 * <p>排除项：正在播**一次性全身动作**（翻滚/滑铲/过热/换弹/进专注的过门/收枪）时不插手，
	 * 那些动作本来就该盖住腿。
	 */
	private static boolean stillAndWasWalking(Entity entity, GunClientState.Entry e) {
		if (TkGunClient.isMoving(entity.getId())) {
			return false;
		}
		if (currentAnimationName == null || !WALK_ANIMS.contains(currentAnimationName)) {
			return false;
		}
		if (e != null && e.active()) {
			switch (e.state) {
				case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY, OVERHEAT, RELOAD, OVER,
						AIM, AIM_FAST, AIM_ROLL, AIM_SLIDE -> {
					return false;       // 一次性全身动作 / 过门动画：不插手
				}
				default -> { }
			}
		}
		return true;
	}

	/** 按当前动作状态挑动画。CONTINUE = 什么都不动（保持上一段），STOP = 交回默认姿势。 */
	private PlayState pick(AnimationState<BowgunRig> state) {
		Entity entity = entityOf(state);
		if (entity == null) {
			// 同上：宁可给个待机姿势也不要 STOP（STOP = 整模型弹回 geo 初始姿势）
			return play(state, "idle");
		}
		GunClientState.Entry e = GunClientState.get(entity.getId());
		if (e != null && e.airborneIdle) {
			return play(state, "idle");
		}
		if (e != null && (e.active() || e.meleeAwaitUntil > 0)
				&& e.state == TkGun.State.MELEE
				&& e.shotIndex == 2
				&& e.total == TkGun.MELEE_DASH_TOTAL_TICKS) {
			if (!e.meleeReturning) {
				// Allow the initial entry from locomotion to blend, but never
				// blend away from or back into the authored ATK_run timeline.
				state.getController().transitionLength(
						"ATK_run".equals(currentAnimationName) ? 0 : 1);
				e.lastMeleeAnimation = "ATK_run";
				return play(state, "ATK_run");
			}
			String recovery = e.dashMeleeReturnRun
					? "ATK_2_dash_to_run" : "ATK_2_dash_to_idle";
			e.lastMeleeAnimation = recovery;
			state.getController().transitionLength(0);
			return play(state, recovery);
		}
		state.getController().transitionLength(1);
		// **上半身叠层模式**（腰射举枪 / 架枪走动 / 换弹走动）：这条控制器只负责腿，
		// 上身交给 `upper` 那条（GeckoLib 里后注册的控制器覆盖先注册的，按骨骼生效）——
		// 这样"架枪走路""边走边换弹"都是"上身保持姿势 + 腿在走"，见 upperAnimationFor 的表。
		if (upperLayerActive(entity)) {
			// **静止时 idle 必须盖掉走路段**（用户 2026-10-16："静止时 idle 动画未能覆盖 walk 系列动画"）：
			// 走路那几段是**循环**动画，只要哪条路径继续让它播着，人一停下就会原地踏步。
			if (stillAndWasWalking(entity, e)) {
				return play(state, walkOrIdle(entity));
			}
			// ⚠️ 2026-10-17（用户："在奔跑状态下满弹匣装填动画未生效"）：上半身叠层在场时，
			// 腿这条**只播 `*_lower`**（只含 `body` + 两条腿的派生版本）—— 作者的 `run/walk_*/idle`
			// 都是全身动画，会把叠层（换弹/端枪）的手臂、枪、胸一起盖掉；奔跑幅度大时尤其明显。
			// ⚠️ 2026-10-17（用户："在idle时装满弹匣弹并进行走时腿部动画失效"）：**换弹期间的腿单独定**，
			// 不能靠 `walkOrIdleLower()` 一句话敷衍 —— 站定换弹时那条会给出 `idle_lower`（普通站姿），
			// 作者整段 `reload` 的**腿部动作**（下蹲再起身，`leg_left` 转了 −67.5°）就丢了。
			//   * 在动（走 / 跑）→ `walk_*_lower` / `run_lower`：腿在走，上半身仍是 `reload_upper`；
			//   * 站定 → `reload_lower`：作者整段 `reload` 的下半身，该有的蹲姿在。
			// 起步 / 停下只是换这条下半身动画；`upper` 那条全程 `reload_upper`、不重播（见 pickUpper）。
			if (e != null && e.active() && e.state == TkGun.State.RELOAD) {
				return TkGunClient.isMoving(entity.getId())
						? play(state, walkOrIdleLower(entity))
						: play(state, "reload_lower");
			}
			return play(state, walkOrIdleLower(entity));
		}
		if (e != null && e.active()) {
			switch (e.state) {
				case FIRE: {
					// **腰射 / 行走射击**（没进专注）：腿交给走路/待机那条动画（这条控制器只管全身，
					// 射击那部分不碰），上半身的 `stand_shooting` 由 pickUpper 那条控制器叠上去。
					// 用户 2026-10-15："不按技能的时候，长按右键就直接用行走射击的动画射击"。
					if (hipFire(entity)) {
						return play(state, walkOrIdle(entity));
					}
					// **专注里**（架着枪）：第 N 发播第 N 段（Focus_shooting / 2..7）。
					// 每发动画名称不同；重新装填后重复使用同一动画时，需要新的 RawAnimation 实例才能重播。
					// 给一个新实例。（抬枪角度由动画里的 Molang 自己算，见上面的注释。）
					String name = TkGun.shotAnimation(e.shotIndex, e.maxShotIndex);
					// **靠服务端的射击序号重播**（2026-10-17）：序号一变就给一份新的 RawAnimation 实例，
					// GeckoLib 才会从头播。原来用"包刚到的那一两 tick（`e.elapsed <= 1`）"当信号，
					// 点按一快就会漏掉一次重播 —— 用户报的"点按过快射击动画丢失"。
					if (e.shotSeq != lastShotSeq) {
						lastShotSeq = e.shotSeq;
						currentAnimationName = name;
						// ⚠️ 前 3 发都叫 `Focus_shooting_extra`；GeckoLib 的
						// `setAnimation` 按 RawAnimation 值比较，换成新对象并不等于重播。
						// 不显式复位时，第 2/3 发会继续停在上一发的时间轴，看起来就是“动画缺失”。
						state.getController().forceAnimationReset();
						return state.setAndContinue(
								software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold(name));
					}
					return play(state, name);
				}
				case AIM:
					// 部署 = 播 Focus_ready 整段（结尾就是作者那个架枪姿势），倍速见 animationSpeed()
					return play(state, "Focus_ready");
				case AIM_FAST:
					// 换弹收尾 / 其他快速举枪仍用滑铲快档。
					return play(state, "sliding_fast_ready");
				case AIM_ROLL:
					// 用户 2026-10-18："将翻滚时蹲射的快速动画改为 `rolling_fast_ready`"。
					return play(state, "rolling_fast_ready");
				case AIM_SLIDE:
					// 滑铲里按 Z 抢枪 / 滑铲中点 Z 中断 → 作者新加的 `sliding_fast_ready`（用户 2026-10-17）。
					return play(state, "sliding_fast_ready");
				case AIM_HOLD:
					// 打完一发的"保持"：**什么都不换**，停在上一段射击动画的最后一帧
					// （用户 2026-10-13："射击完保持尾针，别做成收枪动作"）。
					// ⚠️ 这里必须是 CONTINUE：换成 play(...) 会从头重播那段（= 又变成"举枪动作"），
					//    而 STOP 更糟（GeckoLib 会把整模型弹回 geo 初始姿势）。
					// 快速蹲射的尾帧必须优先于移动判断：滑铲残余惯性即使晚一 tick 才清掉，
					// 也不能在这里插入 walk_* 或 Focus_hold，否则会再次取样 sliding_fast_ready。
					if (e != null && e.readyHoldAnimation != null) {
						return play(state, e.readyHoldAnimation);
					}
					// 一动方向键立刻脱离，进 run（和服务器那边 stopAim 的判定一致）。
					if (TkGunClient.isMoving(entity.getId())) {
						return play(state, walkOrIdle(entity));
					}
					if (stillAndWasWalking(entity, e)) {
						return play(state, walkOrIdle(entity));
					}
					// **架枪保持 = 播 `Focus` 循环**（2026-10-17 用户给了这段循环动画，专门用来防
					// "专注姿势被 idle 之类顶掉"）。以前这里是 CONTINUE（保持上一发尾帧），
					// 状态包稍有偏差就会掉回 idle。
					return e == null
							? play(state, "Focus_hold")
							: play(state, TkGun.focusHoldAnimation(e.shotIndex, e.maxShotIndex));
				case SKILL_JUMP:
					return play(state, GunClientState.skillJumpAnimation(entity.getId()));
				case FORTRESS_SKILL_LOAD:
					return play(state, "skill_load");
				case FORTRESS_SKILL_GATHER:
					return play(state, "gather");
				case FORTRESS_SKILL_READY_HIT:
					return play(state, "ready_hit");
				case FORTRESS_SKILL_HIT:
					return play(state, "hit");
				case OVER:
					// Focus_over starts from the crouched posture. A blended
					// transition from idle_lower briefly shows a standing pose.
					state.getController().transitionLength(0);
					return play(state, "Focus_over");
				case RELOAD:
					// **腿那一半**（上身那半由 `upper` 控制器全程播 `reload_upper`，不重播）：
					//   * 站定/待机 → `reload_lower` = 作者整段 `reload` 的下半身（body + 两条腿），
					//     这样"整段换弹"的观感是完整的；
					//   * 走/跑起来 → 立刻换成 `walk_*_lower` / `run_lower`，腿不再被换弹按在原地
					//     （用户 2026-10-17："idle 时装满弹匣并进行走时腿部动画失效"）。
					return TkGunClient.isMoving(entity.getId())
							? play(state, walkOrIdleLower(entity))
							: play(state, "reload_lower");
				case OVERHEAT:
					// 2026-10-13 新素材：作者补了**专用过热动画** Overheat（2 秒，
					// 抬枪排汽 → 定格 → 放下）。这就是用户要的"出伤帧立刻触发 overheat 动画"。
					return play(state, "Overheat");
				case INSPECT:
					// 过载连射那段（4 秒），只在 /tkgun state <p> inspect 里看到
					return play(state, TkGun.overShootingAnimation());
				case SLIDE:
					return play(state, "sliding");
				case SLIDE_READY:
					// 新素材把 `sliding_ready` 换成了同样 0.667 秒的 `fast_ready`
					return play(state, "fast_ready");
				case ROLL:
					return play(state, "roll");
				case ROLL_TO_IDLE:
					return play(state, "roll_to_idle");
				case MELEE:
					return play(state, walkOrIdle(entity));
				case IDLE:
					// 待机 / 四方向走动 / 跑：全都看"有没有真在动、往哪个方向动"
					return play(state, walkOrIdle(entity));
				default:
					break;
			}
		}
		// 客户端短状态先于服务端 AIM_HOLD 过期时，仍保持快速预备动画的尾帧，
		// 不在 IDLE 与 Focus_hold 之间切换。首次射击会把该标记清掉。
		if (e != null && e.readyHoldAnimation != null && GunClientState.focusish(entity.getId())) {
			return play(state, e.readyHoldAnimation);
		}
		if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
			// 走路 = 四个方向的 walk_*（作者 2026-10-14 新加的），疾跑 = run。
			//
			// ⚠️ 判定必须用 TkGunClient.isMoving（**自己算位置增量**），不能用：
			//   * `player.horizontalSpeed` —— MC 源码里它只在 `Entity.move()` 里 `+=`、永不衰减，
			//     跑一次就永久大于阈值 → 停下来一直卡在 run（2026-10-13 用户实测报的 bug）；
			//   * `player.isSprinting()` —— 按蹲那一 tick 会被原版打断，而且"在疾跑"和"在动"是两回事。
			String walk = TkGunClient.walkAnimation(entity.getId());
			if (walk != null) {
				return play(state, walk);
			}
		}
		// 刚打完一发的余韵（FIRE 状态刚过、AIM_HOLD 包还没到的那 1~2 tick）：
		// 保持射击尾帧（CONTINUE = 什么都不换），别掉回 idle ——
		// 否则服务端随后的 AIM_HOLD 也是 CONTINUE，会把 idle 当成"要保持的姿势"（用户就是这么看到"收枪动作"的）。
		if (GunClientState.holdingShotFire(entity.getId())) {
			if (stillAndWasWalking(entity, e)) {
				return play(state, walkOrIdle(entity));
			}
			return PlayState.CONTINUE;
		}
		// ⚠️ 兜底**必须**给一段动画，绝不能 STOP（2026-10-12 用户实测）：GeckoLib 里没有通道/没有动画的骨骼
		// 会被 AnimationProcessor 重置回 geo 的初始值 —— STOP 就等于把整模型拉回**静止姿势**：
		// 枪卡在模型原位，而且 fire1..4 / dash1..4 这些特效平面回到 scale=1，
		// 表现就是"打完一发偶尔整把枪弹回初始状态、特效全摊出来"。
		// 用户 2026-10-13："玩家模型必须时刻都是处于动画状态" —— 所以这里永远返回一段动画。
		// 常态 = **idle**（新素材里作者做了专门的待机动画，1.5 秒循环）。
		return play(state, walkOrIdle(entity));
	}

	/**
	 * 作者在动画文件里标的循环语义：**只有 run 是 loop=true**，其余都是 hold_on_last_frame
	 * （播一次、停在最后一帧）。
	 *
	 * <p>⚠️ 别一律 thenLoop：Focus_ready 开头就是个"下蹲起势"，循环播就变成"一直在重复下蹲"，
	 * 而且所有姿势永远停在最开头几帧（看着像动作和枪都不对）——2026-10-12 用户实测。
	 * {@code RawAnimation} 也必须缓存：每帧 new 一个新的会被 GeckoLib 当成新动画从头播。
	 */
	/**
	 * 循环播放的那几段（= 作者在 bbmodel 里标 `loop` 的：待机 + 四方向走动 + 跑）。
	 * 其余全是"播一次、停在最后一帧"。
	 */
	private static final Set<String> LOOPING = new HashSet<>(Set.of(
			"idle", "run", "walk_front", "walk_back", "walk_left", "walk_right",
			"Focus_hold", "idle_upper"));
	private static final Map<String, RawAnimation> RAW = new HashMap<>();

	private static RawAnimation rawFor(String name) {
		// ⚠️⚠️ 非循环动画必须用 **thenPlayAndHold**（GeckoLib 4.8 的实际方法名），绝不能是 thenPlay！
		// thenPlay = 播一次就结束 → GeckoLib 把控制器 **STOP** 掉；控制器一停，AnimationProcessor 里
		// "这一帧没有通道的骨骼重置回 initial snapshot" 就会把整模型拉回 geo 初始姿势 —— 表现就是
		// "连打完 / 动画自动结束后，枪卡在模型原位、fire1..6 / dash 特效平面 scale=1 全摊出来"。
		// 用户 2026-10-13 第三次报这个症状，根因就在这一行（上次只堵了 PlayState.STOP，没堵 thenPlay）。
		return RAW.computeIfAbsent(name, n -> isLooping(n)
				? RawAnimation.begin().thenLoop(n)
				: RawAnimation.begin().thenPlayAndHold(n));
	}

	/**
	 * 这一段是不是**循环**播放。
	 *
	 * <p>除了作者标了 loop 的那几段，`*_lower`（下半身派生版，见导出脚本 `derive_lower_variants()`）
	 * 也必须循环 —— 它们就是 `idle/run/walk_*` 本人，只是去掉了上半身通道；
	 * 用 `thenPlayAndHold` 会让腿播一次就定住。
	 *
	 * <p>例外：`reload_lower`（作者整段 `reload` 的下半身）是**一次性**的 —— 源动画标
	 * `hold_on_last_frame`，循环播会让腿在装填窗口里反复"蹲下取弹"（末尾跳回第一帧）。
	 */
	private static boolean isLooping(String name) {
		if (name.endsWith("_lower")) {
			return !name.startsWith("reload");
		}
		return LOOPING.contains(name);
	}

	private BowgunRig() {
	}
}
