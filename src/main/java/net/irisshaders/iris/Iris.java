package net.irisshaders.iris;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.platform.GlDebug;
import com.mojang.blaze3d.platform.InputConstants;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.gui.screen.ScreenHandler;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.discovery.ShaderpackDirectoryManager;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

@Mod("oculus")
public class Iris {
	public static final String MODID = "oculus";
	public static final String MODNAME = "ForgeOculus";
	public static final IrisLogging logger = new IrisLogging(MODNAME);

	private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
	private static final String backupVersionNumber = "1.21";

	public static NamespacedId lastDimension = null;
	public static boolean testing = false;

	private static Path shaderpacksDirectory;
	private static ShaderpackDirectoryManager shaderpacksDirectoryManager;
	private static ShaderPack currentPack;
	private static String currentPackName;
	private static Optional<Exception> storedError = Optional.empty();

	// ---------------------------------------------------------------------------
	// Lifecycle state flags
	//
	// Forge 1.21.1 initialization order:
	//
	//   1. Mod constructor          → register MOD bus listeners only
	//   2. RegisterKeyMappingsEvent → construct + register KeyMappings (MOD bus)
	//   3. FMLClientSetupEvent      → config, directories, irisConfig (MOD bus,
	//                                  enqueueWork so it runs on render thread)
	//   4. RenderSystem.initRenderer→ GL is ready; PBRTextureManager init,
	//                                  debug setup, shader pack load
	//   5. Title screen init        → pipeline preparation (FORGE bus)
	//   6. Every client tick        → keybind handling (FORGE bus)
	//
	// We never call Minecraft.getInstance() during steps 1-3 because the
	// Minecraft instance does not exist yet, which previously caused
	// NoSuchMethodError (SRG name m_91087_).
	// ---------------------------------------------------------------------------

	/** True once FMLClientSetupEvent has completed (config loaded, dirs created). */
	private static boolean initialized = false;

	/** True once RenderSystem.initRenderer has completed (GL context ready). */
	private static boolean renderSystemReady = false;

	/**
	 * Set to true if onRenderSystemInit fires before FMLClientSetupEvent finishes.
	 * We'll complete the GL-dependent init on the next safe opportunity.
	 */
	private static boolean pendingRenderSystemInit = false;

	private static PipelineManager pipelineManager;
	private static IrisConfig irisConfig;
	private static FileSystem zipFileSystem;
	private static String IRIS_VERSION;
	private static boolean fallback;
	private static boolean resetShaderPackOptions = false;

	public static KeyMapping reloadKeybind;
	public static KeyMapping toggleShadersKeybind;
	public static KeyMapping shaderpackScreenKeybind;
	public static KeyMapping wireframeKeybind;

	// =========================================================================
	// Mod constructor — only wire up MOD bus events here, nothing else.
	// The Minecraft instance does NOT exist at this point.
	// =========================================================================

	// 1. ADD THIS CONSTRUCTOR HERE
	public Iris() throws IOException {
		IrisConfig.getInstance();
		// 1. Instantiate irisConfig with a safe path
		Path configPath = Minecraft.getInstance().gameDirectory.toPath()
				.resolve("config")
				.resolve("iris.properties");
		irisConfig = new IrisConfig(configPath); // <-- assign first

		// 2. Now it's safe to load
		irisConfig.load();

		// 3. Continue with mod setup
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::setup);

		modEventBus.addListener(Iris::onRegisterKeyMappings);
		modEventBus.addListener(Iris::onClientSetup);

		MinecraftForge.EVENT_BUS.addListener(Iris::onKeyInput);
		MinecraftForge.EVENT_BUS.addListener(Iris::onClientTick);

