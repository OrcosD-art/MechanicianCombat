package com.tk.gun.neoforge;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;

public final class NeoForgeEventBridge {
	private NeoForgeEventBridge() {
	}

	public static void installCommon(IEventBus modBus) {
		modBus.addListener(NeoForgeEventBridge::registerPayloads);
		modBus.addListener(NeoForgeEventBridge::registerAttributes);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1")
				.executesOn(HandlerThread.MAIN);
		for (PayloadTypeRegistry.Registration registration :
				PayloadTypeRegistry.c2s().values()) {
			registrar.playToServer(registration.id(), registration.codec(),
					(payload, context) -> ServerPlayNetworking.dispatch(payload, context));
		}
		for (PayloadTypeRegistry.Registration registration :
				PayloadTypeRegistry.s2c().values()) {
			registrar.playToClient(registration.id(), registration.codec(),
					(payload, context) -> NeoForgeClientNetworkBridge.dispatch(payload));
		}
	}

	private static void registerAttributes(EntityAttributeCreationEvent event) {
		FabricDefaultAttributeRegistry.defaults().forEach(
				(type, attributes) -> event.put(type, attributes.get()));
	}

	static void serverPostTick(MinecraftServer server) {
		for (ServerTickEvents.EndTick listener :
				ServerTickEvents.END_SERVER_TICK.listeners()) {
			listener.onEndTick(server);
		}
	}

	static void serverStarted(MinecraftServer server) {
		for (ServerLifecycleEvents.ServerLifecycle listener :
				ServerLifecycleEvents.SERVER_STARTED.listeners()) {
			listener.onServer(server);
		}
	}

	static void serverStopping(MinecraftServer server) {
		for (ServerLifecycleEvents.ServerLifecycle listener :
				ServerLifecycleEvents.SERVER_STOPPING.listeners()) {
			listener.onServer(server);
		}
	}

	static void serverStopped(MinecraftServer server) {
		for (ServerLifecycleEvents.ServerLifecycle listener :
				ServerLifecycleEvents.SERVER_STOPPED.listeners()) {
			listener.onServer(server);
		}
	}

	static void playerJoin(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| serverPlayer.getServer() == null) {
			return;
		}
		ServerPlayNetworkHandler handler = serverPlayer.networkHandler;
		for (ServerPlayConnectionEvents.Join listener :
				ServerPlayConnectionEvents.JOIN.listeners()) {
			listener.onPlayReady(handler, null, serverPlayer.getServer());
		}
	}

	static void playerDisconnect(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| serverPlayer.getServer() == null) {
			return;
		}
		for (ServerPlayConnectionEvents.Disconnect listener :
				ServerPlayConnectionEvents.DISCONNECT.listeners()) {
			listener.onDisconnect(serverPlayer.networkHandler, serverPlayer.getServer());
		}
	}

	static void commands(com.mojang.brigadier.CommandDispatcher<
			net.minecraft.server.command.ServerCommandSource> dispatcher,
			net.minecraft.command.CommandRegistryAccess context,
			net.minecraft.server.command.CommandManager.RegistrationEnvironment environment) {
		for (CommandRegistrationCallback listener :
				CommandRegistrationCallback.EVENT.listeners()) {
			listener.register(dispatcher, context, environment);
		}
	}

	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		for (ServerLivingEntityEvents.AllowDamage listener :
				ServerLivingEntityEvents.ALLOW_DAMAGE.listeners()) {
			if (!listener.allowDamage(entity, source, amount)) {
				return false;
			}
		}
		return true;
	}

	static boolean allowDeath(LivingEntity entity, DamageSource source) {
		for (ServerLivingEntityEvents.AllowDeath listener :
				ServerLivingEntityEvents.ALLOW_DEATH.listeners()) {
			if (!listener.allowDeath(entity, source, Float.MAX_VALUE)) {
				return false;
			}
		}
		return true;
	}

	static void afterDeath(LivingEntity entity, DamageSource source) {
		for (ServerLivingEntityEvents.AfterDeath listener :
				ServerLivingEntityEvents.AFTER_DEATH.listeners()) {
			listener.afterDeath(entity, source);
		}
	}

	static void afterDamage(LivingEntity entity, DamageSource source,
			float baseDamage, float damageTaken, boolean blocked) {
		for (ServerLivingEntityEvents.AfterDamage listener :
				ServerLivingEntityEvents.AFTER_DAMAGE.listeners()) {
			listener.afterDamage(entity, source, baseDamage, damageTaken, blocked);
		}
	}
}
