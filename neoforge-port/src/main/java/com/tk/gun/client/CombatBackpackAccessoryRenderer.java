package com.tk.gun.client;

import com.tk.gun.TkGun;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/** Accessories renderer for players still using the vanilla player renderer. */
public final class CombatBackpackAccessoryRenderer implements AccessoryRenderer {
	private static final java.util.Set<String> ATTACH_DIAGNOSTICS =
			new java.util.HashSet<>();
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <M extends LivingEntity> void render(ItemStack stack, SlotReference reference,
												 MatrixStack matrices, EntityModel<M> model,
												 VertexConsumerProvider consumers, int light,
												 float limbAngle, float limbDistance, float tickDelta,
												 float animationProgress, float headYaw, float headPitch) {
		// Holding a gun renders the backpack from CombatBackpackRigLayer.
		// Rendering the Accessories copy as well causes a second antenna C3.
		if (reference.entity() instanceof net.minecraft.entity.player.PlayerEntity player
				&& TkGun.holdingGun(player)) {
			return;
		}
		if (!(model instanceof PlayerEntityModel playerModel)) {
			return;
		}
		net.minecraft.client.model.ModelTransform bodyTransform =
				playerModel.body.getTransform();
		String diagnosticKey = reference.entity().getId() + ":"
				+ JumpBackpackClientState.phase(reference.entity().getId());
		if (JumpBackpackClientState.isActive(reference.entity().getId())
				&& ATTACH_DIAGNOSTICS.add(diagnosticKey)) {
			TkGun.LOGGER.info(
					"[mechaniciancombat] backpack chest attach part=body pivot=({},{},{}) "
							+ "rotation=({},{},{})",
					bodyTransform.pivotX, bodyTransform.pivotY, bodyTransform.pivotZ,
					bodyTransform.pitch, bodyTransform.yaw, bodyTransform.roll);
		}
		matrices.push();
		try {
			AccessoryRenderer.translateToChest(matrices, playerModel, reference.entity());
			// translateToChest already applies the 1/16 player-model scale. Offsets below
			// are therefore skin pixels, not blocks.
			matrices.translate(0.5D, 0.75D, -0.25D);
			matrices.scale(-2.0F, 2.0F, -2.0F);
			CombatBackpackGeoRenderer.getAccessory().renderAttached(
					stack, reference.entity(), matrices, consumers, light, tickDelta);
		} finally {
			matrices.pop();
		}
	}

	@Override
	public boolean shouldRenderInFirstPerson(net.minecraft.util.Arm arm, ItemStack stack,
											 SlotReference reference) {
		return false;
	}
}
