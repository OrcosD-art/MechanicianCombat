package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;

import java.util.Locale;

/** Persistent module and cooldown state stored on the Mechanician chestplate. */
public final class ArmorUpgradeData {
	public static final int MAX_SHIELD_CHARGES = 3;
	/** Both authored helmet clips are 0.75 seconds at 20 ticks per second. */
	public static final int HELMET_EXPAND_TICKS = 15;
	public static final int HELMET_FOLD_TICKS = 15;
	private static final String SHIELD_COOLDOWN_UNTIL = "mechaniciancombat_shield_cooldown_until";
	private static final String TOTEM_COOLDOWN_UNTIL = "mechaniciancombat_totem_cooldown_until";
	private static final String SHIELD_CHARGES = "mechaniciancombat_shield_charges";
	private static final String SHIELD_RECHARGE_KILLS = "mechaniciancombat_shield_recharge_kills";
	private static final String SHIELD_RECHARGE_AT = "mechaniciancombat_shield_recharge_at";
	private static final String SHIELD_RECHARGE_WINDOW_UNTIL =
			"mechaniciancombat_shield_recharge_window_until";
	private static final String HELMET_EXPANDED = "mechaniciancombat_helmet_expanded";
	private static final String HELMET_TRANSITION_STARTED =
			"mechaniciancombat_helmet_transition_started";
	private static final String HELMET_TRANSITION_DURATION =
			"mechaniciancombat_helmet_transition_duration";
	private static final String PART_STACKS = "mechaniciancombat_armor_part_stacks";

	private ArmorUpgradeData() {
	}

	public static ItemStack equippedChestplate(PlayerEntity player) {
		ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
		return isChestplate(chest) ? chest : ItemStack.EMPTY;
	}

	public static boolean isChestplate(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.getItem() instanceof MechanicianArmorItem armor
				&& armor.piece() == MechanicianArmorPiece.CHESTPLATE;
	}

	public static String partId(ItemStack chest, ArmorPartItem.Kind kind) {
		if (!isPartContainer(chest, kind)) {
			return "";
		}
		return customData(chest).getString(partKey(kind));
	}

	public static boolean hasPart(ItemStack chest, ArmorPartItem.Kind kind) {
		return !partId(chest, kind).isEmpty();
	}

	public static void setPartId(ItemStack chest, ArmorPartItem.Kind kind,
								 String itemId) {
		if (!isPartContainer(chest, kind)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, chest, nbt -> {
			if (itemId == null || itemId.isEmpty()) {
				nbt.remove(partKey(kind));
			} else {
				nbt.putString(partKey(kind), itemId);
			}
		});
	}

	/**
	 * Returns the currently equipped armor item that owns the requested module
	 * group. C parts live on the chestplate, T parts on the leggings and F parts
	 * on the boots.
	 */
	public static ItemStack equippedPartContainer(PlayerEntity player,
			ArmorPartItem.Kind kind) {
		ItemStack stack = player.getEquippedStack(kind.equipmentSlot());
		return isPartContainer(stack, kind) ? stack : ItemStack.EMPTY;
	}

	public static boolean isPartContainer(ItemStack stack,
			ArmorPartItem.Kind kind) {
		if (stack == null || stack.isEmpty()
				|| !(stack.getItem() instanceof MechanicianArmorItem armor)) {
			return false;
		}
		return switch (kind.group()) {
			case CHEST -> armor.piece() == MechanicianArmorPiece.CHESTPLATE;
			case LEGS -> armor.piece() == MechanicianArmorPiece.LEGGINGS;
			case FEET -> armor.piece() == MechanicianArmorPiece.BOOTS;
		};
	}

