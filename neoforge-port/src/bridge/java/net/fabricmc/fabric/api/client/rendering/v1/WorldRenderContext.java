package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

public record WorldRenderContext(ClientWorld world, MatrixStack matrixStack,
		VertexConsumerProvider consumers, Camera camera,
		RenderTickCounter tickCounter) {
}
