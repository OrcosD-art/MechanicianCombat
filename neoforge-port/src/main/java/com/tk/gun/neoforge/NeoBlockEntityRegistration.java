package com.tk.gun.neoforge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

public final class NeoBlockEntityRegistration {
	private NeoBlockEntityRegistration() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity> BlockEntityType<T> create(
			BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
		try {
			Class<?> factoryType = null;
			for (Class<?> nested : BlockEntityType.class.getDeclaredClasses()) {
				if (nested.getSimpleName().equals("BlockEntitySupplier")) {
					factoryType = nested;
					break;
				}
			}
			if (factoryType == null) {
				throw new IllegalStateException("BlockEntitySupplier not found");
			}
			Object proxy = Proxy.newProxyInstance(factoryType.getClassLoader(),
					new Class<?>[]{factoryType}, (instance, method, args) ->
							factory.apply((BlockPos) args[0], (BlockState) args[1]));
			Class<?> builderType = Class.forName(
					"net.minecraft.world.level.block.entity.BlockEntityType$Builder");
			Object builder = builderType.getMethod("of", factoryType, Block[].class)
					.invoke(null, proxy, blocks);
			return (BlockEntityType<T>) builderType.getMethod("build",
					com.mojang.datafixers.types.Type.class).invoke(builder, new Object[]{null});
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to create block entity type", exception);
		}
	}
}
