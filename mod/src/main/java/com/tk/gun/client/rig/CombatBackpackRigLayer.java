package com.tk.gun.client.rig;

import com.tk.gun.client.CombatBackpackGeoRenderer;
import com.tk.gun.item.CombatBackpackItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

/** Draws the backpack on the chest bone of the custom Mechanic player rig. */
public final class CombatBackpackRigLayer extends GeoRenderLayer<BowgunRig> {
	private static final Map<Integer, ItemStack> LAST_EQUIPPED = new HashMap<>();

	public CombatBackpackRigLayer(BowgunRigRenderer renderer) {
		super(renderer);
	}

	public static void clearCachedStack(int entityId) {
		LAST_EQUIPPED.remove(entityId);
	}

	@Override
	public void renderForBone(MatrixStack matrices, BowgunRig animatable, GeoBone bone,
							  RenderLayer renderLayer, VertexConsumerProvider consumers,
							  VertexConsumer buffer, float tickDelta, int light, int overlay) {
		if (!"chest".equals(bone.getName())) {
			return;
		}
		Entity entity = ((BowgunRigRenderer) getRenderer()).getCurrentEntity();
		if (!(entity instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		ItemStack stack = CombatBackpackItem.equippedStack(player);
		if (!stack.isEmpty()) {
			LAST_EQUIPPED.put(player.getId(), stack.copy());
		} else {
			stack = LAST_EQUIPPED.getOrDefault(player.getId(), ItemStack.EMPTY);
		}
		if (stack.isEmpty()) {
			return;
		}
		matrices.push();
		try {
			matrices.translate(0.0D, 0.75D, 0.0D);
			CombatBackpackGeoRenderer.getRig().renderAttached(
					stack, player, matrices, consumers, light, tickDelta);
		} finally {
			matrices.pop();
		}
	}
}
