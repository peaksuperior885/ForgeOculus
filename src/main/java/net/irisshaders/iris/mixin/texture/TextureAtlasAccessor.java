package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
	@Accessor(value = "texturesByName", remap = false)
	Map<ResourceLocation, TextureAtlasSprite> getTexturesByName();

	@Accessor(value = "mipLevel", remap = false)
	int getMipLevel();

	@Invoker("getWidth")
	int callGetWidth();

	@Invoker("getHeight")
	int callGetHeight();
}