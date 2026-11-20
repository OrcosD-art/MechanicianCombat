package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import com.tk.gun.item.BackpackPartItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import org.joml.Vector3d;
import net.minecraft.util.math.Vec3d;

/** Draws the skill flag in the same entity-space basis as the player rig. */
public final class WarBannerHeldRenderer
		extends GeoReplacedEntityRenderer<
				AbstractClientPlayerEntity, BackpackPartItem> {
	private static EntityRendererFactory.Context context;
	private static WarBannerHeldRenderer instance;
	private static AbstractClientPlayerEntity activePlayer;
	private static double seconds;

	private WarBannerHeldRenderer(EntityRendererFactory.Context context,
			BackpackPartItem item) {
		super(context, new WarBannerHeldGeoModel(), item);
	}

	public static void setContext(EntityRendererFactory.Context context) {
		WarBannerHeldRenderer.context = context;
	}

	public static void renderIfActive(AbstractClientPlayerEntity player,
			float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		if (context == null || player.getWorld() == null
				|| !WarBannerSkillClientState.isActive(player.getId())
				|| !(TkGunServer.WAR_BANNER instanceof BackpackPartItem item)) {
			return;
		}
		if (instance == null) {
			instance = new WarBannerHeldRenderer(context, item);
		}
		activePlayer = player;
		seconds = WarBannerSkillClientState.animationSeconds(
				player.getId(), player.getWorld().getTime(), tickDelta);
		try {
			EntityRenderer<AbstractClientPlayerEntity> renderer = instance;
			renderer.render(player, yaw, tickDelta, matrices, consumers, light);
		} finally {
			activePlayer = null;
		}
	}

	@Override
	public void actuallyRender(MatrixStack matrices, BackpackPartItem animatable,
			BakedGeoModel model, RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int colour) {
		super.actuallyRender(matrices, animatable, model, renderType,
				consumers, buffer, true, tickDelta,
				LightmapTextureManager.MAX_LIGHT_COORDINATE,
				packedOverlay, colour);
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			BackpackPartItem animatable, GeoBone bone, RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int colour) {
		if (activePlayer != null) {
			WarBannerHeldPose.apply(
					bone, seconds,
					WarBannerSkillClientState.usesGunAnimation(
							activePlayer.getId()));
			if ("flag_maker".equals(bone.getName())
					&& activePlayer == net.minecraft.client.MinecraftClient
							.getInstance().player) {
				Vector3d world = bone.getWorldPosition();
				WarBannerSkillClientState.updateLocator(
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
		if ("flag".equals(bone.getName())) {
			super.renderCubesOfBone(
					matrices, bone, buffer, packedLight, packedOverlay, colour);
		}
	}
}
