package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** 本模组所有可作为弹药的物品判定。 */
public final class AmmoItems {
	public static boolean isAmmo(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		if (item == TkGunServer.BOLT) {
			return true;
		}
		return TkGunServer.BULLET_ITEMS != null && TkGunServer.BULLET_ITEMS.contains(item);
	}

	private AmmoItems() {
	}
}
