package net.irisshaders.iris.mixin.state_tracking;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class MixinRenderTarget implements Blaze3dRenderTargetExt {

	// -----------------------------------------------------------------------
	// Blaze3dRenderTargetExt — allows IrisRenderingPipeline to cast
	// MainTarget to Blaze3dRenderTargetExt and track buffer versioning.
	// Both mixins previously targeted RenderTarget.class separately which
	// caused a conflict — merged here into one class.
	// -----------------------------------------------------------------------

	@Unique
	private int iris$depthBufferVersion = 0;

	@Unique
	private int iris$colorBufferVersion = 0;

	@Inject(method = "createBuffers(IIZ)V", at = @At("TAIL"), remap = false)
	private void iris$onCreateBuffers(int width, int height, boolean clearError, CallbackInfo ci) {
		this.iris$depthBufferVersion++;
		this.iris$colorBufferVersion++;
	}

	@Inject(method = "resize(IIZ)V", at = @At("TAIL"), remap = false)
	private void iris$onResize(int width, int height, boolean clearError, CallbackInfo ci) {
		this.iris$depthBufferVersion++;
		this.iris$colorBufferVersion++;
	}

	@Override
	public int iris$getDepthBufferVersion() {
		return iris$depthBufferVersion;
	}

	@Override
	public int iris$getColorBufferVersion() {
		return iris$colorBufferVersion;
	}

	// -----------------------------------------------------------------------
	// Framebuffer bind tracking — disables Iris shader rendering when a
	// non-main framebuffer is bound (handles Glowing effect, Lifts mod, etc.)
	// -----------------------------------------------------------------------

	@SuppressWarnings("ConstantValue")
	@Inject(method = "bindWrite(Z)V", at = @At("RETURN"), remap = false)
	private void iris$onBindFramebuffer(boolean bl, CallbackInfo ci) {
		boolean mainBound = this == (Object) Minecraft.getInstance().getMainRenderTarget();
		Iris.getPipelineManager().getPipeline()
				.ifPresent(pipeline -> pipeline.setIsMainBound(mainBound));
	}
}