package net.irisshaders.iris.config;

import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class IrisConfig {
	private static final String COMMENT =
			"This file stores configuration options for Iris, such as the currently active shaderpack";

	private final Path propertiesPath;
	private String shaderPackName = null;
	private boolean enableShaders = true;
	private boolean enableDebugOptions = false;
	private boolean disableUpdateMessage = false;

	// Singleton instance for lazy access
	private static IrisConfig instance;

	// ------------------------
	// Constructors
	// ------------------------

	/** No-arg constructor sets default config path in .minecraft/config/iris.properties */
	public IrisConfig() {
		this.propertiesPath = net.minecraft.client.Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve("config")
				.resolve("iris.properties");
	}

	/** Path-based constructor allows custom location */
	public IrisConfig(Path propertiesPath) {
		this.propertiesPath = propertiesPath;
	}

	// ------------------------
	// Lazy singleton accessor
	// ------------------------
	public static IrisConfig getInstance() {
		if (instance == null) {
			try {
				instance = new IrisConfig(); // default path
				instance.load();
			} catch (Exception e) {
				System.err.println("NeOculus: Failed to load IrisConfig: " + e.getMessage());
			}
		}
		return instance;
	}

	public static IrisConfig getInstance(Path customPath) {
		if (instance == null) {
			try {
				instance = new IrisConfig(customPath);
				instance.load();
			} catch (Exception e) {
				System.err.println("NeOculus: Failed to load IrisConfig: " + e.getMessage());
			}
		}
		return instance;
	}

	// ------------------------
	// Load & save
	// ------------------------
	public void load() throws IOException {
		if (!Files.exists(propertiesPath)) return;

		Properties properties = new Properties();
		try (InputStream is = Files.newInputStream(propertiesPath)) {
			properties.load(is);
		}

		shaderPackName = properties.getProperty("shaderPack");
		enableShaders = !"false".equals(properties.getProperty("enableShaders"));
		enableDebugOptions = "true".equals(properties.getProperty("enableDebugOptions"));
		disableUpdateMessage = "true".equals(properties.getProperty("disableUpdateMessage"));

		try {
			IrisVideoSettings.shadowDistance = Integer.parseInt(
					properties.getProperty("maxShadowRenderDistance", "32"));
			IrisVideoSettings.colorSpace = ColorSpace.valueOf(
					properties.getProperty("colorSpace", "SRGB"));
		} catch (Exception e) {
			IrisVideoSettings.shadowDistance = 32;
			IrisVideoSettings.colorSpace = ColorSpace.SRGB;
		}
	}

	public void save() throws IOException {
		Files.createDirectories(propertiesPath.getParent());

		Properties properties = new Properties();
		properties.setProperty("shaderPack", shaderPackName != null ? shaderPackName : "");
		properties.setProperty("enableShaders", String.valueOf(enableShaders));
		properties.setProperty("enableDebugOptions", String.valueOf(enableDebugOptions));
		properties.setProperty("disableUpdateMessage", String.valueOf(disableUpdateMessage));
		properties.setProperty("maxShadowRenderDistance", String.valueOf(IrisVideoSettings.shadowDistance));
		properties.setProperty("colorSpace", IrisVideoSettings.colorSpace.name());

		try (OutputStream os = Files.newOutputStream(propertiesPath)) {
			properties.store(os, COMMENT);
		}
	}

	// ------------------------
	// Getters / Setters
	// ------------------------
	public boolean areShadersEnabled() { return enableShaders; }
	public void setShadersEnabled(boolean enabled) { this.enableShaders = enabled; }
	public Optional<String> getShaderPackName() { return Optional.ofNullable(shaderPackName); }
	public void setShaderPackName(String name) {
		this.shaderPackName = (name == null || name.isEmpty() || name.equals("(internal)"))
				? null : name;
	}
	public boolean areDebugOptionsEnabled() { return enableDebugOptions; }
	public void setDebugEnabled(boolean enabled) { this.enableDebugOptions = enabled; }
	public boolean shouldDisableUpdateMessage() { return disableUpdateMessage; }
}