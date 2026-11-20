package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerPlayConnectionEvents {
	@FunctionalInterface
	public interface Join {
		void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender,
				MinecraftServer server);
	}

	@FunctionalInterface
	public interface Disconnect {
		void onDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server);
	}

	public interface PacketSender {
	}

	public static final Event<Join> JOIN = new Event<>();
	public static final Event<Disconnect> DISCONNECT = new Event<>();

	private ServerPlayConnectionEvents() {
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
