package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteContents.AnimatedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.Ticker.class)
public interface SpriteContentsTickerAccessor {
	@Accessor(value = "frame", remap = false)
	int getFrame();

	@Accessor(value = "frame", remap = false)
	void setFrame(int frame);

	@Accessor(value = "subFrame", remap = false)
	int getSubFrame();

	@Accessor(value = "subFrame", remap = false)
	void setSubFrame(int subFrame);

	@Accessor(value = "animationInfo", remap = false)
	AnimatedTexture getAnimationInfo();
}
