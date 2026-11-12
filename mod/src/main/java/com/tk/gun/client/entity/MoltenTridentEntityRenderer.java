package com.tk.gun.client.entity;

import com.tk.gun.TkGunServer;
import com.tk.gun.entity.MoltenTridentEntity;
import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.TridentEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 熔铁矢专用三叉戟实体：使用资源包中的 3D 箭矢模型。 */
public class MoltenTridentEntityRenderer extends EntityRenderer<MoltenTridentEntity> {
	private static final Set<Integer> LOGGED = ConcurrentHashMap.newKeySet();
	public MoltenTridentEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(MoltenTridentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		Vec3d velocity = entity.getVelocity();
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
			TkGun.LOGGER.info("[mechaniciancombat] 熔铁矢实体渲染: id={} pos=({}, {}, {}) stack={} item={}",
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
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(MoltenTridentEntity entity) {
		return TridentEntityRenderer.TEXTURE;
	}
}
