package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.Locale;

/** Persistent module and cooldown state stored on the Mechanician chestplate. */
public final class ArmorUpgradeData {
	public static final int MAX_SHIELD_CHARGES = 3;
	private static final String SHIELD_COOLDOWN_UNTIL = "tkgun_shield_cooldown_until";
	private static final String TOTEM_COOLDOWN_UNTIL = "tkgun_totem_cooldown_until";
	private static final String SHIELD_CHARGES = "tkgun_shield_charges";
	private static final String SHIELD_RECHARGE_KILLS = "tkgun_shield_recharge_kills";
	private static final String SHIELD_RECHARGE_AT = "tkgun_shield_recharge_at";

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
		if (!isChestplate(chest)) {
			return "";
		}
		return customData(chest).getString(partKey(kind));
	}

	public static boolean hasPart(ItemStack chest, ArmorPartItem.Kind kind) {
		return !partId(chest, kind).isEmpty();
	}

	public static void setPartId(ItemStack chest, ArmorPartItem.Kind kind,
								 String itemId) {
		if (!isChestplate(chest)) {
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

	public static int shieldRechargeKills(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		NbtCompound data = customData(chest);
		return data.contains(SHIELD_RECHARGE_KILLS)
				? Math.max(0, Math.min(3, data.getInt(SHIELD_RECHARGE_KILLS)))
				: 0;
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

	private static String partKey(ArmorPartItem.Kind kind) {
		return "tkgun_armor_part_" + kind.name().toLowerCase(Locale.ROOT);
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
