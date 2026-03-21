package net.irisshaders.batchedentityrendering.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.irisshaders.batchedentityrendering.impl.MemoryTrackingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ByteBufferBuilder.class)
public abstract class MixinByteBufferBuilder implements MemoryTrackingBuffer {
    @Shadow(remap = false)    private int capacity;

    @Shadow(remap = false)    private int writeOffset;

    @Shadow(remap = false)    public abstract void close();

    @Override
    public long getAllocatedSize() {
        return this.capacity;
    }

    @Override
    public long getUsedSize() {
        return this.writeOffset;
    }

    @Override
    public void freeAndDeleteBuffer() {
        this.close();
    }
}