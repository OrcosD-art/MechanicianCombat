package com.tk.gun.mixin;

import com.tk.gun.client.FallbackShoulderCamera;
import com.tk.gun.client.CameraPerspectiveTransition;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 2000)
public abstract class FallbackShoulderCameraMixin {
	@Shadow
	protected abstract void method_19327(double x, double y, double z);

	@Shadow
	protected abstract void method_19325(float yaw, float pitch);

	@Inject(method = "method_19321(Lnet/minecraft/class_1922;Lnet/minecraft/class_1297;ZZF)V",
			at = @At("TAIL"))
	private void mechaniciancombat$applyFallbackShoulder(BlockView view, Entity focusedEntity,
											  boolean thirdPerson, boolean inverseView,
											  float tickDelta, CallbackInfo ci) {
		if (thirdPerson && !inverseView) {
			FallbackShoulderCamera.Pose pose =
					FallbackShoulderCamera.computePose(view, focusedEntity, tickDelta);
			if (pose != null) {
				this.method_19327(pose.position().x, pose.position().y,
						pose.position().z);
				this.method_19325(pose.yaw(), pose.pitch());
			}
		}
		Camera camera = (Camera) (Object) this;
		CameraPerspectiveTransition.Pose transition =
				CameraPerspectiveTransition.apply(camera.getPos(),
						camera.getYaw(), camera.getPitch());
		if (transition != null) {
			this.method_19327(transition.position().x,
					transition.position().y, transition.position().z);
			this.method_19325(transition.yaw(), transition.pitch());
		}
	}
}