		logger.info("ForgeOculus: Mod instance created successfully.");
	}

	private void setup(final FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			// Put your initialization logic here (configs, folders, etc.)
			initialized = true;
			logger.info("ForgeOculus: Client setup completed on the render thread.");
		});
	}

	public Iris(IEventBus modEventBus, ModContainer modContainer) {
		try {
			IRIS_VERSION = ModList.get()
					.getModContainerById(MODID)
					.map(c -> c.getModInfo().getVersion().toString())
					.orElse("unknown");
		} catch (Exception e) {
			IRIS_VERSION = "unknown";
		}

		// MOD bus — fires at the correct time for each event type
		modEventBus.addListener(Iris::onRegisterKeyMappings);
		modEventBus.addListener(Iris::onClientSetup);

		// FORGE bus — fires during actual game runtime
		MinecraftForge.EVENT_BUS.addListener(Iris::onKeyInput);
		MinecraftForge.EVENT_BUS.addListener(Iris::onClientTick);

		if (FMLLoader.getDist().isClient()) {
			try {
				modContainer.registerExtensionPoint(
						ConfigScreenHandler.ConfigScreenFactory.class,
						() -> new ConfigScreenHandler.ConfigScreenFactory(
								(mc, lastScreen) -> new net.irisshaders.iris.gui.screen.ShaderPackScreen(lastScreen)
						)
				);
			} catch (Exception ignored) {}
		}
	}

	// =========================================================================
	// Step 2: RegisterKeyMappingsEvent (MOD bus)
	// KeyMapping constructors are ONLY safe here — not in the mod constructor,
	// not in FMLClientSetupEvent, and not during Options loading.
	// =========================================================================

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		reloadKeybind = new KeyMapping(
				"iris.keybind.reload",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				"iris.keybinds");

		toggleShadersKeybind = new KeyMapping(
				"iris.keybind.toggleShaders",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				"iris.keybinds");

		shaderpackScreenKeybind = new KeyMapping(
				"iris.keybind.shaderPackSelection",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				"iris.keybinds");

		wireframeKeybind = new KeyMapping(
				"iris.keybind.wireframe",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				"iris.keybinds");

		event.register(reloadKeybind);
		event.register(toggleShadersKeybind);
		event.register(shaderpackScreenKeybind);
		event.register(wireframeKeybind);
	}

	// =========================================================================
	// Step 3: FMLClientSetupEvent (MOD bus)
	// Minecraft is constructed but the render thread hasn't started yet.
	// Use enqueueWork() to run on the main thread after construction finishes.
	// Safe for: config, file I/O, DHCompat. NOT safe for: GL, pipelines, textures.
	// =========================================================================

	private static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			try {

				if (!Files.exists(getShaderpacksDirectory())) {
					Files.createDirectories(getShaderpacksDirectory());
				}

				initialized = true;

				// If the render system initialized before we did (rare but possible),
				// complete that work now.
				if (pendingRenderSystemInit) {
					pendingRenderSystemInit = false;
					completeRenderSystemInit();
				}

			} catch (IOException e) {
				logger.warn("Failed to create the shaderpacks directory!");
				logger.warn("", e);
			}
		});
	}

	// =========================================================================
	// Step 4a: Called from MixinRenderSystem when RenderSystem.initRenderer()
	// completes. GL context is ready. May fire before FMLClientSetupEvent
	// finishes in rare cases, so we guard with the initialized flag.
	// =========================================================================

	public static void onRenderSystemInit() {
		if (!initialized) {
			logger.warn("RenderSystem init fired before FMLClientSetupEvent completed — deferring GL init.");
			pendingRenderSystemInit = true;
			return;
		}
		completeRenderSystemInit();
	}

	private static void completeRenderSystemInit() {
		PBRTextureManager.INSTANCE.init();
		renderSystemReady = true;

		// Apply debug state now that GL is ready — safe because we're on
		// the render thread and irisConfig is guaranteed non-null here.
		applyDebugState(irisConfig.areDebugOptionsEnabled());

		// Load the shader pack unless DH is present (it defers loading).
		if (LoadingModList.get().getModFileById("distanthorizons") == null) {
			loadShaderpack();
		}
	}

	public static IrisConfig getIrisConfig() {
		if (irisConfig == null) {
			System.out.println("ForgeOculus: getIrisConfig called while null! Initializing now...");
			try {
				Path configPath = Minecraft.getInstance().gameDirectory.toPath().resolve("iris.properties");
				irisConfig = new IrisConfig(configPath); // ✅ initialize first
				irisConfig.load(); // now safe
			} catch (Exception e) {
				System.err.println("ForgeOculus: Failed to load config! " + e.getMessage());
			}
		}
		return irisConfig;
	}
	// =========================================================================
	// Step 4b: Called from MixinRenderSystem during initRenderer — fires
	// slightly before onRenderSystemInit. Keep this minimal and GL-only;
	// never call Minecraft.getInstance() here.
	// =========================================================================

	public static void duringRenderSystemInit() {
		// Intentionally left minimal. Debug state is applied in
		// completeRenderSystemInit() once both initialized and GL-ready.
		// Previously this called setDebug() which called Minecraft.getInstance()
		// before the instance existed, causing NoSuchMethodError.
	}

	// =========================================================================
	// Step 5: Called from MixinTitleScreen when the title screen first loads.
	// Minecraft is fully ready. Safe for pipeline preparation.
	// =========================================================================

	public static void onLoadingComplete() {
		if (!initialized) {
			logger.warn("onLoadingComplete called before initialization completed — ignoring.");
			return;
		}

		lastDimension = DimensionId.OVERWORLD;
		Iris.getPipelineManager().preparePipeline(DimensionId.OVERWORLD);
	}

	// =========================================================================
	// Step 6: Client tick (FORGE bus) — keybind handling every tick.
	// Minecraft.getInstance() is fully safe here.
	// =========================================================================

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START) return;
		if (!initialized || !renderSystemReady) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) return;

		handleKeybinds(mc);
	}

	private static void onKeyInput(InputEvent.Key event) {
		if (!initialized || !renderSystemReady) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			handleKeybinds(mc);
		}
	}

	// =========================================================================
	// Public API — keybind handling, shader loading, etc.
	// =========================================================================

	public static void handleKeybinds(Minecraft minecraft) {
		if (reloadKeybind == null) return; // Safety: keybinds not yet registered

		if (reloadKeybind.consumeClick()) {
			try {
				reload();
				if (minecraft.player != null) {
					minecraft.player.displayClientMessage(
							Component.translatable("iris.shaders.reloaded"), false);
				}
			} catch (Exception e) {
				logger.error("Error while reloading Shaders for " + MODNAME + "!", e);
				if (minecraft.player != null) {
					minecraft.player.displayClientMessage(
							Component.translatable("iris.shaders.reloaded.failure",
											Throwables.getRootCause(e).getMessage())
									.withStyle(ChatFormatting.RED), false);
				}
			}
		} else if (toggleShadersKeybind.consumeClick()) {
			try {
				toggleShaders(minecraft, !irisConfig.areShadersEnabled());
			} catch (Exception e) {
				logger.error("Error while toggling shaders!", e);
				if (minecraft.player != null) {
					minecraft.player.displayClientMessage(
							Component.translatable("iris.shaders.toggled.failure",
											Throwables.getRootCause(e).getMessage())
									.withStyle(ChatFormatting.RED), false);
				}
				setShadersDisabled();
				fallback = true;
			}
		} else if (shaderpackScreenKeybind.consumeClick()) {
			ScreenHandler.openShaderPackScreen();
		} else if (wireframeKeybind.consumeClick()) {
			if (irisConfig.areDebugOptionsEnabled()
					&& minecraft.player != null
					&& !Minecraft.getInstance().isLocalServer()) {
				minecraft.player.displayClientMessage(
						Component.literal("No cheating; wireframe only in singleplayer!"), false);
			}
		}
	}

	public static boolean shouldActivateWireframe() {
		return irisConfig != null
				&& irisConfig.areDebugOptionsEnabled()
				&& wireframeKeybind != null
				&& wireframeKeybind.isDown();
	}

	public static void toggleShaders(Minecraft minecraft, boolean enabled) throws IOException {
		irisConfig.setShadersEnabled(enabled);
		irisConfig.save();
		reload();
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(
					enabled
							? Component.translatable("iris.shaders.toggled", currentPackName)
							: Component.translatable("iris.shaders.disabled"),
					false);
		}
	}

	/**
	 * Applies the GL debug state. Only call this when the GL context and
	 * Minecraft instance are both available (i.e. from completeRenderSystemInit
	 * or later).
	 */
	public static void applyDebugState(boolean enable) {
		try {
			if (irisConfig != null) {
				irisConfig.setDebugEnabled(enable);
				irisConfig.save();
			}
		} catch (IOException e) {
			logger.fatal("Failed to save config!", e);
		}

		if (enable) {
			GLDebug.setupDebugMessageCallback();
		} else {
			GLDebug.reloadDebugState();
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.options != null) {
				GlDebug.enableDebugCallback(mc.options.glDebugVerbosity, false);
			}
		}
	}

	/**
	 * Full debug toggle — safe to call at runtime (player is in world).
	 */
	public static void setDebug(boolean enable) {
		System.out.println("DEBUG CHECK: If you see this, the JAR is updated!");
		applyDebugState(enable);

		int success = enable ? GLDebug.setupDebugMessageCallback() : 1;

		logger.info("Debug functionality is "
				+ (enable ? "enabled, logging will be more verbose!" : "disabled."));

		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.player != null) {
			mc.player.displayClientMessage(
					Component.translatable(success != 0
							? (enable ? "iris.shaders.debug.enabled" : "iris.shaders.debug.disabled")
							: "iris.shaders.debug.failure"),
					false);
			if (success == 2) {
				mc.player.displayClientMessage(
						Component.translatable("iris.shaders.debug.restart"), false);
			}
		}
	}

	public static void loadShaderpack() {
		if (irisConfig == null) {
			if (!initialized) {
				throw new IllegalStateException(
						"Iris::loadShaderpack was called before initialization completed.");
			} else {
				throw new NullPointerException("Iris.irisConfig was null unexpectedly");
			}
		}

		if (!irisConfig.areShadersEnabled()) {
			logger.info("Shaders are disabled because enableShaders is set to false in iris.properties");
			setShadersDisabled();
			return;
		}

		Optional<String> externalName = irisConfig.getShaderPackName();
		if (externalName.isEmpty()) {
			logger.info("Shaders are disabled because no valid shaderpack is selected");
			setShadersDisabled();
			return;
		}

		if (!loadExternalShaderpack(externalName.get())) {
			logger.warn("Falling back to normal rendering without shaders because the shaderpack could not be loaded");
			setShadersDisabled();
			fallback = true;
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean loadExternalShaderpack(String name) {
		Path shaderPackRoot;
		Path shaderPackConfigTxt;

		try {
			shaderPackRoot = getShaderpacksDirectory().resolve(name);
			shaderPackConfigTxt = getShaderpacksDirectory().resolve(name + ".txt");
		} catch (InvalidPathException e) {
			logger.error("Failed to load the shaderpack \"{}\" because it contains invalid characters in its path", name);
			return false;
		}

		if (!isValidShaderpack(shaderPackRoot)) {
			logger.error("Pack \"{}\" is not valid! Can't load it.", name);
			return false;
		}

		Path shaderPackPath;
		boolean isZip = false;

		if (!Files.isDirectory(shaderPackRoot) && shaderPackRoot.toString().endsWith(".zip")) {
			Optional<Path> optionalPath;
			try {
				optionalPath = loadExternalZipShaderpack(shaderPackRoot);
			} catch (FileSystemNotFoundException | NoSuchFileException e) {
				logger.error("Failed to load the shaderpack \"{}\" because it does not exist in your shaderpacks folder!", name);
				return false;
			} catch (ZipException e) {
				logger.error("The shaderpack \"{}\" appears to be corrupted, please try downloading it again!", name);
				return false;
			} catch (IOException e) {
				logger.error("Failed to load the shaderpack \"{}\"!", name);
				logger.error("", e);
				return false;
			}

			if (optionalPath.isPresent()) {
				shaderPackPath = optionalPath.get();
			} else {
				logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
				return false;
			}
			isZip = true;
		} else {
			if (!Files.exists(shaderPackRoot)) {
				logger.error("Failed to load the shaderpack \"{}\" because it does not exist!", name);
				return false;
			}
			shaderPackPath = shaderPackRoot.resolve("shaders");
		}

		if (!Files.exists(shaderPackPath)) {
			logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
			return false;
		}

		Map<String, String> changedConfigs = tryReadConfigProperties(shaderPackConfigTxt)
				.map(properties -> (Map<String, String>) (Object) properties)
				.orElse(new HashMap<>());

		changedConfigs.putAll(shaderPackOptionQueue);
		clearShaderPackOptionQueue();

		if (resetShaderPackOptions) {
			changedConfigs.clear();
		}
		resetShaderPackOptions = false;

		try {
			currentPack = new ShaderPack(shaderPackPath, changedConfigs,
					StandardMacros.createStandardEnvironmentDefines(), isZip);

			MutableOptionValues changedConfigsValues =
					currentPack.getShaderPackOptions().getOptionValues().mutableCopy();

			Properties configsToSave = new Properties();
			changedConfigsValues.getBooleanValues()
					.forEach((k, v) -> configsToSave.setProperty(k, Boolean.toString(v)));
			changedConfigsValues.getStringValues().forEach(configsToSave::setProperty);

			tryUpdateConfigPropertiesFile(shaderPackConfigTxt, configsToSave);
		} catch (Exception e) {
			logger.error("Failed to load the shaderpack \"{}\"!", name);
			logger.error("", e);
			return false;
		}

		fallback = false;
		currentPackName = name;
		logger.info("Using shaderpack: " + name);
		return true;
	}

	private static Optional<Path> loadExternalZipShaderpack(Path shaderpackPath) throws IOException {
		FileSystem zipSystem = FileSystems.newFileSystem(shaderpackPath, Iris.class.getClassLoader());
		zipFileSystem = zipSystem;

		Path potentialShaderDir = zipSystem.getPath("shaders");
		if (Files.exists(potentialShaderDir)) {
			return Optional.of(potentialShaderDir);
		}

		Path root = zipSystem.getRootDirectories().iterator().next();
		try (Stream<Path> stream = Files.walk(root)) {
			return stream
					.filter(Files::isDirectory)
					.filter(path -> path.endsWith("shaders"))
					.findFirst();
		}
	}

	private static void setShadersDisabled() {
		currentPack = null;
		fallback = false;
		currentPackName = "(off)";
		logger.info("Shaders are disabled");
	}

	public static void reload() throws IOException {
		destroyEverything();
		loadShaderpack();

		if (Minecraft.getInstance().level != null) {
			Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
		}
	}

	private static void destroyEverything() {
		currentPack = null;
		getPipelineManager().destroyPipeline();

		if (zipFileSystem != null) {
			try {
				zipFileSystem.close();
			} catch (NoSuchFileException e) {
				logger.warn("Failed to close the shaderpack zip when reloading because it was deleted, proceeding anyways.");
			} catch (IOException e) {
				logger.error("Failed to close zip file system?", e);
			}
		}
	}

	public static NamespacedId getCurrentDimension() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			return new NamespacedId(
					level.dimension().location().getNamespace(),
					level.dimension().location().getPath());
		}
		return lastDimension;
	}

	private static WorldRenderingPipeline createPipeline(NamespacedId dimensionId) {
		if (currentPack == null) {
			return new VanillaRenderingPipeline();
		}

		ProgramSet programs = currentPack.getProgramSet(dimensionId);
		try {
			return new IrisRenderingPipeline(programs);
		} catch (Exception e) {
			if (irisConfig.areDebugOptionsEnabled()) {
				ScreenHandler.openDebugLoadFailedGridScreen(
						Component.literal(e instanceof ShaderCompileException
								? "Failed to compile shaders" : "Exception"), e);
			} else {
				Minecraft mc = Minecraft.getInstance();
				if (mc != null && mc.player != null) {
					mc.player.displayClientMessage(
							Component.translatable(e instanceof ShaderCompileException
											? "iris.load.failure.shader"
											: "iris.load.failure.generic")
									.append(Component.literal("Copy Info")
											.withStyle(arg -> arg
													.withUnderlined(true)
													.withColor(ChatFormatting.BLUE)
													.withClickEvent(new ClickEvent(
															ClickEvent.Action.COPY_TO_CLIPBOARD,
															e.getMessage())))),
							false);
				} else {
					storedError = Optional.of(e);
				}
			}
			logger.error("Failed to create shader rendering pipeline, disabling shaders!", e);
			fallback = true;
			return new VanillaRenderingPipeline();
		}
	}

	// =========================================================================
	// Accessors
	// =========================================================================

	@NotNull
	public static PipelineManager getPipelineManager() {
		if (pipelineManager == null) {
			pipelineManager = new PipelineManager(Iris::createPipeline);
		}
		return pipelineManager;
	}

	public static boolean isPackInUseQuick() {
		return pipelineManager != null
				&& pipelineManager.getPipelineNullable() instanceof IrisRenderingPipeline;
	}

	public static void loadShaderpackWhenPossible() {
		// Kept for compatibility — now a no-op since loading is deferred
		// automatically via the renderSystemReady flag.
	}

	public static Optional<Exception> getStoredError() {
		Optional<Exception> stored = Iris.storedError;
		storedError = Optional.empty();
		return stored;
	}

	@NotNull
	public static Optional<ShaderPack> getCurrentPack() {
		return Optional.ofNullable(currentPack);
	}

	public static String getCurrentPackName() {
		return currentPackName;
	}

	public static boolean isFallback() {
		return fallback;
	}

	public static boolean isRenderSystemReady() {
		return renderSystemReady;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static String getVersion() {
		return ModList.get().getModContainerById("oculus") // Use your mod's ID from mods.toml
				.map(container -> container.getModInfo().getVersion().toString())
				.orElse("1.8.7.beta1"); // Fallback string if something goes wrong
	}

	public static String getFormattedVersion() {
		ChatFormatting color;
		String version = getVersion();

		if (!FMLEnvironment.production) {
			color = ChatFormatting.GOLD;
			version = version + " (Development Environment)";
		} else if (version.endsWith("-dirty") || version.contains("unknown") || version.endsWith("-nogit")) {
			color = ChatFormatting.RED;
		} else if (version.contains("+rev.")) {
			color = ChatFormatting.LIGHT_PURPLE;
		} else {
			color = ChatFormatting.GREEN;
		}

		return color + version;
	}

	public static String getReleaseTarget() {
		SharedConstants.tryDetectVersion();
		return SharedConstants.getCurrentVersion().isStable()
				? SharedConstants.getCurrentVersion().getName()
				: backupVersionNumber;
	}

	public static String getBackupVersionNumber() {
		return backupVersionNumber;
	}

	public static Path getShaderpacksDirectory() {
		if (shaderpacksDirectory == null) {
			shaderpacksDirectory = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
		}
		return shaderpacksDirectory;
	}

	public static ShaderpackDirectoryManager getShaderpacksDirectoryManager() {
		if (shaderpacksDirectoryManager == null) {
			shaderpacksDirectoryManager = new ShaderpackDirectoryManager(getShaderpacksDirectory());
		}
		return shaderpacksDirectoryManager;
	}

	public static Map<String, String> getShaderPackOptionQueue() {
		return shaderPackOptionQueue;
	}

	public static void queueShaderPackOptionsFromProfile(Profile profile) {
		getShaderPackOptionQueue().putAll(profile.optionValues);
	}

	public static void queueShaderPackOptionsFromProperties(Properties properties) {
		queueDefaultShaderPackOptionValues();
		properties.stringPropertyNames().forEach(key ->
				getShaderPackOptionQueue().put(key, properties.getProperty(key)));
	}

	public static void queueDefaultShaderPackOptionValues() {
		clearShaderPackOptionQueue();
		getCurrentPack().ifPresent(pack -> {
			OptionSet options = pack.getShaderPackOptions().getOptionSet();
			OptionValues values = pack.getShaderPackOptions().getOptionValues();

			options.getStringOptions().forEach((key, mOpt) -> {
				if (values.getStringValue(key).isPresent()) {
					getShaderPackOptionQueue().put(key, mOpt.getOption().getDefaultValue());
				}
			});
			options.getBooleanOptions().forEach((key, mOpt) -> {
				if (values.getBooleanValue(key) != OptionalBoolean.DEFAULT) {
					getShaderPackOptionQueue().put(key, Boolean.toString(mOpt.getOption().getDefaultValue()));
				}
			});
		});
	}

	public static void clearShaderPackOptionQueue() {
		getShaderPackOptionQueue().clear();
	}

	public static void resetShaderPackOptionsOnNextReload() {
		resetShaderPackOptions = true;
	}

	public static boolean shouldResetShaderPackOptionsOnNextReload() {
		return resetShaderPackOptions;
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	private static Optional<Properties> tryReadConfigProperties(Path path) {
		Properties properties = new Properties();
		if (Files.exists(path)) {
			try (InputStream is = Files.newInputStream(path)) {
				properties.load(is);
			} catch (IOException e) {
				return Optional.empty();
			}
		}
		return Optional.of(properties);
	}

	private static void tryUpdateConfigPropertiesFile(Path path, Properties properties) {
		try {
			if (properties.isEmpty()) {
				if (Files.exists(path)) Files.delete(path);
				return;
			}
			try (OutputStream out = Files.newOutputStream(path)) {
				properties.store(out, null);
			}
		} catch (IOException ignored) {}
	}

	public static boolean isValidToShowPack(Path pack) {
		return Files.isDirectory(pack) || pack.toString().endsWith(".zip");
	}

	public static boolean isValidShaderpack(Path pack) {
		if (Files.isDirectory(pack)) {
			if (pack.equals(getShaderpacksDirectory())) return false;
			try (Stream<Path> stream = Files.walk(pack)) {
				return stream
						.filter(Files::isDirectory)
						.filter(path -> !path.equals(pack))
						.anyMatch(path -> path.endsWith("shaders"));
			} catch (IOException ignored) {
				return false;
			}
		}

		if (pack.toString().endsWith(".zip")) {
			try (FileSystem zipSystem = FileSystems.newFileSystem(pack, Iris.class.getClassLoader())) {
				Path root = zipSystem.getRootDirectories().iterator().next();
				try (Stream<Path> stream = Files.walk(root)) {
					return stream
							.filter(Files::isDirectory)
							.anyMatch(path -> path.endsWith("shaders"));
				}
			} catch (ZipError zipError) {
				Iris.logger.warn("The ZIP at " + pack + " is corrupt");
			} catch (IOException ignored) {}
		}

		return false;
	}
}