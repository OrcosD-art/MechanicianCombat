package net.minecraft.client.render;

import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.util.Identifier;

/** Foreground-only particle layers for MechanicianCombat markers. */
public final class AnchorLockRenderLayers {
	private AnchorLockRenderLayers() {
	}

	public static RenderLayer create(Identifier texture, String name) {
		return create(texture, name, RenderPhase.ALWAYS_DEPTH_TEST);
	}

	public static RenderLayer createDepthTested(Identifier texture,
			String name) {
		return create(texture, name, RenderPhase.LEQUAL_DEPTH_TEST);
	}

	/**
	 * Invisible depth-aware geometry used to write the slash-hit stencil mask.
	 * Color and depth writes are disabled; only stencil writes are expected.
	 */
	public static RenderLayer createStencilMask(String name) {
		RenderLayer.MultiPhaseParameters parameters =
				RenderLayer.MultiPhaseParameters.builder()
						.texture(RenderPhase.NO_TEXTURE)
						.program(new RenderPhase.ShaderProgram(
								GameRenderer::getPositionColorProgram))
						.transparency(RenderPhase.NO_TRANSPARENCY)
						.depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
						.cull(RenderPhase.DISABLE_CULLING)
						.lightmap(RenderPhase.DISABLE_LIGHTMAP)
						.writeMaskState(new RenderPhase.WriteMaskState(
								false, false))
						.build(false);
		return RenderLayer.of(name, VertexFormats.POSITION_COLOR,
				DrawMode.QUADS, 1536, parameters);
	}

	private static RenderLayer create(Identifier texture, String name,
			RenderPhase.DepthTest depthTest) {
		RenderLayer.MultiPhaseParameters parameters =
				RenderLayer.MultiPhaseParameters.builder()
						.texture(new RenderPhase.Texture(
								texture, false, false))
						.program(new RenderPhase.ShaderProgram(
								GameRenderer::getPositionTexColorProgram))
						.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
						.depthTest(depthTest)
						.cull(RenderPhase.DISABLE_CULLING)
						.lightmap(RenderPhase.ENABLE_LIGHTMAP)
						.writeMaskState(RenderPhase.COLOR_MASK)
						.build(false);
		return RenderLayer.of(name,
				VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
				DrawMode.QUADS, 1536, parameters);
	}
}
