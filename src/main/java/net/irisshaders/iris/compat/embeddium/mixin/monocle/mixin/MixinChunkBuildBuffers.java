package net.irisshaders.iris.compat.embeddium.mixin.monocle.mixin;

import net.irisshaders.iris.compat.embeddium.impl.BlockContextHolder;
import net.irisshaders.iris.compat.embeddium.impl.VertexEncoderInterface;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.BakedChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkBuildBuffers.class, remap = false)
public abstract class MixinChunkBuildBuffers implements BlockSensitiveBufferBuilder {

	@Shadow(remap = false) @Final
	private Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders;

	@Unique
	private final BlockContextHolder contextHolder = new BlockContextHolder();

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void setupContextHolder(ChunkVertexType vertexType, CallbackInfo ci) {
		// Step 1: Get all the builders from the map we shadowed
		for (BakedChunkModelBuilder builder : this.builders.values()) {
			// Step 2: For each builder, check every possible facing
			// This covers all 6 sides + UNASSIGNED (where translucency usually lives)
			for (ModelQuadFacing facing : ModelQuadFacing.values()) {
				ChunkMeshBufferBuilder vertexBuffer = builder.getVertexBuffer(facing);

				if (vertexBuffer != null) {
					// Step 3: Inject the Monocle context holder
					((VertexEncoderInterface) vertexBuffer).iris$setContextHolder(contextHolder);
				}
			}
		}
	}

	@Override
	public void beginBlock(int block, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
		contextHolder.setBlockData(block, renderType, blockEmission, localPosX, localPosY, localPosZ);
	}

	@Override
	public void overrideBlock(int i) {
		contextHolder.overrideBlock(i);
	}

	@Override
	public void restoreBlock() {
		contextHolder.restoreBlock();
	}

	@Override
	public void endBlock() {
		contextHolder.setBlockData(0, (byte) 0, (byte) 0, 0, 0, 0);
	}

	@Override
	public void ignoreMidBlock(boolean b) {
		contextHolder.setIgnoreMidBlock(b);
	}
}