package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import com.tk.gun.entity.HunterSpearReturnEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the returning loyalty projectile with the spear item's 3D model. */
public final class HunterSpearReturnEntityRenderer
		extends EntityRenderer<HunterSpearReturnEntity> {
	private static final Identifier TEXTURE =
			TkGun.id("textures/item/mechanician_hunter_spear.png");

	public HunterSpearReturnEntityRenderer(
			EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(HunterSpearReturnEntity entity, float yaw,
			float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		Vec3d velocity = entity.getVelocity();
		Vec3d direction = velocity.lengthSquared() < 1.0E-6D
				? entity.getRotationVector() : velocity.normalize();
		matrices.push();
		matrices.multiply(new Quaternionf().rotationTo(
				new Vector3f(0.0F, 1.0F, 0.0F),
				new Vector3f((float) direction.x, (float) direction.y,
						(float) direction.z)));
		ItemStack stack = entity.getWeaponStack();
		if (stack.isEmpty() && TkGunServer.HUNTER_SPEAR != null) {
			stack = new ItemStack(TkGunServer.HUNTER_SPEAR);
		}
		MinecraftClient.getInstance().getItemRenderer().renderItem(
				stack, ModelTransformationMode.NONE, light,
				OverlayTexture.DEFAULT_UV, matrices, consumers,
				entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(HunterSpearReturnEntity entity) {
		return TEXTURE;
	}
}
