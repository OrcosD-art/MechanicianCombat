package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.client.rig.BowgunRig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.loading.math.MolangQueries;

import java.util.function.ToDoubleFunction;

/**
 * **让作者写的那几句"跟随视角 Molang"在 tkgun 里真的生效**（2026-10-16 用户："那不能让我们这个里头能用 Molang 吗"）。
 *
 * <h2>为什么需要它</h2>
 * GeckoLib 4.8 的取视角 query 是**按类型强转**实现的（`javap` 逐条读过 `MolangQueries`）：
 * <pre>
 *   query.head_x_rotation = ((LivingEntity) actor.animatable()).getPitch(tick)
 *   query.head_y_rotation = ((LivingEntity) actor.animatable()).getYaw(tick)        // = headYaw 绝对值
 *   query.body_y_rotation = lerp(tick, prevBodyYaw, bodyYaw)
 *   query.body_x_rotation = actor.animatable() instanceof LivingEntity ? 0 : ((Entity) …).getPitch(tick)
 * </pre>
 * 而 tkgun 的 rig 是 **`GeoReplacedEntity`（`BowgunRig.INSTANCE`，不是实体）** → 每条都命中
 * `Attempted to use Molang variable for incompatible animatable type (…)` 然后**按 0 处理**
 * （v3 素材那句 `-query.body_x_rotation / 3` 就是这么静默失效的：枪一直没跟着俯仰抬）。
 *
 * <h2>怎么修（不碰 GeckoLib 的代码、不用 Mixin）</h2>
 * `MolangQueries.setActorVariable(String, ToDoubleFunction&lt;Actor&gt;)` 是 **public** 的，而且
 * GeckoLib 只在**类初始化时注册一遍**（`updateActor` 每帧只换 `ACTOR` 这个持有者，不重新注册）
 * —— 所以这里把这 4 个 query **用同一套语义重新实现一遍**：
 * <ul>
 *   <li>**我们的 rig**：从"正在画的那个玩家"（动画状态里的 ENTITY ticket，兜底用
 *       {@link BowgunRig#CURRENT_PLAYER}）取值，并且**按 Bedrock 的口径**给（因为作者的模型是照
 *       Bedrock 写的）：抬头为正 → `-pitch`；`head_y_rotation` 是"头相对身体"且符号与 MC 的
 *       `(headYaw - bodyYaw)` 相反 → `-(headYaw - bodyYaw)`；`body_y_rotation` 仍是绝对 `bodyYaw`。</li>
 *   <li>**其它 animatable**（tkm 的怪、别的 mod 的 GeckoLib 模型）：**原样**走 GeckoLib 那两个公式，
 *       行为一个字节都不变（这点很重要：这个变量表是全局共享的）。</li>
 * </ul>
 * 好处：动画文件里那句 Molang 是**原封不动**的（逐关键帧的时机、插值都还是作者的），
 * 以后作者再写别的视角/状态 Molang 也能直接用。
 */
public final class TkGunMolang {
	private static boolean installed;

