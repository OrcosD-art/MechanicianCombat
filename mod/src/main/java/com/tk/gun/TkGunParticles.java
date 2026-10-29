package com.tk.gun;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 711 素材里那个「散热粒子.json」是 Bedrock 草稿（identifier/texture 都是空的），
 * 这里把它补完并落到本服能用的形式上：一个真正的 MC 粒子 tkgun:heat_vent，
 * 12 帧贴图在 resources/assets/tkgun/textures/particle/heat_vent_0..11.png。
 *
 * 2026-10-13 机械师素材又给了 `粒子.zip`：`converge.png`(32x192) / `xplo.png`(48x288)
 * 两张竖条翻页贴图 —— 切成单帧后登记成
 * {@link #ENERGY_CONVERGE}（枪口能量汇聚）和 {@link #ENERGY_XPLO}（命中能量爆），
 * 生成脚本 `_mech_work\make_particles.py`。
 */
public final class TkGunParticles {
	/** SimpleParticleType 既是注册表里的类型，也是 spawn 时要传的 effect。 */
	public static final SimpleParticleType HEAT_VENT = FabricParticleTypes.simple(true);
	/** 枪口能量汇聚（素材 粒子.zip 的 converge，6 帧）。 */
	public static final SimpleParticleType ENERGY_CONVERGE = FabricParticleTypes.simple(true);
	/** 命中能量爆（素材 粒子.zip 的 xplo，6 帧）。 */
	public static final SimpleParticleType ENERGY_XPLO = FabricParticleTypes.simple(true);
	/** Explosive-bolt impact effect using the original orange xplo2 texture strip. */
	public static final SimpleParticleType EXPLOSIVE_BOLT_IMPACT = FabricParticleTypes.simple(true);
	/**
	 * **二级加速弹壳**（素材 v4 `粒子\二级加速弹壳.png`，6 帧 48x48）：
	 * 专注连射射速提上来之后，每出一发甩出去的弹壳（见 `TkGunServer#fireOnce`）。
	 */
	public static final SimpleParticleType SHELL_BOOST = FabricParticleTypes.simple(true);
	/** 枪口自发光辉光（与 fire1..4 枪口焰同为 5 tick，由亮到暗）。 */
	public static final SimpleParticleType MUZZLE_GLOW = FabricParticleTypes.simple(true);
	/** Cutting-round impact effect (6 frames from 粒子/切割弹/cutting.png). */
	public static final SimpleParticleType CUTTING_EXPLO = FabricParticleTypes.simple(true);
	/** Sculk-round impact effect (8 frames from 粒子/幽匿弹/sonic_boom.ase). */
	public static final SimpleParticleType SONIC_BOOM = FabricParticleTypes.simple(true);
	/** Grapeshot pellet impact effect, six frames from grapeshot_explo.png. */
	public static final SimpleParticleType GRAPESHOT_IMPACT = FabricParticleTypes.simple(true);
	/** Rose quartz impact effect, four frames from rose_quartz_explo.png. */
	public static final SimpleParticleType ROSE_QUARTZ_IMPACT = FabricParticleTypes.simple(true);
	/** Full-bright rose quartz guidance trail. */
	public static final SimpleParticleType ROSE_QUARTZ_TRAIL = FabricParticleTypes.simple(true);
	/** Veritas ATK_3 authored thunder flash. */
	public static final SimpleParticleType THUNDER = FabricParticleTypes.simple(true);
	/** Molten golem convergence particle that follows its rendered locator bone. */
	public static final SimpleParticleType GOLEM_ENERGY_CONVERGE = FabricParticleTypes.simple(true);
	/** Second-stage convergence authored as a distinct six-frame texture strip. */
	public static final SimpleParticleType GOLEM_ENERGY_CONVERGE_2 = FabricParticleTypes.simple(true);

	public static void register() {
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("heat_vent"), HEAT_VENT);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("energy_converge"), ENERGY_CONVERGE);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("energy_xplo"), ENERGY_XPLO);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("explosive_bolt_impact"),
				EXPLOSIVE_BOLT_IMPACT);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("shell_boost"), SHELL_BOOST);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("muzzle_glow"), MUZZLE_GLOW);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("cutting_explo"), CUTTING_EXPLO);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("sonic_boom"), SONIC_BOOM);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("grapeshot_impact"), GRAPESHOT_IMPACT);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("rose_quartz_explo"), ROSE_QUARTZ_IMPACT);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("rose_quartz_trail"), ROSE_QUARTZ_TRAIL);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("thunder"), THUNDER);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("golem_energy_converge"),
				GOLEM_ENERGY_CONVERGE);
		Registry.register(Registries.PARTICLE_TYPE, TkGun.id("golem_energy_converge_2"),
				GOLEM_ENERGY_CONVERGE_2);
		TkGun.LOGGER.info("[tkgun] 粒子已注册: {} / {} / {} / {} / {} / {} / {} / {} / {} / {}", TkGun.id("heat_vent"),
				TkGun.id("energy_converge"), TkGun.id("energy_xplo"), TkGun.id("shell_boost"),
				TkGun.id("muzzle_glow"), TkGun.id("sonic_boom"), TkGun.id("grapeshot_impact"),
				TkGun.id("rose_quartz_explo"), TkGun.id("rose_quartz_trail"), TkGun.id("thunder"));
	}

	private TkGunParticles() {
	}
}
