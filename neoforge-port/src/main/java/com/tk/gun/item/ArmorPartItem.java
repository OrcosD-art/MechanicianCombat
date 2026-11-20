package com.tk.gun.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.entity.EquipmentSlot;

import java.util.List;

/** Installable module for the Mechanician chestplate. */
public final class ArmorPartItem extends Item {
	public enum Kind {
		C1(Group.CHEST),
		C2(Group.CHEST),
		C3(Group.CHEST),
		T1(Group.LEGS),
		T2(Group.LEGS),
		T3(Group.LEGS),
		F1(Group.FEET),
		F2(Group.FEET),
		F3(Group.FEET);

		public enum Group {
			CHEST(EquipmentSlot.CHEST),
			LEGS(EquipmentSlot.LEGS),
			FEET(EquipmentSlot.FEET);

			private final EquipmentSlot equipmentSlot;

			Group(EquipmentSlot equipmentSlot) {
				this.equipmentSlot = equipmentSlot;
			}

			public EquipmentSlot equipmentSlot() {
				return equipmentSlot;
			}
		}

		private final Group group;

		Kind(Group group) {
			this.group = group;
		}

		public Group group() {
			return group;
		}

		public EquipmentSlot equipmentSlot() {
			return group.equipmentSlot();
		}
	}

	private final Kind kind;

	public ArmorPartItem(Kind kind, Settings settings) {
		super(settings.maxCount(1));
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public int slotIndex() {
		return kind.ordinal();
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context,
							  List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("tooltip.mechaniciancombat.armor_part."
				+ kind.name().toLowerCase()));
		if (kind == Kind.C3) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.soul_chain.chain"));
		}
	}
}
