package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.RedDustParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.SimpleParticleType;
import org.joml.Vector3f;

/** Rose quartz guidance trail with the same full-bright handling as impact effects. */
public class GlowingRoseTrailParticle extends RedDustParticle {
	private static final DustParticleEffect EFFECT =
			new DustParticleEffect(new Vector3f(0.96F, 0.25F, 0.55F), 0.55F);

	private GlowingRoseTrailParticle(ClientWorld world, double x, double y, double z,
									 double vx, double vy, double vz, SpriteProvider provider) {
		super(world, x, y, z, vx, vy, vz, EFFECT, provider);
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	public record Factory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public GlowingRoseTrailParticle createParticle(SimpleParticleType type, ClientWorld world,
													   double x, double y, double z,
													   double vx, double vy, double vz) {
			return new GlowingRoseTrailParticle(world, x, y, z, vx, vy, vz, provider);
		}
	}
}
