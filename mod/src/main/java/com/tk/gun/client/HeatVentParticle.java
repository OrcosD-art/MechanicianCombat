package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * Heat-vent sprite using the source's 3x3-block, 12-frame rotate-y definition.
 * Vanilla's Y-only billboard keeps it upright while following camera yaw, and the
 * fixed maximum brightness prevents the effect from disappearing in dark areas.
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
		this.scale = SIZE;
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

	@Override
	public Rotator getRotator() {
		// Vanilla's Y-only billboard stays upright and follows camera yaw without pitch.
		return Rotator.Y_AND_W_ONLY;
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
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
