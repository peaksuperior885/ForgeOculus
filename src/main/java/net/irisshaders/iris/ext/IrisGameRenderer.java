package net.irisshaders.iris.ext;

import net.minecraft.client.renderer.PostChain;

public interface IrisGameRenderer {
    default PostChain iris$getBlurEffect() {
        return null;
    }
}