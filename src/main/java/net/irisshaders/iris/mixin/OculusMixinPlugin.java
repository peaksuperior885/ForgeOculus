package net.irisshaders.iris.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class OculusMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Could register extra logic if needed
    }

    @Override
    public String getRefMapperConfig() {
        return null; // Let the mixin config handle it
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Force it to true so we can actually see the errors if they happen
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not needed for now
    }

    @Override
    public List<String> getMixins() {
        // You could dynamically register mixins here if needed
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Optional: pre-processing before mixin application
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Optional: post-processing after mixin application
    }
}