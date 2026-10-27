package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 散热白汽：从枪身/肩膀位置往上飘、逐渐变淡。
 *
 * <h2>为什么和作者在 Snowstorm 里看到的"尺寸 / Y 轴旋转"不一样（2026-10-17 用户报的）</h2>
 * 作者的源文件 {@code 粒子\散热.json}（Bedrock 格式，Snowstorm 用的就是它）写的是：
 * <pre>
 *   "minecraft:particle_appearance_billboard": {
 *       "size": [3, 3],                        // Bedrock 里 size 的单位是**格** → 3x3 格
 *       "facing_camera_mode": "rotate_y",      // 四边形保持**竖直**，只有 yaw 跟着相机转
 *       "uv": { ... "frames_per_second": 12 }   // 12 帧 / 秒
 *   },
 *   "minecraft:particle_lifetime_expression": { "max_lifetime": 1 }   // 寿命 1 秒
 * </pre>
 * 我们原来是随手写的，两处都对不上：
 * <ul>
 *   <li><b>尺寸</b>：{@code scale = 0.12 + rand*0.08}（0.12~0.2 格）比作者的 3 格小 <b>15~25 倍</b>；</li>
 *   <li><b>朝向</b>：{@code extends SpriteBillboardParticle} = MC 默认的"<b>完全面向相机</b>" billboard
 *       （连俯仰一起跟）→ 抬头/低头看时像"贴着视线翻的贴纸"，而作者要的是"立着的卡片"
 *       （只有水平旋转跟相机，也就是 {@code rotate_y}）。</li>
 * </ul>
 * 现在按作者源文件对齐：尺寸默认 {@link #SIZE} = 3 格（{@code -Dtkgun.ventscale=<格>} 可覆盖）、
 * 寿命 {@link #LIFETIME} = 20 tick（1 秒，正好把 12 帧按 12fps 播完）、朝向固定为**自绘的 rotate_y 四边形**。
 */
public class HeatVentParticle extends SpriteBillboardParticle {
	/** 尺寸（**格**）：作者 Snowstorm 源里就是 {@code "size": [3, 3]}。 */
	private static final float SIZE = sizeFromProperty();
	/** 寿命（tick）：作者源里 {@code max_lifetime: 1} = 1 秒 = 20 tick。 */
	private static final int LIFETIME = 20;
	private final SpriteProvider provider;

	protected HeatVentParticle(ClientWorld world, double x, double y, double z,
							   double vx, double vy, double vz, SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.scale = SIZE;                   // 单位是格（见类注释）
		this.setSpriteForAge(provider);
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.gravityStrength = -0.012F;      // 往上飘
		this.collidesWithWorld = false;
	}

	private static float sizeFromProperty() {
		String s = System.getProperty("tkgun.ventscale");
		if (s != null) {
			try {
				float v = Float.parseFloat(s);
				if (v > 0.0F) {
					return v;
				}
			} catch (NumberFormatException ignored) {
				// 参数写坏了就用作者的值
			}
		}
		return 3.0F;                         // 作者 Snowstorm 源里的 size: [3, 3]
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteForAge(this.provider);
		float life = (float) this.age / (float) this.maxAge;
		this.setAlpha(1.0F - life);          // 越飘越淡
	}

	@Override
	public ParticleTextureSheet getType() {
		// ParticleManager only renders built-in sheets. A custom sheet instance is
		// accepted into the particle map but never visited by renderParticles().
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	/**
	 * **只在水平方向跟相机转**（Bedrock 的 {@code facing_camera_mode: "rotate_y"}）：
	 * 四边形保持竖直，只有 yaw 跟着相机 —— 不像 MC 默认那样连俯仰一起跟。
	 */
	@Override
	public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
		Vec3d cam = camera.getPos();
		float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.getX());
		float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.getY());
		float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.getZ());
		float half = this.getSize(tickDelta) * 0.5F;
		int light = this.getBrightness(tickDelta);
		// MC：yaw 0 = 朝 +Z，方向向量 = (-sin, cos)；右向量 = (cos, sin)
		double yaw = Math.toRadians(camera.getYaw());
		float rx = (float) Math.cos(yaw);
		float rz = (float) Math.sin(yaw);
		float minU = this.getMinU();
		float maxU = this.getMaxU();
		float minV = this.getMinV();
		float maxV = this.getMaxV();
		this.quad(vertexConsumer, light, px - rx * half, py - half, pz - rz * half, minU, maxV);
		this.quad(vertexConsumer, light, px + rx * half, py - half, pz + rz * half, maxU, maxV);
		this.quad(vertexConsumer, light, px + rx * half, py + half, pz + rz * half, maxU, minV);
		this.quad(vertexConsumer, light, px - rx * half, py + half, pz - rz * half, minU, minV);
	}

	private void quad(VertexConsumer vc, int light, float x, float y, float z, float u, float v) {
		vc.vertex(x, y, z).texture(u, v).color(this.red, this.green, this.blue, this.alpha).light(light);
	}

	public record Factory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public HeatVentParticle createParticle(SimpleParticleType type, ClientWorld world,
											   double x, double y, double z,
											   double vx, double vy, double vz) {
			return new HeatVentParticle(world, x, y, z, vx, vy, vz, this.provider);
		}
	}
}
