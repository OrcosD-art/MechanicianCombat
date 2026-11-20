package com.tk.gun.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;

/** 弩炮的弹药：熔铁弩矢。和枪击/栓声/空弹仓配套 —— 没有它就走空弹仓音提示。 */
public class BoltItem extends Item {
	public BoltItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		String damageKey = switch (path) {
			case "bolt" -> "tooltip.mechaniciancombat.bolt.damage";
			case "explosive_bolt" -> "tooltip.mechaniciancombat.explosive_bolt.damage";
			case "cutting_bolt" -> "tooltip.mechaniciancombat.cutting_bolt.damage";
			case "sculk_bolt" -> "tooltip.mechaniciancombat.sculk_bolt.damage";
			case "rose_quartz_bolt" -> "tooltip.mechaniciancombat.rose_quartz_bolt.damage";
			case "armor_piercing_bolt" -> "tooltip.mechaniciancombat.armor_piercing_bolt.damage";
			default -> "tooltip.mechaniciancombat.basic.damage";
		};
		String effectKey = switch (path) {
			case "bolt" -> "tooltip.mechaniciancombat.bolt.effect";
			case "explosive_bolt" -> "tooltip.mechaniciancombat.explosive_bolt.effect";
			case "cutting_bolt" -> "tooltip.mechaniciancombat.cutting_bolt.effect";
			case "sculk_bolt" -> "tooltip.mechaniciancombat.sculk_bolt.effect";
			case "rose_quartz_bolt" -> "tooltip.mechaniciancombat.rose_quartz_bolt.effect";
			case "armor_piercing_bolt" -> "tooltip.mechaniciancombat.armor_piercing_bolt.effect";
			default -> "tooltip.mechaniciancombat.basic.effect";
		};
		BulletTooltips.addDamage(tooltip, damageKey);
		BulletTooltips.addEffect(tooltip, effectKey);
	}
}
