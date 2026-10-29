package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;

/**
 * 腰射期间是否要求 ShoulderSurfing 使用“相机与玩家耦合”模式。
 *
 * <p>耦合时 ShoulderSurfing 会把鼠标的 yaw/pitch 直接写进玩家实体，玩家正面与屏幕中心准星
 * 始终一致；退出腰射（右键瞄准 / 专注 / 翻滚 / 滑铲 / 过热等）后立即恢复解耦。
 */
public final class ShoulderCouplingState {
	private static boolean registered;

	public static boolean shoulderCouplingRegistered() {
		return registered;
	}

	static void markRegistered() {
		registered = true;
	}

	public static boolean shouldCoupleCamera(MinecraftClient client) {
		if (client == null || client.player == null || client.currentScreen != null) {
			return false;
		}
		if (!TkGun.holdingGun(client.player)) {
			// ShoulderSurfing may expose vanilla THIRD_PERSON_BACK underneath
			// its own perspective. Keep non-item shoulder use decoupled.
			if (ShoulderSurfBridge.isOverShoulderActive()
					&& !client.player.isUsingItem()) {
				return false;
			}
			// Outside gun handling, vanilla third-person should keep camera and
			// player coupled instead of inheriting the gun's decoupled mode.
			return client.options.getPerspective()
					== net.minecraft.client.option.Perspective.THIRD_PERSON_BACK;
		}
		if (client.options.getPerspective()
				== net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT) {
			return false;
		}
		GunClientState.Entry state = GunClientState.get(client.player.getId());
		// Close combat owns the camera direction for the full authored attack,
		// including movement and sprint. Do not fall through to sprint decoupling.
		if (state != null && state.state == TkGun.State.MELEE) {
			return true;
		}
		// 右键瞄准由 aim→locator 中心线闭环控制身体 yaw；ShoulderSurfing 继续解耦，
		// 否则它会和中心线控制器同时写 yaw。使用其它物品时同样不接管。
		if (client.player.isUsingItem()) {
			return false;
		}
		int id = client.player.getId();
		// 疾跑 / 翻滚后的加速冲刺必须保持解耦，由原版/ShoulderSurfing 的移动视角处理；
		// 否则每 tick 写 yaw 会和冲刺中的相机插值互相拉扯。
		if (usesDecoupledSprint(client)) {
			return false;
		}
		if (GunClientState.focusish(id) || GunClientState.aimLocked(id)) {
			return false;
		}
		if (state == null) {
			return true;
		}
		// Standing idle is decoupled so the camera can look around without
		// rotating the stationary body. Walking in IDLE state remains coupled.
		if (state.state == TkGun.State.IDLE && !TkGunClient.isMoving(id)) {
			return false;
		}
		return switch (state.state) {
			case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY,
					OVERHEAT, OVER, INSPECT -> false;
			default -> true;
		};
	}

	/**
	 * True when the camera/body split must survive a held right-click.
	 * ShoulderSurfing's when_using_item=ALWAYS path would otherwise turn the body.
	 */
	public static boolean requiresDecoupledView(MinecraftClient client) {
		return decoupledReason(client) != 0;
	}

	/**
	 * Identifies which decoupled state currently owns the camera. Returning the
	 * actual reason lets aiming start inside one state, then release only when a
	 * different decoupled state is entered while right-click is still held.
	 */
	public static int decoupledReason(MinecraftClient client) {
		if (client == null || client.player == null || client.currentScreen != null
				|| !TkGun.holdingGun(client.player)) {
			return 0;
		}
		int id = client.player.getId();
		GunClientState.Entry state = GunClientState.get(id);
		if (state != null && state.state == TkGun.State.MELEE) {
			return 0;
		}
		if (usesDecoupledSprint(client)) {
			return client.player.isSprinting() ? 1 : 2;
		}
		if (GunClientState.focusish(id)) {
			return 3;
		}
		if (state == null) {
			return GunClientState.aimLocked(id) ? 12 : 0;
		}
		return switch (state.state) {
			case ROLL -> 4;
			case ROLL_TO_IDLE -> 5;
			case SLIDE -> 7;
			case SLIDE_READY -> 8;
			case OVERHEAT -> 9;
			case OVER -> 10;
			case INSPECT -> 11;
			default -> GunClientState.aimLocked(id) ? 12 : 0;
		};
	}

	/**
	 * Includes the sprint-start input frame. Vanilla sets isSprinting() later in
	 * the movement tick, so checking only the flag can couple the camera for one
	 * frame immediately after sprint begins.
	 */
	private static boolean usesDecoupledSprint(MinecraftClient client) {
		return isSprintDirectionActive(client);
	}

	/** Sprint, roll acceleration, or a held sprint key with active movement input. */
	public static boolean isSprintDirectionActive(MinecraftClient client) {
		if (client == null || client.player == null) {
			return false;
		}
		boolean movementKey = client.options.forwardKey.isPressed()
				|| client.options.backKey.isPressed()
				|| client.options.leftKey.isPressed()
				|| client.options.rightKey.isPressed();
		return client.player.isSprinting()
				|| GunClientState.dashing(client.player.getId())
				|| GunClientState.rolling(client.player.getId())
				|| (client.options.sprintKey.isPressed()
						&& movementKey);
	}

	private ShoulderCouplingState() {
	}
}