	/**
	 * Stores the complete module stack so item components survive equipping,
	 * removing and re-equipping the armor piece.
	 */
	public static void setStoredPart(ItemStack container,
			ArmorPartItem.Kind kind, ItemStack stack,
			RegistryWrapper.WrapperLookup registries) {
		if (!isPartContainer(container, kind)) {
			return;
		}
		boolean valid = stack.getItem() instanceof ArmorPartItem part
				&& part.kind() == kind;
		String itemId = valid
				? Registries.ITEM.getId(stack.getItem()).toString() : "";
		NbtElement encoded = valid
				? stack.copyWithCount(1).encode(registries) : null;
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, container, nbt -> {
			if (valid) {
				nbt.putString(partKey(kind), itemId);
			} else {
				nbt.remove(partKey(kind));
			}
			NbtCompound parts = nbt.getCompound(PART_STACKS);
			if (encoded == null) {
				parts.remove(kind.name());
			} else {
				parts.put(kind.name(), encoded.copy());
			}
			if (parts.getKeys().isEmpty()) {
				nbt.remove(PART_STACKS);
			} else {
				nbt.put(PART_STACKS, parts);
			}
		});
	}

	public static ItemStack storedPart(ItemStack container,
			ArmorPartItem.Kind kind, RegistryWrapper.WrapperLookup registries) {
		if (!isPartContainer(container, kind)) {
			return ItemStack.EMPTY;
		}
		NbtCompound parts = customData(container).getCompound(PART_STACKS);
		if (!parts.contains(kind.name(), NbtElement.COMPOUND_TYPE)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = ItemStack.fromNbtOrEmpty(
				registries, parts.getCompound(kind.name()));
		return stack.getItem() instanceof ArmorPartItem part
				&& part.kind() == kind ? stack : ItemStack.EMPTY;
	}

	public static long shieldCooldownUntil(ItemStack chest) {
		return longValue(chest, SHIELD_COOLDOWN_UNTIL);
	}

	public static long totemCooldownUntil(ItemStack chest) {
		return longValue(chest, TOTEM_COOLDOWN_UNTIL);
	}

	public static long shieldRechargeAt(ItemStack chest) {
		return longValue(chest, SHIELD_RECHARGE_AT);
	}

	public static int shieldCharges(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		NbtCompound data = customData(chest);
		return data.contains(SHIELD_CHARGES)
				? Math.max(0, Math.min(MAX_SHIELD_CHARGES,
						data.getInt(SHIELD_CHARGES)))
				: MAX_SHIELD_CHARGES;
	}

	public static boolean helmetExpanded(ItemStack chest) {
		if (!isChestplate(chest)) {
			return false;
		}
		return customData(chest).getBoolean(HELMET_EXPANDED);
	}

	public static long helmetTransitionStarted(ItemStack chest) {
		return longValue(chest, HELMET_TRANSITION_STARTED);
	}

	public static int helmetTransitionDuration(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		NbtCompound data = customData(chest);
		return Math.max(0, data.getInt(HELMET_TRANSITION_DURATION));
	}

	public static void beginHelmetTransition(ItemStack chest, boolean expanded,
											 long startedAt) {
		if (!isChestplate(chest)) {
			return;
		}
		int duration = expanded ? HELMET_EXPAND_TICKS : HELMET_FOLD_TICKS;
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, chest, nbt -> {
			nbt.putBoolean(HELMET_EXPANDED, expanded);
			nbt.putLong(HELMET_TRANSITION_STARTED, startedAt);
			nbt.putInt(HELMET_TRANSITION_DURATION, duration);
		});
	}

	public static int shieldRechargeKills(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		NbtCompound data = customData(chest);
		return data.contains(SHIELD_RECHARGE_KILLS)
				? Math.max(0, Math.min(3, data.getInt(SHIELD_RECHARGE_KILLS)))
				: 0;
	}

	public static long shieldRechargeWindowUntil(ItemStack chest) {
		return longValue(chest, SHIELD_RECHARGE_WINDOW_UNTIL);
	}

	public static void setShieldCooldownUntil(ItemStack chest, long until) {
		setLong(chest, SHIELD_COOLDOWN_UNTIL, until);
	}

	public static void setTotemCooldownUntil(ItemStack chest, long until) {
		setLong(chest, TOTEM_COOLDOWN_UNTIL, until);
	}

	public static void setShieldRechargeAt(ItemStack chest, long until) {
		setLong(chest, SHIELD_RECHARGE_AT, until);
	}

	public static void setShieldCharges(ItemStack chest, int charges) {
		if (!isChestplate(chest)) {
			return;
		}
		int clamped = Math.max(0, Math.min(MAX_SHIELD_CHARGES, charges));
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, chest,
				nbt -> nbt.putInt(SHIELD_CHARGES, clamped));
	}

	public static void setShieldRechargeKills(ItemStack chest, int kills) {
		if (!isChestplate(chest)) {
			return;
		}
		int clamped = Math.max(0, Math.min(3, kills));
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, chest, nbt -> {
			if (clamped == 0) {
				nbt.remove(SHIELD_RECHARGE_KILLS);
			} else {
				nbt.putInt(SHIELD_RECHARGE_KILLS, clamped);
			}
		});
	}

	public static void setShieldRechargeWindowUntil(ItemStack chest, long until) {
		setLong(chest, SHIELD_RECHARGE_WINDOW_UNTIL, until);
	}

	private static String partKey(ArmorPartItem.Kind kind) {
		return "mechaniciancombat_armor_part_" + kind.name().toLowerCase(Locale.ROOT);
	}

	private static long longValue(ItemStack stack, String key) {
		if (!isChestplate(stack)) {
			return 0L;
		}
		NbtCompound data = customData(stack);
		return data.contains(key) ? data.getLong(key) : 0L;
	}

	private static void setLong(ItemStack stack, String key, long value) {
		if (!isChestplate(stack)) {
			return;
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
			if (value > 0L) {
				nbt.putLong(key, value);
			} else {
				nbt.remove(key);
			}
		});
	}

	private static NbtCompound customData(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt();
	}
}
