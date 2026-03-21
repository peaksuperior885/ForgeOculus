package net.irisshaders.batchedentityrendering.mixin;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Accessor(value = "sortOnUpload", remap = false)
	boolean shouldSortOnUpload();
}
