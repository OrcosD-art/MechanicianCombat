package com.tk.gun.client;

import com.tk.gun.TkGunServer;
import com.tk.gun.item.MechanicianHunterSpearItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/** Draws the hunter spear from the authored deployment animation. */
public final class HunterSpearHeldRenderer
		extends GeoReplacedEntityRenderer<
				AbstractClientPlayerEntity, MechanicianHunterSpearItem> {
	private static EntityRendererFactory.Context context;
	private static HunterSpearHeldRenderer instance;
	private static AbstractClientPlayerEntity activePlayer;
	private static double seconds;

	private HunterSpearHeldRenderer(EntityRendererFactory.Context context,
			MechanicianHunterSpearItem item) {
		super(context, new HunterSpearDeployGeoModel(), item);
	}

	public static void setContext(EntityRendererFactory.Context context) {
		HunterSpearHeldRenderer.context = context;
	}

	public static void renderIfActive(AbstractClientPlayerEntity player,
			float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		if (context == null || player.getWorld() == null
				|| !HunterSpearSkillClientState.isActive(player.getId())
				|| !(TkGunServer.HUNTER_SPEAR
				instanceof MechanicianHunterSpearItem item)) {
			return;
		}
		if (instance == null) {
			instance = new HunterSpearHeldRenderer(context, item);
		}
		activePlayer = player;
		seconds = HunterSpearSkillClientState.animationSeconds(
				player.getId(), player.getWorld().getTime(), tickDelta);
		try {
			EntityRenderer<AbstractClientPlayerEntity> renderer = instance;
			renderer.render(player, yaw, tickDelta, matrices, consumers, light);
		} finally {
			activePlayer = null;
		}
	}

	@Override
	public void actuallyRender(MatrixStack matrices,
			MechanicianHunterSpearItem animatable, BakedGeoModel model,
			RenderLayer renderType, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		super.actuallyRender(matrices, animatable, model, renderType,
				consumers, buffer, true, tickDelta,
				LightmapTextureManager.MAX_LIGHT_COORDINATE,
				packedOverlay, colour);
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			MechanicianHunterSpearItem animatable, GeoBone bone,
			RenderLayer renderType, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		if (activePlayer != null) {
			HunterSpearHeldPose.apply(bone, seconds);
			if ("flag_maker".equals(bone.getName())
					&& activePlayer == MinecraftClient.getInstance().player) {
				Vector3d world = bone.getWorldPosition();
				HunterSpearSkillClientState.updateMarker(
						activePlayer.getId(),
						new Vec3d(world.x, world.y, world.z));
			}
		}
		super.renderRecursively(matrices, animatable, bone, renderType,
				consumers, buffer, isReRender, tickDelta,
				LightmapTextureManager.MAX_LIGHT_COORDINATE,
				packedOverlay, colour);
	}

	@Override
	public void renderCubesOfBone(MatrixStack matrices, GeoBone bone,
			VertexConsumer buffer, int packedLight, int packedOverlay,
			int colour) {
		if ("spear".equals(bone.getName())) {
			super.renderCubesOfBone(
					matrices, bone, buffer, packedLight, packedOverlay, colour);
		}
	}
}
