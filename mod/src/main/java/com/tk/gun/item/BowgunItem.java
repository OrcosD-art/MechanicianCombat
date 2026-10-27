package com.tk.gun.item;

import com.tk.gun.api.GunDefinition;
import com.tk.gun.api.GunRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.UseAction;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * 破垒重炮物品。右键负责举枪瞄准，左键负责射击；专注模式通过 Z 键切换。
 * useAction 使用 NONE，所有武器姿势由 GeckoLib rig 播放。
 */
public class BowgunItem extends Item {
	private final Identifier gunId;

	public BowgunItem(Identifier gunId, Settings settings) {
		super(settings);
		this.gunId = gunId;
	}

	public Identifier gunId() {
		return gunId;
	}

	public GunDefinition definition() {
		return GunRegistry.get(gunId);
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(definition().nameTranslationKey());
	}

	@Override
	public boolean onStackClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player) {
		// 从副手取出枪械必须保留；只有把鼠标中的物品放进副手时才拒绝。
		if (isOffhandSlot(slot, player) && !player.currentScreenHandler.getCursorStack().isEmpty()) {
			return true;
		}
		return super.onStackClicked(stack, slot, clickType, player);
	}

	@Override
	public boolean onClicked(ItemStack stack, ItemStack cursorStack, Slot slot, ClickType clickType,
							 PlayerEntity player, net.minecraft.inventory.StackReference cursorStackReference) {
		// 鼠标持有枪械时，点击空的或已有物品的副手槽都不会执行交换。
		return isOffhandSlot(slot, player)
				|| super.onClicked(stack, cursorStack, slot, clickType, player, cursorStackReference);
	}

	private static boolean isOffhandSlot(Slot slot, PlayerEntity player) {
		return slot != null && player != null
				&& slot.inventory == player.getInventory()
				&& slot.getIndex() == PlayerInventory.OFF_HAND_SLOT;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		String description = Text.translatable(definition().descriptionTranslationKey()).getString();
		for (String part : description.split("\\r?\\n|；|;")) {
			String line = part.trim();
			if (!line.isEmpty()) {
				tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
			}
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		// **不可右键瞄准的状态 → 直接屏蔽右键输入**（用户 2026-10-17）：返回 `fail` 就不会
		// `setCurrentHand`，玩家不会进入"使用物品"状态（没有举枪姿势、没有减速、没有 FOV）。
		// 客户端看镜像状态；服务端看权威状态（两边同一条规则：翻滚/滑铲 + 结束后 1 秒）。
		if (world.isClient) {
			if (user == net.minecraft.client.MinecraftClient.getInstance().player
					&& !com.tk.gun.client.GunCamera.aimAllowed(
							net.minecraft.client.MinecraftClient.getInstance())) {
				return TypedActionResult.fail(stack);
			}
		} else if (user instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			if (com.tk.gun.TkGunServer.aimInputLocked(sp)) {
				return TypedActionResult.fail(stack);
			}
		}
		// 右键建立举枪使用状态；专注由 Z 单独切换，两者不通过同一次右键输入互相改写。
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		// 右键举枪是按住状态，不使用原版蓄力时长。
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		// 松开右键不直接切换专注或开火；服务端状态由独立输入与状态机维护。
	}
}
