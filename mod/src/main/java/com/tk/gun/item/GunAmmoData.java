package com.tk.gun.item;

import com.tk.gun.TkGun;
import com.tk.gun.screen.AmmoStorageScreenHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

/** 绑定在枪械 ItemStack 上的 8 格弹仓与当前选中槽。 */
public final class GunAmmoData {
	public static final int SIZE = 8;
	private static final String ROOT = "tkgun_ammo";
	private static final String SELECTED = "Selected";
	private static final String MAG = "tkgun_mag";
	private static final String MAG_AMMO = "tkgun_mag_ammo";
	private static final String MAG_VERSION = "tkgun_mag_version";

	public static ItemStack heldGun(PlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		return TkGun.isGun(stack) ? stack : ItemStack.EMPTY;
	}

	private static RegistryWrapper.WrapperLookup lookup(PlayerEntity player) {
		return player.getWorld().getRegistryManager();
	}

	public static DefaultedList<ItemStack> read(ItemStack gun, RegistryWrapper.WrapperLookup lookup) {
		DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
		NbtCompound root = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt().getCompound(ROOT);
		Inventories.readNbt(root, items, lookup);
		return items;
	}

	public static int selected(ItemStack gun) {
		NbtCompound root = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt().getCompound(ROOT);
		return Math.floorMod(root.getByte(SELECTED), SIZE);
	}

	/** 枪械是否已经保存过弹匣状态（用于区分新枪与“故意打空”的旧枪）。 */
	public static boolean hasSavedMagazine(ItemStack gun) {
		NbtCompound custom = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return custom.contains(MAG_AMMO);
	}

	public static int savedMagazineCount(ItemStack gun) {
		NbtCompound custom = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return custom.getInt(MAG);
	}

	public static String savedMagazineAmmo(ItemStack gun) {
		NbtCompound custom = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return custom.getString(MAG_AMMO);
	}

	public static int savedMagazineVersion(ItemStack gun) {
		NbtCompound custom = gun.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		return custom.getInt(MAG_VERSION);
	}

