package com.tk.gun.neoforge;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the Fabric-era direct Registry.register calls to NeoForge's deferred
 * RegisterEvent lifecycle. Values are constructed immediately, while insertion
 * is delayed until NeoForge opens the corresponding registry.
 */
public final class RegistryBridge {
	private record Pending(Identifier id, Object value) {
	}

	private static final Map<RegistryKey<? extends Registry<?>>, List<Pending>> PENDING =
			new HashMap<>();

	private RegistryBridge() {
	}

	public static void install(IEventBus modBus) {
		modBus.addListener(RegistryBridge::onRegister);
	}

	public static <T> T register(Registry<? super T> registry, Identifier id, T value) {
		@SuppressWarnings("unchecked")
		RegistryKey<? extends Registry<?>> key =
				(RegistryKey<? extends Registry<?>>) registry.getKey();
		PENDING.computeIfAbsent(key, ignored -> new ArrayList<>())
				.add(new Pending(id, value));
		return value;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void onRegister(RegisterEvent event) {
		RegistryKey key = event.getRegistryKey();
		List<Pending> entries = PENDING.remove(key);
		if (entries == null) {
			return;
		}
		for (Pending entry : entries) {
			event.register(key, entry.id(), () -> entry.value());
		}
	}
}
