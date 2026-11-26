package com.tk.gun.screen;

import com.tk.gun.TkGunServer;
import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Equipment viewer and chestplate module installer.
 *
 * <p>The three authored slots mirror the player's chest, legs and feet
 * equipment slots. They are never extracted, inserted or persisted.</p>
 */
public final class ArmorModificationTableScreenHandler extends ScreenHandler {
	public static final int ARMOR_SLOT_START = 0;
	public static final int ARMOR_SLOT_COUNT = 3;
	public static final int PART_SLOT_START = ARMOR_SLOT_COUNT;
	public static final int PART_SLOT_COUNT = ArmorPartItem.Kind.values().length;
	public static final int PLAYER_SLOT_START = PART_SLOT_START + PART_SLOT_COUNT;
	public static final int HOTBAR_SLOT_START = PLAYER_SLOT_START + 27;

	private static final EquipmentSlot[] DISPLAY_SLOTS = {
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};
	private static final int[][] ARMOR_SLOT_POSITIONS = {
			{7, 11},
			{7, 29},
			{7, 47}
	};
	private static final int[][] PART_SLOT_POSITIONS = {
			{26, 11},
			{44, 11},
			{62, 11},
			{26, 29},
			{44, 29},
			{62, 29},
			{26, 47},
			{44, 47},
			{62, 47}
	};
	private static final ArmorPartItem.Kind[] PART_SECTIONS = {
			ArmorPartItem.Kind.C1,
			ArmorPartItem.Kind.T1,
			ArmorPartItem.Kind.F1
	};

	public static final ScreenHandlerType<ArmorModificationTableScreenHandler> TYPE =
			new ExtendedScreenHandlerType<ArmorModificationTableScreenHandler, BlockPos>(
					(syncId, playerInventory, pos) ->
							new ArmorModificationTableScreenHandler(
									syncId, playerInventory, pos),
					BlockPos.PACKET_CODEC);

	private final BlockPos pos;
	private final PlayerEntity owner;
	private final Inventory armorInventory = new SimpleInventory(ARMOR_SLOT_COUNT);
	private final Inventory partInventory = new SimpleInventory(PART_SLOT_COUNT);
	private final ItemStack[] lastPartContainers = {
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};
	private boolean syncing;

