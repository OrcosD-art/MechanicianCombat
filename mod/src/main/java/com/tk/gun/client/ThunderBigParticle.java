package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** Veritas ATK_hold_over final-hit thunder bloom, authored at 12 FPS. */
public final class ThunderBigParticle extends SpriteBillboardParticle {
	private static final int FRAME_COUNT = 9;
	private static final int FRAMES_PER_SECOND = 12;
	private static final int LIFETIME = 20;

	private final SpriteProvider provider;

	private ThunderBigParticle(ClientWorld world, double x, double y, double z,
			SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.scale = 16.0F;
		this.velocityX = 0.0D;
		this.velocityY = 0.0D;
		this.velocityZ = 0.0D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		updateSprite();
	}

	private void updateSprite() {
		int frame = Math.min(FRAME_COUNT - 1,
				this.age * FRAMES_PER_SECOND / 20);
		this.setSprite(this.provider.getSprite(frame, FRAME_COUNT - 1));
	}

	@Override
	public void tick() {
		super.tick();
		updateSprite();
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public Rotator getRotator() {
		return Rotator.Y_AND_W_ONLY;
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	public record Factory(SpriteProvider provider)
			implements ParticleFactory<SimpleParticleType> {
		@Override
		public ThunderBigParticle createParticle(SimpleParticleType type,
				ClientWorld world, double x, double y, double z,
				double vx, double vy, double vz) {
			return new ThunderBigParticle(world, x, y, z, this.provider);
		}
	}
}
