package com.tk.gun.mixin;

import com.tk.gun.client.MeleeImpactClientState;
import com.tk.gun.client.MeleeRenderStateSnapshot;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class MeleeEntityFreezeRenderMixin {
	@Unique
	private MeleeRenderStateSnapshot tkgun$savedRenderState;

	@Inject(method = "method_22977(Lnet/minecraft/class_1297;DDDFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V",
			at = @At("HEAD"))
	private void tkgun$freezeTargetForRender(Entity entity, double cameraX,
			double cameraY, double cameraZ, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		MeleeImpactClientState.FreezeState state =
				MeleeImpactClientState.frozenTarget(entity.getId());
		if (state == null) {
			tkgun$savedRenderState = null;
			return;
		}
		tkgun$savedRenderState = MeleeRenderStateSnapshot.capture(entity);
		double shake = state.getShake(tickDelta);
		double frozenX = state.x() + state.shakeX() * shake;
		double frozenZ = state.z() + state.shakeZ() * shake;
		entity.setPos(frozenX, state.y(), frozenZ);
		entity.prevX = frozenX;
		entity.prevY = state.y();
		entity.prevZ = frozenZ;
		entity.setYaw(state.yaw());
		entity.setPitch(state.pitch());
		entity.prevYaw = state.yaw();
		entity.prevPitch = state.pitch();
		if (entity instanceof LivingEntity living) {
			living.setHeadYaw(state.headYaw());
			living.setBodyYaw(state.bodyYaw());
			living.prevHeadYaw = state.headYaw();
			living.prevBodyYaw = state.bodyYaw();
		}
	}

	@Inject(method = "method_22977(Lnet/minecraft/class_1297;DDDFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V",
			at = @At("TAIL"))
	private void tkgun$restoreTargetAfterRender(Entity entity, double cameraX,
			double cameraY, double cameraZ, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		MeleeRenderStateSnapshot saved = tkgun$savedRenderState;
		tkgun$savedRenderState = null;
		if (saved == null) {
			return;
		}
		entity.setPos(saved.x(), saved.y(), saved.z());
		entity.prevX = saved.prevX();
		entity.prevY = saved.prevY();
		entity.prevZ = saved.prevZ();
		entity.setYaw(saved.yaw());
		entity.setPitch(saved.pitch());
		entity.prevYaw = saved.prevYaw();
		entity.prevPitch = saved.prevPitch();
		if (entity instanceof LivingEntity living) {
			living.setHeadYaw(saved.headYaw());
			living.setBodyYaw(saved.bodyYaw());
			living.prevHeadYaw = saved.prevHeadYaw();
			living.prevBodyYaw = saved.prevBodyYaw();
		}
	}
}
