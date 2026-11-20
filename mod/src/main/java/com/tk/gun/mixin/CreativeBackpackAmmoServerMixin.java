package com.tk.gun.mixin;

import com.tk.gun.item.BackpackAmmoData;
import com.tk.gun.item.BackpackAmmoSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla creative inventory actions only accept slots 1 through 45. The
 * backpack's persistent ammunition slot is the appended slot 46, so mirror
 * vanilla's validated creative-write path for that slot only.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class CreativeBackpackAmmoServerMixin {
	@Inject(method = "method_12070", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/class_3225;method_14268()Z",
			shift = At.Shift.AFTER), cancellable = true)
	private void mechaniciancombat$acceptCreativeAmmoSlot(
			CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
		ServerPlayerEntity player =
				((ServerPlayNetworkHandler) (Object) this).getPlayer();
		int slotId = packet.slot();
		if (!player.isCreative() || slotId < 0
				|| slotId >= player.playerScreenHandler.slots.size()) {
			return;
		}
		Slot slot = player.playerScreenHandler.getSlot(slotId);
		if (!(slot instanceof BackpackAmmoSlot)) {
			return;
		}
		ItemStack stack = packet.stack();
		boolean valid = stack.isEmpty()
				|| (stack.isItemEnabled(player.getWorld().getEnabledFeatures())
				&& stack.getCount() <= stack.getMaxCount()
				&& BackpackAmmoData.storageAccepts(player, stack));
		if (valid) {
			slot.setStack(stack.copy());
			player.playerScreenHandler.sendContentUpdates();
		} else {
			// Rejecting only the packet can leave an over-stacked client
			// prediction visible until another inventory update happens.
			player.playerScreenHandler.syncState();
		}
		ci.cancel();
	}
}
