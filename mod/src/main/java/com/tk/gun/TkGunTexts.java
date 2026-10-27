package com.tk.gun;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * **玩家可见的状态提示文案**（2026-10-17 用户："清除聊天栏中的专注没生效文本，以及当前所有状态的
 * 提示文本，给出一份列表，我将查看后并提供新的文本以替换"）。
 *
 * <p>2026-10-17 用户给了新文案，**只填了这几条**（其余保持空串 = 不显示）：
 * <ul>
 *   <li>{@link #FOCUS_ENTER}、{@link #FOCUS_OVERHEATED}、{@link #OVERHEAT_START}、
 *       {@link #OVERHEAT_NO_FIRE}、{@link #OVERHEAT_NO_RELOAD}（都是"正在冷却枪管！"那组）、
 *       {@link #OVERHEAT_WARN}、{@link #NEED_AIM}、{@link #MAG_EMPTY}、{@link #DODGE_LOCKED}；</li>
 *   <li>用户原话："除提供文本外全部删除" → `FOCUS_EXIT` / `FOCUS_NO_GUN` / `RELOADING` /
 *       `CANT_FIRE` / `HUD_RELOADING` / `HUD_FOCUS` 继续留空、不显示。</li>
 * </ul>
 *
 * <p>约定：调用点统一走 {@link #say}（空串直接跳过）；每个常量上面那行注释是**被清掉的原文**，
 * 方便以后对照；`%d` 是运行期要填的数（闪避冷却秒数）。
 *
 * <p>⚠️ 原来客户端那条"§e[tkgun] 专注没生效 …"（服务端旧 jar 诊断）已经**整段删掉**，
 * 不在这里留占位 —— 用户 2026-10-17 点名要清掉它。
 */
public final class TkGunTexts {
	/** 专注提示里的按键由客户端 KeyBinding 动态解析，不再写死 Z / Alt。 */
	public static final String FOCUS_ENTER = "全自动扳机   ";
	/** 原文：§7专注解除 */
	public static final String FOCUS_EXIT = "";
	/** 原文：§7「破垒重炮·专注」需要§f手持破垒重炮§7才能用 */
	public static final String FOCUS_NO_GUN = "";
	/** 原文：§6弩炮过热 §7— 正在散热，进不了专注 */
	public static final String FOCUS_OVERHEATED = "正在冷却枪管！";
	/** 原文：§c过热！ §7枪管在排汽，等它凉下来 */
	public static final String OVERHEAT_START = "正在冷却枪管！";
	/** 原文：§e枪管发烫 §7— 快到过热了 */
	public static final String OVERHEAT_WARN = "枪管高温！";
	/** 原文：§6弩炮过热 §7— 正在散热，先别扣扳机 */
	public static final String OVERHEAT_NO_FIRE = "正在冷却枪管！";
	/** 原文：§6弩炮过热 §7— 等它凉下来再装填 */
	public static final String OVERHEAT_NO_RELOAD = "正在冷却枪管！";
	/** 原文：§7装填中… */
	public static final String RELOADING = "";
	/** 原文：§7先按住右键举枪瞄准，再扣扳机 */
	public static final String NEED_AIM = "";
	/** 原文：§7这个动作里不能开枪 */
	public static final String CANT_FIRE = "";
	/** 原文：§7弹夹空了 §f— 按 §bF §f装填 */
	public static final String MAG_EMPTY = "无弹药！";
	/**
	 * 原文：§7闪避键还在冷却（连锁闪避后 %d 秒）；上一版文本：`闪避冷却（ %d 秒）`。
	 * ⚠️ 2026-10-17 用户："游戏内闪避冷却文本存在问题不符合当前状态机，进行清除" → **留空（不显示）**。
	 */
	public static final String DODGE_LOCKED = "";
	/** 原文（右下角 HUD 里换弹时那个大字）：装填 */
	public static final String HUD_RELOADING = "";
	/** 原文（右下角 HUD 弹夹格上方那行）：专注 */
	public static final String HUD_FOCUS = "";

	/** 发一条玩家可见提示；文案为空串就什么都不发（= 这一版先不显示）。 */
	public static void say(ServerPlayerEntity player, String text, boolean actionBar) {
		if (text == null || text.isEmpty()) {
			return;
		}
		player.sendMessage(Text.literal(text), actionBar);
	}

	/** 专注提示：起身键和翻滚键使用玩家当前的自定义绑定。 */
	public static Text focusEnterText() {
		return Text.literal(FOCUS_ENTER)
				.append(Text.keybind("key.sneak"))
				.append(Text.literal("/技能键起身    "))
				.append(Text.keybind(CombatRollHook.isInstalled()
						? "keybinds.combat_roll.roll" : "key.tkgun.roll"))
				.append(Text.literal("/闪避键翻滚"));
	}

	/** 瞄准提示：右键使用原版 use 键的当前绑定。 */
	public static Text needAimText() {
		return Text.keybind("key.use").append(Text.literal("瞄准"));
	}

	public static void say(ServerPlayerEntity player, Text text, boolean actionBar) {
		if (text == null || text.getString().isEmpty()) {
			return;
		}
		player.sendMessage(text, actionBar);
	}

	private TkGunTexts() {
	}
}
