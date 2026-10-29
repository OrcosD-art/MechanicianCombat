package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.rig.BowgunRigClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持枪时先让 GeckoLib 绘制枪械并采集身体姿势，然后继续原版 PlayerRenderer。
 * <ol>
 *   <li>把 {@code EntityRendererFactory.Context} 记下来 —— GeckoLib 的渲染器构造要用；</li>
 *   <li>渲染玩家时，如果手里是破垒重炮 → GeckoLib 绘制枪械，原版继续绘制身体与装备。</li>
 * </ol>
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererRigMixin {
	@Inject(method = "<init>(Lnet/minecraft/class_5617$class_5618;Z)V", at = @At("TAIL"))
	private void tkgun$rememberContext(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		BowgunRigClient.setContext(ctx);
	}

	@Inject(method = "method_4215(Lnet/minecraft/class_742;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("HEAD"))
	private void tkgun$renderRig(AbstractClientPlayerEntity player, float yaw, float tickDelta,
								 MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		BowgunRigClient.renderIfHolding(player, yaw, tickDelta, matrices, consumers, light);
	}

	/**
	 * The gun rig already contains the authored crouch/focus pose. Letting the
	 * vanilla player model also enable its sneak pose applies two crouch rotations
	 * and body offsets to the same limbs.
	 */
	@Inject(method = "method_4218(Lnet/minecraft/class_742;)V", at = @At("TAIL"))
	private void tkgun$suppressStackedVanillaCrouch(AbstractClientPlayerEntity player,
													CallbackInfo ci) {
		if (!TkGun.holdingGun(player)) {
			return;
		}
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel model) {
			model.sneaking = false;
		}
	}
}
