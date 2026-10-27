package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hides only the stored offhand stack while retaining the main-hand item render. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class OffhandStorageThirdPersonMixin {
	@Redirect(method = "method_17162",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/class_1309;method_6079()Lnet/minecraft/class_1799;"))
	private ItemStack tkgun$hideStoredOffhandThirdPerson(LivingEntity entity) {
		ItemStack offhand = entity.getStackInHand(net.minecraft.util.Hand.OFF_HAND);
		return BackpackLauncherRules.hasActiveLauncher(entity)
				? ItemStack.EMPTY : offhand;
	}
}
