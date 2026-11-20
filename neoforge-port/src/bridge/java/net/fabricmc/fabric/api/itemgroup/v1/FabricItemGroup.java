package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.item.ItemGroup;

public final class FabricItemGroup {
	private FabricItemGroup() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private net.minecraft.text.Text displayName = net.minecraft.text.Text.empty();
		private java.util.function.Supplier<net.minecraft.item.ItemStack> icon =
				() -> net.minecraft.item.ItemStack.EMPTY;
		private java.util.function.BiConsumer<ItemGroup.DisplayContext,
				FabricItemGroupEntries> entries =
				(context, collector) -> {
				};

		public Builder displayName(net.minecraft.text.Text name) {
			displayName = name;
			return this;
		}

		public Builder icon(
				java.util.function.Supplier<net.minecraft.item.ItemStack> itemIcon) {
			icon = itemIcon;
			return this;
		}

		public Builder entries(java.util.function.BiConsumer<ItemGroup.DisplayContext,
				FabricItemGroupEntries> collector) {
			entries = collector;
			return this;
		}

		public ItemGroup build() {
			return new ItemGroup.Builder(ItemGroup.Row.TOP, 7)
					.displayName(displayName)
					.icon(icon)
					.entries((context, output) ->
							entries.accept(context,
									new FabricItemGroupEntries(context, output)))
					.build();
		}
	}
}
