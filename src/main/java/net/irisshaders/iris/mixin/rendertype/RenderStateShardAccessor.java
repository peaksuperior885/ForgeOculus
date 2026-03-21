package net.irisshaders.iris.mixin.rendertype;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.class)
public interface RenderStateShardAccessor {
	@Accessor(value = "TRANSLUCENT_TRANSPARENCY", remap = false)
	static RenderStateShard.TransparencyStateShard getTranslucentTransparency() {
		throw new AssertionError();
	}

	@Accessor(value = "name", remap = false)
	String getName();
}
