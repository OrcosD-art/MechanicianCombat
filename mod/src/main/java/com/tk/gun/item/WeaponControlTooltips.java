package com.tk.gun.item;

import com.tk.gun.CombatRollHook;
import com.tk.gun.TkGun;
import com.tk.gun.api.GunDefinition;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Adds live key-binding instructions to weapon tooltips. */
final class WeaponControlTooltips {
	static void addWeaponControls(List<Text> tooltip, GunDefinition definition) {
		tooltip.add(Text.translatable(
				"tooltip.mechaniciancombat.controls.header")
				.formatted(Formatting.GOLD));
		boolean kineticSword = definition.id().equals(TkGun.id("kinetic_sword"));
		add(tooltip, "key.attack", kineticSword
				? "tooltip.mechaniciancombat.controls.attack_melee"
				: "tooltip.mechaniciancombat.controls.fire");
		add(tooltip, "key.use", kineticSword
				? "tooltip.mechaniciancombat.controls.special_melee"
				: "tooltip.mechaniciancombat.controls.aim");
		if (!definition.meleeOnly()) {
			add(tooltip, "key.sneak",
					"tooltip.mechaniciancombat.controls.focus");
			add(tooltip, "key.swapOffhand",
					"tooltip.mechaniciancombat.controls.reload");
		}
		if (definition.id().equals(TkGun.id("abyssal_ancient_cannon"))
				|| definition.id().equals(TkGun.id("bowgun"))
				|| kineticSword) {
			add(tooltip, "key.jump",
					"tooltip.mechaniciancombat.controls.skill_jump");
		}
		add(tooltip, "key.sprint",
				"tooltip.mechaniciancombat.controls.anchor");
		add(tooltip, CombatRollHook.isInstalled()
						? "keybinds.combat_roll.roll"
						: "key.mechaniciancombat.roll",
				"tooltip.mechaniciancombat.controls.dodge");
		add(tooltip, "key.mechaniciancombat.backpack_action",
				"tooltip.mechaniciancombat.controls.backpack");
		if (!definition.meleeOnly()) {
			add(tooltip, "key.mechaniciancombat.ammo_ring",
					"tooltip.mechaniciancombat.controls.ammo_ring");
		}
	}

	static void addHunterSpearControls(List<Text> tooltip) {
		tooltip.add(Text.translatable(
				"tooltip.mechaniciancombat.controls.header")
				.formatted(Formatting.GOLD));
		add(tooltip, "key.use",
				"tooltip.mechaniciancombat.controls.hunter_deploy");
		add(tooltip, "key.use",
				"tooltip.mechaniciancombat.controls.hunter_recover");
	}

	private static void add(List<Text> tooltip, String key, String action) {
		tooltip.add(Text.literal("• ")
				.formatted(Formatting.DARK_GRAY)
				.append(Text.keybind(key).formatted(Formatting.GOLD))
				.append(Text.literal(" ").formatted(Formatting.GRAY))
				.append(Text.translatable(action)
						.formatted(Formatting.GRAY)));
	}

	private WeaponControlTooltips() {
	}
}
