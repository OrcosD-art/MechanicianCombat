package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the offhand visual and reveals the backpack ammunition slot. */
@Mixin(InventoryScreen.class)
public abstract class InventoryBackpackGuiMixin {
	private static final Identifier MECHANICIAN_INVENTORY_OVERLAY =
			Identifier.of("mechaniciancombat",
					"textures/gui/container/player_inventory_backpack.png");

	@Inject(method = "method_2389(Lnet/minecraft/class_332;FII)V",
			at = @At("TAIL"))
	private void mechaniciancombat$drawBackpackAmmoGui(DrawContext context,
			float delta, int mouseX, int mouseY, CallbackInfo ci) {
		if (MinecraftClient.getInstance().player == null
				|| !BackpackLauncherRules.usesAmmoStorage(
						MinecraftClient.getInstance().player)) {
			return;
		}
		HandledScreenCoordinateAccessor screen =
				(HandledScreenCoordinateAccessor) (Object) this;
		// Keep the occupied offhand appearance, then draw a separate ammo
		// well immediately to its right. The ammo slot itself is at 95,44;
		// sharing the vanilla offhand coordinate made every click resolve to
		// the offhand slot first.
		context.drawTexture(MECHANICIAN_INVENTORY_OVERLAY,
				screen.mechaniciancombat$getGuiX() + 76,
				screen.mechaniciancombat$getGuiY() + 43, 76.0F, 43.0F,
				18, 18, 256, 256);
		context.drawTexture(MECHANICIAN_INVENTORY_OVERLAY,
				screen.mechaniciancombat$getGuiX() + 94,
				screen.mechaniciancombat$getGuiY() + 43, 76.0F, 43.0F,
				18, 18, 256, 256);
	}
}