	/** 客户端启动时装一次（`TkGunClient#onInitializeClient`）。 */
	public static void install() {
		if (installed) {
			return;
		}
		installed = true;
		set(MolangQueries.HEAD_X_ROTATION, TkGunMolang::headXRotation);
		set(MolangQueries.BODY_X_ROTATION, TkGunMolang::bodyXRotation);
		set(MolangQueries.HEAD_Y_ROTATION, TkGunMolang::headYRotation);
		set(MolangQueries.BODY_Y_ROTATION, TkGunMolang::bodyYRotation);
		TkGun.LOGGER.info("[tkgun] 视角 Molang 已接管：query.head_x/head_y/body_x/body_y_rotation 对 tkgun rig "
				+ "会按「被替换的那个玩家」求值（原先对 GeoReplacedEntity 一律返回 0 并刷错误日志）");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void set(String name, ToDoubleFunction<MolangQueries.Actor<?>> fn) {
		MolangQueries.setActorVariable(name, (ToDoubleFunction) probe(name, fn));
	}

	/**
	 * 包一层诊断（`-Dtkgun.demo` / `-Dtkgun.verbose` 才打）：每 40 tick 打一行"这个 query 被谁问、答了几度"。
	 * 用来确认"值到底有没有被 Molang 取走" —— 只在真正求值时才会打印（不打印 = 动画里那句没在被读）。
	 */
	private static ToDoubleFunction<MolangQueries.Actor<?>> probe(String name, ToDoubleFunction<MolangQueries.Actor<?>> fn) {
		return actor -> {
			double v = fn.applyAsDouble(actor);
			if (VERBOSE) {
				long now = System.currentTimeMillis();
				Long last = PROBE_TICK.get(name);
				if (last == null || now - last >= 1000L) {   // 每个 query 最多每秒一行
					PROBE_TICK.put(name, now);
					TkGun.LOGGER.info("[tkgun] Molang query 求值 {} = {}（animatable={}）", name,
							Math.round(v * 100.0) / 100.0, actor.animatable().getClass().getSimpleName());
				}
			}
			return v;
		};
	}

	private static final boolean VERBOSE = System.getProperty("tkgun.demo") != null
			|| System.getProperty("tkgun.verbose") != null;
	private static final java.util.Map<String, Long> PROBE_TICK = new java.util.HashMap<>();

	/** 这个 animatable 是不是"被 tkgun rig 替换的玩家"（是就用玩家的值，不是就走 GeckoLib 原逻辑）。 */
	private static LivingEntity rigPlayer(MolangQueries.Actor<?> actor) {
		if (!(actor.animatable() instanceof BowgunRig)) {
			return null;
		}
		AnimationState<?> state = actor.animationState();
		Object ticket = state.getExtraData().get(DataTickets.ENTITY);
		if (ticket instanceof LivingEntity living) {
			return living;
		}
		return BowgunRig.CURRENT_PLAYER;   // 渲染器每帧设的"正在画的那个玩家"，和动画倍速用的是同一个
	}

	private static double headXRotation(MolangQueries.Actor<?> actor) {
		LivingEntity player = rigPlayer(actor);
		if (player != null) {
			return -player.getPitch(partialTick(actor));   // Bedrock：抬头为正（MC 的 pitch 是低头为正）
		}
		return actor.animatable() instanceof LivingEntity living ? living.getPitch(partialTick(actor)) : 0.0D;
	}

	private static double bodyXRotation(MolangQueries.Actor<?> actor) {
		LivingEntity player = rigPlayer(actor);
		if (player != null) {
			return -player.getPitch(partialTick(actor));   // 玩家身上"身体的俯仰"和头是同一个（MC 只有一份 pitch）
		}
		return actor.animatable() instanceof LivingEntity
				? 0.0D                                        // ← GeckoLib 原样：活体恒 0（不是笔误）
				: actor.animatable() instanceof Entity entity ? entity.getPitch(partialTick(actor)) : 0.0D;
	}

	private static double headYRotation(MolangQueries.Actor<?> actor) {
		LivingEntity player = rigPlayer(actor);
		if (player != null) {
			// ⚠️ 本地玩家**瞄准时**必须返回 0：模型朝向是被 GunCamera 硬拧到"枪口 → 弹着点"的，
			//    而上身再按"原版 headYaw/bodyYaw"拧一点，整把枪（chest 的子骨）就跟着偏，
			//    瞄准补偿再把**整个模型**转回去 → 结果就是用户 2026-10-16 报的
			//    「模型没有面对着弹点」（枪对了、人不对）。脑袋跟着鼠标转的表现留给别人看你的时候。
			if (isLocalAiming(player)) {
				return 0.0D;
			}
			// Bedrock 的 head_y_rotation = 头相对身体，符号与 MC 的差相反（Bedrock 玩家模型里 head 的 Y 旋转写
			// `-query.head_y_rotation`）→ 作者的 `-query.head_y_rotation / 3` 落到 MC 骨头上就是 +(头身差)/3。
			return -MathHelper.wrapDegrees(player.getHeadYaw() - player.getBodyYaw());
		}
		return actor.animatable() instanceof LivingEntity living ? living.getYaw(partialTick(actor)) : 0.0D;
	}

	/** 本地玩家现在是不是在瞄准/架枪（右键举着枪，或者在专注里）。 */
	private static boolean isLocalAiming(LivingEntity player) {
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.player != player) {
			return false;
		}
		if (client.options.useKey.isPressed()) {
			return com.tk.gun.client.GunCamera.aimAllowed(client);
		}
		return com.tk.gun.client.GunClientState.focused(player.getId());
	}

	private static double bodyYRotation(MolangQueries.Actor<?> actor) {
		LivingEntity player = rigPlayer(actor);
		if (player != null) {
			return MathHelper.lerp(partialTick(actor), player.prevBodyYaw, player.bodyYaw);
		}
		return actor.animatable() instanceof LivingEntity living
				? MathHelper.lerp(partialTick(actor), living.prevBodyYaw, living.bodyYaw)
				: 0.0D;
	}

	private static float partialTick(MolangQueries.Actor<?> actor) {
		return actor.animationState().getPartialTick();
	}

	private TkGunMolang() {
	}
}
