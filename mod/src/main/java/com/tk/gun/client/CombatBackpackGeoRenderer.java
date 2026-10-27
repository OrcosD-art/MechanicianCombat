package com.tk.gun.client;

import com.tk.gun.item.CombatBackpackItem;
import com.tk.gun.item.CombatBackpackAnimation;
import com.tk.gun.item.BackpackUpgradeData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.List;

/** Renders the wearable backpack at an already-transformed chest attachment point. */
public final class CombatBackpackGeoRenderer extends GeoItemRenderer<CombatBackpackItem> {
	private static CombatBackpackGeoRenderer accessoryInstance;
	private static CombatBackpackGeoRenderer rigInstance;
	private static CombatBackpackGeoRenderer previewInstance;
	private static final java.util.Map<Integer, Vec3d> FRONT_POSITIONS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3d> FRONT_LOCAL_OFFSETS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> FRONT_SAMPLE_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3d> REAR_POSITIONS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3d> REAR_LOCAL_OFFSETS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> REAR_SAMPLE_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3d> SUMMON_LOCATORS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> SUMMON_LOCATOR_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3d> CANONICAL_FRONT_OFFSETS =
			new java.util.HashMap<>();
	private static final java.util.Map<Integer, AimFilter> AIM_FILTERS =
			new java.util.HashMap<>();
	private static final double AIM_DEADBAND_COS =
			Math.cos(Math.toRadians(2.0D));
	private static final double AIM_SMOOTHING = 0.25D;
	private LivingEntity currentEntity;
	private boolean suppressC3Cubes;
	private boolean autoAimCubesOnly;
	private boolean allowAutoAimCubeRender;
	private boolean attachedCoreInstalled;
	private BackpackUpgradeData.LauncherType attachedLauncherType =
			BackpackUpgradeData.LauncherType.NONE;

	private static final class AimFilter {
		int targetId = -1;
		long tick = -1L;
		Vec3d direction = Vec3d.ZERO;
	}

	private CombatBackpackGeoRenderer(GeoModel<CombatBackpackItem> model) {
		super(model);
	}

	public static CombatBackpackGeoRenderer getAccessory() {
		if (accessoryInstance == null) {
			accessoryInstance = new CombatBackpackGeoRenderer(new CombatBackpackGeoModel());
		}
		return accessoryInstance;
	}

	public static CombatBackpackGeoRenderer getRig() {
		if (rigInstance == null) {
			rigInstance = new CombatBackpackGeoRenderer(new CombatBackpackRigGeoModel());
		}
		return rigInstance;
	}

	public static CombatBackpackGeoRenderer getPreview() {
		if (previewInstance == null) {
			previewInstance = new CombatBackpackGeoRenderer(new CombatBackpackGeoModel());
		}
		return previewInstance;
	}

	/**
	 * Draws the selected backpack through GeckoLib's item renderer while forcing
	 * the Maintenance animation. The caller supplies the GUI transform and scissor.
	 */
	public void renderMaintenancePreview(
			ItemStack stack,
			MatrixStack matrices,
			VertexConsumerProvider consumers,
			int light,
			int overlay) {
		if (!(stack.getItem() instanceof CombatBackpackItem)) {
			return;
		}
		this.attachedLauncherType = BackpackUpgradeData.canUseLauncher(stack)
				? BackpackUpgradeData.launcherType(stack)
				: BackpackUpgradeData.LauncherType.NONE;
		this.attachedCoreInstalled = BackpackUpgradeData.hasCore(stack);
		CombatBackpackAnimation.beginModificationPreview();
		try {
			// method_3166 is GeckoLib's item-render entrypoint in this mapping set.
			super.method_3166(stack, ModelTransformationMode.GUI, matrices, consumers, light, overlay);
		} finally {
			doPostRenderCleanup();
			CombatBackpackAnimation.endModificationPreview();
			this.attachedLauncherType = BackpackUpgradeData.LauncherType.NONE;
			this.attachedCoreInstalled = false;
		}
	}

