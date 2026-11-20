package com.tk.gun.neoforge;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * NeoForge container factories read an auxiliary data buffer. Fabric-style
 * MenuProvider#openMenu does not carry that buffer, so all custom menus go
 * through NeoForge's IPlayerExtension overloads.
 */
public final class NeoMenuOpening {
	private NeoMenuOpening() {
	}

	public static void openBlock(Object player, Object menuProvider,
			Object blockPos) {
		try {
			Class<?> menuProviderType = Class.forName(
					"net.minecraft.world.MenuProvider");
			for (Method method : player.getClass().getMethods()) {
				if (!"openMenu".equals(method.getName())
						|| method.getParameterCount() != 2
						|| method.getParameterTypes()[0] != menuProviderType) {
					continue;
				}
				if (method.getParameterTypes()[1].isInstance(blockPos)) {
					method.setAccessible(true);
					method.invoke(player, menuProvider, blockPos);
					return;
				}
			}
			throw new IllegalStateException("NeoForge openMenu(BlockPos) not found");
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to open block menu", exception);
		}
	}

	public static void openByte(Object player, Object menuProvider, byte value) {
		try {
			Class<?> menuProviderType = Class.forName(
					"net.minecraft.world.MenuProvider");
			Consumer<Object> data = buffer -> {
				try {
					buffer.getClass().getMethod("writeByte", int.class)
							.invoke(buffer, value);
				} catch (ReflectiveOperationException exception) {
					throw new IllegalStateException(
							"Unable to write menu opening data", exception);
				}
			};
			for (Method method : player.getClass().getMethods()) {
				if (!"openMenu".equals(method.getName())
						|| method.getParameterCount() != 2
						|| method.getParameterTypes()[0] != menuProviderType
						|| method.getParameterTypes()[1] != Consumer.class) {
					continue;
				}
				method.setAccessible(true);
				method.invoke(player, menuProvider, data);
				return;
			}
			throw new IllegalStateException("NeoForge openMenu(Consumer) not found");
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to open data menu", exception);
		}
	}
}
