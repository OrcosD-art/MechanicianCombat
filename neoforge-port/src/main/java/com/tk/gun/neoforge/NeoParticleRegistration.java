package com.tk.gun.neoforge;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.lang.reflect.Proxy;
import java.util.function.Function;

/** Package bridge for Minecraft's package-private sprite factory type. */
public final class NeoParticleRegistration {
	private NeoParticleRegistration() {
	}

	public static <T extends ParticleEffect> void spriteSet(
			RegisterParticleProvidersEvent event, ParticleType<T> type,
			Function<SpriteProvider, ParticleFactory<T>> factory) {
		try {
			Class<?> spriteAware = null;
			for (Class<?> nested : ParticleManager.class.getDeclaredClasses()) {
				// Development mappings call this SpriteAwareFactory; production
				// Mojang mappings expose the same interface as
				// SpriteParticleRegistration.
				if (nested.getSimpleName().equals("SpriteAwareFactory")
						|| nested.getSimpleName().equals("SpriteParticleRegistration")) {
					spriteAware = nested;
					break;
				}
			}
			if (spriteAware == null) {
				throw new IllegalStateException("SpriteAwareFactory not found");
			}
			Object proxy = Proxy.newProxyInstance(spriteAware.getClassLoader(),
					new Class<?>[]{spriteAware}, (instance, method, args) ->
							factory.apply((SpriteProvider) args[0]));
			RegisterParticleProvidersEvent.class
					.getMethod("registerSpriteSet", ParticleType.class, spriteAware)
					.invoke(event, type, proxy);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to register particle sprite set", exception);
		}
	}
}
