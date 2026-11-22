package com.tk.gun.client;

import com.tk.gun.TkGunServer;
import com.tk.gun.item.KineticSwordFpaItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Set;

/** Camera-space playback of the existing Kinetic Sword clips. */
public final class KineticSwordFirstPersonRenderer
		extends GeoItemRenderer<KineticSwordFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static final double FPA_OFFSET_X = 0.28D;
	private static final double FPA_OFFSET_Y = -0.31D;
	private static final double FPA_OFFSET_Z = -0.71D;
	private static final Set<String> SWORD_BONES = Set.of(
			"Great_sword", "B1", "Blade", "C1", "C2");
	private static KineticSwordFirstPersonRenderer instance;
	private static String lastAnimation;
	private static long lastRevision = Long.MIN_VALUE;

	private KineticSwordFirstPersonRenderer(KineticSwordFpaItem item) {
		super(new KineticSwordFpaGeoModel());
		this.animatable = item;
		addRenderLayer(new com.tk.gun.client.rig.KineticSwordEnergyItemLayer(
				this));
		addRenderLayer(
				new com.tk.gun.client.rig.KineticSwordRotateEffectItemLayer(
						this));
	}

	public static void render(ClientPlayerEntity player, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		if (!(TkGunServer.KINETIC_SWORD_FPA_MODEL
				instanceof KineticSwordFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new KineticSwordFirstPersonRenderer(item);
		}
		KineticSwordFirstPersonState.Snapshot snapshot =
				KineticSwordFirstPersonState.resolve(player);
		KineticSwordFirstPersonState.update(snapshot);
		ItemStack stack = new ItemStack(item);
		instance.currentItemStack = stack;
		if (!snapshot.animation().equals(lastAnimation)
				|| snapshot.revision() != lastRevision) {
			lastAnimation = snapshot.animation();
			lastRevision = snapshot.revision();
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers()
					.get("sword_fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		ModelTransformationMode mode =
				player.getMainArm() == Arm.LEFT
						? ModelTransformationMode.FIRST_PERSON_LEFT_HAND
						: ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
		matrices.push();
		try {
			matrices.translate(FPA_OFFSET_X, FPA_OFFSET_Y, FPA_OFFSET_Z);
			instance.method_3166(stack, mode, matrices, consumers, light,
					OverlayTexture.DEFAULT_UV);
		} finally {
			matrices.pop();
		}
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	@Override
	public void preRender(MatrixStack matrices, KineticSwordFpaItem animatable,
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
	public void renderCubesOfBone(MatrixStack matrices, GeoBone bone,
			VertexConsumer buffer, int packedLight, int packedOverlay,
			int colour) {
		if (SWORD_BONES.contains(bone.getName())) {
			super.renderCubesOfBone(matrices, bone, buffer,
					LightmapTextureManager.MAX_LIGHT_COORDINATE,
					packedOverlay, colour);
		}
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			KineticSwordFpaItem animatable, GeoBone bone,
			RenderLayer renderType, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		if (!isReRender && "effects".equals(bone.getName())
				&& net.minecraft.client.MinecraftClient.getInstance()
						.player != null) {
			org.joml.Vector3d world = bone.getWorldPosition();
			com.tk.gun.client.rig.BowgunRigRenderer
					.acceptFirstPersonEffectsPosition(
							net.minecraft.client.MinecraftClient
									.getInstance().player.getId(),
							new net.minecraft.util.math.Vec3d(
									world.x, world.y, world.z));
		}
		if (com.tk.gun.client.rig.KineticSwordEnergyEffect
				.isEnergyBone(bone.getName())
				&& net.minecraft.client.MinecraftClient.getInstance().player
						!= null) {
			com.tk.gun.client.rig.KineticSwordEnergyEffect.applyVisibility(
					bone, net.minecraft.client.MinecraftClient
							.getInstance().player,
					KineticSwordFirstPersonState.animationName(), tickDelta);
		}
		com.tk.gun.client.rig.KineticSwordRotateEffect.apply(
				bone, net.minecraft.client.MinecraftClient.getInstance().player,
				KineticSwordFirstPersonState.animationName(), tickDelta);
		super.renderRecursively(matrices, animatable, bone, renderType,
				consumers, buffer, isReRender, tickDelta,
				LightmapTextureManager.MAX_LIGHT_COORDINATE,
				packedOverlay, colour);
	}
}
