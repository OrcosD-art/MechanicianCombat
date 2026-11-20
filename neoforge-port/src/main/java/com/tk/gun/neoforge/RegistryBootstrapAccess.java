package com.tk.gun.neoforge;

import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * NeoForge freezes built-in registries before the mod constructor. Fabric-era
 * item constructors create intrusive holders immediately, so those registries
 * are temporarily opened while the object graph is constructed. They are
 * frozen again before NeoForge dispatches its own RegisterEvent callbacks.
 */
public final class RegistryBootstrapAccess {
	private final List<Registry<?>> opened = new ArrayList<>();

	private RegistryBootstrapAccess() {
	}

	public static RegistryBootstrapAccess open(Registry<?>... registries) {
		RegistryBootstrapAccess access = new RegistryBootstrapAccess();
		for (Registry<?> registry : registries) {
			try {
				Field frozen = field(registry.getClass(), "frozen");
				frozen.setBoolean(registry, false);
				Field intrusive = field(registry.getClass(),
						"unregisteredIntrusiveHolders");
				if (intrusive.get(registry) == null) {
					intrusive.set(registry, new IdentityHashMap<>());
				}
				access.opened.add(registry);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(
						"Unable to open registry " + registry, exception);
			}
		}
		return access;
	}

	public void close() {
		for (Registry<?> registry : opened) {
			try {
				field(registry.getClass(), "frozen").setBoolean(registry, true);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(
						"Unable to re-freeze registry " + registry, exception);
			}
		}
		opened.clear();
	}

	private static Field field(Class<?> type, String name)
			throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}
}
