package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.FrameInfo.class)
public interface SpriteContentsFrameInfoAccessor {
	@Accessor(value = "index", remap = false)
	int getIndex();

	@Accessor(value = "time", remap = false)
	int getTime();
}
