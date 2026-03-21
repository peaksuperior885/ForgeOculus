package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Window.class, priority = 1010)
public class MixinWindow {
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"), remap = false)
	private void iris$enableDebugContext(WindowEventHandler arg, ScreenManager arg2, DisplayData arg3, String string, String string2) {
		GLFW.glfwDefaultWindowHints();

		// -----------------------------------------------------------------------
		// FIX: getIrisConfig() returns null during Window.<init> because this
		// mixin fires before FMLClientSetupEvent (where irisConfig is created).
		// Guard with null check — defaulting to no debug context is safe here.
		// -----------------------------------------------------------------------
		IrisConfig config = Iris.getIrisConfig();
		if (config != null && config.areDebugOptionsEnabled()) {
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
			Iris.logger.info("OpenGL debug context activated.");
		}
	}
}