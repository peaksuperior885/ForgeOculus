package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes GlStateManager.TextureState.binding with remap = false so that
 * non-mixin code (TextureInfoCache) can read the currently-bound texture ID
 * without going through the refmap, which would resolve to the stale SRG
 * name "f_84801_" and crash with NoSuchFieldError on Parchment 1.21.1.
 */
@Mixin(GlStateManager.TextureState.class)
public interface TextureStateAccessor {
    @Accessor(value = "binding", remap = false)
    int getBinding();
}