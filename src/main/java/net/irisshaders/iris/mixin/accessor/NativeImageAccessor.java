package net.irisshaders.iris.mixin.accessor;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NativeImage.class)
public interface NativeImageAccessor {
    /**
     * Maps to the private 'pixels' field in NativeImage,
     * which is the long pointer to the buffer.
     */
    @Accessor(value = "pixels", remap = false)
    long getPixels();
}