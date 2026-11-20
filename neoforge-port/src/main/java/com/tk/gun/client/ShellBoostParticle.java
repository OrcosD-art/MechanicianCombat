package com.tk.gun.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * **二级加速弹壳**（v4 素材 `粒子\二级加速弹壳.png`，6 帧 48x48）：
 * 专注连射射速提上来之后甩出去的弹壳 —— 逐帧翻页 + 一点重力 + 会旋转着掉。
 *
 * <p>和服务端那簇 `heat_vent` 一样，位置/初速都是服务端给的（`TkGunServer#fireOnce` 从抛壳口甩），
 * 这里只管"长什么样、怎么飘"。
 */
public class ShellBoostParticle extends SpriteBillboardParticle {
	private final SpriteProvider provider;

	protected ShellBoostParticle(ClientWorld world, double x, double y, double z,
								 double vx, double vy, double vz, SpriteProvider provider) {
		super(world, x, y, z);
		this.provider = provider;
		this.maxAge = 14 + this.random.nextInt(6);
		this.scale = 0.16F + this.random.nextFloat() * 0.06F;
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.gravityStrength = 0.9F;       // 弹壳会掉下来
		this.collidesWithWorld = true;
		this.setSpriteForAge(provider);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteForAge(this.provider);   // 6 帧逐帧翻页
		float life = (float) this.age / (float) this.maxAge;
		this.setAlpha(life > 0.7F ? Math.max(0.0F, 1.0F - (life - 0.7F) / 0.3F) : 1.0F);
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	public record Factory(SpriteProvider provider) implements ParticleFactory<SimpleParticleType> {
		@Override
		public ShellBoostParticle createParticle(SimpleParticleType type, ClientWorld world,
												 double x, double y, double z,
												 double vx, double vy, double vz) {
			return new ShellBoostParticle(world, x, y, z, vx, vy, vz, this.provider);
		}
	}
}
