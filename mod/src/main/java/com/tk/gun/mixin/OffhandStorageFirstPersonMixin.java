package com.tk.gun.mixin;

import com.tk.gun.client.WarBannerSkillClientState;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The launcher storage slot is inventory-only and has no first-person hand model. */
@Mixin(HeldItemRenderer.class)
public abstract class OffhandStorageFirstPersonMixin {
	@Shadow
	private ItemStack field_4047;

	@Shadow
	private ItemStack field_4048;

	@Redirect(method = "method_22976",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/class_759;field_4047:Lnet/minecraft/class_1799;"))
	private ItemStack tkgun$hideSkillAnimationRightHand(
			HeldItemRenderer renderer) {
		if (MinecraftClient.getInstance().player != null
				&& MinecraftClient.getInstance().player.getMainArm() == Arm.RIGHT
				&& WarBannerSkillClientState.isActive(
						MinecraftClient.getInstance().player.getId())) {
			return ItemStack.EMPTY;
		}
		return ((OffhandStorageFirstPersonMixin) (Object) renderer).field_4047;
	}

	@Redirect(method = "method_22976",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/class_759;field_4048:Lnet/minecraft/class_1799;"))
	private ItemStack tkgun$hideStoredOffhandField(HeldItemRenderer renderer) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (BackpackLauncherRules.hasActiveLauncher(client.player)
				|| (client.player != null
						&& client.player.getMainArm() == Arm.LEFT
						&& WarBannerSkillClientState.isActive(
								client.player.getId()))) {
			return ItemStack.EMPTY;
		}
		return ((OffhandStorageFirstPersonMixin) (Object) renderer).field_4048;
	}
}
