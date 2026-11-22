package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Resolves the existing Kinetic Sword state machine into the animation played
 * by the camera-space first-person renderer. Gameplay state is never changed.
 */
public final class KineticSwordFirstPersonState {
	public record Snapshot(String animation, long revision, double speed) {
	}

	private static volatile Snapshot snapshot =
			new Snapshot("idle", 0L, 1.0D);

	public static Snapshot resolve(ClientPlayerEntity player) {
		int id = player.getId();
		GunClientState.Entry gun = GunClientState.get(id);
		SoulAnchorSkillClientState.Entry anchor =
				SoulAnchorSkillClientState.get(id);

		if (anchor != null && anchor.swordSkill()) {
			String name = SoulAnchorSkillClientState.animationName(id);
			if (name != null) {
				return new Snapshot(name, anchor.revision(), 1.0D);
			}
		}

		if (SoulAnchorSkillClientState.postSwordDragActive(id)) {
			if (player.isOnGround()) {
				SoulAnchorSkillClientState.clearPostSwordDrag(id);
			} else {
				String name = player.getVelocity().y > 0.01D
						? "chain_drag_sword" : "chain_drag_sword_fall";
				return new Snapshot(name, gun == null ? 0L : gun.shotSeq,
						1.0D);
			}
		}

		long revision = gun == null ? 0L : gun.shotSeq;
		String name;
		if (gun != null && gun.state == TkGun.State.SKILL_JUMP) {
			name = GunClientState.kineticSwordJumpAnimation(
					id, gun.kineticSwordRunJump);
		} else if (gun != null && gun.state == TkGun.State.MELEE) {
			int step = Math.max(1, Math.min(6, gun.shotIndex));
			name = gun.meleeReturning
					? "GS_ATK_" + step + "_back-idle"
					: gun.kineticSwordSpecial > 0
							? TkGun.kineticSwordSpecialAnimation(
									gun.kineticSwordSpecial)
							: "GS_ATK_" + step;
		} else if (!player.isOnGround()) {
			name = GunClientState.kineticSwordJumpAnimation(id,
					gun != null && gun.kineticSwordRunJump);
		} else if (gun != null && gun.state == TkGun.State.IDLE
				&& TkGunClient.ticks() < gun.kineticSwordLandingIdleUntil) {
			name = "fall_back-idle";
		} else {
			String walk = TkGunClient.walkAnimation(id);
			boolean moving = TkGunClient.isMoving(id)
					|| TkGunClient.hasMovementInput(id);
			boolean sprintInput = player.isSprinting();
			MinecraftClient client = MinecraftClient.getInstance();
			if (player == client.player) {
				sprintInput = sprintInput
						|| client.options.sprintKey.isPressed();
			}
			double horizontalSpeed = player.getVelocity().horizontalLength();
			boolean running = moving && (walk != null && "run".equals(walk)
					|| sprintInput || horizontalSpeed >= 0.25D);
			if (running) {
				name = "GS_run";
			} else if (walk != null && walk.startsWith("walk_")) {
				name = "GS_" + walk;
			} else {
				name = "idle";
			}
		}
		return new Snapshot(name, revision, speedFor(id, name, gun));
	}

	private static double speedFor(int id, String name,
			GunClientState.Entry gun) {
		if (MeleeImpactClientState.freezesAnimation(id)) {
			return 0.0D;
		}
		if (name.startsWith("GS_walk_") || "GS_run".equals(name)) {
			return TkGunClient.walkAnimationSpeed(id);
		}
		if (name.startsWith("GS_ATK_") && gun != null) {
			if (gun.kineticSwordAccelerationAttack) {
				return 1.0D;
			}
			if (gun.state == TkGun.State.MELEE
					&& gun.kineticSwordSpecial <= 0
					&& gun.shotIndex >= 2 && gun.shotIndex <= 5) {
				int baseTicks = TkGun.KINETIC_SWORD_ATTACK_TICKS[
						gun.shotIndex - 1];
				return baseTicks / (double) Math.max(1, gun.total);
			}
		}
		return 1.0D;
	}

	public static void update(Snapshot value) {
		snapshot = value == null
				? new Snapshot("idle", 0L, 1.0D) : value;
	}

	public static String animationName() {
		return snapshot.animation();
	}

	public static long revision() {
		return snapshot.revision();
	}

	public static double animationSpeed() {
		return snapshot.speed();
	}

	private KineticSwordFirstPersonState() {
	}
}
