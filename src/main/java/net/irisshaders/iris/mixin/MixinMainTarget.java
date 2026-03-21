package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.pipeline.MainTarget;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements Blaze3dRenderTargetExt on MainTarget.
 *
 * IMPORTANT: MainTarget has NO createBuffers() or resize() methods —
 * those don't exist on this class. Injecting into them caused
 * defaultRequire:1 to kill the entire mixin, meaning the interface
 * was never applied and the cast in IrisRenderingPipeline always failed.
 *
 * MainTarget uses createFrameBuffer(int, int) internally which IS
 * declared on this class — we inject there instead.
 */
@Mixin(value = MainTarget.class, priority = 900)
public abstract class MixinMainTarget implements Blaze3dRenderTargetExt {

    @Unique
    private int iris$depthBufferVersion = 0;

    @Unique
    private int iris$colorBufferVersion = 0;

    @Inject(method = "createFrameBuffer", at = @At("TAIL"), remap = false)
    private void iris$onCreateFrameBuffer(int width, int height, CallbackInfo ci) {
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
}