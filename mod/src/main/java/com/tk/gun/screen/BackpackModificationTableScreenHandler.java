package com.tk.gun.screen;

import com.tk.gun.TkGunServer;
import com.tk.gun.block.BackpackModificationTableBlockEntity;
import com.tk.gun.item.BackpackPartItem;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Persistent backpack component installer.
 *
 * <p>The backpack remains in the right-hand slot. The upper-left component slot
 * accepts only a core, the upper-right accepts only a portable launcher, and the
 * lower two slots remain reserved. Closing the screen never returns the backpack
 * to the player.</p>
 */
public final class BackpackModificationTableScreenHandler extends ScreenHandler {
	public static final int BACKPACK_SLOT_X = 152;
	public static final int BACKPACK_SLOT_Y = 66;
	public static final int BACKPACK_SLOT_INDEX =
			BackpackModificationTableBlockEntity.BACKPACK_SLOT;
	public static final int FIRST_PART_SLOT_INDEX = 1;
	public static final int PLAYER_SLOT_START = 5;
	public static final int HOTBAR_SLOT_START = 32;
	public static final int CORE_PART_SLOT = 0;
	public static final int LAUNCHER_PART_SLOT = 1;

	private static final int[][] PART_SLOT_POSITIONS = {
			{8, 30},
			{26, 30},
			{8, 48},
			{26, 48}
	};

	public static final ScreenHandlerType<BackpackModificationTableScreenHandler> TYPE =
			new ExtendedScreenHandlerType<>(
					(syncId, playerInventory, pos) ->
							new BackpackModificationTableScreenHandler(
									syncId, playerInventory, pos),
					BlockPos.PACKET_CODEC);

	private final BlockPos pos;
	private final Inventory tableInventory;
	private final PlayerEntity owner;
	private ItemStack lastBackpack = ItemStack.EMPTY;
	private boolean installing;

