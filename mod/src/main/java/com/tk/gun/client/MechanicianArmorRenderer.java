package com.tk.gun.client;

import com.tk.gun.item.MechanicianArmorItem;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/** Draws the authored chest, leg, and boot geometry on vanilla player poses. */
public final class MechanicianArmorRenderer implements ArmorRenderer {
	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers,
					   ItemStack stack, LivingEntity entity, EquipmentSlot slot,
					   int light,
					   BipedEntityModel<LivingEntity> baseModel) {
		if (!(stack.getItem() instanceof MechanicianArmorItem item)) {
			return;
		}
		float limbAngle = entity.limbAnimator.getPos();
		float limbDistance = entity.limbAnimator.getSpeed();
		float headYaw = entity.getHeadYaw() - entity.getBodyYaw();
		switch (item.piece()) {
			case CHESTPLATE -> MechanicianArmorGeometry.render(
					item.piece(), matrices, consumers, baseModel, light, entity,
					limbAngle, limbDistance, headYaw,
					MechanicianArmorGeometry.Part.BODY,
					MechanicianArmorGeometry.Part.RIGHT_ARM,
					MechanicianArmorGeometry.Part.LEFT_ARM);
			case LEGGINGS, BOOTS -> MechanicianArmorGeometry.render(
					item.piece(), matrices, consumers, baseModel, light, entity,
					limbAngle, limbDistance, headYaw,
					MechanicianArmorGeometry.Part.RIGHT_LEG,
					MechanicianArmorGeometry.Part.LEFT_LEG);
			default -> {
			}
		}
	}
}
