package com.tk.gun.client;

import com.tk.gun.block.ArmorDressingStationBlockEntity;
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

/** Draws the dressing player's skin and second skin layer on the authored head cube. */
public final class ArmorDressingHeadSkinLayer
		extends GeoRenderLayer<ArmorDressingStationBlockEntity> {
	public ArmorDressingHeadSkinLayer(
			GeoRenderer<ArmorDressingStationBlockEntity> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack matrices,
			ArmorDressingStationBlockEntity animatable, GeoBone bone,
			RenderLayer renderLayer, VertexConsumerProvider consumers,
			VertexConsumer buffer, float tickDelta, int light, int overlay) {
		if (!"head".equals(bone.getName())) {
			return;
		}
		ArmorDressingStationBlockEntity.Phase phase = animatable.phase();
		if (phase == ArmorDressingStationBlockEntity.Phase.INACTIVE) {
			return;
		}
		AbstractClientPlayerEntity player =
				ArmorDressingClientState.playerAt(animatable.getPos());
		boolean handoff = phase
				== ArmorDressingStationBlockEntity.Phase.ACTIVE
				&& ArmorDressingClientState.dressingHandoffActive(
						animatable.getPos());
		boolean dressingVisual = phase
				== ArmorDressingStationBlockEntity.Phase.DRESSING
				|| handoff;
		if (player != null) {
			boolean mounted = phase
					== ArmorDressingStationBlockEntity.Phase.ACTIVE
					&& animatable.getPos().equals(
							ArmorDressingClientState.mountedStationPos(
									player.getId()));
			if (!dressingVisual && !mounted) {
				return;
			}
		}
		Identifier texture = player == null
				? DefaultSkinHelper.getTexture()
				: player.getSkinTextures().texture();
		VertexConsumer skinBuffer = consumers.getBuffer(
				RenderLayer.getEntityCutoutNoCull(texture));
		float animationSeconds = handoff
				? ArmorStationCreateAnimationRuntime.dressingTailSeconds(
						animatable)
				: (float) animatable.animationSeconds(tickDelta);
		boolean hideSecondLayer =
				dressingVisual && "盔甲着装3".equals(
						animatable.dressingAnimation())
				|| ArmorHeadSkinGeometry.shouldHideStationOverlay(
						player, dressingVisual, animationSeconds);
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
