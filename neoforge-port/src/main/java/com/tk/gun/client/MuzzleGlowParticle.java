package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * 枪口辉光：每发子弹在 locator 处生成一颗自发光 billboard。
 *
 * <p>作者模型自带 `fire1..4` 的枪口焰约 0.2083s（5 tick）。这颗粒子用同样时长：
 * 起始白黄、随后降到暗橙并淡出，避免辉光比火焰先消失或拖尾。
 */
public class MuzzleGlowParticle extends SpriteBillboardParticle {
	/** 与 `stand_shooting` / `Focus_shooting` 的 `fire1..4` 最末关键帧对齐。 */
	private static final int LIFETIME = 5;

	private final SpriteProvider provider;

	protected MuzzleGlowParticle(ClientWorld world, double x, double y, double z,
								 SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.scale = 1.20F;                  // 约一格大小的柔和光团
		this.setColor(1.0F, 0.95F, 0.72F);
		this.setAlpha(1.0F);
		this.collidesWithWorld = false;
		this.gravityStrength = 0.0F;
		this.setSpriteForAge(provider);
	}

	@Override
	public void tick() {
		super.tick();
		float t = this.maxAge <= 1 ? 1.0F : (float) this.age / (float) this.maxAge;
		// 白黄（亮）→ 暗橙（暗），同时透明度和尺寸衰减。
		this.setColor(1.0F, 1.0F - 0.60F * t, 0.95F - 0.88F * t);
		this.setAlpha(1.0F - t);
		this.scale = 1.20F * (1.0F - 0.22F * t);
		this.setSpriteForAge(this.provider);
	}

	/** 粒子不参与世界光照，保持“发光”而不是随环境变暗。 */
	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	public record Factory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public MuzzleGlowParticle createParticle(SimpleParticleType type, ClientWorld world,
												 double x, double y, double z,
												 double vx, double vy, double vz) {
			return new MuzzleGlowParticle(world, x, y, z, this.provider);
		}
	}
}
