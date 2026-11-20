package net.fabricmc.fabric.api.screenhandler.v1;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public interface ExtendedScreenHandlerFactory<D> extends NamedScreenHandlerFactory {
	D getScreenOpeningData(ServerPlayerEntity player);
}
