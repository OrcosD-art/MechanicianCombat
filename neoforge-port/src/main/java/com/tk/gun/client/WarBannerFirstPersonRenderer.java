package com.tk.gun.client;

import com.tk.gun.TkGunServer;
import com.tk.gun.item.WarBannerFpaItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Vector3d;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Camera-space War Banner rendering while skill_flag is active. */
public final class WarBannerFirstPersonRenderer
		extends GeoItemRenderer<WarBannerFpaItem> {
	/** First-person authoring camera is the player's head pivot at y=24px. */
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static WarBannerFirstPersonRenderer instance;
	private static ClientPlayerEntity activePlayer;
	private static long lastStartedAt = -1L;
	private static double seconds;

	private WarBannerFirstPersonRenderer(WarBannerFpaItem item) {
		super(new WarBannerFpaGeoModel());
		this.animatable = item;
	}

	public static void render(ClientPlayerEntity player, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		if (!(TkGunServer.WAR_BANNER_FPA_MODEL
				instanceof WarBannerFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new WarBannerFirstPersonRenderer(item);
		}
		ItemStack renderStack = new ItemStack(item);
		// GeoItemRenderer#getInstanceId delegates through currentItemStack; set it
		// before resetting the controller, otherwise the first frame can trigger
		// an NPE while currentItemStack is still null.
		instance.currentItemStack = renderStack;
		long startedAt = WarBannerSkillClientState.startedAt(player.getId());
		if (startedAt != lastStartedAt) {
			lastStartedAt = startedAt;
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		activePlayer = player;
		seconds = WarBannerSkillClientState.animationSeconds(
				player.getId(), player.getWorld().getTime(), tickDelta);
		try {
			instance.renderByItem(renderStack,
					ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
					matrices, consumers, light, OverlayTexture.DEFAULT_UV);
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
		} finally {
			activePlayer = null;
		}
	}

	@Override
	public void preRender(MatrixStack matrices, WarBannerFpaItem animatable,
			BakedGeoModel model, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		// render normally behaves like a normal item renderer and adds a
		// +0.5/+0.51/+0.5 center offset.  The FPA file is already authored in
		// player-model coordinates, so apply only the head-pivot-to-camera
		// translation and do not inherit that item-space offset.
		itemRenderTranslations =
				new Matrix4f(matrices.peek().getPositionMatrix());
		scaleModelForRender(scaleWidth, scaleHeight, matrices, animatable,
				model, isReRender, tickDelta, packedLight, packedOverlay);
		matrices.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			WarBannerFpaItem animatable, GeoBone bone, RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int colour) {
		if (activePlayer != null) {
			// Keep the controller responsible for time progression, but enforce
			// the converted FPA channels at the final bone matrix stage. This
			// prevents the item renderer from dropping position/scale channels
			// while retaining rotation-only playback.
			WarBannerHeldPose.applyFirstPerson(bone, seconds);
			if ("flag".equals(bone.getName())) {
				Vector3d local = bone.getWorldPosition();
				var camera = MinecraftClient.getInstance()
						.gameRenderer.getCamera().getPos();
				WarBannerSkillClientState.updateLocator(
						activePlayer.getId(),
						camera.add(local.x, local.y, local.z));
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
