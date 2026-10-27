package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.entity.BulletEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * **子弹**（`tkgun:bullet`）的渲染器：一张永远面向摄像机的贴图，逐帧翻页。
 *
 * <p>贴图 = 素材 `粒子/bullet.png`（48x144 = **3 帧 48x48**，作者那套 Bedrock 粒子就是按这个切的，
 * 见 `粒子/bullet.particle.json` 里的 flipbook：size_UV 48x48 / step_UV (0,48)）。
 * 帧率照粒子 json 的 9 fps，三帧循环。
 *
 * <p>面朝相机：直接用 {@code dispatcher.getRotation()}（就是原版粒子/名牌那套 billboard 的相机朝向），
 * 所以从任何角度看都是一颗完整的子弹，不需要模型。
 */
public class BulletRenderer extends EntityRenderer<BulletEntity> {
	private static final Identifier TEXTURE = Identifier.of("tkgun", "textures/entity/bullet.png");
	private static final Identifier CUTTING_TEXTURE =
			Identifier.of("tkgun", "textures/entity/cutting_bullet.png");
	private static final Identifier SCULK_TEXTURE =
			Identifier.of("tkgun", "textures/entity/sculk_bullet.png");
	private static final Identifier GRAPESHOT_TEXTURE =
			Identifier.of("tkgun", "textures/entity/grapeshot.png");
	private static final Identifier ROSE_QUARTZ_TEXTURE =
			Identifier.of("tkgun", "textures/entity/rose_quartz_bullet.png");
	/** 贴图是一列 3 帧 */
	private static final int FRAMES = 4;
	private static final float FRAMES_PER_SECOND = 12.0F;
	/** 画多大（格，1/16 格为单位的话这把枪整体比例下看着合适） */
	/** 用户定义：2×2 方块；1 方块 = 16 像素单位，所以渲染边长是 2 格。 */
	private static final float SIZE = 3.0F;

	public BulletRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(BulletEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		if (entity.isInvisible()) {
			return;
		}
		int style = entity.bulletStyle();
		if (style == BulletEntity.STYLE_TRAIL) {
			return;
		}
		Identifier texture = switch (style) {
			case BulletEntity.STYLE_CUTTING -> CUTTING_TEXTURE;
			case BulletEntity.STYLE_SCULK -> SCULK_TEXTURE;
			case BulletEntity.STYLE_GRAPESHOT -> GRAPESHOT_TEXTURE;
			case BulletEntity.STYLE_ROSE_QUARTZ -> ROSE_QUARTZ_TEXTURE;
			default -> TEXTURE;
		};
		int frames = style == BulletEntity.STYLE_SCULK ? 6
				: (style == BulletEntity.STYLE_GRAPESHOT ? 3 : FRAMES);
		float framesPerSecond = style == BulletEntity.STYLE_SCULK ? 10.0F
				: (style == BulletEntity.STYLE_GRAPESHOT
				? 60.0F / TkGun.GRAPESHOT_FLIGHT_TICKS
				: (style == BulletEntity.STYLE_ROSE_QUARTZ
				? FRAMES_PER_SECOND * 2.0F : FRAMES_PER_SECOND));
		float seconds = (entity.age() + tickDelta) / 20.0F;
		int frame = ((int) (seconds * framesPerSecond)) % frames;
		float v0 = frame / (float) frames;
		float v1 = (frame + 1) / (float) frames;
		float renderSize = style == BulletEntity.STYLE_GRAPESHOT ? 1.0F : SIZE;
		float half = renderSize / 2.0F;

		matrices.push();
		// 面向摄像机（和原版名牌/粒子一致的做法）
		MatrixStack.Entry entry = matrices.peek();
		entry.getPositionMatrix().rotate(this.dispatcher.getRotation());
		Matrix4f m = entry.getPositionMatrix();
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture));
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
	public Identifier getTexture(BulletEntity entity) {
		return TEXTURE;
	}
}
