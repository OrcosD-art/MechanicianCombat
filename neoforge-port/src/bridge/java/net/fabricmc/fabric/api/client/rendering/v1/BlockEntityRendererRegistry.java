package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;

/**
 * Compile-time bridge for shared client code. NeoForge registers block entity
 * renderers directly in its EntityRenderersEvent handler.
 */
public final class BlockEntityRendererRegistry {
	private BlockEntityRendererRegistry() {
	}

	public static <E extends BlockEntity> void register(
			BlockEntityType<E> type,
			BlockEntityRendererFactory<? super E> factory) {
		// Registered by NeoForgeClientBootstrap.
	}
}
