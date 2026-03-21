package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class IrisVideoSettings {

	// -------------------------------------------------------------------------
	// FORGE 1.21.1 FIX:
	// These were previously static final fields initialized at class-load time:
	//
	//   private static final Tooltip DISABLED_TOOLTIP =
	//       Tooltip.create(Component.translatable("..."));
	//
	// This caused a NoSuchMethodError (SRG name m_237115_) because the class
	// was being loaded during onEarlyInitialize(), before Minecraft's
	// Component/translation system is fully bootstrapped.
	//
	// Solution: make them lazily initialized on first access so they are only
	// constructed when actually needed (i.e. when the options screen opens),
	// by which point the translation system is fully ready.
	// -------------------------------------------------------------------------

	private static Tooltip disabledTooltip;
	private static Tooltip enabledTooltip;

	private static Tooltip getDisabledTooltip() {
		if (disabledTooltip == null) {
			disabledTooltip = Tooltip.create(Component.translatable("options.iris.shadowDistance.disabled"));
		}
		return disabledTooltip;
	}

	private static Tooltip getEnabledTooltip() {
		if (enabledTooltip == null) {
			enabledTooltip = Tooltip.create(Component.translatable("options.iris.shadowDistance.enabled"));
		}
		return enabledTooltip;
	}

	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;

	public static final OptionInstance<Integer> RENDER_DISTANCE = new ShadowDistanceOption<>(
			"options.iris.shadowDistance",
			mc -> {
				WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

				if (pipeline != null) {
					if (pipeline.getForcedShadowRenderDistanceChunksForDisplay().isPresent()) {
						return getDisabledTooltip();
					} else {
						return getEnabledTooltip();
					}
				} else {
					return getEnabledTooltip();
				}
			},
			(arg, d) -> {
				WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

				if (pipeline != null) {
					d = pipeline.getForcedShadowRenderDistanceChunksForDisplay().orElse(d);
				}

				if (d <= 0.0) {
					return Component.translatable("options.generic_value",
							Component.translatable("options.iris.shadowDistance"),
							"0 (disabled)");
				} else {
					return Component.translatable("options.generic_value",
							Component.translatable("options.iris.shadowDistance"),
							Component.translatable("options.chunks", d));
				}
			},
			new OptionInstance.IntRange(0, 32),
			getOverriddenShadowDistance(shadowDistance),
			integer -> {
				shadowDistance = integer;
				try {
					Iris.getIrisConfig().save();
				} catch (IOException e) {
					Iris.logger.fatal("Failed to save config!", e);
				}
			});

	public static int getOverriddenShadowDistance(int base) {
		return Iris.getPipelineManager().getPipeline()
				.map(pipeline -> pipeline.getForcedShadowRenderDistanceChunksForDisplay().orElse(base))
				.orElse(base);
	}

	public static boolean isShadowDistanceSliderEnabled() {
		return Iris.getPipelineManager().getPipeline()
				.map(pipeline -> !pipeline.getForcedShadowRenderDistanceChunksForDisplay().isPresent())
				.orElse(true);
	}
}