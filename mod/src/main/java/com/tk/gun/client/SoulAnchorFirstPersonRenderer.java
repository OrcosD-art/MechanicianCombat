package com.tk.gun.client;

import com.tk.gun.TkGunServer;
import com.tk.gun.TkGun;
import com.tk.gun.item.SoulAnchorFpaItem;
import com.tk.gun.item.SoulAnchorFpaRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.HashSet;
import java.util.Set;

/** Camera-space first-person model for Soul Anchor ready and throw phases. */
public final class SoulAnchorFirstPersonRenderer
		extends GeoItemRenderer<SoulAnchorFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static SoulAnchorFirstPersonRenderer instance;
	private static SoulAnchorFpaRenderState.Mode lastMode =
			SoulAnchorFpaRenderState.Mode.NONE;
	private static ClientPlayerEntity activePlayer;
	private static double seconds;
	private static boolean throwing;
	private static long lastLoggedModeStartedAt = -1L;
	private static long lastBoneLogAt = -1L;
	private static long tracedStartedAt = -1L;
	private static final Set<String> TRACED_BONES = new HashSet<>();

	private SoulAnchorFirstPersonRenderer(SoulAnchorFpaItem item) {
		super(new SoulAnchorFpaGeoModel());
		this.animatable = item;
	}

	public static void render(ClientPlayerEntity player, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		if (!(TkGunServer.SOUL_ANCHOR_FPA_MODEL
				instanceof SoulAnchorFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new SoulAnchorFirstPersonRenderer(item);
		}
		ItemStack stack = new ItemStack(item);
		instance.currentItemStack = stack;
		SoulAnchorFpaRenderState.Mode mode =
				SoulAnchorSkillClientState.ready(player.getId())
						? SoulAnchorFpaRenderState.Mode.READY
						: SoulAnchorFpaRenderState.Mode.THROW;
		SoulAnchorFpaRenderState.set(mode);
		if (mode != lastMode) {
			lastMode = mode;
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("anchor");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		activePlayer = player;
		seconds = SoulAnchorSkillClientState.firstPersonSeconds(
				player.getId(), tickDelta);
		throwing =
				SoulAnchorSkillClientState.firstPersonThrowing(player.getId());
		long startedAt = SoulAnchorSkillClientState.startedAt(player.getId());
		if (startedAt != lastLoggedModeStartedAt) {
			lastLoggedModeStartedAt = startedAt;
			tracedStartedAt = startedAt;
			TRACED_BONES.clear();
			TkGun.LOGGER.info(
					"[mechaniciancombat] Soul Anchor FPA mode={} seconds={} item={}",
					mode, seconds, item);
		}
		try {
			instance.method_3166(stack,
					ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
					matrices, consumers, light, OverlayTexture.DEFAULT_UV);
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
		} finally {
			activePlayer = null;
			SoulAnchorFpaRenderState.set(
					SoulAnchorFpaRenderState.Mode.NONE);
		}
	}

	@Override
	public void preRender(MatrixStack matrices, SoulAnchorFpaItem animatable,
			BakedGeoModel model, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		itemRenderTranslations =
				new Matrix4f(matrices.peek().getPositionMatrix());
		scaleModelForRender(scaleWidth, scaleHeight, matrices, animatable,
				model, isReRender, tickDelta, packedLight, packedOverlay);
		matrices.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			SoulAnchorFpaItem animatable, GeoBone bone, RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int colour) {
		if (activePlayer != null) {
			if (tracedStartedAt == SoulAnchorSkillClientState
					.startedAt(activePlayer.getId())
					&& TRACED_BONES.add(bone.getName())) {
				TkGun.LOGGER.info(
						"[mechaniciancombat] Soul Anchor FPA traversal bone='{}' cubes={}",
						bone.getName(), bone.getCubes().size());
			}
			WarBannerHeldPose.applyAnchorFirstPerson(
					bone, seconds, throwing);
			long now = activePlayer.getWorld().getTime();
			if ("anchor".equals(bone.getName())
					&& now - lastBoneLogAt >= 10L) {
				lastBoneLogAt = now;
				TkGun.LOGGER.info(
						"[mechaniciancombat] Soul Anchor FPA bone pos=({}, {}, {}) rot=({}, {}, {}) scale=({}, {}, {})",
						bone.getPosX(), bone.getPosY(), bone.getPosZ(),
						bone.getRotX(), bone.getRotY(), bone.getRotZ(),
						bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
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
		if ("anchor".equals(bone.getName())) {
			super.renderCubesOfBone(
					matrices, bone, buffer, packedLight, packedOverlay, colour);
		}
	}
}