	public BackpackModificationTableScreenHandler(
			int syncId,
			PlayerInventory playerInventory,
			BlockPos pos) {
		super(TYPE, syncId);
		this.pos = pos.toImmutable();
		this.owner = playerInventory.player;
		if (playerInventory.player instanceof ServerPlayerEntity
				&& playerInventory.player.getWorld()
						.getBlockEntity(this.pos)
						instanceof BackpackModificationTableBlockEntity table) {
			tableInventory = table.inventory();
		} else {
			tableInventory = new SimpleInventory(BackpackModificationTableBlockEntity.SIZE);
		}

		addSlot(new Slot(tableInventory, BackpackModificationTableBlockEntity.BACKPACK_SLOT,
				BACKPACK_SLOT_X, BACKPACK_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof CombatBackpackItem;
			}
		});
		for (int index = 0; index < PART_SLOT_POSITIONS.length; index++) {
			int slotIndex = BackpackModificationTableBlockEntity.PART_SLOT_START + index;
			int[] position = PART_SLOT_POSITIONS[index];
			int fixedIndex = index;
			addSlot(new Slot(tableInventory, slotIndex, position[0], position[1]) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.getItem() instanceof BackpackPartItem part
							&& acceptsPart(fixedIndex, part.kind());
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9,
						8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
		syncTableState();
		normalizeFixedPartSlots();
	}

	@Override
	public ScreenHandlerType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getStack();
		ItemStack copy = original.copy();
		boolean moved = false;
		if (slotIndex < PLAYER_SLOT_START) {
			moved = insertItem(
					original,
					PLAYER_SLOT_START,
					slots.size(),
					true);
		} else if (original.getItem() instanceof CombatBackpackItem) {
			moved = insertItem(
					original,
					BackpackModificationTableBlockEntity.BACKPACK_SLOT,
					BackpackModificationTableBlockEntity.BACKPACK_SLOT + 1,
					true);
		} else if (original.getItem() instanceof BackpackPartItem) {
			BackpackPartItem.Kind kind =
					((BackpackPartItem) original.getItem()).kind();
			int target = BackpackModificationTableBlockEntity.PART_SLOT_START
					+ fixedSlot(kind);
			moved = insertItem(original, target, target + 1, true);
		} else if (slotIndex < HOTBAR_SLOT_START) {
			moved = insertItem(original, HOTBAR_SLOT_START, slots.size(), true);
		} else {
			moved = insertItem(original, PLAYER_SLOT_START, HOTBAR_SLOT_START, true);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		syncTableState();
		return copy;
	}

	@Override
	public void onSlotClick(
			int slotIndex,
			int button,
			SlotActionType actionType,
			PlayerEntity player) {
		super.onSlotClick(slotIndex, button, actionType, player);
		syncTableState();
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		super.onContentChanged(inventory);
		syncTableState();
	}

	private void syncTableState() {
		if (installing || !(owner instanceof ServerPlayerEntity)) {
			return;
		}
		ItemStack backpack = tableInventory.getStack(
				BackpackModificationTableBlockEntity.BACKPACK_SLOT);
		if (!(backpack.getItem() instanceof CombatBackpackItem)) {
			if (!lastBackpack.isEmpty()) {
				stowInstalledPart(lastBackpack, BackpackPartItem.Kind.CORE);
					stowInstalledLauncher(lastBackpack);
				tableInventory.markDirty();
				sendContentUpdates();
			}
			lastBackpack = ItemStack.EMPTY;
			return;
		}
		installing = true;
		try {
			boolean replaced = !ItemStack.areEqual(backpack, lastBackpack);
			boolean preserveCore = false;
			boolean preserveLauncher = false;
			if (replaced) {
				if (BackpackUpgradeData.hasCore(backpack)
						&& !hasPart(BackpackPartItem.Kind.CORE)) {
					if (insertPart(
							BackpackPartItem.Kind.CORE)) {
						preserveCore = false;
					} else {
						preserveCore = true;
					}
				}
				if (BackpackUpgradeData.hasLauncher(backpack)
						&& !hasLauncherPart()) {
					if (insertLauncherPart(
							BackpackUpgradeData.launcherType(backpack))) {
						preserveLauncher = false;
					} else {
						preserveLauncher = true;
					}
				}
			}
			int coreSlot = partSlot(BackpackPartItem.Kind.CORE);
			ItemStack launcherPart = launcherPartStack();
			BackpackUpgradeData.LauncherType launcherType =
					launcherPart.getItem() instanceof BackpackPartItem part
							&& part.kind() == BackpackPartItem.Kind.AUTO_LAUNCHER
							? BackpackUpgradeData.LauncherType.AUTO
							: launcherPart.getItem() instanceof BackpackPartItem summonPart
									&& summonPart.kind() == BackpackPartItem.Kind.SUMMON_ANTENNA
									? BackpackUpgradeData.LauncherType.SUMMON_ANTENNA
							: launcherPart.isEmpty() && preserveLauncher
									? BackpackUpgradeData.launcherType(backpack)
									: BackpackUpgradeData.LauncherType.PORTABLE;
			boolean core = coreSlot >= 0 || preserveCore;
			boolean launcher = core
					&& (!launcherPart.isEmpty() || preserveLauncher);
			BackpackUpgradeData.setCore(backpack, core);
			BackpackUpgradeData.setLauncherType(backpack,
					launcher ? launcherType : BackpackUpgradeData.LauncherType.NONE);
			BackpackUpgradeData.setCoreSlot(
					backpack,
					coreSlot >= 0 ? coreSlot
							: (preserveCore ? BackpackUpgradeData.coreSlot(backpack) : -1));
			BackpackUpgradeData.setLauncherSlot(
					backpack,
					launcher ? LAUNCHER_PART_SLOT
							: (preserveLauncher ? BackpackUpgradeData.launcherSlot(backpack) : -1));
			lastBackpack = backpack.copy();
			tableInventory.markDirty();
		} finally {
			installing = false;
		}
		sendContentUpdates();
	}

	private void stowInstalledLauncher(ItemStack backpack) {
		if (!BackpackUpgradeData.hasLauncher(backpack)) {
			return;
		}
		for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
				slot < BackpackModificationTableBlockEntity.SIZE; slot++) {
			ItemStack stack = tableInventory.getStack(slot);
			if (stack.getItem() instanceof BackpackPartItem part && part.isLauncherKind()) {
				removeOne(tableInventory, slot, stack);
				return;
			}
		}
	}

