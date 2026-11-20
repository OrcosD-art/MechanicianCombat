package net.fabricmc.loader.api;

import net.neoforged.fml.ModList;

public final class FabricLoader {
	private static final FabricLoader INSTANCE = new FabricLoader();

	public static FabricLoader getInstance() {
		return INSTANCE;
	}

	public boolean isModLoaded(String id) {
		return ModList.get().isLoaded(id);
	}
}
