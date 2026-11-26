package com.tk.gun.screen;

import com.tk.gun.item.AmmoItems;
import com.tk.gun.item.GunAmmoData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 机械师弹药环。
 *
 * <p>8 个环形槽位直接映射玩家背包中存档使用的
 * {@code 3,4,12,13,20,23,29,32}，其余背包槽仍然可见、可正常整理。
 * 环形槽只允许放入本模组弹药。
 */
public final class AmmoStorageScreenHandler extends ScreenHandler {
	public static final int SIZE = GunAmmoData.SIZE;
	private static final int CENTER_X = 88;
	private static final int CENTER_Y = 56;
	private static final int RADIUS = 37;

	public static final ScreenHandlerType<AmmoStorageScreenHandler> TYPE =
			createVanillaType();
	private final PropertyDelegate selectedSlot;

	public AmmoStorageScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(TYPE, syncId);
		GunAmmoData.AmmoInventory serverInventory =
				playerInventory.player instanceof ServerPlayerEntity serverPlayer
						? GunAmmoData.inventory(serverPlayer) : null;
		var inventory = serverInventory != null
				? serverInventory
				: new net.minecraft.inventory.SimpleInventory(SIZE);
		for (int index = 0; index < SIZE; index++) {
			addSlot(new Slot(inventory, index, slotX(index), slotY(index)) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return AmmoItems.isAmmo(stack);
				}
			});
		}
		for (int index = 0; index < 36; index++) {
			addSlot(new Slot(playerInventory, index, inventoryX(index), inventoryY(index)));
		}
		ArrayPropertyDelegate selected = new ArrayPropertyDelegate(1);
		selected.set(0, serverInventory == null ? 0 : serverInventory.selected());
		addProperties(selected);
		this.selectedSlot = selected;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (slotIndex < SIZE && !slot.hasStack()) {
			return depositAmmo(player, slot);
		}
		ItemStack original = slot.getStack();
		ItemStack copy = original.copy();
		if (slotIndex < SIZE) {
			player.getInventory().insertStack(original);
		} else if (AmmoItems.isAmmo(original)
				&& insertItem(original, 0, SIZE, true)) {
			// 弹药优先进入环形仓库。
		} else {
			int inventoryIndex = slotIndex - SIZE;
			if (inventoryIndex < 9) {
				if (!insertItem(original, SIZE + 9, SIZE + 36, true)) {
					return ItemStack.EMPTY;
				}
			} else if (!insertItem(original, SIZE, SIZE + 9, true)) {
				return ItemStack.EMPTY;
			}
		}
		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return copy;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
		if (type == SlotActionType.QUICK_MOVE && slotIndex >= 0 && slotIndex < SIZE
				&& !slots.get(slotIndex).hasStack()) {
			depositAmmo(player, slots.get(slotIndex));
			return;
		}
		super.onSlotClick(slotIndex, button, type, player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	public int getSelectedSlot() {
		return Math.floorMod(selectedSlot.get(0), SIZE);
	}

	public void setSelectedSlot(int slot) {
		selectedSlot.set(0, Math.floorMod(slot, SIZE));
	}

	private static ItemStack depositAmmo(PlayerEntity player, Slot target) {
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack candidate = inventory.getStack(i);
			if (!AmmoItems.isAmmo(candidate)) {
				continue;
			}
			ItemStack moved = candidate.copy();
			target.setStack(moved);
			inventory.setStack(i, ItemStack.EMPTY);
			return moved.copy();
		}
		return ItemStack.EMPTY;
	}

	private static int inventoryX(int index) {
		// The texture paints each 18x18 box at the slot coordinate, while Slot.x/y
		// denotes the inset 16x16 item/highlight area.
		return 9 + (index < 9 ? index : (index - 9) % 9) * 18;
	}

	private static int inventoryY(int index) {
		return index < 9 ? 185 : 123 + ((index - 9) / 9) * 18;
	}

	public static int slotX(int index) {
		double angle = -Math.PI / 2.0D + index * Math.PI * 2.0D / SIZE;
		return Math.round(CENTER_X + (float) Math.cos(angle) * RADIUS) - 8;
	}

	public static int slotY(int index) {
		double angle = -Math.PI / 2.0D + index * Math.PI * 2.0D / SIZE;
		return Math.round(CENTER_Y + (float) Math.sin(angle) * RADIUS) - 8;
	}

	/** Opens through vanilla's menu packet, including the NeoForge bridge. */
	public static net.minecraft.screen.NamedScreenHandlerFactory createFactory() {
		return new SimpleNamedScreenHandlerFactory(
				(syncId, playerInventory, player) ->
						new AmmoStorageScreenHandler(syncId, playerInventory),
				Text.translatable("ui.mechaniciancombat.ammo_storage"));
	}

	@SuppressWarnings("unchecked")
	private static ScreenHandlerType<AmmoStorageScreenHandler> createVanillaType() {
		try {
			Class<?> factoryType = Arrays.stream(
							ScreenHandlerType.class.getDeclaredClasses())
					.filter(Class::isInterface)
					.filter(type -> Arrays.stream(type.getDeclaredMethods())
							.anyMatch(method -> method.getParameterCount() == 2
									&& method.getParameterTypes()[0] == int.class
									&& method.getParameterTypes()[1]
											== PlayerInventory.class))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"ScreenHandlerType.Factory not found"));
			Object factory = Proxy.newProxyInstance(
					AmmoStorageScreenHandler.class.getClassLoader(),
					new Class<?>[]{factoryType},
					(proxy, method, args) -> {
						if (method.getDeclaringClass() == Object.class) {
							return switch (method.getName()) {
								case "toString" ->
										"mechaniciancombat:ammo_storage_factory";
								case "hashCode" -> System.identityHashCode(proxy);
								case "equals" -> proxy == args[0];
								default -> null;
							};
						}
						return new AmmoStorageScreenHandler(
								(int) args[0], (PlayerInventory) args[1]);
					});
			Constructor<ScreenHandlerType> constructor =
					(Constructor<ScreenHandlerType>) ScreenHandlerType.class
							.getDeclaredConstructor(factoryType, FeatureSet.class);
			constructor.setAccessible(true);
			return (ScreenHandlerType<AmmoStorageScreenHandler>)
					constructor.newInstance(factory, FeatureSet.empty());
		} catch (Throwable throwable) {
			throw new ExceptionInInitializerError(throwable);
		}
	}
}
