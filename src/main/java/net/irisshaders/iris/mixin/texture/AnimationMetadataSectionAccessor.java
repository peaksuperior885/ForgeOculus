package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimationMetadataSection.class)
public interface AnimationMetadataSectionAccessor {
	@Accessor(value = "frameWidth", remap = false)
	int getFrameWidth();

	@Mutable
	@Accessor(value = "frameWidth", remap = false)
	void setFrameWidth(int frameWidth);

	@Accessor(value = "frameHeight", remap = false)
	int getFrameHeight();

	@Mutable
	@Accessor(value = "frameHeight", remap = false)
	void setFrameHeight(int frameHeight);
}
