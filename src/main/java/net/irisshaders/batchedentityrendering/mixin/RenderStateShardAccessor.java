package net.irisshaders.batchedentityrendering.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.class)
public interface RenderStateShardAccessor {
	@Accessor(value = "NO_TRANSPARENCY", remap = false)
	static RenderStateShard.TransparencyStateShard getNO_TRANSPARENCY() {
		throw new AssertionError();
	}

	@Accessor(value = "GLINT_TRANSPARENCY", remap = false)
	static RenderStateShard.TransparencyStateShard getGLINT_TRANSPARENCY() {
		throw new AssertionError();
	}

	@Accessor(value = "CRUMBLING_TRANSPARENCY", remap = false)
	static RenderStateShard.TransparencyStateShard getCRUMBLING_TRANSPARENCY() {
		throw new AssertionError();
	}
}
