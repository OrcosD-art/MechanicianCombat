package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public final class FabricItemGroupEntries {
	private final ItemGroup.DisplayContext context;
	private final ItemGroup.Entries output;

	public FabricItemGroupEntries(ItemGroup.DisplayContext context,
			ItemGroup.Entries output) {
		this.context = context;
		this.output = output;
	}

	public ItemGroup.DisplayContext getContext() {
		return context;
	}

	public void add(ItemConvertible item) {
		output.add(item);
	}

	public void add(ItemStack stack) {
		output.add(stack);
	}
}
