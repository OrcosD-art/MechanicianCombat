package com.tk.gun.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes LivingEntityRenderer#addFeature to the NeoForge renderer setup. */
@Mixin(LivingEntityRenderer.class)
public interface PlayerEntityRendererLayerAccessor {
	@Invoker("method_4046(Lnet/minecraft/class_3887;)Z")
	boolean mechaniciancombat$addFeature(
			FeatureRenderer<AbstractClientPlayerEntity,
					PlayerEntityModel<AbstractClientPlayerEntity>> layer);
}
