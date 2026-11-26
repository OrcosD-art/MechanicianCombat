package com.tk.gun.client;

import com.tk.gun.entity.ArmorDressingPerformerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Draws the dressing player's skin and second skin layer on the performer head. */
public final class ArmorDressingPerformerHeadSkinLayer
		extends GeoRenderLayer<ArmorDressingPerformerEntity> {
	public ArmorDressingPerformerHeadSkinLayer(
			GeoRenderer<ArmorDressingPerformerEntity> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack matrices,
			ArmorDressingPerformerEntity animatable, GeoBone bone,
			RenderLayer renderLayer, VertexConsumerProvider consumers,
			VertexConsumer buffer, float tickDelta, int light, int overlay) {
		if (!"head".equals(bone.getName())) {
			return;
		}
		AbstractClientPlayerEntity player = animatable.getWorld()
				.getEntityById(animatable.playerId())
				instanceof AbstractClientPlayerEntity found ? found : null;
		Identifier texture = player == null
				? DefaultSkinHelper.getTexture()
				: player.getSkinTextures().texture();
		VertexConsumer skinBuffer = consumers.getBuffer(
				RenderLayer.getEntityCutoutNoCull(texture));
		boolean hideSecondLayer =
				"盔甲着装3".equals(animatable.animationName())
				|| ArmorHeadSkinGeometry.shouldHideOverlay(
						player, true,
						(float) animatable.animationSeconds(tickDelta));
		for (GeoCube cube : bone.getCubes()) {
			getRenderer().renderCube(matrices,
					ArmorHeadSkinGeometry.base(cube),
					skinBuffer, light, overlay, -1);
			if (!hideSecondLayer) {
				getRenderer().renderCube(matrices,
						ArmorHeadSkinGeometry.overlay(cube),
						skinBuffer, light, overlay, -1);
			}
		}
	}
}
