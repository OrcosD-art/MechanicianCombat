package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 只在「手持破垒重炮」时额外渲染 GeckoLib 枪械 rig；玩家身体仍交给原版 PlayerRenderer。
 *
 * <p>渲染器实例要 {@code EntityRendererFactory.Context}，从原版 {@code PlayerEntityRenderer}
 * 的构造里拿到（见 {@code PlayerEntityRendererRigMixin}），之后复用同一个实例。
 */
public final class BowgunRigClient {
	private static EntityRendererFactory.Context context;
	private static BowgunRigRenderer renderer;

	public static void setContext(EntityRendererFactory.Context ctx) {
		context = ctx;
	}

	/** rig 渲染器实例（第一次拿到枪时建）—— 自检/诊断要用。 */
	public static BowgunRigRenderer getRenderer() {
		return renderer;
	}

	/**
	 * 让 GeckoLib 评估本帧枪械动画并绘制枪械与特效。玩家身体姿势由
	 * {@link BowgunPlayerAnimatorPose} 传给原版玩家模型。
	 *
	 * @return false = 继续原版 PlayerRenderer，绘制身体、皮肤和装备层
	 */
	public static boolean renderIfHolding(AbstractClientPlayerEntity player, float yaw, float tickDelta,
										  MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		if (!TkGun.holdingGun(player) || context == null) {
			return false;
		}
		if (renderer == null) {
			renderer = new BowgunRigRenderer(context);
			TkGun.LOGGER.info("[tkgun] rig 渲染器就绪（GeckoLib: geo=tkgun:bowgun_rig 动画=animations/entity/bowgun_rig.animation.json）");
		}
		// 2026-10-17 用户："撤销关于水平角度的修正" —— 渲染矩阵补角 / 渲染前 setYaw 那两套都删掉。
		// 现在身体方向只由两处决定：① `GunCamera#tickEnd` 那个"追枪口→准星点"的 tick 环（原样保留）；
		// ② **每次开火时**把身体直接转到准星（相机）方向（见 `TkGunClient#sendFirePacket`）。
		BowgunRig.CURRENT_PLAYER = player;   // 动画倍速（部署快/慢）要知道在画谁
		// ⚠️ 必须用 **MC 的类型** 去调这个渲染方法（不能直接 `renderer.render(...)`）。
		// GeckoLib 的 GeoReplacedEntityRenderer 是"覆写 MC 的 EntityRenderer.render"，
		// 而我们的 jar 没有 refmap：调用点如果落在"Mod 类"身上，重映射时查不到 MC 的方法名，
		// 名字就原样留着 → 运行期 NoSuchMethodError:
		//   'void com.tk.gun.client.rig.BowgunRigRenderer.render(class_1297, FF, class_4587, class_4597, I)'
		// （2026-10-12 实崩过：Iris 阴影视图里每次画玩家都抛，直接崩客户端）。
		// 走 EntityRenderer 这个 MC 类型，tiny-remapper 才能把 render → method_3936 映射对，
		// 虚调用照样会派发到 GeckoLib 的覆写上。
		EntityRenderer<AbstractClientPlayerEntity> asVanillaRenderer = renderer;
		asVanillaRenderer.render(player, yaw, tickDelta, matrices, consumers, light);
		return false;
	}

	private BowgunRigClient() {
	}
}
