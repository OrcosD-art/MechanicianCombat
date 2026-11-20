package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * Tutorial-30 inspired rising light mote: a bright center, short cross flare,
 * then a broken diagonal tail. The sprite itself carries the pixel-art fade.
 */
public final class WarBannerSparkParticle extends SpriteBillboardParticle {
	private static final int FRAME_COUNT = 8;
	private static final int FRAME_TICKS = 2;
	private static final int LIFETIME = 16;

	private final SpriteProvider provider;
	private final float driftPhase;
	private final float baseScale;

	private WarBannerSparkParticle(ClientWorld world, double x, double y,
			double z, SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.baseScale = 0.20F + this.random.nextFloat() * 0.10F;
		this.scale = baseScale;
		this.velocityX = (this.random.nextDouble() - 0.5D) * 0.012D;
		this.velocityY = 0.035D + this.random.nextDouble() * 0.035D;
		this.velocityZ = (this.random.nextDouble() - 0.5D) * 0.012D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		this.driftPhase = this.random.nextFloat() * MathHelper.TAU;
		updateSprite();
	}

	private void updateSprite() {
		int frame = Math.min(FRAME_COUNT - 1, this.age / FRAME_TICKS);
		this.setSprite(this.provider.getSprite(frame, FRAME_COUNT));
	}

	@Override
	public void tick() {
		super.tick();
		float phase = driftPhase + this.age * 0.38F;
		this.velocityX += MathHelper.cos(phase) * 0.0012D;
		this.velocityZ += MathHelper.sin(phase) * 0.0012D;
		float life = (float) this.age / (float) this.maxAge;
		this.scale = this.baseScale * (1.0F - life * 0.32F);
		this.setAlpha(1.0F - life * 0.48F);
		updateSprite();
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public Rotator getRotator() {
		return Rotator.ALL_AXIS;
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	public record Factory(SpriteProvider provider)
			implements ParticleFactory<SimpleParticleType> {
		@Override
		public WarBannerSparkParticle createParticle(SimpleParticleType type,
				ClientWorld world, double x, double y, double z,
				double vx, double vy, double vz) {
			return new WarBannerSparkParticle(world, x, y, z, this.provider);
		}
	}
}
