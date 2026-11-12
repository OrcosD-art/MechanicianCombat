package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.TkGunClient;
import com.tk.gun.client.WarBannerSkillClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 专注期间清零移动输入；翻滚按下帧保留方向供 Combat Roll 计算翻滚向量。 */
@Mixin(KeyboardInput.class)
public abstract class FocusMovementLockMixin {
	@Inject(method = "method_3129(ZF)V", at = @At("TAIL"))
	private void mechaniciancombat$lockFocusMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		int playerId = client.player.getId();
		boolean holdingGun = TkGun.holdingGun(client.player);
		boolean warBannerLock =
				WarBannerSkillClientState.usesGunAnimation(playerId);
		if (!holdingGun && !warBannerLock) {
			return;
		}
		boolean focusLock = GunClientState.focusish(playerId);
		GunClientState.Entry entry = GunClientState.get(playerId);
		boolean dashMeleeHold = entry != null && entry.active()
				&& entry.dashMeleeActive;
		boolean atk3Lock = entry != null && entry.state == TkGun.State.MELEE
				&& entry.shotIndex == 3 && !entry.meleeReturning
				&& !entry.meleeHoldOver
				&& entry.elapsed < TkGun.MELEE_ATK3_MAIN_END_TICK;
		boolean holdOverLock = entry != null && entry.state == TkGun.State.MELEE
				&& entry.meleeHoldOver && !entry.meleeReturning;
		boolean skillJumpLock = entry != null
				&& entry.state == TkGun.State.SKILL_JUMP;
		boolean fallAttackLock = entry != null
				&& (entry.state == TkGun.State.FALL_ATTACK
						|| entry.state == TkGun.State.FALL_ATTACK_RECOVERY);
		boolean skillLoadOnly = entry != null
				&& entry.state == TkGun.State.FORTRESS_SKILL_LOAD
				&& !entry.fortressCrouchedLoad;
		boolean slideSkillLoadLock = entry != null
				&& entry.state == TkGun.State.FORTRESS_SKILL_LOAD
				&& entry.fortressSlideLoad;
		boolean skillCrouchedLoadLock = entry != null
				&& entry.state == TkGun.State.FORTRESS_SKILL_LOAD
				&& entry.fortressCrouchedLoad;
		boolean skillGatherLock = entry != null
				&& (entry.state == TkGun.State.FORTRESS_SKILL_READY_GATHER
						|| entry.state == TkGun.State.FORTRESS_SKILL_GATHER);
		boolean skillHitLock = entry != null
				&& entry.state == TkGun.State.FORTRESS_SKILL_HIT;
		boolean movementLock = focusLock || warBannerLock || dashMeleeHold || atk3Lock
				|| holdOverLock || skillJumpLock || fallAttackLock || skillGatherLock
				|| skillHitLock || skillCrouchedLoadLock || slideSkillLoadLock;
		if (!movementLock && !skillLoadOnly) {
			return;
		}
		KeyboardInput self = (KeyboardInput) (Object) this;
		if (movementLock && !TkGunClient.isRollTriggerTick(client)) {
			self.movementForward = 0.0F;
			self.movementSideways = 0.0F;
		}
		self.jumping = false;
		self.sneaking = false;
	}
}
