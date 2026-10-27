package com.tk.gun.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Installable component for a combat backpack. */
public final class BackpackPartItem extends Item {
	public enum Kind {
		CORE,
		LAUNCHER,
		AUTO_LAUNCHER,
		SUMMON_ANTENNA
	}

	private final Kind kind;

	public BackpackPartItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public boolean isLauncherKind() {
		return kind == Kind.LAUNCHER || kind == Kind.AUTO_LAUNCHER
				|| kind == Kind.SUMMON_ANTENNA;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
							  TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		if (kind == Kind.LAUNCHER) {
			tooltip.add(Text.translatable("tooltip.tkgun.portable_launcher.storage"));
			tooltip.add(Text.translatable("tooltip.tkgun.portable_launcher.ammo"));
			tooltip.add(Text.translatable("tooltip.tkgun.portable_launcher.trajectory"));
		} else if (kind == Kind.AUTO_LAUNCHER) {
			tooltip.add(Text.translatable("tooltip.tkgun.auto_portable_launcher.storage"));
			tooltip.add(Text.translatable("tooltip.tkgun.auto_portable_launcher.attack"));
			tooltip.add(Text.translatable("tooltip.tkgun.auto_portable_launcher.charge"));
		} else if (kind == Kind.SUMMON_ANTENNA) {
			tooltip.add(Text.translatable("tooltip.tkgun.summon_antenna.deploy"));
			tooltip.add(Text.translatable("tooltip.tkgun.summon_antenna.mark"));
			tooltip.add(Text.translatable("tooltip.tkgun.summon_antenna.cast"));
		}
	}
}
