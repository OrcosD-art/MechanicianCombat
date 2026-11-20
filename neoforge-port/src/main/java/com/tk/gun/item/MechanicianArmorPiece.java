package com.tk.gun.item;

import com.tk.gun.TkGun;
import net.minecraft.util.Identifier;

public enum MechanicianArmorPiece {
	UNDERSUIT("undersuit"),
	HELMET("helmet"),
	CHESTPLATE("chestplate"),
	LEGGINGS("leggings"),
	BOOTS("boots");

	private final String modelName;

	MechanicianArmorPiece(String modelName) {
		this.modelName = modelName;
	}

	public Identifier modelLocation() {
		return TkGun.id("armor_models/" + modelName + ".bbmodel");
	}

	public Identifier textureLocation() {
		return TkGun.id("textures/entity/mechanician_" + modelName + ".png");
	}

	public String modelName() {
		return modelName;
	}
}
