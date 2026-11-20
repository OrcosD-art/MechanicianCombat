package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;

/** Persistent ammunition stored by an ammo-using combat backpack. */
public final class BackpackAmmoData {
	private static final String STORED_AMMO =
			"mechaniciancombat_backpack_stored_ammo";

	public static ItemStack stored(LivingEntity entity) {
		if (entity == null) {
			return ItemStack.EMPTY;
		}
		ItemStack backpack = CombatBackpackItem.equippedStack(entity);
		ItemStack decoded = stored(backpack,
				entity.getWorld().getRegistryManager());
		return storageAccepts(entity, decoded) ? decoded : ItemStack.EMPTY;
	}

	public static ItemStack stored(ItemStack backpack,
			RegistryWrapper.WrapperLookup registries) {
		NbtCompound raw = rawStored(backpack);
		if (raw.getKeys().isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack decoded = ItemStack.fromNbtOrEmpty(registries, raw);
		return isSingleAcceptedStack(decoded)
				? decoded : ItemStack.EMPTY;
	}

	public static void setStored(ItemStack backpack, ItemStack stack,
			RegistryWrapper.WrapperLookup registries) {
		if (backpack == null || backpack.isEmpty()
				|| !(backpack.getItem() instanceof CombatBackpackItem)) {
			return;
		}
		if (stack != null && !stack.isEmpty()) {
			if (!isSingleAcceptedStack(stack)) {
				return;
			}
			stack = stack.copyWithCount(
					Math.min(stack.getCount(), stack.getMaxCount()));
		}
		ItemStack persisted = stack;
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (persisted == null || persisted.isEmpty()) {
				nbt.remove(STORED_AMMO);
			} else {
				nbt.put(STORED_AMMO,
						persisted.copy().encodeAllowEmpty(registries));
			}
		});
	}

	public static boolean storageAccepts(LivingEntity entity, ItemStack stack) {
		if (entity == null || !isSingleAcceptedStack(stack)
				|| !BackpackLauncherRules.usesAmmoStorage(entity)) {
			return false;
		}
		BackpackUpgradeData.LauncherType type = BackpackUpgradeData.launcherType(
				CombatBackpackItem.equippedStack(entity));
		return switch (type) {
			case PORTABLE -> BackpackLauncherRules.isLaunchable(stack);
			case AUTO, JUMP_THRUSTER ->
					stack.isOf(Items.FIREWORK_ROCKET);
			default -> false;
		};
	}

	/** Ammunition occupies exactly one regular item stack, never multiple groups. */
	public static boolean isSingleAcceptedStack(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getCount() > 0
				&& stack.getCount() <= stack.getMaxCount()
				&& BackpackLauncherRules.isLaunchable(stack);
	}

	public static void absorbOffhand(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack offhand = inventory.getStack(
				PlayerInventory.OFF_HAND_SLOT);
		if (offhand.isEmpty()) {
			return;
		}
		ItemStack backpack = CombatBackpackItem.equippedStack(player);
		ItemStack incoming = offhand.copy();
		inventory.setStack(PlayerInventory.OFF_HAND_SLOT, ItemStack.EMPTY);
		storeIncoming(player, backpack, incoming);
		if (!incoming.isEmpty()) {
			inventory.offerOrDrop(incoming);
		}
		markChanged(player);
	}

	public static boolean storeIncoming(ServerPlayerEntity player,
			ItemStack incoming) {
		return storeIncoming(player, CombatBackpackItem.equippedStack(player),
				incoming);
	}

	private static boolean storeIncoming(ServerPlayerEntity player,
			ItemStack backpack, ItemStack incoming) {
		if (!storageAccepts(player, incoming)) {
			return false;
		}
		RegistryWrapper.WrapperLookup registries =
				player.getServerWorld().getRegistryManager();
		ItemStack current = stored(backpack, registries);
		if (!current.isEmpty()
				&& !ItemStack.areItemsAndComponentsEqual(current, incoming)) {
			return false;
		}
		int count = Math.min(incoming.getCount(),
				incoming.getMaxCount() - current.getCount());
		if (count <= 0) {
			return false;
		}
		ItemStack merged = current.isEmpty()
				? incoming.copyWithCount(count) : current.copy();
		if (!current.isEmpty()) {
			merged.increment(count);
		}
		setStored(backpack, merged, registries);
		incoming.decrement(count);
		markChanged(player);
		return true;
	}

	public static boolean consumeOne(ServerPlayerEntity player) {
		ItemStack backpack = CombatBackpackItem.equippedStack(player);
		RegistryWrapper.WrapperLookup registries =
				player.getServerWorld().getRegistryManager();
		ItemStack stored = stored(backpack, registries);
		if (stored.isEmpty()) {
			return false;
		}
		stored.decrement(1);
		setStored(backpack, stored, registries);
		markChanged(player);
		return true;
	}

	public static void returnStoredIfUnsupported(ServerPlayerEntity player) {
		ItemStack backpack = CombatBackpackItem.equippedStack(player);
		if (backpack.isEmpty()) {
			return;
		}
		RegistryWrapper.WrapperLookup registries =
				player.getServerWorld().getRegistryManager();
		ItemStack stored = stored(backpack, registries);
		if (stored.isEmpty() || storageAccepts(player, stored)) {
			return;
		}
		setStored(backpack, ItemStack.EMPTY, registries);
		player.getInventory().offerOrDrop(stored);
		markChanged(player);
	}

	public static void markChanged(ServerPlayerEntity player) {
		player.getInventory().markDirty();
		player.playerScreenHandler.syncState();
		if (player.currentScreenHandler != player.playerScreenHandler) {
			player.currentScreenHandler.syncState();
		}
	}

	private static NbtCompound rawStored(ItemStack backpack) {
		if (backpack == null || backpack.isEmpty()) {
			return new NbtCompound();
		}
		NbtCompound data = backpack.getOrDefault(
				DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		if (!data.contains(STORED_AMMO, NbtElement.COMPOUND_TYPE)) {
			return new NbtCompound();
		}
		return data.getCompound(STORED_AMMO);
	}

	private BackpackAmmoData() {
	}
}