	public void renderAttached(ItemStack stack, LivingEntity entity, MatrixStack matrices,
							   VertexConsumerProvider consumers, int light, float tickDelta) {
		if (!(stack.getItem() instanceof CombatBackpackItem item)) {
			return;
		}
		this.currentEntity = entity;
		CombatBackpackAnimation.setRenderingPlayer(entity.getId());
		this.animatable = item;
		this.currentItemStack = stack;
		this.attachedLauncherType = BackpackUpgradeData.canUseLauncher(stack)
				? BackpackUpgradeData.launcherType(stack)
				: BackpackUpgradeData.LauncherType.NONE;
		this.attachedCoreInstalled = BackpackUpgradeData.hasCore(stack);
		this.renderPerspective = ModelTransformationMode.NONE;
		try {
			Identifier texture = getTextureLocation(item);
			RenderLayer layer = getRenderType(item, texture, consumers, tickDelta);
			VertexConsumer buffer = consumers.getBuffer(layer);
			defaultRender(matrices, item, consumers, layer, buffer, tickDelta, tickDelta, light);
		} finally {
			CombatBackpackAnimation.clearRenderingPlayer();
			this.currentEntity = null;
			this.attachedLauncherType = BackpackUpgradeData.LauncherType.NONE;
			this.attachedCoreInstalled = false;
			doPostRenderCleanup();
		}
	}

	public static Vec3d frontPosition(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		net.minecraft.entity.Entity entity = client.world == null ? null
				: client.world.getEntityById(playerId);
		Vec3d canonical = CANONICAL_FRONT_OFFSETS.get(playerId);
		if (canonical != null && entity != null) {
			return entity.getPos().add(rotateY(canonical, modelYaw(entity)));
		}
		Vec3d exact = FRONT_POSITIONS.get(playerId);
		Long sampleTick = FRONT_SAMPLE_TICKS.get(playerId);
		if (entity != null && exact != null && sampleTick != null
				&& Math.abs(client.world.getTime() - sampleTick) <= 2L) {
			return exact;
		}
		Vec3d local = FRONT_LOCAL_OFFSETS.get(playerId);
		if (local != null && entity != null) {
			return entity.getPos().add(rotateY(local, modelYaw(entity)));
		}
		return exact;
	}

	/** Frozen portable-launcher, non-aim origin shared by every launcher state. */
	public static Vec3d canonicalLaunchPosition(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		net.minecraft.entity.Entity entity = client.world == null ? null
				: client.world.getEntityById(playerId);
		Vec3d canonical = CANONICAL_FRONT_OFFSETS.get(playerId);
		if (canonical != null && entity != null) {
			return entity.getPos().add(rotateY(canonical, modelYaw(entity)));
		}
		return frontPosition(playerId);
	}

	public static Vec3d rearPosition(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		net.minecraft.entity.Entity entity = client.world == null ? null
				: client.world.getEntityById(playerId);
		Vec3d exact = REAR_POSITIONS.get(playerId);
		Long sampleTick = REAR_SAMPLE_TICKS.get(playerId);
		if (entity != null && exact != null && sampleTick != null
				&& Math.abs(client.world.getTime() - sampleTick) <= 2L) {
			return exact;
		}
		Vec3d local = REAR_LOCAL_OFFSETS.get(playerId);
		if (local != null && entity != null) {
			return entity.getPos().add(rotateY(local, modelYaw(entity)));
		}
		return exact;
	}

