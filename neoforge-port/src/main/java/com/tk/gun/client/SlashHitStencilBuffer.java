package com.tk.gun.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.tk.gun.TkGun;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Lazily attaches an 8-bit stencil renderbuffer to Minecraft's main FBO.
 *
 * <p>The vanilla main FBO has a depth texture but no stencil attachment.
 * Keeping the attachment local to this renderer lets slash hits use an exact
 * two-pass mask without changing framebuffer formats globally.</p>
 */
final class SlashHitStencilBuffer {
	private static final int INVALID = -1;

	private static int attachedFbo = INVALID;
	private static int renderbuffer = INVALID;
	private static int width = INVALID;
	private static int height = INVALID;
	private static int failedFbo = INVALID;
	private static int failedWidth = INVALID;
	private static int failedHeight = INVALID;
	private static boolean active;
	private static int previousFbo;

	private SlashHitStencilBuffer() {
	}

	static boolean begin(Framebuffer framebuffer) {
		if (framebuffer == null || framebuffer.fbo < 0) {
			return false;
		}
		previousFbo = GlStateManager.getBoundFramebuffer();
		if (!ensureAttached(framebuffer)) {
			restoreFramebuffer(previousFbo);
			return false;
		}
		if (GlStateManager.getBoundFramebuffer() != framebuffer.fbo) {
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
					framebuffer.fbo);
		}
		GL11.glEnable(GL11.GL_STENCIL_TEST);
		GlStateManager._stencilMask(0xFF);
		GL11.glClearStencil(0);
		GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
		active = true;
		return true;
	}

	static void end() {
		if (!active) {
			return;
		}
		GL11.glDisable(GL11.GL_STENCIL_TEST);
		GlStateManager._stencilMask(0xFF);
		GlStateManager._stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
		GlStateManager._stencilOp(GL11.GL_KEEP, GL11.GL_KEEP,
				GL11.GL_KEEP);
		active = false;
		restoreFramebuffer(previousFbo);
	}

	private static boolean ensureAttached(Framebuffer framebuffer) {
		int fbo = framebuffer.fbo;
		int fboWidth = framebuffer.textureWidth;
		int fboHeight = framebuffer.textureHeight;
		if (attachedFbo == fbo && renderbuffer != INVALID
				&& width == fboWidth && height == fboHeight) {
			return true;
		}
		if (failedFbo == fbo && failedWidth == fboWidth
				&& failedHeight == fboHeight) {
			return false;
		}

		releaseAttachment();
		int previous = GlStateManager.getBoundFramebuffer();
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		int stencil = GlStateManager.glGenRenderbuffers();
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencil);
		GlStateManager._glRenderbufferStorage(GL30.GL_RENDERBUFFER,
				GL30.GL_STENCIL_INDEX8, fboWidth, fboHeight);
		GlStateManager._glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
				GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, stencil);
		int status = GlStateManager.glCheckFramebufferStatus(
				GL30.GL_FRAMEBUFFER);
		if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
			GlStateManager._glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
					GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, 0);
			GlStateManager._glDeleteRenderbuffers(stencil);
			failedFbo = fbo;
			failedWidth = fboWidth;
			failedHeight = fboHeight;
			if (previous != fbo) {
				GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
						previous);
			}
			TkGun.LOGGER.warn(
					"[mechaniciancombat] slash-hit stencil unavailable "
							+ "(fbo={}, {}x{}, status=0x{}); "
							+ "falling back to depth-tested particles",
					fbo, fboWidth, fboHeight,
					Integer.toHexString(status));
			return false;
		}
		attachedFbo = fbo;
		renderbuffer = stencil;
		width = fboWidth;
		height = fboHeight;
		failedFbo = INVALID;
		TkGun.LOGGER.info(
				"[mechaniciancombat] slash-hit target stencil active "
						+ "(fbo={}, {}x{})",
				fbo, fboWidth, fboHeight);
		if (previous != fbo) {
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
					previous);
		}
		return true;
	}

	private static void releaseAttachment() {
		if (renderbuffer == INVALID) {
			return;
		}
		int previous = GlStateManager.getBoundFramebuffer();
		if (attachedFbo != INVALID) {
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
					attachedFbo);
			GlStateManager._glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
					GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, 0);
		}
		GlStateManager._glDeleteRenderbuffers(renderbuffer);
		renderbuffer = INVALID;
		attachedFbo = INVALID;
		width = INVALID;
		height = INVALID;
		if (previous != attachedFbo) {
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
					previous);
		}
	}

	private static void restoreFramebuffer(int fbo) {
		if (GlStateManager.getBoundFramebuffer() != fbo) {
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		}
	}
}
