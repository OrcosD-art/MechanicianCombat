package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Persistent installed-component flags stored on a Combat Backpack item. */
public final class BackpackUpgradeData {
	public enum LauncherType {
		NONE,
		PORTABLE,
		AUTO,
		SUMMON_ANTENNA
	}

	private static final String CORE = "tkgun_backpack_core";
	private static final String LAUNCHER = "tkgun_backpack_launcher";
	private static final String LAUNCHER_TYPE = "tkgun_backpack_launcher_type";
	private static final String CORE_SLOT = "tkgun_backpack_core_slot";
	private static final String LAUNCHER_SLOT = "tkgun_backpack_launcher_slot";

	public static boolean hasCore(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(CORE);
	}

	public static boolean hasLauncher(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(LAUNCHER);
	}

	public static boolean canUseLauncher(ItemStack backpack) {
		return hasCore(backpack) && hasLauncher(backpack);
	}

	public static LauncherType launcherType(ItemStack backpack) {
		if (!hasLauncher(backpack)) {
			return LauncherType.NONE;
		}
		int type = customData(backpack).getInt(LAUNCHER_TYPE);
		return switch (type) {
			case 2 -> LauncherType.AUTO;
			case 3 -> LauncherType.SUMMON_ANTENNA;
			default -> LauncherType.PORTABLE;
		};
	}

	public static int coreSlot(ItemStack backpack) {
		return slotValue(backpack, CORE_SLOT);
	}

	public static int launcherSlot(ItemStack backpack) {
		return slotValue(backpack, LAUNCHER_SLOT);
	}

	public static void installCore(ItemStack backpack) {
		setCore(backpack, true);
	}

	public static void installLauncher(ItemStack backpack) {
		if (isBackpack(backpack) && hasCore(backpack)) {
			setLauncher(backpack, true);
		}
	}

	public static void setCore(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(CORE, true);
			} else {
				nbt.remove(CORE);
			}
		});
	}

	public static void setLauncher(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(LAUNCHER, true);
			} else {
				nbt.remove(LAUNCHER);
				nbt.remove(LAUNCHER_TYPE);
			}
		});
	}

	public static void setLauncherType(ItemStack backpack, LauncherType type) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (type == LauncherType.NONE) {
				nbt.remove(LAUNCHER);
				nbt.remove(LAUNCHER_TYPE);
			} else {
				nbt.putBoolean(LAUNCHER, true);
				int encoded = switch (type) {
					case AUTO -> 2;
					case SUMMON_ANTENNA -> 3;
					default -> 1;
				};
				nbt.putInt(LAUNCHER_TYPE, encoded);
			}
		});
	}

	public static void setCoreSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, CORE_SLOT, slot);
	}

	public static void setLauncherSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, LAUNCHER_SLOT, slot);
	}

	private static int slotValue(ItemStack backpack, String key) {
		if (!isBackpack(backpack)) {
			return -1;
		}
		NbtCompound data = customData(backpack);
		return data.contains(key) ? data.getInt(key) : -1;
	}

	private static void setSlotValue(ItemStack backpack, String key, int slot) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (slot >= 0 && slot < 4) {
				nbt.putInt(key, slot);
			} else {
				nbt.remove(key);
			}
		});
	}

	private static NbtCompound customData(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt();
	}

	private static boolean isBackpack(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.getItem() instanceof CombatBackpackItem;
	}

	private BackpackUpgradeData() {
	}
}
