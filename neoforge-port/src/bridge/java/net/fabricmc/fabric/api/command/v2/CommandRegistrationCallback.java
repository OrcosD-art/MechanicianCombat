package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@FunctionalInterface
public interface CommandRegistrationCallback {
	Event EVENT = new Event();

	void register(CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment);

	final class Event {
		private final List<CommandRegistrationCallback> listeners =
				new CopyOnWriteArrayList<>();

		public void register(CommandRegistrationCallback listener) {
			listeners.add(listener);
		}

		public List<CommandRegistrationCallback> listeners() {
			return listeners;
		}
	}
}
