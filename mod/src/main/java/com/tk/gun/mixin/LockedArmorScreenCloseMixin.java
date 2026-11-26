package com.tk.gun.mixin;

import com.tk.gun.client.LockedArmorGuiOverlay;
import com.tk.gun.client.ModGuiFadeOverlay;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class LockedArmorScreenCloseMixin {
	@Inject(method = "method_25419", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$fadeLockedArmorGui(CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (LockedArmorGuiOverlay.shouldDelayClose(screen)
				|| ModGuiFadeOverlay.shouldDelayClose(screen)) {
			ci.cancel();
		}
	}
}
