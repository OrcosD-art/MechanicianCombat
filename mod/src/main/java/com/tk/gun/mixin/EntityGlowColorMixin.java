package com.tk.gun.mixin;

import com.tk.gun.client.SoulAnchorMarkClientState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the anchor mark color for the vanilla glowing silhouette. */
@Mixin(Entity.class)
public abstract class EntityGlowColorMixin {
	@Inject(method = "method_22861", at = @At("RETURN"),
			cancellable = true)
	private void mechaniciancombat$anchorGlowColor(
			CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		int color = SoulAnchorMarkClientState.glowColor(entity.getId());
		if (color >= 0) {
			cir.setReturnValue(color);
		}
	}
}
