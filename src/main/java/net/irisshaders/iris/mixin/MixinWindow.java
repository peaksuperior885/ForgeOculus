package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.irisshaders.iris.Iris;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Window.class, priority = 1010)
public class MixinWindow {
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"), remap = false)
	private void iris$enableDebugContext(WindowEventHandler arg, ScreenManager arg2, DisplayData arg3, String string, String string2) {
		GLFW.glfwDefaultWindowHints();
		// Added a null check here just in case config isn't ready yet
		if (Iris.getIrisConfig() != null && Iris.getIrisConfig().areDebugOptionsEnabled()) {
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
			Iris.logger.info("OpenGL debug context activated.");
		}
	}

	/**
	 * This is the fix for the Blue Void.
	 * It tells Iris/NeOculus to update the viewport and buffers when the window size changes.
	 */
	@Inject(method = "onResize", at = @At("RETURN"))
	private void iris$onManualResize(long window, int width, int height, CallbackInfo ci) {
		// This is the cleanest way. It bypasses the need for casting
		// and works regardless of what the pipeline method is named.
		if (net.irisshaders.iris.Iris.getPipelineManager().getPipeline().isPresent()) {
			net.irisshaders.iris.Iris.getPipelineManager().destroyPipeline();
		}
	}
}