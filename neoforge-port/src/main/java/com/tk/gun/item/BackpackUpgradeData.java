package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/** Persistent installed-component flags stored on a Combat Backpack item. */
public final class BackpackUpgradeData {
	public enum LauncherType {
		NONE,
		PORTABLE,
		AUTO,
		SUMMON_ANTENNA,
		JUMP_THRUSTER,
		BATTLE_BANNER
	}

	private static final String CORE = "mechaniciancombat_backpack_core";
	private static final String LAUNCHER = "mechaniciancombat_backpack_launcher";
	private static final String LAUNCHER_TYPE = "mechaniciancombat_backpack_launcher_type";
	private static final String CORE_SLOT = "mechaniciancombat_backpack_core_slot";
	private static final String LAUNCHER_SLOT = "mechaniciancombat_backpack_launcher_slot";
	private static final String MISSILE_BOX = "mechaniciancombat_backpack_missile_box";
	private static final String MISSILE_BOX_SLOT = "mechaniciancombat_backpack_missile_box_slot";
	private static final String GOLEM_CAPSULE =
			"mechaniciancombat_backpack_golem_capsule";
	private static final String GOLEM_CAPSULE_SLOT =
			"mechaniciancombat_backpack_golem_capsule_slot";
	private static final String WORKSTATION_BINDING =
			"mechaniciancombat_backpack_workstation_binding";

	public static boolean hasCore(ItemStack backpack) {
		if (!isBackpack(backpack)) {
			return false;
		}
		NbtCompound data = customData(backpack);
		return data.getBoolean(CORE) || data.contains(CORE_SLOT);
	}

	public static boolean hasLauncher(ItemStack backpack) {
		if (!isBackpack(backpack)) {
			return false;
		}
		NbtCompound data = customData(backpack);
		return data.getBoolean(LAUNCHER)
				|| data.contains(LAUNCHER_TYPE)
				|| data.contains(LAUNCHER_SLOT);
	}

	public static boolean canUseLauncher(ItemStack backpack) {
		return hasCore(backpack) && hasLauncher(backpack);
	}

	public static boolean canUseJumpThruster(ItemStack backpack) {
		return canUseLauncher(backpack)
				&& launcherType(backpack) == LauncherType.JUMP_THRUSTER;
	}

	public static boolean hasMissileBox(ItemStack backpack) {
		if (!isBackpack(backpack)) {
			return false;
		}
		NbtCompound data = customData(backpack);
		return data.getBoolean(MISSILE_BOX)
				|| data.contains(MISSILE_BOX_SLOT);
	}

	public static boolean canUseMissileBox(ItemStack backpack) {
		return canUseJumpThruster(backpack) && hasMissileBox(backpack);
	}

	public static boolean hasGolemCapsule(ItemStack backpack) {
		if (!isBackpack(backpack)) {
			return false;
		}
		NbtCompound data = customData(backpack);
		return data.getBoolean(GOLEM_CAPSULE)
				|| data.contains(GOLEM_CAPSULE_SLOT);
	}

	public static UUID workstationBinding(ItemStack backpack) {
		if (!isBackpack(backpack)) {
			return null;
		}
		NbtCompound data = customData(backpack);
		return data.containsUuid(WORKSTATION_BINDING)
				? data.getUuid(WORKSTATION_BINDING) : null;
	}

	public static void setWorkstationBinding(ItemStack backpack,
			UUID binding) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (binding == null) {
				nbt.remove(WORKSTATION_BINDING);
			} else {
				nbt.putUuid(WORKSTATION_BINDING, binding);
			}
		});
	}

	public static boolean canSummonGolems(ItemStack backpack) {
		return canUseLauncher(backpack)
				&& launcherType(backpack) == LauncherType.SUMMON_ANTENNA
				&& hasGolemCapsule(backpack);
	}

	public static LauncherType launcherType(ItemStack backpack) {
		if (!hasLauncher(backpack)) {
			return LauncherType.NONE;
		}
		int type = customData(backpack).getInt(LAUNCHER_TYPE);
		return switch (type) {
			case 2 -> LauncherType.AUTO;
			case 3 -> LauncherType.SUMMON_ANTENNA;
			case 4 -> LauncherType.JUMP_THRUSTER;
			case 5 -> LauncherType.BATTLE_BANNER;
			default -> LauncherType.PORTABLE;
		};
	}

	public static int coreSlot(ItemStack backpack) {
		return slotValue(backpack, CORE_SLOT);
	}

	public static int launcherSlot(ItemStack backpack) {
		return slotValue(backpack, LAUNCHER_SLOT);
	}

	public static int missileBoxSlot(ItemStack backpack) {
		return slotValue(backpack, MISSILE_BOX_SLOT);
	}

	public static int golemCapsuleSlot(ItemStack backpack) {
		return slotValue(backpack, GOLEM_CAPSULE_SLOT);
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
				nbt.remove(MISSILE_BOX);
				nbt.remove(MISSILE_BOX_SLOT);
				nbt.remove(GOLEM_CAPSULE);
				nbt.remove(GOLEM_CAPSULE_SLOT);
			} else {
				nbt.putBoolean(LAUNCHER, true);
				int encoded = switch (type) {
					case AUTO -> 2;
					case SUMMON_ANTENNA -> 3;
					case JUMP_THRUSTER -> 4;
					case BATTLE_BANNER -> 5;
					default -> 1;
				};
				nbt.putInt(LAUNCHER_TYPE, encoded);
				if (type != LauncherType.JUMP_THRUSTER) {
					nbt.remove(MISSILE_BOX);
					nbt.remove(MISSILE_BOX_SLOT);
				}
				if (type != LauncherType.SUMMON_ANTENNA) {
					nbt.remove(GOLEM_CAPSULE);
					nbt.remove(GOLEM_CAPSULE_SLOT);
				}
			}
		});
	}

	public static void setCoreSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, CORE_SLOT, slot);
	}

	public static void setLauncherSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, LAUNCHER_SLOT, slot);
	}

	public static void setMissileBox(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(MISSILE_BOX, true);
			} else {
				nbt.remove(MISSILE_BOX);
				nbt.remove(MISSILE_BOX_SLOT);
			}
		});
	}

	public static void setMissileBoxSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, MISSILE_BOX_SLOT, slot);
	}

	public static void setGolemCapsule(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(GOLEM_CAPSULE, true);
			} else {
				nbt.remove(GOLEM_CAPSULE);
				nbt.remove(GOLEM_CAPSULE_SLOT);
			}
		});
	}

	public static void setGolemCapsuleSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, GOLEM_CAPSULE_SLOT, slot);
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
