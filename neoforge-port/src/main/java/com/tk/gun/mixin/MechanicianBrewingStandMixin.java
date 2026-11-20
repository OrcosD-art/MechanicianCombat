package com.tk.gun.mixin;

import com.tk.gun.item.MechanicianBrewingRecipes;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends brewing stands with item-to-item recipes. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class MechanicianBrewingStandMixin {
	private static final int CUSTOM_BREW_TICKS = 400;

	@Inject(method = "method_31665", at = @At("HEAD"), cancellable = true)
	private static void mechaniciancombat$tickCustomBrewing(World world,
			BlockPos pos, BlockState state,
			BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
		if (!(world instanceof ServerWorld)) {
			return;
		}
		BrewingStandBlockEntityAccessor access =
				(BrewingStandBlockEntityAccessor) (Object) blockEntity;
		DefaultedList<ItemStack> inventory = access
				.mechaniciancombat$getInventory();
		ItemStack template = MechanicianBrewingRecipes.find(
				inventory.get(3), inventory.get(0));
		if (template.isEmpty()) {
			return;
		}
		ItemStack output = inventory.get(2);
		if (!output.isEmpty()
				&& (!ItemStack.areItemsAndComponentsEqual(output, template)
				|| output.getCount() + template.getCount()
						> output.getMaxCount())) {
			access.mechaniciancombat$setBrewTime(0);
			ci.cancel();
			return;
		}

		ItemStack fuelStack = inventory.get(4);
		if (access.mechaniciancombat$getFuel() <= 0
				&& fuelStack.isOf(Items.BLAZE_POWDER)) {
			access.mechaniciancombat$setFuel(20);
			fuelStack.decrement(1);
		}
		if (access.mechaniciancombat$getFuel() <= 0) {
			updateBottleState(world, pos, state, inventory);
			blockEntity.markDirty();
			ci.cancel();
			return;
		}
		if (access.mechaniciancombat$getBrewTime() <= 0) {
			access.mechaniciancombat$setBrewTime(CUSTOM_BREW_TICKS);
			access.mechaniciancombat$setFuel(
					access.mechaniciancombat$getFuel() - 1);
		} else {
			access.mechaniciancombat$setBrewTime(
					access.mechaniciancombat$getBrewTime() - 1);
		}
		if (access.mechaniciancombat$getBrewTime() <= 0) {
			craft(world, pos, inventory, template);
		}
		updateBottleState(world, pos, state, inventory);
		blockEntity.markDirty();
		world.markDirty(pos);
		world.updateComparators(pos, state.getBlock());
		ci.cancel();
	}

	@Inject(method = "method_5437", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$allowCustomInputs(int slot,
			ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		BrewingStandBlockEntityAccessor access =
				(BrewingStandBlockEntityAccessor) (Object) this;
		DefaultedList<ItemStack> inventory = access
				.mechaniciancombat$getInventory();
		if (slot == 3 && MechanicianBrewingRecipes.acceptsInput(stack)) {
			cir.setReturnValue(true);
		} else if (slot == 0
				&& MechanicianBrewingRecipes.acceptsReagent(
						inventory.get(3), stack)) {
			cir.setReturnValue(true);
		}
	}

	private static void craft(World world, BlockPos pos,
			DefaultedList<ItemStack> inventory, ItemStack template) {
		inventory.get(3).decrement(1);
		inventory.get(0).decrement(1);
		ItemStack output = inventory.get(2);
		if (output.isEmpty()) {
			inventory.set(2, template.copy());
		} else {
			output.increment(template.getCount());
		}
		world.syncWorldEvent(1035, pos, 0);
	}

	private static void updateBottleState(World world, BlockPos pos,
			BlockState state, DefaultedList<ItemStack> inventory) {
		BlockState updated = state;
		for (int index = 0; index < 3; index++) {
			updated = updated.with(
					BrewingStandBlock.BOTTLE_PROPERTIES[index],
					!inventory.get(index).isEmpty());
		}
		if (updated != state) {
			world.setBlockState(pos, updated, 2);
		}
	}
}
