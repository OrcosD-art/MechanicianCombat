package com.tk.gun.mixin;

import com.tk.gun.item.MechanicianArmorItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mechanician armor is rendered by NeoMechanicianArmorRenderLayer. Suppress
 * vanilla's netherite-style armor layer so both models are never visible at
 * once.
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class MechanicianVanillaArmorLayerMixin {
	@Inject(method = "method_4169(Lnet/minecraft/class_4587;"
			+ "Lnet/minecraft/class_4597;Lnet/minecraft/class_1309;"
			+ "Lnet/minecraft/class_1304;ILnet/minecraft/class_572;)V",
			at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$skipVanillaArmorSlot(MatrixStack matrices,
			VertexConsumerProvider consumers, LivingEntity entity,
			EquipmentSlot slot, int light, BipedEntityModel<?> model,
			CallbackInfo ci) {
		ItemStack stack = entity.getEquippedStack(slot);
		if (stack.getItem() instanceof MechanicianArmorItem) {
			ci.cancel();
		}
	}
}
