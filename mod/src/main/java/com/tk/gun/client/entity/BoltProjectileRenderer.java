package com.tk.gun.client.entity;

import com.tk.gun.entity.BoltProjectileEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * 弩矢弹丸的客户端渲染器：**什么都不画**。
 *
 * <p>飞出去的样子全靠服务端每 tick 沿路径铺的曳光粒子（Flame/Smoke）+ 命中时的爆裂粒子，
 * 所以这里连模型都不需要有（返回一张已存在的贴图当占位，满足父类抽象方法即可）。
 * 这样也彻底断了"看起来像原版箭"的可能——用户 2026-10-12 反复强调过。
 */
public class BoltProjectileRenderer extends EntityRenderer<BoltProjectileEntity> {
	private static final Identifier DUMMY = Identifier.of("tkgun", "textures/entity/bowgun_gun.png");

	public BoltProjectileRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(BoltProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		// 故意空实现：弹体不画。
	}

	@Override
	public Identifier getTexture(BoltProjectileEntity entity) {
		return DUMMY;
	}
}
