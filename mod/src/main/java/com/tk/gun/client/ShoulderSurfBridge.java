package com.tk.gun.client;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.callback.ICameraCouplingCallback;
import com.github.exopandora.shouldersurfing.api.callback.IPlayerStateCallback;
import com.github.exopandora.shouldersurfing.api.model.Perspective;
import com.github.exopandora.shouldersurfing.plugin.ShoulderSurfingRegistrar;
import com.tk.gun.TkGun;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 唯一碰 ShoulderSurfing 的地方（用户 2026-10-14："不是我们装了一个越肩视角的 mod，你在它基础上改造不行吗"）。
 *
 * <p>为什么要单独一个类：里面 import 的是它的 API，**没装这个 mod 时这个类的方法一跑就
 * {@code NoClassDefFoundError}**。所以每个方法都自己 try/catch 住（调用方拿到的就是"没有/失败"，
 * 退回原版第三人称），客户端不会因为这个 mod 被删掉就崩。
 *
 * <p>它替我们干的事（都在它的配置里，见 `config/shouldersurfing-client.toml`）：
 * <ul>
 *   <li>`default_perspective = SHOULDER_SURFING` —— 越肩相机本体；</li>
 *   <li>`decoupled_camera = true` —— 相机和身体朝向解耦（2026-10-14 帮用户打开的，原来 false）；</li>
 *   <li>`crosshair_type = STATIC` —— 屏幕正中那个 `- . -` 准星（我们只管枪口圈，别重复画）。</li>
 * </ul>
 * ⚠️ 它的 "when_using_item 转向玩家" 在解耦模式下对这把枪**没生效**（实测），所以
 * "长按右键身体转向鼠标"现在由我们自己在 {@link GunCamera} 里做。
 */
public final class ShoulderSurfBridge {
	/** 拿枪之前玩家自己用的视角（我们替他把视角切走了 → 枪离手时还回去）。 */
	private static Perspective previous;

	/**
	 * 在腰射期间强制 ShoulderSurfing 使用耦合相机。
	 *
	 * <p>ShoulderSurfing 在插件加载结束后会冻结自己的 registrar；tkgun 是后加载的普通模组，
	 * 因此这里直接拿到它已经建好的 cameraCouplingCallbacks 列表并追加回调。
	 * 这是该版本专门提供的扩展点，不需要替换 ShoulderSurfing 的 jar。
	 */
	@SuppressWarnings("unchecked")
	public static void registerHipFireCoupling() {
		try {
			ShoulderSurfingRegistrar registrar = ShoulderSurfingRegistrar.getInstance();
			Field field = ShoulderSurfingRegistrar.class.getDeclaredField("cameraCouplingCallbacks");
			field.setAccessible(true);
			List<ICameraCouplingCallback> callbacks =
					(List<ICameraCouplingCallback>) field.get(registrar);
			callbacks.add(ShoulderCouplingState::shouldCoupleCamera);

			Field playerStatesField =
					ShoulderSurfingRegistrar.class.getDeclaredField("playerStateCallbacks");
			playerStatesField.setAccessible(true);
			List<IPlayerStateCallback> playerStates =
					(List<IPlayerStateCallback>) playerStatesField.get(registrar);
			playerStates.add(0, new IPlayerStateCallback() {
				@Override
				public Result isUsingItem(IsUsingContext context) {
					if (context.minecraft().player == context.cameraEntity()
							&& context.minecraft().player != null
							&& context.minecraft().player.isUsingItem()
							&& ShoulderCouplingState.requiresDecoupledView(context.minecraft())) {
						return Result.FALSE;
					}
					return Result.PASS;
				}
			});
			ShoulderCouplingState.markRegistered();
			TkGun.LOGGER.info("[tkgun] ShoulderSurfing：腰射/瞄准相机耦合回调已注册");
		} catch (Throwable t) {
			TkGun.LOGGER.warn("[tkgun] ShoulderSurfing 腰射耦合回调注册失败，改用 GunCamera 直接跟随：{}",
					t.toString());
		}
	}

	/**
	 * 拿枪：如果现在不是越肩视角就切过去，并记住原来那个。
	 *
	 * @return **这个 mod 在不在**（false = 调用方退回原版第三人称；已经在越肩视角时返回 true 但没动视角）
	 */
	public static boolean goOverShoulder() {
		try {
			IShoulderSurfing api = ShoulderSurfing.getInstance();
			if (api == null) {
				return false;
			}
			if (Perspective.current() != Perspective.SHOULDER_SURFING) {
				previous = Perspective.current();
				api.changePerspective(Perspective.SHOULDER_SURFING);
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 枪离手：把我们切走的那次视角还回去（没切过就什么都不做）。 */
	public static boolean leaveOverShoulder() {
		try {
			IShoulderSurfing api = ShoulderSurfing.getInstance();
			if (api != null && previous != null
					&& Perspective.current() == Perspective.SHOULDER_SURFING) {
				api.changePerspective(previous);
			}
			previous = null;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** True only for ShoulderSurfing's own perspective, not vanilla third-person back. */
	public static boolean isOverShoulderActive() {
		try {
			return ShoulderSurfing.getInstance() != null
					&& Perspective.current() == Perspective.SHOULDER_SURFING;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 诊断用（演示模式日志里带上它，好确认"现在到底是它的越肩视角还是原版第三人称"）。 */
	public static String describe() {
		try {
			IShoulderSurfing api = ShoulderSurfing.getInstance();
			if (api == null) {
				return "无";
			}
			return Perspective.current() + "/解耦=" + api.isCameraDecoupled()
					+ "/自由看=" + api.isFreeLooking() + "/越肩=" + api.isShoulderSurfing();
		} catch (Throwable t) {
			return "不可用";
		}
	}

	private ShoulderSurfBridge() {
	}
}
