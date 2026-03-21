package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.irisshaders.iris.gl.BooleanStateExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GlStateManager.BooleanState.class)
public class MixinGlStateManagerBooleanState implements BooleanStateExtended {
    @Unique
    private boolean currentState;

    @Override
    public void setUnknownState() {
        // This forces Minecraft to re-send the GL command next time it thinks it knows the state
        this.currentState = !this.currentState;
    }
}