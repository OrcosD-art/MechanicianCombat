package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** Veritas ATK_3 thunder flash using the authored 7-frame sheet. */
public final class ThunderParticle extends SpriteBillboardParticle {
	private static final int FRAME_COUNT = 7;
	private static final int FRAME_STEP_TICKS = 2; // 10 FPS at 20 TPS
	private static final int LIFETIME = 20;

	private final SpriteProvider provider;

	private ThunderParticle(ClientWorld world, double x, double y, double z,
							SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.scale = 6.0F;
		this.velocityX = 0.0D;
		this.velocityY = 0.0D;
		this.velocityZ = 0.0D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		updateSprite();
	}

	private void updateSprite() {
		int frame = Math.min(FRAME_COUNT - 1, this.age / FRAME_STEP_TICKS);
		this.setSprite(this.provider.getSprite(frame, FRAME_COUNT));
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

	public record Factory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public ThunderParticle createParticle(SimpleParticleType type, ClientWorld world,
											  double x, double y, double z,
											  double vx, double vy, double vz) {
			return new ThunderParticle(world, x, y, z, this.provider);
		}
	}
}
