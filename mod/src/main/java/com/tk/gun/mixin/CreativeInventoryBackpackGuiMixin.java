package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import com.tk.gun.item.BackpackAmmoSlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Applies the same occupied-offhand overlay on the creative survival tab. */
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryBackpackGuiMixin {
	private static final Identifier MECHANICIAN_INVENTORY_OVERLAY =
			Identifier.of("mechaniciancombat",
					"textures/gui/container/creative_inventory_backpack.png");

	/**
	 * Creative remaps the player handler's final slot from its internal
	 * fallback position to this compact location in tab_inventory.
	 */
	@ModifyArgs(method = "method_2466", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/class_481$class_484;<init>(Lnet/minecraft/class_1735;III)V"))
	private void mechaniciancombat$moveAmmoScreenSlot(Args args) {
		if (args.get(0) instanceof BackpackAmmoSlot) {
			args.set(2, 127);
			args.set(3, 21);
		}
	}

	@Inject(method = "method_2389(Lnet/minecraft/class_332;FII)V",
			at = @At("TAIL"))
	private void mechaniciancombat$drawBackpackAmmoGui(DrawContext context,
			float delta, int mouseX, int mouseY, CallbackInfo ci) {
		if (MinecraftClient.getInstance().player == null
				|| CreativeInventoryScreenAccessor
						.mechaniciancombat$getSelectedTab().getType()
						!= ItemGroup.Type.INVENTORY
				|| !BackpackLauncherRules.usesAmmoStorage(
						MinecraftClient.getInstance().player)) {
			return;
		}
		HandledScreenCoordinateAccessor screen =
				(HandledScreenCoordinateAccessor) (Object) this;
		context.drawTexture(MECHANICIAN_INVENTORY_OVERLAY,
				screen.mechaniciancombat$getGuiX() + 35,
				screen.mechaniciancombat$getGuiY() + 20, 35.0F, 20.0F,
				16, 16, 256, 256);
		context.drawTexture(MECHANICIAN_INVENTORY_OVERLAY,
				screen.mechaniciancombat$getGuiX() + 126,
				screen.mechaniciancombat$getGuiY() + 20, 126.0F, 20.0F,
				18, 18, 256, 256);
	}
}
