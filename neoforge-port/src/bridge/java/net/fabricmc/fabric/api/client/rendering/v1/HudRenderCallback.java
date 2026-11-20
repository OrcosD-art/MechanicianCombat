package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HudRenderCallback {
	@FunctionalInterface
	public interface HudRender {
		void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter);
	}

	public static final Event EVENT = new Event();

	private HudRenderCallback() {
	}

	public static final class Event {
		private final List<HudRender> listeners = new CopyOnWriteArrayList<>();

		public void register(HudRender listener) {
			listeners.add(listener);
		}

		public List<HudRender> listeners() {
			return listeners;
		}
	}
}
