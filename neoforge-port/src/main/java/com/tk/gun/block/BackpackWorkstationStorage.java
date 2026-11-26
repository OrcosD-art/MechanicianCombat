package com.tk.gun.block;

import com.tk.gun.item.BackpackPartItem;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import net.minecraft.block.Block;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Shared temporary backpack inventory surface for the two workstation blocks. */
public interface BackpackWorkstationStorage {
	int BACKPACK_SLOT = 0;
	int PART_SLOT_START = 1;
	int PART_SLOT_COUNT = 4;
	int SIZE = PART_SLOT_START + PART_SLOT_COUNT;

	SimpleInventory backpackInventory();

	default ItemStack backpackForDrops(World world) {
		return backpackInventory().getStack(BACKPACK_SLOT);
	}

	default void dropBackpackInventory(World world, BlockPos pos) {
		if (!(world instanceof ServerWorld)) {
			return;
		}
		SimpleInventory inventory = backpackInventory();
		ItemStack backpack = backpackForDrops(world);
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty()
					&& !isInstalledPartAlreadyStored(backpack, stack, slot)) {
				Block.dropStack(world, pos, stack);
			}
		}
		inventory.clear();
	}

	private static boolean isInstalledPartAlreadyStored(ItemStack backpack,
			ItemStack stack, int inventorySlot) {
		if (!(backpack.getItem() instanceof CombatBackpackItem)
				|| !(stack.getItem() instanceof BackpackPartItem part)) {
			return false;
		}
		int partSlot = inventorySlot
				- PART_SLOT_START;
		return switch (part.kind()) {
			case CORE -> BackpackUpgradeData.hasCore(backpack)
					&& partSlot == storedSlot(
							BackpackUpgradeData.coreSlot(backpack), 0);
			case LAUNCHER, AUTO_LAUNCHER, SUMMON_ANTENNA, JUMP_THRUSTER,
					BATTLE_BANNER ->
					BackpackUpgradeData.hasLauncher(backpack)
							&& partSlot == storedSlot(
									BackpackUpgradeData.launcherSlot(backpack), 1)
							&& part.kind() == kindFor(
									BackpackUpgradeData.launcherType(backpack));
			case MISSILE_BOX -> BackpackUpgradeData.hasMissileBox(backpack)
					&& partSlot == storedSlot(
							BackpackUpgradeData.missileBoxSlot(backpack), 3);
			case GOLEM_CAPSULE -> BackpackUpgradeData.hasGolemCapsule(backpack)
					&& partSlot == storedSlot(
							BackpackUpgradeData.golemCapsuleSlot(backpack), 3);
		};
	}

	private static int storedSlot(int stored, int fallback) {
		return stored >= 0 && stored < PART_SLOT_COUNT
				? stored : fallback;
	}

	private static BackpackPartItem.Kind kindFor(
			BackpackUpgradeData.LauncherType type) {
		return switch (type) {
			case AUTO -> BackpackPartItem.Kind.AUTO_LAUNCHER;
			case SUMMON_ANTENNA -> BackpackPartItem.Kind.SUMMON_ANTENNA;
			case JUMP_THRUSTER -> BackpackPartItem.Kind.JUMP_THRUSTER;
			case BATTLE_BANNER -> BackpackPartItem.Kind.BATTLE_BANNER;
			case PORTABLE, NONE -> BackpackPartItem.Kind.LAUNCHER;
		};
	}
}
