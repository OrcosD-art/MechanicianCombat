package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunTooltipState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标记 HandledScreen 当前悬停的是枪械，供 tooltip 背景渲染器替换背景。 */
@Mixin(HandledScreen.class)
public abstract class GunTooltipScreenMixin {
	@Shadow
	protected Slot field_2787; // focusedSlot

	@Inject(method = "method_2380(Lnet/minecraft/class_332;II)V", at = @At("HEAD"))
	private void tkgun$markGunTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		GunTooltipState.set(field_2787 != null && field_2787.hasStack()
				&& TkGun.isGun(field_2787.getStack()));
	}

	@Inject(method = "method_2380(Lnet/minecraft/class_332;II)V", at = @At("RETURN"))
	private void tkgun$clearGunTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		GunTooltipState.set(false);
	}
}
