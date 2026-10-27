package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The launcher storage slot is inventory-only and has no first-person hand model. */
@Mixin(HeldItemRenderer.class)
public abstract class OffhandStorageFirstPersonMixin {
	@Shadow
	private ItemStack field_4048;

	@Redirect(method = "method_22976",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/class_759;field_4048:Lnet/minecraft/class_1799;"))
	private ItemStack tkgun$hideStoredOffhandField(HeldItemRenderer renderer) {
		if (BackpackLauncherRules.hasActiveLauncher(MinecraftClient.getInstance().player)) {
			return ItemStack.EMPTY;
		}
		return ((OffhandStorageFirstPersonMixin) (Object) renderer).field_4048;
	}
}
