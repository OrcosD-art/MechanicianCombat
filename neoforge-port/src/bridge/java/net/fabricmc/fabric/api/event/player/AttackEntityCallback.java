package net.fabricmc.fabric.api.event.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@FunctionalInterface
public interface AttackEntityCallback {
	Event EVENT = new Event();

	ActionResult interact(PlayerEntity player, World world, Hand hand, Entity entity,
			EntityHitResult hitResult);

	final class Event {
		private final List<AttackEntityCallback> listeners = new CopyOnWriteArrayList<>();

		public void register(AttackEntityCallback listener) {
			listeners.add(listener);
		}

		public List<AttackEntityCallback> listeners() {
			return listeners;
		}
	}
}
