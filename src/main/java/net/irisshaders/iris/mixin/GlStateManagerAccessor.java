package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface GlStateManagerAccessor {
	@Accessor(value = "BLEND", remap = false)
	static GlStateManager.BlendState getBLEND() {
		throw new UnsupportedOperationException("Not accessed");
	}

	@Accessor(value = "COLOR_MASK", remap = false)
	static GlStateManager.ColorMask getCOLOR_MASK() {
		throw new UnsupportedOperationException("Not accessed");
	}

	@Accessor(value = "DEPTH", remap = false)
	static GlStateManager.DepthState getDEPTH() {
		throw new UnsupportedOperationException("Not accessed");
	}

	@Accessor(value = "activeTexture", remap = false)
	static int getActiveTexture() {
		throw new UnsupportedOperationException("Not accessed");
	}

	@Accessor(value = "TEXTURES", remap = false)
	static GlStateManager.TextureState[] getTEXTURES() {
		throw new UnsupportedOperationException("Not accessed");
	}
}
