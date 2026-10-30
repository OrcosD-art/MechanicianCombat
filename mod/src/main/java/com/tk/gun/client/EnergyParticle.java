package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * 机械师素材里那两张"竖条翻页"粒子：**按寿命逐帧播**（`setSpriteForAge`）。
 *
 * <p>{@code particle-muzzle}=枪口能量汇聚（converge，6 帧，短促 0.3s）、
 * {@code particle-hit}=命中能量爆（xplo，6 帧，稍长带淡出）。
 * 两个粒子各有一个 SpriteProvider（帧序列由 `assets/tkgun/particles/*.json` 定），
 * 所以这里用同一个类、两组参数，各给一个 Factory。
 */
public class EnergyParticle extends SpriteBillboardParticle {
	private final SpriteProvider provider;
	private final boolean fade;
	private int attachedPlayerId = -1;
	private String attachedLocator = "aim";
	private static volatile SpriteProvider activeProvider;

	protected EnergyParticle(ClientWorld world, double x, double y, double z,
							 SpriteProvider provider, int maxAge, float scale, boolean fade) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = maxAge;
		this.scale = scale;
		this.fade = fade;
		this.velocityX = 0.0D;
		this.velocityY = 0.0D;
		this.velocityZ = 0.0D;
		this.gravityStrength = 0.0F;
		this.collidesWithWorld = false;
		this.setSpriteForAge(provider);
	}

	@Override
	public void tick() {
		if (attachedPlayerId >= 0) {
			net.minecraft.util.math.Vec3d locator =
					"aim".equals(attachedLocator)
							? com.tk.gun.client.rig.BowgunRigRenderer
									.aimPosition(attachedPlayerId)
							: com.tk.gun.client.rig.BowgunRigRenderer
									.locatorPosition(attachedPlayerId);
			if (locator != null) {
				setPos(locator.x, locator.y, locator.z);
			}
		}
		super.tick();
		this.setSpriteForAge(this.provider);      // 逐帧翻页（寿命归一化）
		if (this.fade) {
			float life = (float) this.age / (float) this.maxAge;
			this.setAlpha(Math.max(0.0F, 1.0F - life * life));
		}
	}

	@Override
	protected int getBrightness(float tickDelta) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	/** 枪口：短促一团能量。 */
	public record MuzzleFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		public MuzzleFactory {
			activeProvider = provider;
		}

		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 7, 1.1F, false);
		}
	}

	/** Spawn a convergence effect that follows the rendered gun locator. */
	public static boolean spawnAttached(net.minecraft.entity.Entity player,
										net.minecraft.util.math.Vec3d position,
										String locator) {
		net.minecraft.client.MinecraftClient client =
				net.minecraft.client.MinecraftClient.getInstance();
		SpriteProvider provider = activeProvider;
		if (player == null || position == null || client.world == null
				|| provider == null) {
			return false;
		}
		EnergyParticle particle = new EnergyParticle(client.world,
				position.x, position.y, position.z, provider, 10, 1.25F, true);
		particle.attachedPlayerId = player.getId();
		particle.attachedLocator = locator == null ? "locator" : locator;
		client.particleManager.addParticle(particle);
		return true;
	}

	/** 命中：大一圈、带淡出。 */
	public record HitFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 1.9F, true);
		}
	}

	public record CuttingFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 12, 2.0F, true);
		}
	}

	/** 幽匿弹：8 帧音爆，0.8 秒，铺满 3 格直径。 */
	public record SonicBoomFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 16, 3.0F, true);
		}
	}

	/** One pellet-sized impact puff for each grapeshot projectile. */
	public record GrapeshotImpactFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 0.55F, true);
		}
	}

	public record RoseQuartzImpactFactory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientWorld world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 1.2F, true);
		}
	}
}