	public static Vec3d summonLocatorPosition(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}
		Long tick = SUMMON_LOCATOR_TICKS.get(playerId);
		if (tick == null || Math.abs(client.world.getTime() - tick) > 3L) {
			return null;
		}
		return SUMMON_LOCATORS.get(playerId);
	}

	@Override
	public void renderRecursively(MatrixStack matrices, CombatBackpackItem animatable, GeoBone bone,
								  RenderLayer renderType, VertexConsumerProvider consumers,
								  VertexConsumer buffer, boolean isReRender, float tickDelta,
								  int packedLight, int packedOverlay, int colour) {
		boolean coreInstalled = attachedCoreInstalled;
		boolean launcherInstalled = coreInstalled
				&& attachedLauncherType != BackpackUpgradeData.LauncherType.NONE;
		boolean autoLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.AUTO;
		boolean summonLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
		Quaternionf aimRotation = null;
		if (currentEntity != null && launcherInstalled
				&& CombatBackpackAnimation.phase(currentEntity.getId())
				== CombatBackpackAnimation.Phase.AIM) {
			if (autoLauncher && "C3".equals(bone.getName())) {
				bone.updateRotation(0.0F, 0.0F, 0.0F);
			} else if ((!autoLauncher && "C3".equals(bone.getName()))
					|| (autoLauncher && "auto_C3".equals(bone.getName()))) {
				aimRotation = calculateAimRotation(matrices, currentEntity, bone);
				if (aimRotation != null) {
					Vector3f euler = aimRotation.getEulerAnglesZYX(new Vector3f());
					bone.updateRotation((float) Math.toDegrees(euler.x()),
							(float) Math.toDegrees(euler.y()),
							(float) Math.toDegrees(euler.z()));
				}
			}
		}
		switch (bone.getName()) {
			case "core" -> bone.setHidden(!coreInstalled);
			case "C1", "C2", "C3" -> bone.setHidden(!launcherInstalled);
			case "auto_C1" -> bone.setHidden(!autoLauncher);
			case "auto_ejector", "auto_box", "auto_sight", "auto_barrel" ->
					bone.setHidden(!autoLauncher);
			case "summon_C1", "summon_C2", "summon_C3", "summon_locator" ->
					bone.setHidden(!summonLauncher);
			default -> {
			}
		}
		if (aimRotation != null) {
			renderC3Independently(matrices, bone, buffer, packedLight, packedOverlay,
					colour, aimRotation);
			boolean previousSuppress = suppressC3Cubes;
			boolean previousAutoAimCubesOnly = autoAimCubesOnly;
			boolean previousAllowAutoAimCubeRender = allowAutoAimCubeRender;
			suppressC3Cubes = true;
			autoAimCubesOnly = autoLauncher && "auto_C3".equals(bone.getName());
			allowAutoAimCubeRender = false;
			try {
				super.renderRecursively(matrices, animatable, bone, renderType, consumers,
						buffer, isReRender, tickDelta, packedLight, packedOverlay, colour);
			} finally {
				suppressC3Cubes = previousSuppress;
				autoAimCubesOnly = previousAutoAimCubesOnly;
				allowAutoAimCubeRender = previousAllowAutoAimCubeRender;
			}
		} else {
			super.renderRecursively(matrices, animatable, bone, renderType, consumers, buffer,
					isReRender, tickDelta, packedLight, packedOverlay, colour);
		}
		if (currentEntity != null) {
			recordLocator(matrices, bone, currentEntity, autoLauncher);
			if ("summon_locator".equals(bone.getName())) {
				Vector3d position = bone.getWorldPosition();
				SUMMON_LOCATORS.put(currentEntity.getId(),
						new Vec3d(position.x, position.y, position.z));
				SUMMON_LOCATOR_TICKS.put(currentEntity.getId(),
						currentEntity.getWorld().getTime());
			}
		}
	}

	@Override
	public void renderCubesOfBone(MatrixStack matrices, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay, int colour) {
		boolean autoLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.AUTO;
		boolean summonLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
		if (("C3".equals(bone.getName()) || "auto_C3".equals(bone.getName()))
				&& suppressC3Cubes) {
			return;
		}
		if (autoLauncher && autoAimCubesOnly
				&& ("auto_ejector".equals(bone.getName())
						|| "auto_box".equals(bone.getName())
						|| "auto_sight".equals(bone.getName())
						|| "auto_barrel".equals(bone.getName()))) {
			return;
		}
		if (autoLauncher && "auto_C3".equals(bone.getName())
				&& !allowAutoAimCubeRender) {
			return;
		}
		if ((autoLauncher || summonLauncher) && ("C1".equals(bone.getName())
				|| "C2".equals(bone.getName())
				|| "C3".equals(bone.getName()))) {
			return;
		}
		super.renderCubesOfBone(matrices, bone, buffer, packedLight, packedOverlay, colour);
	}

	private void renderC3Independently(MatrixStack matrices, GeoBone bone,
									   VertexConsumer buffer, int packedLight,
									   int packedOverlay, int colour,
									   Quaternionf rotation) {
		boolean previousAllowAutoAimCubeRender = allowAutoAimCubeRender;
		allowAutoAimCubeRender = true;
		matrices.push();
		try {
			RenderUtil.translateMatrixToBone(matrices, bone);
			RenderUtil.translateToPivotPoint(matrices, bone);
			matrices.multiply(rotation);
			RenderUtil.scaleMatrixForBone(matrices, bone);
			RenderUtil.translateAwayFromPivotPoint(matrices, bone);
			renderCubesOfBone(matrices, bone, buffer, packedLight, packedOverlay, colour);
		} finally {
			matrices.pop();
			allowAutoAimCubeRender = previousAllowAutoAimCubeRender;
		}
	}

	private static Quaternionf calculateAimRotation(MatrixStack matrices, LivingEntity entity,
													 GeoBone c3) {
		int targetId = CombatBackpackClientState.targetEntityId(entity.getId());
		if (targetId < 0) {
			AIM_FILTERS.remove(entity.getId());
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		net.minecraft.entity.Entity targetEntity = client.world == null ? null
				: client.world.getEntityById(targetId);
		if (targetEntity == null) {
			AIM_FILTERS.remove(entity.getId());
			return null;
		}
		Vec3d targetWorld = targetEntity.getBoundingBox().getCenter();
		// r306: filter a launcher-independent world direction, so turret motion
		// cannot feed back into the next smoothing sample.
		Vec3d aimOrigin = entity.getBoundingBox().getCenter();
		Vec3d aimAxis = targetWorld.subtract(aimOrigin);
		if (aimAxis.lengthSquared() < 1.0E-8D) {
			return null;
		}
		// Shared by C3 and auto_C3, covering every launcher using this renderer.
		Vec3d filteredDirection = filterAimDirection(
				entity.getId(), targetId, aimAxis.normalize(),
				entity.getWorld().getTime());

		String frontName = "auto_C3".equals(c3.getName()) ? "auto_front" : "front";
		String rearName = frontName.equals("auto_front") ? "auto_rear" : "rear";
		GeoBone front = findBoneRecursive(c3, frontName);
		GeoBone rear = findBoneRecursive(c3, rearName);
		Vector3f localAxis = front != null && rear != null
				? new Vector3f(
						front.getPivotX() - rear.getPivotX(),
						front.getPivotY() - rear.getPivotY(),
						front.getPivotZ() - rear.getPivotZ()).normalize()
				: new Vector3f(0.0F, 0.0F, -1.0F);
		Matrix3f parentNormal = new Matrix3f(matrices.peek().getNormalMatrix());
		Vector3f targetInParent = parentNormal.invert().transform(
				new Vector3f((float) filteredDirection.x,
						(float) filteredDirection.y, (float) filteredDirection.z))
				.normalize();
		return new Quaternionf().rotationTo(localAxis, targetInParent);
	}

	private static Vec3d filterAimDirection(int playerId, int targetId,
											 Vec3d current, long tick) {
		AimFilter filter = AIM_FILTERS.computeIfAbsent(playerId,
				ignored -> new AimFilter());
		if (filter.targetId != targetId) {
			filter.targetId = targetId;
			filter.tick = tick;
			filter.direction = current;
			return current;
		}
		if (filter.tick == tick) {
			return filter.direction;
		}
		filter.tick = tick;
		if (filter.direction.lengthSquared() < 1.0E-8D
				|| filter.direction.dotProduct(current) >= AIM_DEADBAND_COS) {
			return filter.direction;
		}
		filter.direction = filter.direction.multiply(1.0D - AIM_SMOOTHING)
				.add(current.multiply(AIM_SMOOTHING))
				.normalize();
		return filter.direction;
	}

	private static GeoBone findBoneRecursive(GeoBone root, String name) {
		if (root == null) {
			return null;
		}
		if (name.equals(root.getName())) {
			return root;
		}
		for (GeoBone child : root.getChildBones()) {
			GeoBone found = findBoneRecursive(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private void recordLocator(MatrixStack matrices, GeoBone bone, LivingEntity entity,
			boolean autoLauncher) {
		String boneName = bone.getName();
		if (!boneName.equals("front") && !boneName.equals("rear")) {
			return;
		}
		matrices.push();
		Vector3f renderedPosition;
		try {
			RenderUtil.prepMatrixForBone(matrices, bone);
			Vector4f point = matrices.peek().getPositionMatrix()
					.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
			renderedPosition = new Vector3f(point.x, point.y, point.z);
		} finally {
			matrices.pop();
		}
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		Vec3d renderSpace = new Vec3d(
				renderedPosition.x, renderedPosition.y, renderedPosition.z);
		Vec3d worldPos = chooseLocatorWorldPosition(entity, renderSpace, camera);
		Vec3d delta = worldPos.subtract(entity.getPos());
		Vec3d local = rotateY(delta, -entity.getBodyYaw());
		if ("front".equals(boneName)) {
			FRONT_POSITIONS.put(entity.getId(), worldPos);
			FRONT_LOCAL_OFFSETS.put(entity.getId(), local);
			FRONT_SAMPLE_TICKS.put(entity.getId(), entity.getWorld().getTime());
			if (autoLauncher || CombatBackpackAnimation.phase(entity.getId())
					== CombatBackpackAnimation.Phase.READY) {
				CANONICAL_FRONT_OFFSETS.put(entity.getId(), local);
			}
		} else {
			REAR_POSITIONS.put(entity.getId(), worldPos);
			REAR_LOCAL_OFFSETS.put(entity.getId(), local);
			REAR_SAMPLE_TICKS.put(entity.getId(), entity.getWorld().getTime());
		}
	}

	private static Vec3d chooseLocatorWorldPosition(LivingEntity entity,
			Vec3d renderSpace, Vec3d camera) {
		Vec3d entityAnchor = entity.getPos().add(0.0D, 1.0D, 0.0D);
		Vec3d cameraRelative = renderSpace.add(camera);
		Vec3d worldAbsolute = renderSpace;
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Vec3d candidate : List.of(worldAbsolute, cameraRelative)) {
			if (!Double.isFinite(candidate.x) || !Double.isFinite(candidate.y)
					|| !Double.isFinite(candidate.z)) {
				continue;
			}
			double distance = candidate.squaredDistanceTo(entityAnchor);
			if (distance <= 16.0D && distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best == null ? cameraRelative : best;
	}

	private static Vec3d rotateY(Vec3d value, float degrees) {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3d(value.x * cos - value.z * sin, value.y,
				value.x * sin + value.z * cos);
	}

	private static float modelYaw(net.minecraft.entity.Entity entity) {
		return entity instanceof LivingEntity living ? living.getBodyYaw() : entity.getYaw();
	}

	@Override
	public void preRender(MatrixStack matrices, CombatBackpackItem animatable, BakedGeoModel model,
						  VertexConsumerProvider consumers, VertexConsumer buffer, boolean isReRender,
						  float tickDelta, int light, int overlay, int colour) {
		this.itemRenderTranslations = new Matrix4f(matrices.peek().getPositionMatrix());
	}
}
