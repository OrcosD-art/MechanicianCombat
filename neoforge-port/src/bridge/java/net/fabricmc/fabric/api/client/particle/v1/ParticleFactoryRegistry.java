package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.particle.ParticleEffect;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ParticleFactoryRegistry {
	public interface PendingParticleFactory<T extends ParticleEffect> {
		ParticleFactory<T> create(SpriteProvider spriteProvider);
	}

	private static final ParticleFactoryRegistry INSTANCE = new ParticleFactoryRegistry();
	private final Map<SimpleParticleType, PendingParticleFactory<?>> factories =
			new LinkedHashMap<>();

	private ParticleFactoryRegistry() {
	}

	public static ParticleFactoryRegistry getInstance() {
		return INSTANCE;
	}

	public <T extends ParticleEffect> void register(SimpleParticleType type,
			PendingParticleFactory<T> factory) {
		factories.put(type, factory);
	}

	public Map<SimpleParticleType, PendingParticleFactory<?>> factories() {
		return factories;
	}
}
