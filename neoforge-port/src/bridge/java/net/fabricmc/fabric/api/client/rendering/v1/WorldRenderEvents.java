package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldRenderEvents {
	@FunctionalInterface
	public interface End {
		void onEnd(WorldRenderContext context);
	}

	public static final Event AFTER_ENTITIES = new Event();

	private WorldRenderEvents() {
	}

	public static final class Event {
		private final List<End> listeners = new CopyOnWriteArrayList<>();

		public void register(End listener) {
			listeners.add(listener);
		}

		public List<End> listeners() {
			return listeners;
		}
	}
}
