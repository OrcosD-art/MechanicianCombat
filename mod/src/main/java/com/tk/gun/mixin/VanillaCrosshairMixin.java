package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide the vanilla HUD crosshair while the MechanicianCombat gun is held. */
@Mixin(InGameHud.class)
public abstract class VanillaCrosshairMixin {
	@Inject(method = "method_1736(Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V",
			at = @At("HEAD"), cancellable = true)
	private void tkgun$hideVanillaCrosshair(DrawContext context,
											RenderTickCounter tickCounter,
											CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && TkGun.holdingGun(client.player)) {
			ci.cancel();
		}
	}
}

