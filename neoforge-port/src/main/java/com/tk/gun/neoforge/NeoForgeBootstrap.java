package com.tk.gun.neoforge;

import com.tk.gun.TkGunServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@Mod("mechaniciancombat")
public final class NeoForgeBootstrap {
	public NeoForgeBootstrap(IEventBus modBus, ModContainer container) {
		RegistryBridge.install(modBus);
		NeoForgeEventBridge.installCommon(modBus);
		NeoForge.EVENT_BUS.addListener(this::serverTick);
		NeoForge.EVENT_BUS.addListener(this::serverStarted);
		NeoForge.EVENT_BUS.addListener(this::serverStopping);
		NeoForge.EVENT_BUS.addListener(this::serverStopped);
		NeoForge.EVENT_BUS.addListener(this::playerLoggedIn);
		NeoForge.EVENT_BUS.addListener(this::playerLoggedOut);
		NeoForge.EVENT_BUS.addListener(this::commands);
		NeoForge.EVENT_BUS.addListener(this::incomingDamage);
		NeoForge.EVENT_BUS.addListener(this::livingDeath);
		NeoForge.EVENT_BUS.addListener(this::damagePost);
		RegistryBootstrapAccess registryAccess =
				RegistryBootstrapAccess.open(
						net.minecraft.registry.Registries.BLOCK,
						net.minecraft.registry.Registries.ITEM,
						net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE,
						net.minecraft.registry.Registries.ENTITY_TYPE);
		try {
			new TkGunServer().onInitialize();
		} finally {
			registryAccess.close();
		}
		if (FMLEnvironment.dist.isClient()) {
			NeoForgeClientBootstrap.initialize(modBus);
		}
	}

	private void serverTick(ServerTickEvent.Post event) {
		NeoForgeEventBridge.serverPostTick(event.getServer());
	}

	private void serverStarted(ServerStartedEvent event) {
		NeoForgeEventBridge.serverStarted(event.getServer());
	}

	private void serverStopping(ServerStoppingEvent event) {
		NeoForgeEventBridge.serverStopping(event.getServer());
	}

	private void serverStopped(ServerStoppedEvent event) {
		NeoForgeEventBridge.serverStopped(event.getServer());
	}

	private void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		NeoForgeEventBridge.playerJoin(event.getEntity());
	}

	private void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		NeoForgeEventBridge.playerDisconnect(event.getEntity());
	}

	private void commands(RegisterCommandsEvent event) {
		NeoForgeEventBridge.commands(event.getDispatcher(), event.getBuildContext(),
				event.getCommandSelection());
	}

	private void incomingDamage(LivingIncomingDamageEvent event) {
		if (!NeoForgeEventBridge.allowDamage(event.getEntity(), event.getSource(),
				event.getAmount())) {
			event.setCanceled(true);
		}
	}

	private void livingDeath(LivingDeathEvent event) {
		if (!NeoForgeEventBridge.allowDeath(event.getEntity(), event.getSource())) {
			event.setCanceled(true);
			return;
		}
		NeoForgeEventBridge.afterDeath(event.getEntity(), event.getSource());
	}

	private void damagePost(LivingDamageEvent.Post event) {
		NeoForgeEventBridge.afterDamage(event.getEntity(), event.getSource(),
				event.getNewDamage(), event.getNewDamage(), false);
	}
}
