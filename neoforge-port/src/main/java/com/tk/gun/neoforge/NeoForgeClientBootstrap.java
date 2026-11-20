package com.tk.gun.neoforge;

import com.tk.gun.client.TkGunClient;
import com.tk.gun.client.NeoMechanicianArmorRenderLayer;
import com.tk.gun.mixin.PlayerEntityRendererLayerAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientBootstrap {
	private NeoForgeClientBootstrap() {
	}

	public static void initialize(IEventBus modBus) {
		modBus.addListener(NeoForgeClientBootstrap::clientSetup);
		modBus.addListener(NeoForgeClientBootstrap::keyMappings);
		modBus.addListener(NeoForgeClientBootstrap::particleProviders);
		modBus.addListener(NeoForgeClientBootstrap::entityRenderers);
		modBus.addListener(NeoForgeClientBootstrap::addLayers);
		NeoForge.EVENT_BUS.addListener(NeoForgeClientBootstrap::clientTick);
		NeoForge.EVENT_BUS.addListener(NeoForgeClientBootstrap::renderGui);
		NeoForge.EVENT_BUS.addListener(NeoForgeClientBootstrap::renderLevel);
	}

	private static void clientSetup(FMLClientSetupEvent event) {
		new TkGunClient().onInitializeClient();
	}

	private static void keyMappings(RegisterKeyMappingsEvent event) {
		// Force TkGunClient's static initializer to create its key bindings
		// before this event snapshots the helper list. NeoForge fires key
		// registration before FMLClientSetupEvent, so merely referencing the
		// helper here leaves the list empty and the X binding never reaches
		// the Controls screen or the input system.
		var backpackAction = TkGunClient.BACKPACK_ACTION_KEY;
		var helmetToggle = TkGunClient.HELMET_TOGGLE_KEY;
		KeyBindingHelper.KEY_BINDINGS.forEach(event::register);
		com.tk.gun.TkGun.LOGGER.info(
				"[mechaniciancombat] NeoForge keybindings registered: {}",
				KeyBindingHelper.KEY_BINDINGS.size());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void particleProviders(RegisterParticleProvidersEvent event) {
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.HEAT_VENT,
				com.tk.gun.client.HeatVentParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.ENERGY_CONVERGE,
				com.tk.gun.client.EnergyParticle.MuzzleFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.ENERGY_XPLO,
				com.tk.gun.client.EnergyParticle.HitFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.EXPLOSIVE_BOLT_IMPACT,
				com.tk.gun.client.EnergyParticle.HitFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SHELL_BOOST,
				com.tk.gun.client.ShellBoostParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.MUZZLE_GLOW,
				com.tk.gun.client.MuzzleGlowParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.CUTTING_EXPLO,
				com.tk.gun.client.EnergyParticle.CuttingFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SONIC_BOOM,
				com.tk.gun.client.EnergyParticle.SonicBoomFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.GRAPESHOT_IMPACT,
				com.tk.gun.client.EnergyParticle.GrapeshotImpactFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SLASH_HIT_1,
				com.tk.gun.client.EnergyParticle.SlashHitFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SLASH_HIT_2,
				com.tk.gun.client.EnergyParticle.SlashHitFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SLASH_HIT_3,
				com.tk.gun.client.EnergyParticle.SlashHitFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.ROSE_QUARTZ_IMPACT,
				com.tk.gun.client.EnergyParticle.RoseQuartzImpactFactory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.ROSE_QUARTZ_TRAIL,
				com.tk.gun.client.GlowingRoseTrailParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.THUNDER,
				com.tk.gun.client.ThunderParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.THUNDER_BIG,
				com.tk.gun.client.ThunderBigParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.FLASH,
				com.tk.gun.client.FlashParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.GOLEM_ENERGY_CONVERGE,
				com.tk.gun.client.GolemEnergyParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.GOLEM_ENERGY_CONVERGE_2,
				com.tk.gun.client.GolemEnergyParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.WAR_BANNER_SPARK,
				com.tk.gun.client.WarBannerSparkParticle.Factory::new);
		NeoParticleRegistration.spriteSet(event,
				com.tk.gun.TkGunParticles.SKILL_SPARK,
				com.tk.gun.client.SkillSparkParticle.Factory::new);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void entityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(com.tk.gun.TkGunServer.BOLT_ENTITY,
				com.tk.gun.client.entity.BoltProjectileRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.BULLET_ENTITY,
				com.tk.gun.client.entity.BulletRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.MOLTEN_TRIDENT_ENTITY,
				com.tk.gun.client.entity.MoltenTridentEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.MOLTEN_GOLEM,
				com.tk.gun.client.entity.MoltenGolemEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.WAR_BANNER_ENTITY,
				com.tk.gun.client.entity.WarBannerEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.HUNTER_TURRET_ENTITY,
				com.tk.gun.client.entity.HunterTurretEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.HUNTER_SPEAR_RETURN_ENTITY,
				com.tk.gun.client.entity.HunterSpearReturnEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.STEEL_ARROW_IMPACT_ENTITY,
				com.tk.gun.client.entity.SteelArrowImpactEntityRenderer::new);
		event.registerEntityRenderer(com.tk.gun.TkGunServer.SOUL_ANCHOR_ENTITY,
				com.tk.gun.client.entity.SoulAnchorProjectileGeoRenderer::new);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addLayers(EntityRenderersEvent.AddLayers event) {
		for (var skin : event.getSkins()) {
			var renderer = event.getSkin(skin);
			((PlayerEntityRendererLayerAccessor) renderer)
					.mechaniciancombat$addFeature(
							new NeoMechanicianArmorRenderLayer(
									(FeatureRendererContext) renderer));
		}
	}

	private static void clientTick(ClientTickEvent.Post event) {
		MinecraftClient client = MinecraftClient.getInstance();
		for (ClientTickEvents.EndTick listener :
				ClientTickEvents.END_CLIENT_TICK.listeners()) {
			listener.onEndTick(client);
		}
	}

	private static void renderGui(RenderGuiEvent.Post event) {
		for (HudRenderCallback.HudRender listener : HudRenderCallback.EVENT.listeners()) {
			listener.onHudRender(event.getGuiGraphics(), event.getPartialTick());
		}
	}

	private static void renderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		WorldRenderContext context = new WorldRenderContext(
				client.world, event.getPoseStack(),
				client.getBufferBuilders().getEntityVertexConsumers(),
				event.getCamera(), event.getPartialTick());
		for (WorldRenderEvents.End listener :
				WorldRenderEvents.AFTER_ENTITIES.listeners()) {
			listener.onEnd(context);
		}
	}
}
