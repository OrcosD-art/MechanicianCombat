package net.fabricmc.fabric.api.event.player;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerBlockBreakEvents {
	@FunctionalInterface
	public interface Before {
		boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos,
				BlockState state, BlockEntity blockEntity);
	}

	public static final Event BEFORE = new Event();

	private PlayerBlockBreakEvents() {
	}

	public static final class Event {
		private final List<Before> listeners = new CopyOnWriteArrayList<>();

		public void register(Before listener) {
			listeners.add(listener);
		}

		public List<Before> listeners() {
			return listeners;
		}
	}
}
