package com.tk.gun.client;

import com.tk.gun.block.ArmorDressingStationBlock;
import com.tk.gun.block.ArmorDressingStationBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class ArmorDressingStationRenderer
		extends GeoBlockRenderer<ArmorDressingStationBlockEntity> {
	public ArmorDressingStationRenderer() {
		super(new ArmorDressingStationGeoModel());
		addRenderLayer(new ArmorDressingHeadSkinLayer(this));
	}

	@Override
	protected Direction getFacing(ArmorDressingStationBlockEntity animatable) {
		var state = animatable.getCachedState();
		return state.contains(ArmorDressingStationBlock.FACING)
				? state.get(ArmorDressingStationBlock.FACING)
				: Direction.NORTH;
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			ArmorDressingStationBlockEntity animatable, GeoBone bone,
			RenderLayer renderType, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int packedColor) {
		ArmorDressingStationBlockEntity.Phase phase = animatable.phase();
		if (bone.getParent() == null) {
			ArmorStationCreateAnimationRuntime.applyStationRoot(
					animatable, bone, tickDelta);
		}
		if ("body".equals(bone.getName())) {
			var player = ArmorDressingClientState.playerAt(
					animatable.getPos());
			boolean mounted = player != null
					&& ArmorDressingClientState.mountedStationPos(
							player.getId()) != null;
			boolean handoff = ArmorDressingClientState
					.dressingHandoffActive(animatable.getPos());
			float scale = phase == ArmorDressingStationBlockEntity.Phase.DRESSING
					|| phase == ArmorDressingStationBlockEntity.Phase.ACTIVE
							&& (mounted || handoff) ? 1.0F : 0.0F;
			bone.setScaleX(scale);
			bone.setScaleY(scale);
			bone.setScaleZ(scale);
		}
		super.renderRecursively(matrices, animatable, bone, renderType,
				consumers, buffer, isReRender, tickDelta, packedLight,
				packedOverlay, packedColor);
	}
}
