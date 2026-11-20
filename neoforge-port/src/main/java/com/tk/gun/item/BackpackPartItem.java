package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Installable component for a combat backpack. */
public final class BackpackPartItem extends Item implements GeoItem {
	public enum Kind {
		CORE,
		LAUNCHER,
		AUTO_LAUNCHER,
		SUMMON_ANTENNA,
		JUMP_THRUSTER,
		MISSILE_BOX,
		BATTLE_BANNER,
		GOLEM_CAPSULE
	}

	private final Kind kind;
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public BackpackPartItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public boolean isLauncherKind() {
		return kind == Kind.LAUNCHER || kind == Kind.AUTO_LAUNCHER
				|| kind == Kind.SUMMON_ANTENNA || kind == Kind.JUMP_THRUSTER
				|| kind == Kind.BATTLE_BANNER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// The War Banner's held skill prop is manually driven by its renderer.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
							  TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		if (kind == Kind.LAUNCHER) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.portable_launcher.storage"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.portable_launcher.ammo"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.portable_launcher.trajectory"));
		} else if (kind == Kind.AUTO_LAUNCHER) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.auto_portable_launcher.storage"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.auto_portable_launcher.attack"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.auto_portable_launcher.charge"));
		} else if (kind == Kind.SUMMON_ANTENNA) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.summon_antenna.deploy"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.summon_antenna.mark"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.summon_antenna.cast"));
		} else if (kind == Kind.JUMP_THRUSTER) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.jump_backpack_thruster.activate"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.jump_backpack_thruster.steer"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.jump_backpack_thruster.restriction"));
		} else if (kind == Kind.MISSILE_BOX) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.missile_box.install"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.missile_box.mark"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.missile_box.fire"));
		} else if (kind == Kind.BATTLE_BANNER) {
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.war_banner.install"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.war_banner.skill"));
			tooltip.add(Text.translatable("tooltip.mechaniciancombat.war_banner.aura"));
		} else if (kind == Kind.GOLEM_CAPSULE) {
			tooltip.add(Text.translatable(
					"tooltip.mechaniciancombat.golem_capsule.install"));
			tooltip.add(Text.translatable(
					"tooltip.mechaniciancombat.golem_capsule.summon"));
			tooltip.add(Text.translatable(
					"tooltip.mechaniciancombat.golem_capsule.creative"));
		}
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (kind != Kind.GOLEM_CAPSULE) {
			return ActionResult.PASS;
		}
		PlayerEntity player = context.getPlayer();
		if (player == null || !player.isCreative()
				|| TkGunServer.MOLTEN_GOLEM == null) {
			return ActionResult.PASS;
		}
		World world = context.getWorld();
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		com.tk.gun.entity.MoltenGolemEntity golem =
				TkGunServer.MOLTEN_GOLEM.create(world);
		if (golem == null) {
			return ActionResult.FAIL;
		}
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		golem.refreshPositionAndAngles(
				pos.getX() + 0.5D, pos.getY(),
				pos.getZ() + 0.5D, player.getYaw(), 0.0F);
		golem.setSummoner(player.getUuid());
		if (!world.spawnEntity(golem)) {
			return ActionResult.FAIL;
		}
		return ActionResult.SUCCESS;
	}
}
