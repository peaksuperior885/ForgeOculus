package net.irisshaders.iris.compat.embeddium.impl.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.embeddedt.embeddium.api.options.structure.OptionStorage;

public class ConfigValueStorage<T> implements OptionStorage<ForgeConfigSpec.ConfigValue<T>> {

    private final ForgeConfigSpec.ConfigValue<T> value;

    public ConfigValueStorage(ForgeConfigSpec.ConfigValue<T> value) {
        this.value = value;
    }

    @Override
    public ForgeConfigSpec.ConfigValue<T> getData() {
        return value;
    }

    @Override
    public void save() {
        value.save();
    }
}
