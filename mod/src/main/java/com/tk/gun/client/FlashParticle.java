package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** Six-frame authored Veritas flash, played at six frames per second. */
public final class FlashParticle extends SpriteBillboardParticle {
	private static final int FRAME_COUNT = 6;
	private static final int LIFETIME = 20;

	private final SpriteProvider provider;

	private FlashParticle(ClientWorld world, double x, double y, double z,
						  SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = LIFETIME;
		this.scale = 3.0F;
		this.velocityX = 0.0D;
		this.velocityY = 0.0D;
		this.velocityZ = 0.0D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		updateSprite();
	}

	private void updateSprite() {
		int frame = Math.min(FRAME_COUNT - 1,
				(int) ((long) this.age * FRAME_COUNT / LIFETIME));
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
		return Rotator.ALL_AXIS;
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	public record Factory(SpriteProvider provider)
			implements ParticleFactory<SimpleParticleType> {
		@Override
		public FlashParticle createParticle(SimpleParticleType type,
											ClientWorld world,
											double x, double y, double z,
											double vx, double vy, double vz) {
			return new FlashParticle(world, x, y, z, this.provider);
		}
	}
}
