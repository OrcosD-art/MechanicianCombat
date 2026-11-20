package com.tk.gun.client;

import com.tk.gun.TkGunServer;
import com.tk.gun.TkGun;
import com.tk.gun.item.CombatPropFpaItem;
import com.tk.gun.item.CombatPropFpaRenderState;
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

import java.util.Set;

/** Camera-space renderer for hunter-anchor and backpack-turret first-person props. */
public final class CombatPropFirstPersonRenderer
		extends GeoItemRenderer<CombatPropFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static CombatPropFirstPersonRenderer hunterAnchor;
	private static CombatPropFirstPersonRenderer portableTurret;
	private static CombatPropFirstPersonRenderer autoTurret;

	private final String assetName;
	private final String visibleBone;
	private final Set<String> hiddenBones;
	private ClientPlayerEntity activePlayer;
	private double seconds;
	private String animationName;
	private CombatPropFpaRenderState.Mode lastMode =
			CombatPropFpaRenderState.Mode.NONE;
	private long lastStartedAt = -1L;

	private CombatPropFirstPersonRenderer(CombatPropFpaItem item,
			String assetName, String visibleBone, Set<String> hiddenBones) {
		super(new CombatPropFpaGeoModel(assetName));
		this.animatable = item;
		this.assetName = assetName;
		this.visibleBone = visibleBone;
		this.hiddenBones = hiddenBones;
	}

	public static boolean renderHunterAnchor(ClientPlayerEntity player,
			float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		if (!(TkGunServer.HUNTER_ANCHOR_FPA_MODEL
				instanceof CombatPropFpaItem item)
				|| !HunterSpearSkillClientState.isActive(player.getId())) {
			return false;
		}
		if (hunterAnchor == null) {
			hunterAnchor = new CombatPropFirstPersonRenderer(
					item, "hunter_anchor_fpa", "spear",
					Set.of("body", "flag", "chest", "head",
							"arm_left", "arm_right",
							"leg_right", "leg_left"));
		}
		hunterAnchor.renderProp(item, player, tickDelta, matrices, consumers, light,
				CombatPropFpaRenderState.Mode.HUNTER_ANCHOR, "fpa",
				HunterSpearSkillClientState.animationSeconds(
						player.getId(), player.getWorld().getTime(), tickDelta),
				HunterSpearSkillClientState.startedAt(player.getId()));
		return true;
	}

	public static boolean renderTurret(ClientPlayerEntity player,
			float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		CombatPropFpaClientState.Prop prop =
				CombatPropFpaClientState.prop(player.getId());
		if (prop == CombatPropFpaClientState.Prop.NONE
				|| prop == CombatPropFpaClientState.Prop.HUNTER_ANCHOR) {
			return false;
		}
		if (!CombatBackpackClientState.turretActivated(player.getId())) {
			return false;
		}
		CombatPropFpaRenderState.Mode mode =
				CombatPropFpaClientState.turretMode(player.getId());
		String animation = switch (mode) {
			case AIM -> "aim";
			case CD -> "CD";
			default -> "ready";
		};
		if (prop == CombatPropFpaClientState.Prop.PORTABLE_TURRET) {
			if (!(TkGunServer.PORTABLE_TURRET_FPA_MODEL
					instanceof CombatPropFpaItem item)) {
				return false;
			}
			if (portableTurret == null) {
				portableTurret = new CombatPropFirstPersonRenderer(
						item, "portable_turret_fpa", "C3",
						Set.of("body", "chest", "head", "arm_left",
								"arm_right", "leg_right", "leg_left"));
			}
			portableTurret.renderProp(item, player, tickDelta, matrices, consumers,
					light, mode, animation,
					CombatPropFpaClientState.turretSeconds(
							player.getId(), tickDelta),
					CombatPropFpaClientState.turretStartedAt(player.getId()));
			return true;
		}
		if (!(TkGunServer.AUTO_TURRET_FPA_MODEL
				instanceof CombatPropFpaItem item)) {
			return false;
		}
		if (autoTurret == null) {
			autoTurret = new CombatPropFirstPersonRenderer(
					item, "auto_turret_fpa", "C3",
					Set.of("body", "chest", "head", "arm_left",
							"arm_right", "leg_right", "leg_left"));
		}
		autoTurret.renderProp(item, player, tickDelta, matrices, consumers,
				light, mode, animation,
				CombatPropFpaClientState.turretSeconds(
						player.getId(), tickDelta),
				CombatPropFpaClientState.turretStartedAt(player.getId()));
		return true;
	}

	private void renderProp(CombatPropFpaItem item, ClientPlayerEntity player,
			float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light,
			CombatPropFpaRenderState.Mode mode, String animation,
			double animationSeconds, long startedAt) {
		ItemStack stack = new ItemStack(item);
		currentItemStack = stack;
		if (mode != lastMode || startedAt != lastStartedAt) {
			lastMode = mode;
			lastStartedAt = startedAt;
			TkGun.LOGGER.info(
					"[mechaniciancombat] {} FPA mode={} phase={} seconds={} player={}",
					assetName, mode,
					CombatBackpackClientState.statusPhase(player.getId()),
					animationSeconds, player.getName().getString());
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(getInstanceId(item));
			var controller = manager.getAnimationControllers().get("fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		CombatPropFpaRenderState.set(mode);
		activePlayer = player;
		seconds = animationSeconds;
		animationName = animation;
		try {
			method_3166(stack, ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
					matrices, consumers, light, OverlayTexture.DEFAULT_UV);
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
		} finally {
			activePlayer = null;
			CombatPropFpaRenderState.set(CombatPropFpaRenderState.Mode.NONE);
		}
	}

	@Override
	public void preRender(MatrixStack matrices, CombatPropFpaItem animatable,
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
			CombatPropFpaItem animatable, GeoBone bone, RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int colour) {
		if (activePlayer != null) {
			CombatPropHeldPose.apply(
					bone, seconds, assetName, animationName);
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
		if (visibleBone.equals(bone.getName())
				|| !hiddenBones.contains(bone.getName())
						&& bone.getCubes().isEmpty()) {
			super.renderCubesOfBone(
					matrices, bone, buffer, packedLight, packedOverlay, colour);
		}
	}
}
