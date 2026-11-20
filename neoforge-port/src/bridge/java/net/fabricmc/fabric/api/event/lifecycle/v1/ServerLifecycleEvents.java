package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerLifecycleEvents {
	public interface ServerLifecycle {
		void onServer(MinecraftServer server);
	}

	public static final Event<ServerLifecycle> SERVER_STARTED = new Event<>();
	public static final Event<ServerLifecycle> SERVER_STOPPING = new Event<>();
	public static final Event<ServerLifecycle> SERVER_STOPPED = new Event<>();

	private ServerLifecycleEvents() {
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
