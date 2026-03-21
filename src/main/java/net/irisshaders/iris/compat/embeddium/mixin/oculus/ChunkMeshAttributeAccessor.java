package net.irisshaders.iris.compat.embeddium.mixin.oculus;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChunkMeshAttribute.class, remap = false)
public interface ChunkMeshAttributeAccessor {
	@Invoker(value = "<init>")
	static ChunkMeshAttribute createChunkMeshAttribute(String name, int ordinal) {
		throw new AssertionError();
	}
}
