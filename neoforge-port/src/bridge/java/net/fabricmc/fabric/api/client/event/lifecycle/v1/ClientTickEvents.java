package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientTickEvents {
	@FunctionalInterface
	public interface EndTick {
		void onEndTick(MinecraftClient client);
	}

	public static final Event END_CLIENT_TICK = new Event();

	private ClientTickEvents() {
	}

	public static final class Event {
		private final List<EndTick> listeners = new CopyOnWriteArrayList<>();

		public void register(EndTick listener) {
			listeners.add(listener);
		}

		public List<EndTick> listeners() {
			return listeners;
		}
	}
}
