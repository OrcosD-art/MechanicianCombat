package com.tk.gun.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Installable module for the Mechanician chestplate. */
public final class ArmorPartItem extends Item {
	public enum Kind {
		C1,
		C2,
		C3
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
		tooltip.add(Text.translatable("tooltip.tkgun.armor_part."
				+ kind.name().toLowerCase()));
		if (kind == Kind.C3) {
			tooltip.add(Text.translatable("tooltip.tkgun.soul_chain.chain"));
		}
	}
}
