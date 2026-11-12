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
		if (WarBannerSkillClientState.isActive(client.player.getId())) {
			return false;
		}
		GunClientState.Entry state = GunClientState.get(client.player.getId());
		if (isFortressCoupledSkillState(state)) {
			return true;
		}
		if (client.options.getPerspective()
				== net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT) {
			return false;
		}
		// Normal melee follows the camera, but the directional dash is locked to
		// its trigger yaw and must keep the camera decoupled from attack heading.
		if (state != null && state.state == TkGun.State.MELEE) {
			return !state.meleeHoldOver && !state.dashMeleeActive;
		}
		// 右键瞄准由 aim→locator 中心线闭环控制身体 yaw；ShoulderSurfing 继续解耦，
		// 否则它会和中心线控制器同时写 yaw。使用其它物品时同样不接管。
		if (client.player.isUsingItem()) {
			return false;
		}
		if (state != null && state.state == TkGun.State.SKILL_JUMP
				&& state.skillJumpHovering) {
			return true;
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
		// Non-aiming idle/walking and manual reloading own a split camera/body pose.
		if (isNonAimingGunIdle(client, state)
				|| isManualReloadDecoupled(client, state)) {
			return false;
		}
		return switch (state.state) {
			case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY,
					OVERHEAT, OVER, INSPECT, SKILL_JUMP,
					FALL_ATTACK, FALL_ATTACK_RECOVERY -> false;
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
		if (WarBannerSkillClientState.isActive(client.player.getId())) {
			return 23;
		}
		int id = client.player.getId();
		GunClientState.Entry state = GunClientState.get(id);
		if (state != null && state.state == TkGun.State.FORTRESS_SKILL_HIT) {
			return 17;
		}
		if (isFortressCoupledSkillState(state)) {
			return 0;
		}
		if (state != null && state.state == TkGun.State.MELEE) {
			return state.dashMeleeActive ? 20 : (state.meleeHoldOver ? 13 : 0);
		}
		if (state != null && state.state == TkGun.State.SKILL_JUMP
				&& state.skillJumpHovering) {
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
		if (isManualReloadDecoupled(client, state)) {
			return 21;
		}
		if (isNonAimingGunIdle(client, state)) {
			return 22;
		}
		return switch (state.state) {
			case ROLL -> 4;
			case ROLL_TO_IDLE -> 5;
			case SLIDE -> 7;
			case SLIDE_READY -> 8;
			case OVERHEAT -> 9;
			case OVER -> 10;
			case INSPECT -> 11;
			case SKILL_JUMP -> 15;
			case FALL_ATTACK -> 18;
			case FALL_ATTACK_RECOVERY -> 19;
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

	/** Camera-relative steering also owns non-aiming walks and manual reloads. */
	public static boolean usesCameraRelativeSteering(MinecraftClient client) {
		if (client == null || client.player == null) {
			return false;
		}
		GunClientState.Entry state = GunClientState.get(client.player.getId());
		return isSprintDirectionActive(client)
				|| isNonAimingGunWalk(client, state)
				|| isManualReloadDecoupled(client, state);
	}

	private static boolean isNonAimingGunWalk(MinecraftClient client,
											  GunClientState.Entry state) {
		if (!isNonAimingGunIdle(client, state)) {
			return false;
		}
		int id = client.player.getId();
		return TkGunClient.isMoving(id)
				|| TkGunClient.hasMovementInput(id);
	}

	private static boolean isNonAimingGunIdle(MinecraftClient client,
											  GunClientState.Entry state) {
		// IDLE is broadcast with ticks=0, so Entry#active() is intentionally false
		// while the player is standing or walking in the normal gun state.
		return state != null && state.state == TkGun.State.IDLE
				&& !state.focus && !client.player.isUsingItem();
	}

	private static boolean isManualReloadDecoupled(MinecraftClient client,
												   GunClientState.Entry state) {
		return state != null && state.active() && state.state == TkGun.State.RELOAD
				&& state.reloading && !state.focus
				&& !client.player.isUsingItem();
	}

	private static boolean isFortressCoupledSkillState(GunClientState.Entry state) {
		return state != null
				&& (state.state == TkGun.State.FORTRESS_SKILL_LOAD
						|| state.state == TkGun.State.FORTRESS_SKILL_READY_GATHER
						|| state.state == TkGun.State.FORTRESS_SKILL_GATHER);
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