	/** 把腰射弹匣的“已打掉发数 + 弹种”写回枪械 NBT，跨存档保留。 */
	public static void saveMagazine(ItemStack gun, int mag, String ammoId) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, gun, custom -> {
			custom.putInt(MAG, Math.max(0, mag));
			custom.putString(MAG_AMMO, ammoId == null ? "" : ammoId);
			custom.putInt(MAG_VERSION, 2);
		});
	}

	public static ItemStack selectedAmmo(PlayerEntity player) {
		ItemStack gun = heldGun(player);
		if (gun.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return read(gun, lookup(player)).get(selected(gun));
	}

	public static boolean selectedIsExplosive(PlayerEntity player) {
		String path = selectedAmmoPath(player);
		return "explosive_bolt".equals(path);
	}

	public static boolean selectedIsCutting(PlayerEntity player) {
		return "cutting_bolt".equals(selectedAmmoPath(player));
	}

	public static String selectedAmmoId(PlayerEntity player) {
		ItemStack ammo = selectedAmmo(player);
		return ammo.isEmpty() ? ""
				: net.minecraft.registry.Registries.ITEM.getId(ammo.getItem()).toString();
	}

	/** 用注册 ID 建一个无附魔的弹药模板；无效 ID 返回空栈。 */
	public static ItemStack stackForId(String ammoId) {
		net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(ammoId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
		return item == net.minecraft.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	private static String selectedAmmoPath(PlayerEntity player) {
		ItemStack ammo = selectedAmmo(player);
		return ammo.isEmpty() ? ""
				: net.minecraft.registry.Registries.ITEM.getId(ammo.getItem()).getPath();
	}

	public static void save(ItemStack gun, DefaultedList<ItemStack> items, int selected,
							RegistryWrapper.WrapperLookup lookup) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, gun, custom -> {
			NbtCompound root = new NbtCompound();
			Inventories.writeNbt(root, items, lookup);
			root.putByte(SELECTED, (byte) Math.floorMod(selected, SIZE));
			custom.put(ROOT, root);
		});
	}

	/** direction = -1 逆时针上一槽，+1 顺时针下一槽。 */
	public static int cycle(PlayerEntity player, int direction) {
		ItemStack gun = heldGun(player);
		if (gun.isEmpty()) {
			return 0;
		}
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		DefaultedList<ItemStack> items = read(gun, lookup);
		int selected = Math.floorMod(selected(gun) + direction, SIZE);
		save(gun, items, selected, lookup);
		player.getInventory().markDirty();
		if (player.currentScreenHandler instanceof AmmoStorageScreenHandler handler) {
			handler.setSelectedSlot(selected);
		}
		ItemStack ammo = items.get(selected);
		player.sendMessage(Text.translatable("text.tkgun.current_ammo",
				ammo.isEmpty() ? Text.translatable("text.tkgun.empty") : ammo.getName()), true);
		return selected;
	}

	/** Selects the current stack of an ammo type while preserving the ring contents. */
	public static boolean selectAmmoType(PlayerEntity player, String ammoId) {
		ItemStack gun = heldGun(player);
		if (gun.isEmpty() || ammoId == null || ammoId.isEmpty()) {
			return false;
		}
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		DefaultedList<ItemStack> items = read(gun, lookup);
		int current = selected(gun);
		if (stackMatches(items.get(current), ammoId)) {
			return true;
		}
		for (int slot = 0; slot < SIZE; slot++) {
			if (stackMatches(items.get(slot), ammoId)) {
				save(gun, items, slot, lookup);
				player.getInventory().markDirty();
				return true;
			}
		}
		return false;
	}

	private static boolean stackMatches(ItemStack stack, String ammoId) {
		return !stack.isEmpty()
				&& ammoId.equals(net.minecraft.registry.Registries.ITEM
				.getId(stack.getItem()).toString());
	}

	/** 从当前选中槽消耗弹药。 */
	public static boolean consume(PlayerEntity player, int amount) {
		ItemStack gun = heldGun(player);
		if (gun.isEmpty()) {
			return false;
		}
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		DefaultedList<ItemStack> items = read(gun, lookup);
		int selected = selected(gun);
		ItemStack ammo = items.get(selected);
		if (ammo.isEmpty() || ammo.getCount() < amount || !AmmoItems.isAmmo(ammo)) {
			player.sendMessage(Text.translatable("text.tkgun.current_ammo",
					Text.translatable("text.tkgun.empty")), true);
			return false;
		}
		ammo.decrement(amount);
		if (ammo.isEmpty()) {
			items.set(selected, ItemStack.EMPTY);
		}
		save(gun, items, selected, lookup);
		player.getInventory().markDirty();
		return true;
	}

	/**
	 * 从所有相同弹种槽位原子消耗 amount 发。
	 * 先扣当前选中槽，再扣其余同弹种槽；若选中槽被扣空，自动切到下一个同弹种槽。
	 */
	public static boolean consumeExpected(PlayerEntity player, String expectedId, int amount) {
		ItemStack gun = heldGun(player);
		if (gun.isEmpty() || amount <= 0 || expectedId == null || expectedId.isEmpty()) {
			return false;
		}
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		DefaultedList<ItemStack> items = read(gun, lookup);
		int selected = selected(gun);
		int total = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty() && expectedId.equals(net.minecraft.registry.Registries.ITEM
					.getId(stack.getItem()).toString())) {
				total += stack.getCount();
			}
		}
		if (total < amount) {
			return false;
		}
		int remaining = consumeFrom(items, selected, expectedId, amount);
		for (int slot = 0; slot < SIZE && remaining > 0; slot++) {
			if (slot == selected) {
				continue;
			}
			remaining = consumeFrom(items, slot, expectedId, remaining);
		}
		if (remaining != 0) {
			return false;
		}
		int newSelected = selected;
		if (items.get(selected).isEmpty()) {
			for (int slot = 0; slot < SIZE; slot++) {
				ItemStack stack = items.get(slot);
				if (!stack.isEmpty() && expectedId.equals(net.minecraft.registry.Registries.ITEM
						.getId(stack.getItem()).toString())) {
					newSelected = slot;
					break;
				}
			}
		}
		save(gun, items, newSelected, lookup);
		player.getInventory().markDirty();
		return true;
	}

	private static int consumeFrom(DefaultedList<ItemStack> items, int slot, String expectedId,
								   int remaining) {
		ItemStack stack = items.get(slot);
		if (stack.isEmpty() || !expectedId.equals(net.minecraft.registry.Registries.ITEM
				.getId(stack.getItem()).toString())) {
			return remaining;
		}
		int take = Math.min(remaining, stack.getCount());
		stack.decrement(take);
		if (stack.isEmpty()) {
			items.set(slot, ItemStack.EMPTY);
		}
		return remaining - take;
	}

	/** Return rounds to a matching stack or an empty gun-ammo slot. */
	public static boolean refund(PlayerEntity player, String ammoId, int amount) {
		if (amount <= 0) {
			return true;
		}
		ItemStack template = stackForId(ammoId);
		return !template.isEmpty() && refund(player, template, amount);
	}

	/** 用带附魔/组件的模板退还弹药，优先合并到组件完全相同的堆叠。 */
	public static boolean refund(PlayerEntity player, ItemStack template, int amount) {
		return refund(player, heldGun(player), template, amount);
	}

	public static boolean refund(PlayerEntity player, ItemStack gun, ItemStack template, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (template.isEmpty() || !AmmoItems.isAmmo(template)) {
			return false;
		}
		if (gun.isEmpty()) {
			return false;
		}
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		DefaultedList<ItemStack> items = read(gun, lookup);
		int maxCount = template.getMaxCount();
		int capacity = 0;
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				capacity += maxCount;
			} else if (ItemStack.areItemsAndComponentsEqual(stack, template)) {
				capacity += Math.max(0, maxCount - stack.getCount());
			}
		}
		if (capacity < amount) {
			return false;
		}
		int remaining = amount;
		for (ItemStack stack : items) {
			if (stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(stack, template)) {
				continue;
			}
			int moved = Math.min(remaining, maxCount - stack.getCount());
			stack.increment(moved);
			remaining -= moved;
			if (remaining == 0) {
				break;
			}
		}
		for (int slot = 0; slot < items.size() && remaining > 0; slot++) {
			if (!items.get(slot).isEmpty()) {
				continue;
			}
			int moved = Math.min(remaining, maxCount);
			items.set(slot, template.copyWithCount(moved));
			remaining -= moved;
		}
		if (remaining != 0) {
			return false;
		}
		save(gun, items, selected(gun), lookup);
		player.getInventory().markDirty();
		return true;
	}

	public static AmmoInventory inventory(PlayerEntity player) {
		ItemStack gun = heldGun(player);
		RegistryWrapper.WrapperLookup lookup = lookup(player);
		return new AmmoInventory(player, gun, read(gun, lookup), selected(gun), lookup);
	}

	public static final class AmmoInventory implements Inventory {
		private final PlayerEntity player;
		private final ItemStack gun;
		private final DefaultedList<ItemStack> items;
		private int selected;
		private final RegistryWrapper.WrapperLookup lookup;

		private AmmoInventory(PlayerEntity player, ItemStack gun, DefaultedList<ItemStack> items,
							  int selected, RegistryWrapper.WrapperLookup lookup) {
			this.player = player;
			this.gun = gun;
			this.items = items;
			this.selected = selected;
			this.lookup = lookup;
		}

		public int selected() {
			return selected;
		}

		public void select(int slot) {
			selected = Math.floorMod(slot, SIZE);
			markDirty();
		}

		@Override
		public int size() {
			return SIZE;
		}

		@Override
		public boolean isEmpty() {
			return items.stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getStack(int slot) {
			return items.get(slot);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			ItemStack stack = Inventories.splitStack(items, slot, amount);
			if (!stack.isEmpty()) {
				markDirty();
			}
			return stack;
		}

		@Override
		public ItemStack removeStack(int slot) {
			ItemStack stack = Inventories.removeStack(items, slot);
			if (!stack.isEmpty()) {
				markDirty();
			}
			return stack;
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			items.set(slot, stack);
			markDirty();
		}

		@Override
		public void markDirty() {
			save(gun, items, selected, lookup);
			player.getInventory().markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return this.player == player && !gun.isEmpty();
		}

		@Override
		public boolean isValid(int slot, ItemStack stack) {
			return AmmoItems.isAmmo(stack);
		}

		@Override
		public void clear() {
			items.clear();
			markDirty();
		}
	}

	private GunAmmoData() {
	}
}
