package com.tk.gun;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

import com.tk.gun.net.GunDemoPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** /tkgun —— 调试/运维用：查状态、强制状态、补弹。 */
public final class TkGunCommand {
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<ServerCommandSource> d) {
		d.register(CommandManager.literal("tkgun").requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.literal("status")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									TkGunServer.PState st = TkGunServer.state(p);
									ctx.getSource().sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
											"tkgun %s: state=%s 剩余=%d 弹夹=%d/%d 第%d发动画=%s 连射=%d 热=%.1f 过热=%b 队列=%d 滑铲=%d 专注=%b",
											p.getName().getString(), st.state,
											Math.max(0, st.stateUntil - p.getWorld().getTime()),
											TkGunServer.activeMag(st), TkGun.magazineCapacity(p, st.deployed), st.shotIndex,
											TkGun.shotAnimation(st.shotIndex,
													TkGun.definition(p).maxShotIndex()), st.combo, st.heat,
											TkGunServer.isOverheated(p), st.shotQueue.size(), st.slideUntil,
											TkGunServer.focused(p))), false);
									return 1;
								})))
				// /tkgun focus <player> —— 进/出**专注（架枪）**状态；技能「破垒重炮·专注」就是调这个。
				// ⚠️ `over` 保留成同一个东西的别名：老 hud jar（技能还叫"强化"那版）喊的是 `tkgun over`，
				//    两边 jar 不同步时至少不会"按了没反应"。
				.then(CommandManager.literal("focus")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									boolean on = TkGunServer.startFocus(p);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"tkgun: 专注 " + (on ? "开启" : "解除/被拒") + " -> "
													+ p.getName().getString()), false);
									return on ? 1 : 0;
								})
								.then(CommandManager.literal("off")
										.executes(ctx -> {
											ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
											TkGunServer.stopFocus(p);
											ctx.getSource().sendFeedback(() -> Text.literal(
													"tkgun: 专注解除 -> " + p.getName().getString()), false);
											return 1;
										}))))
				.then(CommandManager.literal("over")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									boolean on = TkGunServer.startFocus(p);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"tkgun: 专注 " + (on ? "开启" : "解除/被拒")
													+ "（over 是旧别名）-> " + p.getName().getString()), false);
									return on ? 1 : 0;
								})
								.then(CommandManager.literal("off")
										.executes(ctx -> {
											ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
											TkGunServer.stopFocus(p);
											return 1;
										}))))
				.then(CommandManager.literal("state")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word())
										.executes(ctx -> {
											ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
											String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "state");
											TkGun.State s = TkGun.State.valueOf(name.toUpperCase(Locale.ROOT));
											int ticks = switch (s) {
												case FIRE -> TkGun.definition(p).fireTicks();
												case OVERHEAT -> TkGun.OVERHEAT_ANIM_TICKS;
												case INSPECT -> TkGun.INSPECT_TICKS;
												case SLIDE -> TkGun.SLIDE_TICKS;
												case AIM_HOLD -> 600;   // 保持姿势：给足时间，等玩家动
												case ROLL -> com.tk.gun.CombatRollHook.ROLL_DURATION;
												default -> 20;
											};
											TkGunServer.forceState(p, s, ticks);
											ctx.getSource().sendFeedback(() -> Text.literal(
													"tkgun: " + p.getName().getString() + " -> " + s), false);
											return 1;
										}))))
				.then(CommandManager.literal("fire")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									TkGunServer.tryFire(p, true);
									return 1;
								})))
				// /tkgun probe <player> —— 开一枪，并让服务端在**同一 tick**把弹丸位置反解成
				// "枪口相对玩家的偏移"打进日志（RCON 往返太慢，弹丸早飞走了/撞地了，测得准）。
				.then(CommandManager.literal("probe")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									TkGunServer.PROBE_NEXT_SHOT = true;
									TkGunServer.tryFire(p, true);
									return 1;
								})))
				// /tkgun slide <player> —— 直接起一次滑铲（跳过"疾跑+蹲"的输入判定），
				// 用来验收滑铲位移（服务端会打「滑铲结束：实测位移 …」到日志）
				.then(CommandManager.literal("slide")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									TkGunServer.forceSlide(p);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"tkgun: 强制滑铲 -> " + p.getName().getString()), false);
									return 1;
								})))
				// /tkgun burst <player> <n> —— 一次入队 n 发，用来验收"连发排队不覆盖"
				.then(CommandManager.literal("burst")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
										.executes(ctx -> {
											ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
											int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n");
											int added = TkGunServer.queueShots(p, n);
											ctx.getSource().sendFeedback(() -> Text.literal(
													"tkgun: 入队 " + added + " 发 -> " + p.getName().getString()), false);
											return added;
										}))))
				// /tkgun kit <player> —— 把武器+箭塞进快捷栏 0/1（联机验证用）
				.then(CommandManager.literal("kit")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									p.getInventory().setStack(0, new ItemStack(TkGunServer.BOWGUN));
									p.getInventory().setStack(1, new ItemStack(TkGunServer.BOLT, 64));
									p.currentScreenHandler.syncState();
									ctx.getSource().sendFeedback(() -> Text.literal(
											"tkgun: 已给 " + p.getName().getString() + " 发武器 + 64 箭"), false);
									return 1;
								})))
				// /tkgun demo <player> <on|off> —— 远程开关客户端的自动演示（自己按键跑全套动作并截图）
				.then(CommandManager.literal("demo")
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
										.executes(ctx -> {
											ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
											boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
											ServerPlayNetworking.send(p, new GunDemoPayload(on));
											ctx.getSource().sendFeedback(() -> Text.literal(
													"tkgun: 演示 " + (on ? "开启" : "关闭") + " -> " + p.getName().getString()), false);
											return 1;
										})))));
	}

	private TkGunCommand() {
	}
}
