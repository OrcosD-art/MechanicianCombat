package com.tk.gun.mixin;

import com.tk.gun.client.FortressSkillCooldownClient;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownProgressMixin {
	@Inject(method = "method_7905", at = @At("RETURN"), cancellable = true)
	private void mechaniciancombat$fortressCooldownProgress(Item item, float tickDelta,
			CallbackInfoReturnable<Float> cir) {
		float progress = FortressSkillCooldownClient.progress(item, tickDelta);
		if (!Float.isNaN(progress)) {
			cir.setReturnValue(progress);
		}
	}
}
