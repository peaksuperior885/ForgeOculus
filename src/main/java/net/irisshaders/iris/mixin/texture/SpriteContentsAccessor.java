package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor(value = "animatedTexture", remap = false)
	SpriteContents.AnimatedTexture getAnimatedTexture();
}
