package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	// We set remap = true (default) to ensure Forge finds the obfuscated field
	@Accessor("blurEffect")
	PostChain getBlurEffect();

	@Accessor("renderHand")
	boolean getRenderHand();

	@Accessor("panoramicMode")
	boolean getPanoramicMode();

	@Invoker("bobView")
	void invokeBobView(PoseStack poseStack, float tickDelta);

	@Invoker("bobHurt")
	void invokeBobHurt(PoseStack poseStack, float tickDelta);

	@Invoker("getFov")
	double invokeGetFov(Camera camera, float tickDelta, boolean b);

	@Invoker("shouldRenderBlockOutline")
	boolean shouldRenderBlockOutlineA();
}