package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerTickEvents {
	public interface EndTick {
		void onEndTick(MinecraftServer server);
	}

	public static final Event<EndTick> END_SERVER_TICK = new Event<>();

	private ServerTickEvents() {
	}

	public static final class Event<T> {
		private final List<T> listeners = new CopyOnWriteArrayList<>();

		public void register(T listener) {
			listeners.add(listener);
		}

		public List<T> listeners() {
			return listeners;
		}
	}
}
