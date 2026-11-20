package com.tk.gun.client;

import com.tk.gun.entity.MoltenGolemEntity;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;

/** Energy convergence that follows the golem's animated locator_right bone. */
public final class GolemEnergyParticle extends SpriteBillboardParticle {
	private final SpriteProvider provider;
	private final int ownerEntityId;
	private final int generation;
	private final boolean normalConverge;

	private GolemEnergyParticle(ClientWorld world, double x, double y, double z,
								SpriteProvider provider, int ownerEntityId,
								float scale, int generation) {
		super(world, x, y, z);
		this.provider = provider;
		this.ownerEntityId = ownerEntityId;
		this.generation = generation;
		this.normalConverge = scale < 1.5F;
		this.maxAge = 20;
		this.scale = scale;
		this.velocityX = 0.0D;
		this.velocityY = 0.0D;
		this.velocityZ = 0.0D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		this.setSpriteForAge(provider);
	}

	@Override
	public void tick() {
		if (superseded()) {
			markDead();
			return;
		}
		super.tick();
		if (this.age < this.maxAge) {
			updateLocator();
		}
		this.setSpriteForAge(this.provider);
		float life = (float) this.age / (float) this.maxAge;
		this.setAlpha(Math.max(0.0F, 1.0F - life * life));
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
		if (superseded()) {
			markDead();
			return;
		}
		updateLocator();
		super.buildGeometry(vertexConsumer, camera, tickDelta);
	}

	private boolean superseded() {
		return this.normalConverge && MoltenGolemEntity.currentConvergeGeneration(
				this.ownerEntityId) != this.generation;
	}

	private void updateLocator() {
		Vec3d locator = MoltenGolemEntity.clientLocatorPosition(
				this.ownerEntityId, this.world.getTime());
		if (locator != null) {
			setPos(locator.x, locator.y, locator.z);
		}
	}

	public record Factory(SpriteProvider provider)
			implements ParticleFactory<SimpleParticleType> {
		@Override
		public GolemEnergyParticle createParticle(SimpleParticleType type,
				ClientWorld world, double x, double y, double z,
				double ownerEntityId, double scale, double generation) {
			return new GolemEnergyParticle(world, x, y, z, provider,
					(int) Math.round(ownerEntityId),
					scale <= 0.0D ? 1.35F : (float) scale,
					(int) Math.round(generation));
		}
	}
}