	public ArmorModificationTableScreenHandler(
			int syncId,
			PlayerInventory playerInventory,
			BlockPos pos) {
		super(TYPE, syncId);
		this.pos = pos.toImmutable();
		this.owner = playerInventory.player;

		for (int index = 0; index < ARMOR_SLOT_COUNT; index++) {
			int[] position = ARMOR_SLOT_POSITIONS[index];
			addSlot(new Slot(armorInventory, index, position[0], position[1]) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return false;
				}

				@Override
				public boolean canTakeItems(PlayerEntity playerEntity) {
					return false;
				}
			});
		}
		for (int index = 0; index < PART_SLOT_COUNT; index++) {
			int[] position = PART_SLOT_POSITIONS[index];
			int fixedIndex = index;
			addSlot(new Slot(partInventory, index, position[0], position[1]) {
				@Override
				public boolean canInsert(ItemStack stack) {
					if (!(stack.getItem() instanceof ArmorPartItem part)
							|| part.slotIndex() != fixedIndex) {
						return false;
					}
					return !ArmorUpgradeData.equippedPartContainer(
							owner, part.kind()).isEmpty();
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(
						playerInventory,
						column + row * 9 + 9,
						8 + column * 18,
						84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
		syncTableState();
	}

	@Override
	public void sendContentUpdates() {
		syncTableState();
		super.sendContentUpdates();
	}

	private void syncTableState() {
		if (syncing || !(owner instanceof ServerPlayerEntity player)) {
			return;
		}
		syncing = true;
		try {
			for (int index = 0; index < DISPLAY_SLOTS.length; index++) {
				ItemStack equipped = player.getEquippedStack(DISPLAY_SLOTS[index]);
				ItemStack displayed = armorInventory.getStack(index);
				if (!ItemStack.areEqual(displayed, equipped)) {
					armorInventory.setStack(index,
							equipped.isEmpty() ? ItemStack.EMPTY : equipped.copy());
				}
			}
			boolean chestChanged = false;
			var registries = player.getServerWorld().getRegistryManager();
			for (int section = 0; section < PART_SECTIONS.length; section++) {
				ArmorPartItem.Kind sectionKind = PART_SECTIONS[section];
				ItemStack container = ArmorUpgradeData.equippedPartContainer(
						player, sectionKind);
				if (!ItemStack.areEqual(container,
						lastPartContainers[section])) {
					loadPartSlots(container, section, registries);
				}
				boolean changed = persistPartSlots(
						container, section, registries);
				if (changed) {
					player.equipStack(sectionKind.equipmentSlot(),
							container.copy());
					if (sectionKind.group()
							== ArmorPartItem.Kind.Group.CHEST) {
						chestChanged = true;
					}
				}
				lastPartContainers[section] =
						container.isEmpty() ? ItemStack.EMPTY : container.copy();
			}
			if (chestChanged) {
				com.tk.gun.ArmorAbilities.sendSnapshot(player);
			}
		} finally {
			syncing = false;
		}
	}

	private void loadPartSlots(ItemStack container, int section,
			net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
		int start = section * 3;
		for (int offset = 0; offset < 3; offset++) {
			ArmorPartItem.Kind kind =
					ArmorPartItem.Kind.values()[start + offset];
			ItemStack stored = ArmorUpgradeData.storedPart(
					container, kind, registries);
			if (stored.isEmpty()) {
				stored = stackForId(
						ArmorUpgradeData.partId(container, kind), kind);
			}
			partInventory.setStack(start + offset, stored);
		}
	}

	private boolean persistPartSlots(ItemStack container, int section,
			net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
		if (container.isEmpty()) {
			return false;
		}
		boolean changed = false;
		int start = section * 3;
		for (int offset = 0; offset < 3; offset++) {
			ArmorPartItem.Kind kind =
					ArmorPartItem.Kind.values()[start + offset];
			ItemStack stack = partInventory.getStack(start + offset);
			boolean valid = stack.getItem() instanceof ArmorPartItem part
					&& part.kind() == kind;
			ItemStack desired = valid ? stack.copyWithCount(1) : ItemStack.EMPTY;
			String installedId = valid
					? Registries.ITEM.getId(stack.getItem()).toString() : "";
			ItemStack stored = ArmorUpgradeData.storedPart(
					container, kind, registries);
			if (stored.isEmpty()) {
				stored = stackForId(
						ArmorUpgradeData.partId(container, kind), kind);
			}
			if (!ItemStack.areEqual(stored, desired)
					|| !Objects.equals(
							ArmorUpgradeData.partId(container, kind),
							installedId)) {
				changed = true;
			}
			ArmorUpgradeData.setStoredPart(
					container, kind, desired, registries);
		}
		return changed;
	}

	private static ItemStack stackForId(String itemId, ArmorPartItem.Kind kind) {
		if (itemId == null || itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		var item = Registries.ITEM.get(id);
		return item instanceof ArmorPartItem part && part.kind() == kind
				? new ItemStack(item) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		if (slotIndex < ARMOR_SLOT_START || slotIndex >= slots.size()
				|| slotIndex < PLAYER_SLOT_START && slotIndex < PART_SLOT_START) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getStack();
		ItemStack copy = original.copy();
		boolean moved;
		if (slotIndex >= PART_SLOT_START && slotIndex < PLAYER_SLOT_START) {
			moved = insertItem(original, PLAYER_SLOT_START, slots.size(), true);
		} else if (original.getItem() instanceof ArmorPartItem part) {
			int target = PART_SLOT_START + part.slotIndex();
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
		return copy;
	}

	@Override
	public ScreenHandlerType<?> getType() {
		return TYPE;
	}

	public BlockPos pos() {
		return pos;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		// Persist modules that were inserted immediately before closing. The
		// slot inventory is only a working copy of data embedded in armor.
		syncTableState();
		partInventory.clear();
		super.onClosed(player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		var state = player.getWorld().getBlockState(pos);
		return com.tk.gun.block.ArmorDressingStationBlock.isStation(state)
				&& player instanceof ServerPlayerEntity serverPlayer
				&& com.tk.gun.block.ArmorDressingStationService
						.isMounted(serverPlayer)
				&& player.squaredDistanceTo(
						pos.getX() + 0.5D,
						pos.getY() + 0.5D,
						pos.getZ() + 0.5D) <= 64.0D;
	}
}
