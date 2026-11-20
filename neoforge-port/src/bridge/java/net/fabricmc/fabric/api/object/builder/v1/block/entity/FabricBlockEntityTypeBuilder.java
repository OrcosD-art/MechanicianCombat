package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class FabricBlockEntityTypeBuilder<T extends BlockEntity> {
	@FunctionalInterface
	public interface Factory<T> {
		T create(BlockPos pos, BlockState state);
	}

	private final Factory<T> factory;
	private final List<Block> blocks = new ArrayList<>();

	private FabricBlockEntityTypeBuilder(Factory<T> factory) {
		this.factory = factory;
	}

	public static <T extends BlockEntity> FabricBlockEntityTypeBuilder<T> create(
			Factory<T> factory, Block... blocks) {
		FabricBlockEntityTypeBuilder<T> builder =
				new FabricBlockEntityTypeBuilder<>(factory);
		for (Block block : blocks) {
			builder.blocks.add(block);
		}
		return builder;
	}

	public FabricBlockEntityTypeBuilder<T> addBlock(Block block) {
		blocks.add(block);
		return this;
	}

	public BlockEntityType<T> build() {
		return com.tk.gun.neoforge.NeoBlockEntityRegistration.create(factory::create,
				blocks.toArray(Block[]::new));
	}
}
