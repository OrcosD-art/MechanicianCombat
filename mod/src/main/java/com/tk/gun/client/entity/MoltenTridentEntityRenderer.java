package com.tk.gun.client.entity;

import com.tk.gun.TkGunServer;
import com.tk.gun.entity.MoltenTridentEntity;
import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.TridentEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 熔铁矢专用三叉戟实体：使用资源包中的 3D 箭矢模型。 */
public class MoltenTridentEntityRenderer extends EntityRenderer<MoltenTridentEntity> {
	private static final Set<Integer> LOGGED = ConcurrentHashMap.newKeySet();
	private static final Identifier STEEL_ARROW =
			Identifier.of("tkgun", "textures/entity/steel_arrow.png");
	private static final int STEEL_ARROW_FRAMES = 5;
	private static final float STEEL_ARROW_FPS = 12.0F;
	private static final float STEEL_ARROW_SIZE = 2.0F;
	private static final double STEEL_ARROW_TIP_OFFSET = 0.55D;

	public MoltenTridentEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(MoltenTridentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		Vec3d velocity = entity.getVelocity();
		boolean flying = !entity.isStopped();
		Vec3d dir = velocity.lengthSquared() < 1.0E-6D
				? entity.getRotationVector() : velocity.normalize();
		matrices.push();
		if (entity.isStopped()) {
			// 命中方块后箭头通常有一半嵌在方块里；沿反方向退回少量，
			// 保证插住的 3D 箭杆在大多数视角下仍可见。
			matrices.translate(-dir.x * 0.25D, -dir.y * 0.25D, -dir.z * 0.25D);
		}
		// 资源模型的 +Y（top）是箭矢前端：直接把 +Y 转到实体速度方向。
		matrices.multiply(new Quaternionf().rotationTo(
				new Vector3f(0.0F, 1.0F, 0.0F),
				new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
		ItemStack stack = entity.getWeaponStack();
		if (stack.isEmpty() && TkGunServer.BOLT != null) {
			stack = new ItemStack(TkGunServer.BOLT);
		}
		if (LOGGED.add(entity.getId())) {
			TkGun.LOGGER.info("[tkgun] 熔铁矢实体渲染: id={} pos=({}, {}, {}) stack={} item={}",
					entity.getId(), Math.round(entity.getX() * 100) / 100.0,
					Math.round(entity.getY() * 100) / 100.0, Math.round(entity.getZ() * 100) / 100.0,
					stack.getCount(), stack.getItem());
		}
		// ItemRenderer 内部已经 translate(-0.5, -0.5, -0.5)，这里不能再偏移，
		// 否则模型会整体偏离实体约 1 格（看起来就像没渲染）。
		MinecraftClient.getInstance().getItemRenderer().renderItem(
				stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		if (flying) {
			renderSteelArrowEffect(entity, tickDelta, dir, matrices, vertexConsumers);
		}
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	private void renderSteelArrowEffect(MoltenTridentEntity entity, float tickDelta, Vec3d dir,
										MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
		float seconds = (entity.age + tickDelta) / 20.0F;
		float duration = STEEL_ARROW_FRAMES / STEEL_ARROW_FPS;
		if (seconds >= duration) {
			return;
		}
		int frame = Math.min(STEEL_ARROW_FRAMES - 1, (int) (seconds * STEEL_ARROW_FPS));
		float v0 = frame / (float) STEEL_ARROW_FRAMES;
		float v1 = (frame + 1) / (float) STEEL_ARROW_FRAMES;
		float half = STEEL_ARROW_SIZE / 2.0F;

		matrices.push();
		matrices.translate(dir.x * STEEL_ARROW_TIP_OFFSET,
				dir.y * STEEL_ARROW_TIP_OFFSET,
				dir.z * STEEL_ARROW_TIP_OFFSET);
		MatrixStack.Entry entry = matrices.peek();
		entry.getPositionMatrix().rotate(this.dispatcher.getRotation());
		Matrix4f m = entry.getPositionMatrix();
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(STEEL_ARROW));
		vc.vertex(m, -half, -half, 0.0F).color(255, 255, 255, 255).texture(0.0F, v1)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vc.vertex(m, half, -half, 0.0F).color(255, 255, 255, 255).texture(1.0F, v1)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vc.vertex(m, half, half, 0.0F).color(255, 255, 255, 255).texture(1.0F, v0)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vc.vertex(m, -half, half, 0.0F).color(255, 255, 255, 255).texture(0.0F, v0)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(MoltenTridentEntity entity) {
		return TridentEntityRenderer.TEXTURE;
	}
}
