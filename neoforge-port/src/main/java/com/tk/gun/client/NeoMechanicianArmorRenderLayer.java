package com.tk.gun.client;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * NeoForge bridge that executes the Fabric-style armor renderers which are
 * registered by TkGunClient.
 */
public final class NeoMechanicianArmorRenderLayer extends FeatureRenderer<
		AbstractClientPlayerEntity,
		PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	public NeoMechanicianArmorRenderLayer(
			FeatureRendererContext<AbstractClientPlayerEntity,
					PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void render(MatrixStack matrices,
			VertexConsumerProvider consumers, int light,
			AbstractClientPlayerEntity entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress,
			float headYaw, float headPitch) {
		PlayerEntityModel<AbstractClientPlayerEntity> model =
				getContextModel();
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = entity.getEquippedStack(slot);
			ArmorRenderer renderer = ArmorRenderer.BY_ITEM.get(stack.getItem());
			if (renderer == null) {
				continue;
			}
			renderer.render(matrices, consumers, stack,
					(LivingEntity) entity, slot, light,
					(BipedEntityModel<LivingEntity>) (BipedEntityModel) model);
		}
	}
}
