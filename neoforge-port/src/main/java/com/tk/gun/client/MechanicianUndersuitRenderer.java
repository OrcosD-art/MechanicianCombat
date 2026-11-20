package com.tk.gun.client;

import com.tk.gun.item.MechanicianArmorPiece;
import com.tk.gun.item.MechanicianUndersuitItem;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/** Accessories renderer for the full-body undersuit. */
public final class MechanicianUndersuitRenderer implements AccessoryRenderer {
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <M extends LivingEntity> void render(ItemStack stack,
												 SlotReference reference,
												 MatrixStack matrices,
												 EntityModel<M> model,
												 VertexConsumerProvider consumers,
												 int light, float limbAngle,
												 float limbDistance,
												 float tickDelta,
												 float animationProgress,
												 float headYaw,
												 float headPitch) {
		if (!(model instanceof PlayerEntityModel playerModel)) {
			return;
		}
		MechanicianArmorGeometry.renderAll(
				MechanicianArmorPiece.UNDERSUIT, matrices, consumers,
				playerModel, light, reference.entity(), limbAngle,
				limbDistance, headYaw);
		if (MechanicianUndersuitItem.isWearingOuterSet(
				reference.entity())) {
			HelmetClientState.Pose pose =
					HelmetClientState.pose(reference.entity());
			MechanicianArmorGeometry.renderHelmet(
					matrices, consumers, playerModel, light,
					reference.entity(), limbAngle, limbDistance,
					headYaw, pose.animation(), pose.seconds());
		}
	}

	@Override
	public boolean shouldRenderInFirstPerson(net.minecraft.util.Arm arm,
											 ItemStack stack,
											 SlotReference reference) {
		return true;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <M extends LivingEntity> void renderOnFirstPerson(
			net.minecraft.util.Arm arm, ItemStack stack,
			SlotReference reference, MatrixStack matrices,
			EntityModel<M> model, VertexConsumerProvider consumers, int light) {
		if (!(model instanceof PlayerEntityModel playerModel)) {
			return;
		}
		MechanicianArmorGeometry.Part part =
				arm == net.minecraft.util.Arm.RIGHT
						? MechanicianArmorGeometry.Part.RIGHT_ARM
						: MechanicianArmorGeometry.Part.LEFT_ARM;
		MechanicianArmorGeometry.render(
				MechanicianArmorPiece.UNDERSUIT, matrices, consumers,
				playerModel, light, reference.entity(), 0.0F, 0.0F,
				0.0F, part);
	}
}
