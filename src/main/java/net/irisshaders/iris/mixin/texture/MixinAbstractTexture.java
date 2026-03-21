package net.irisshaders.iris.mixin.texture;

import net.irisshaders.iris.texture.TextureTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public abstract class MixinAbstractTexture {

	// remap = false: use the Parchment/Mojmap name "id" literally at runtime.
	// Without this the refmap translates it to the stale SRG name "f_117950_"
	// which no longer exists in 1.21.1, causing the FATAL @Shadow field error.
	@Shadow(remap = false)
	protected int id;

	// remap = false: use the Parchment/Mojmap method name "getId" literally.
	// Without this the refmap translates it to the stale SRG name "m_117963_"
	// which no longer exists in 1.21.1, causing the FATAL @Inject target error.
	@Inject(method = "getId", at = @At("RETURN"), remap = false)
	private void iris$afterGetId(CallbackInfoReturnable<Integer> cir) {
		if (this.id != -1) {
			TextureTracker.INSTANCE.trackTexture(this.id, (AbstractTexture) (Object) this);
		}
	}
}