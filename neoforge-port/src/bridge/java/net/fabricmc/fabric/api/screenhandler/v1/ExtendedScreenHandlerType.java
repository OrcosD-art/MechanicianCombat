package net.fabricmc.fabric.api.screenhandler.v1;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class ExtendedScreenHandlerType {
	@FunctionalInterface
	public interface ExtendedFactory<T extends ScreenHandler, D> {
		T create(int syncId, PlayerInventory inventory, D data);
	}

	private ExtendedScreenHandlerType() {
	}

	public static <T extends ScreenHandler, D> ScreenHandlerType<T> create(
			ExtendedFactory<T, D> factory,
			PacketCodec<? super RegistryByteBuf, D> codec) {
		return IMenuTypeExtension.create((syncId, inventory, data) ->
				factory.create(syncId, inventory, codec.decode(data)));
	}
}
