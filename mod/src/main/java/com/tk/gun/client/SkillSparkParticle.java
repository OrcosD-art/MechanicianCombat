package com.tk.gun.client;

import com.tk.gun.client.rig.BowgunRigRenderer;
import com.tk.gun.client.rig.BowgunRig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Veritas close-combat skill spark, five frames at the authored 12 FPS. */
public final class SkillSparkParticle extends SpriteBillboardParticle {
	private static final int FRAME_COUNT = 5;
	private static final int LIFETIME = 10;
	private static volatile SpriteProvider activeProvider;
	private static final Map<Integer, SkillSparkParticle> ACTIVE =
			new ConcurrentHashMap<>();

	private final SpriteProvider provider;
	private int attachedEntityId = -1;
	private String attachedLocator = "aim";
	private String ownerAnimation;

	private SkillSparkParticle(ClientWorld world, double x, double y,
			double z, SpriteProvider provider) {
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
		// The source flipbook is authored at 12 FPS. Use the real age so the
		// last frame is selected; getSprite(frame, FRAME_COUNT) would cap at
		// frameCount - 2.
		int frame = Math.min(FRAME_COUNT - 1, this.age * 12 / 20);
		this.setSprite(this.provider.getSprite(frame, FRAME_COUNT - 1));
	}

	@Override
	public void tick() {
		String currentAnimation = effectiveAnimation();
		if (ownerAnimation != null && !ownerAnimation.equals(currentAnimation)) {
			markDead();
			ACTIVE.remove(attachedEntityId, this);
			return;
		}
		if (attachedEntityId >= 0) {
			Vec3d locator = "aim".equals(attachedLocator)
					? BowgunRigRenderer.aimPosition(attachedEntityId)
					: BowgunRigRenderer.locatorPosition(attachedEntityId);
			if (locator != null) {
				setPos(locator.x, locator.y, locator.z);
			}
		}
		super.tick();
		updateSprite();
		if (this.dead && attachedEntityId >= 0) {
			ACTIVE.remove(attachedEntityId, this);
		}
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
		public Factory {
			activeProvider = provider;
		}

		@Override
		public SkillSparkParticle createParticle(SimpleParticleType type,
				ClientWorld world, double x, double y, double z,
				double vx, double vy, double vz) {
			return new SkillSparkParticle(
					world, x, y, z, this.provider);
		}
	}

	public static boolean spawnAttached(Entity entity, Vec3d position,
			String locator) {
		MinecraftClient client = MinecraftClient.getInstance();
		SpriteProvider provider = activeProvider;
		if (entity == null || position == null || client.world == null
				|| provider == null) {
			return false;
		}
		SkillSparkParticle particle = new SkillSparkParticle(client.world,
				position.x, position.y, position.z, provider);
		particle.attachedEntityId = entity.getId();
		particle.attachedLocator = locator == null ? "aim" : locator;
		particle.ownerAnimation = effectiveAnimation();
		SkillSparkParticle previous = ACTIVE.put(
				particle.attachedEntityId, particle);
		if (previous != null && previous != particle) {
			previous.markDead();
		}
		client.particleManager.addParticle(particle);
		return true;
	}

	public static void clearForEntity(int entityId) {
		SkillSparkParticle previous = ACTIVE.remove(entityId);
		if (previous != null) {
			previous.markDead();
		}
	}

	private static String effectiveAnimation() {
		String upper = BowgunRig.currentUpperAnimation();
		return upper != null && !upper.isBlank()
				? upper : BowgunRig.currentAnimation();
	}
}