	private void stowInstalledPart(ItemStack backpack, BackpackPartItem.Kind kind) {
		boolean installed = kind == BackpackPartItem.Kind.CORE
				? BackpackUpgradeData.hasCore(backpack)
				: BackpackUpgradeData.hasLauncher(backpack);
		if (!installed) {
			return;
		}
		for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
				slot < BackpackModificationTableBlockEntity.SIZE;
				slot++) {
			ItemStack stack = tableInventory.getStack(slot);
			if (stack.getItem() instanceof BackpackPartItem part
					&& part.kind() == kind) {
				removeOne(tableInventory, slot, stack);
				return;
			}
		}
	}

	private void removeOne(Inventory inventory, int slot, ItemStack stack) {
		stack.decrement(1);
		if (stack.isEmpty()) {
			inventory.setStack(slot, ItemStack.EMPTY);
		}
	}

	private boolean hasPart(BackpackPartItem.Kind kind) {
		return partSlot(kind) >= 0;
	}

	private boolean hasLauncherPart() {
		return !launcherPartStack().isEmpty();
	}

	private ItemStack launcherPartStack() {
		ItemStack stack = tableInventory.getStack(
				BackpackModificationTableBlockEntity.PART_SLOT_START + LAUNCHER_PART_SLOT);
		return stack.getItem() instanceof BackpackPartItem part && part.isLauncherKind()
				? stack : ItemStack.EMPTY;
	}

	private int partSlot(BackpackPartItem.Kind kind) {
		ItemStack stack = tableInventory.getStack(
				BackpackModificationTableBlockEntity.PART_SLOT_START + fixedSlot(kind));
		return stack.getItem() instanceof BackpackPartItem part && part.kind() == kind
				? fixedSlot(kind) : -1;
	}

	private boolean insertPart(BackpackPartItem.Kind kind) {
		int fixedIndex = BackpackModificationTableBlockEntity.PART_SLOT_START
				+ fixedSlot(kind);
		if (tableInventory.getStack(fixedIndex).isEmpty()) {
			tableInventory.setStack(fixedIndex, newPart(kind));
			return true;
		}
		return false;
	}

	private boolean insertLauncherPart(BackpackUpgradeData.LauncherType type) {
		if (type == BackpackUpgradeData.LauncherType.NONE) {
			return false;
		}
		return insertPart(switch (type) {
			case AUTO -> BackpackPartItem.Kind.AUTO_LAUNCHER;
			case SUMMON_ANTENNA -> BackpackPartItem.Kind.SUMMON_ANTENNA;
			default -> BackpackPartItem.Kind.LAUNCHER;
		});
	}

	private void normalizeFixedPartSlots() {
		if (installing || !(owner instanceof ServerPlayerEntity)) {
			return;
		}
		installing = true;
		try {
			ItemStack core = ItemStack.EMPTY;
			ItemStack launcher = ItemStack.EMPTY;
			for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
					slot < BackpackModificationTableBlockEntity.SIZE; slot++) {
				ItemStack stack = tableInventory.getStack(slot);
				if (stack.isEmpty()) {
					continue;
				}
				BackpackPartItem.Kind kind = stack.getItem() instanceof BackpackPartItem part
						? part.kind() : null;
				if (kind == BackpackPartItem.Kind.CORE && core.isEmpty()) {
					core = stack.copy();
				} else if ((kind == BackpackPartItem.Kind.LAUNCHER
						|| kind == BackpackPartItem.Kind.AUTO_LAUNCHER
						|| kind == BackpackPartItem.Kind.SUMMON_ANTENNA)
						&& launcher.isEmpty()) {
					launcher = stack.copy();
				} else {
					owner.getInventory().offerOrDrop(stack.copy());
				}
				tableInventory.setStack(slot, ItemStack.EMPTY);
			}
			tableInventory.setStack(
					BackpackModificationTableBlockEntity.PART_SLOT_START + CORE_PART_SLOT,
					core);
			tableInventory.setStack(
					BackpackModificationTableBlockEntity.PART_SLOT_START + LAUNCHER_PART_SLOT,
					launcher);
			tableInventory.markDirty();
		} finally {
			installing = false;
		}
		sendContentUpdates();
	}

	private static int fixedSlot(BackpackPartItem.Kind kind) {
		return kind == BackpackPartItem.Kind.CORE
				? CORE_PART_SLOT : LAUNCHER_PART_SLOT;
	}

	private static boolean acceptsPart(int slot, BackpackPartItem.Kind kind) {
		return switch (slot) {
			case CORE_PART_SLOT -> kind == BackpackPartItem.Kind.CORE;
			case LAUNCHER_PART_SLOT ->
					kind == BackpackPartItem.Kind.LAUNCHER
							|| kind == BackpackPartItem.Kind.AUTO_LAUNCHER
							|| kind == BackpackPartItem.Kind.SUMMON_ANTENNA;
			default -> false;
		};
	}

	private ItemStack newPart(BackpackPartItem.Kind kind) {
		return switch (kind) {
			case CORE -> new ItemStack(TkGunServer.BACKPACK_CORE);
			case LAUNCHER -> new ItemStack(TkGunServer.PORTABLE_LAUNCHER);
			case AUTO_LAUNCHER -> new ItemStack(TkGunServer.AUTO_PORTABLE_LAUNCHER);
			case SUMMON_ANTENNA -> new ItemStack(TkGunServer.SUMMON_ANTENNA);
		};
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getWorld().getBlockState(pos)
				.isOf(TkGunServer.BACKPACK_MODIFICATION_TABLE)
				&& player.squaredDistanceTo(
						pos.getX() + 0.5D,
						pos.getY() + 0.5D,
						pos.getZ() + 0.5D) <= 64.0D;
	}
}
