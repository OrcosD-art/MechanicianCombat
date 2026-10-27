package com.tk.gun.item;

import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.util.List;

/** 弹药 tooltip 的统一排版：标点只用于拆分，不进入实际显示文本。 */
final class BulletTooltips {
	static void addDamage(List<Text> tooltip, String damageKey) {
		tooltip.add(Text.translatable("tooltip.tkgun.damage", Text.translatable(damageKey))
				.formatted(Formatting.GRAY));
	}

	static void addEffect(List<Text> tooltip, String effectKey) {
		String raw = Text.translatable(effectKey).getString();
		boolean first = true;
		for (String part : raw.split("\\r?\\n|；|;")) {
			String line = part.trim();
			if (line.isEmpty()) {
				continue;
			}
			MutableText text = first
					? Text.translatable("tooltip.tkgun.effect", Text.literal(line))
					: Text.literal(line);
			tooltip.add(text.formatted(Formatting.GRAY));
			first = false;
		}
	}

	private BulletTooltips() {
	}
}
