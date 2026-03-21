package net.irisshaders.iris.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.OldImageButton;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.element.screen.IrisButton;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.CommentedElementWidget;
import net.irisshaders.iris.ext.IrisGameRenderer;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.transforms.SmoothedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.irisshaders.iris.uniforms.CameraUniforms.client;

public class ShaderPackScreen extends Screen implements HudHideable {

	public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

	private static final Component SELECT_TITLE = Component.translatable("pack.iris.select.title")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final Component CONFIGURE_TITLE = Component.translatable("pack.iris.configure.title")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final int COMMENT_PANEL_WIDTH = 314;

	private final Screen parent;
	private final MutableComponent irisTextComponent;
	private final FrameUpdateNotifier notifier = new FrameUpdateNotifier();

	private ShaderPackSelectionList shaderPackList;
	private @Nullable ShaderPackOptionList shaderOptionList = null;
	private @Nullable NavigationController navigation = null;
	private Button screenSwitchButton;
	private Component notificationDialog = null;
	private int notificationDialogTimer = 0;
	private @Nullable AbstractElementWidget<?> hoveredElement = null;
	private Optional<Component> hoveredElementCommentTitle = Optional.empty();
	private List<FormattedCharSequence> hoveredElementCommentBody = new ArrayList<>();
	private int hoveredElementCommentTimer = 0;
	private boolean optionMenuOpen = false;
	private boolean dropChanges = false;
	private MutableComponent developmentComponent;
	private MutableComponent updateComponent;
	private boolean guiHidden = false;

	public final SmoothedFloat blurTransition = new SmoothedFloat(2, 2, () -> {
		if (guiHidden) return 0.0f;
		else if (this.optionMenuOpen) return 0.1f;
		else return (float) this.minecraft.options.getMenuBackgroundBlurriness();
	}, notifier);

	private float guiButtonHoverTimer = 0.0f;
	private Button openFolderButton;
	private float backgroundInit = 0.0f;

	public final SmoothedFloat listTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden || this.optionMenuOpen) return 0.0f;
		else return backgroundInit;
	}, notifier);

	public final SmoothedFloat buttonTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden) return 0.0f;
		else return backgroundInit;
	}, notifier);

	private OldImageButton showHideButton;

	public ShaderPackScreen(Screen parent) {
		super(Component.translatable("options.iris.shaderPackSelection.title"));
		this.parent = parent;

		String irisName = Iris.MODNAME + " " + Iris.getVersion();

		if (!FMLEnvironment.production) {
			this.developmentComponent = Component.literal("Development Environment")
					.withStyle(ChatFormatting.GOLD);
		}

		this.irisTextComponent = Component.literal(irisName).withStyle(ChatFormatting.GRAY);
		refreshForChangedPack();
	}

	// =========================================================================
	// Rendering
	// =========================================================================

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		notifier.onNewFrame();
		// Force backgroundInit to 1 so buttons are visible immediately —
		// previously SmoothedFloat starting at 0 caused buttons to be
		// invisible until they animated in, which on some setups never happened.
		backgroundInit = 1.0f;

		// Debug mode toggle (Ctrl+D)
		if (Screen.hasControlDown() && InputConstants.isKeyDown(
				Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_D)) {
			Minecraft.getInstance().setScreen(new ConfirmScreen(
					option -> {
						Iris.setDebug(option);
						Minecraft.getInstance().setScreen(this);
					},
					Component.literal("Shader debug mode toggle"),
					Component.literal("Debug mode helps investigate problems and shows shader errors. Would you like to enable it?"),
					Component.literal("Yes"),
					Component.literal("No")));
		}

		if (!this.guiHidden) {
			// Render background + widgets via super
			super.render(guiGraphics, mouseX, mouseY, delta);

			// Render the active list on top
			if (optionMenuOpen && this.shaderOptionList != null) {
				this.shaderOptionList.render(guiGraphics, mouseX, mouseY, delta);
			} else if (this.shaderPackList != null) {
				this.shaderPackList.render(guiGraphics, mouseX, mouseY, delta);
			}

			// Title
			guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

			// Subtitle / notification
			if (notificationDialog != null && notificationDialogTimer > 0) {
				guiGraphics.drawCenteredString(this.font, notificationDialog, this.width / 2, 21, 0xFFFFFF);
			} else {
				guiGraphics.drawCenteredString(this.font,
						optionMenuOpen ? CONFIGURE_TITLE : SELECT_TITLE,
						this.width / 2, 21, 0xFFFFFF);
			}

			// Comment panel
			if (this.isDisplayingComment()) {
				int panelHeight = Math.max(50, 18 + (this.hoveredElementCommentBody.size() * 10));
				int x = this.width / 2 - 157;
				int y = this.height - (panelHeight + 4);
				GuiUtil.drawPanel(guiGraphics, x, y, COMMENT_PANEL_WIDTH, panelHeight);
				guiGraphics.drawString(font,
						this.hoveredElementCommentTitle.orElse(Component.empty()), x + 4, y + 4, 0xFFFFFF);
				for (int i = 0; i < this.hoveredElementCommentBody.size(); i++) {
					guiGraphics.drawString(font, this.hoveredElementCommentBody.get(i),
							x + 4, (y + 16) + (i * 10), 0xFFFFFF);
				}
			}

		} else {
			// GUI hidden — only render blur + show/hide button
			this.renderBlurredBackground(delta);
			if (this.showHideButton != null) {
				this.showHideButton.render(guiGraphics, mouseX, mouseY, delta);
			}
		}

		// Drain the top-layer render queue (tooltips, dialogs, etc.)
		for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
			render.run();
		}
		TOP_LAYER_RENDER_QUEUE.clear();

		// Version/update text in bottom-left
		if (this.developmentComponent != null) {
			guiGraphics.drawString(font, developmentComponent, 2, this.height - 10, 0xFFFFFF);
			guiGraphics.drawString(font, irisTextComponent, 2, this.height - 20, 0xFFFFFF);
		} else if (this.updateComponent != null) {
			guiGraphics.drawString(font, updateComponent, 2, this.height - 10, 0xFFFFFF);
			guiGraphics.drawString(font, irisTextComponent, 2, this.height - 20, 0xFFFFFF);
		} else {
			guiGraphics.drawString(font, irisTextComponent, 2, this.height - 10, 0xFFFFFF);
		}

		float previousHoverTimer = this.guiButtonHoverTimer;
		if (previousHoverTimer == this.guiButtonHoverTimer) {
			this.guiButtonHoverTimer = 0.0f;
		}
	}

	// =========================================================================
	// Init — button layout
	// =========================================================================

	@Override
	protected void init() {
		super.init();

		// Force buttons visible immediately
		backgroundInit = 1.0f;

		boolean inWorld = this.minecraft.level != null;

		this.removeWidget(this.shaderPackList);
		this.removeWidget(this.shaderOptionList);

		this.shaderPackList = new ShaderPackSelectionList(
				this, this.minecraft, this.width, this.height,
				32, this.height - 58 - 32, 0, this.width);

		if (Iris.getCurrentPack().isPresent() && this.navigation != null) {
			ShaderPack currentPack = Iris.getCurrentPack().get();
			this.shaderOptionList = new ShaderPackOptionList(
					this, this.navigation, currentPack, this.minecraft,
					this.width, this.height, 32, this.height - 58 - 32, 0, this.width);
			this.navigation.setActiveOptionList(this.shaderOptionList);
			this.shaderOptionList.rebuild();
		} else {
			optionMenuOpen = false;
			this.shaderOptionList = null;
		}

		this.clearWidgets();

		if (!this.guiHidden) {
			// Add the active list widget
			if (optionMenuOpen && shaderOptionList != null) {
				this.addRenderableWidget(shaderOptionList);
			} else {
				this.addRenderableWidget(shaderPackList);
			}

			// ---------------------------------------------------------------
			// Bottom row (y = height - 27):
			//   [Cancel]  [Apply]  [Done]
			// Centred around width/2.
			// Button width = 100, gap = 4.
			// Total width of three buttons = 308.
			// Left edge = width/2 - 154
			// ---------------------------------------------------------------
			int bw = 100; // button width
			int bh = 20;  // button height
			int gap = 4;
			int bottomY = this.height - 27;
			int rowLeft = this.width / 2 - (bw * 3 + gap * 2) / 2;

			this.addRenderableWidget(IrisButton.iris$builder(
							CommonComponents.GUI_CANCEL,
							button -> this.dropChangesAndClose(),
							buttonTransition)
					.bounds(rowLeft, bottomY, bw, bh)
					.build());

			this.addRenderableWidget(IrisButton.iris$builder(
							Component.translatable("options.iris.apply"),
							button -> this.applyChanges(),
							buttonTransition)
					.bounds(rowLeft + bw + gap, bottomY, bw, bh)
					.build());

			this.addRenderableWidget(IrisButton.iris$builder(
							CommonComponents.GUI_DONE,
							button -> onClose(),
							buttonTransition)
					.bounds(rowLeft + (bw + gap) * 2, bottomY, bw, bh)
					.build());

			// ---------------------------------------------------------------
			// Top row (y = height - 51):
			//   [Open Shader Pack Folder]   [Shader Pack List / Settings]
			// Each button width = 152, gap = 8, centred around width/2.
			// Total = 312. Left edge = width/2 - 156.
			// ---------------------------------------------------------------
			int topBw = 152;
			int topGap = 8;
			int topY = this.height - 51;
			int topRowLeft = this.width / 2 - (topBw * 2 + topGap) / 2;

			this.openFolderButton = IrisButton.iris$builder(
							Component.translatable("options.iris.openShaderPackFolder"),
							button -> openShaderPackFolder(),
							buttonTransition)
					.bounds(topRowLeft, topY, topBw, bh)
					.build();
			this.addRenderableWidget(openFolderButton);

			this.screenSwitchButton = this.addRenderableWidget(IrisButton.iris$builder(
							Component.translatable("options.iris.shaderPackList"),
							button -> {
								this.optionMenuOpen = !this.optionMenuOpen;
								this.applyChanges();
								setFocused(shaderPackList.getFocused());
								this.init();
							},
							buttonTransition)
					.bounds(topRowLeft + topBw + topGap, topY, topBw, bh)
					.build());

			refreshScreenSwitchButton();
		}

		// Show/hide button (only in-world)
		if (inWorld) {
			Component showOrHide = this.guiHidden
					? Component.translatable("options.iris.gui.show")
					: Component.translatable("options.iris.gui.hide");

			// Place it just past the right end of the top row, or fall back to near the right edge
			int showHideX = Math.min(this.width - 24, this.width / 2 + 156 + 8);

			this.showHideButton = new OldImageButton(
					showHideX, this.height - 39,
					20, 20,
					this.guiHidden ? 20 : 0, 146, 20,
					GuiUtil.IRIS_WIDGETS_TEX,
					256, 256,
					button -> {
						this.guiHidden = !this.guiHidden;
						this.init();
					},
					showOrHide);

			showHideButton.setTooltip(Tooltip.create(showOrHide));
			showHideButton.setTooltipDelay(Duration.ofSeconds(10));
			this.addRenderableWidget(showHideButton);
		}

		this.hoveredElement = null;
		this.hoveredElementCommentTimer = 0;
	}

	// =========================================================================
	// Pack / navigation helpers
	// =========================================================================

	public void refreshForChangedPack() {
		if (Iris.getCurrentPack().isPresent()) {
			ShaderPack currentPack = Iris.getCurrentPack().get();
			this.navigation = new NavigationController(currentPack.getMenuContainer());
			if (this.shaderOptionList != null) {
				this.shaderOptionList.applyShaderPack(currentPack);
				this.shaderOptionList.rebuild();
			}
		} else {
			this.navigation = null;
		}
		refreshScreenSwitchButton();
	}

	public void refreshScreenSwitchButton() {
		if (this.screenSwitchButton != null && this.shaderPackList != null) {
			this.screenSwitchButton.setMessage(
					optionMenuOpen
							? Component.translatable("options.iris.shaderPackList")
							: Component.translatable("options.iris.shaderPackSettings"));
			this.screenSwitchButton.active =
					optionMenuOpen || shaderPackList.getTopButtonRow().shadersEnabled;
		}
	}

	// =========================================================================
	// Blur
	// =========================================================================

	private void processFixedBlur(float tick) {
		if (client.gameRenderer instanceof IrisGameRenderer irisGameRenderer) {
			PostChain blurEffect = irisGameRenderer.iris$getBlurEffect();
			float g = Math.min(
					(float) this.minecraft.options.getMenuBackgroundBlurriness(),
					this.blurTransition.getAsFloat());
			if (blurEffect != null && g >= 1.0F) {
				blurEffect.setUniform("Radius", g);
				blurEffect.process(tick);
			}
		} else {
			Iris.logger.warn("GameRenderer is not an instance of IrisGameRenderer! Mixin failed.");
		}
	}

	@Override
	protected void renderBlurredBackground(float pScreen0) {
		processFixedBlur(pScreen0);
		this.minecraft.getMainRenderTarget().bindWrite(false);
	}

	// =========================================================================
	// Tick & input
	// =========================================================================

	@Override
	public void tick() {
		super.tick();
		if (this.notificationDialogTimer > 0) this.notificationDialogTimer--;
		if (this.hoveredElement != null) this.hoveredElementCommentTimer++;
		else this.hoveredElementCommentTimer = 0;
	}

	@Override
	public boolean mouseClicked(double d, double e, int i) {
		return super.mouseClicked(d, e, i);
	}

	@Override
	public boolean keyPressed(int key, int j, int k) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (this.guiHidden) {
				this.guiHidden = false;
				this.init();
				return true;
			} else if (this.navigation != null && this.navigation.hasHistory()) {
				this.navigation.back();
				return true;
			} else if (this.optionMenuOpen) {
				this.optionMenuOpen = false;
				this.init();
				return true;
			}
		} else if (key == GLFW.GLFW_KEY_TAB) {
			if (!optionMenuOpen && this.shaderPackList != null) {
				shaderPackList.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
			}
			this.optionMenuOpen = !this.optionMenuOpen;
			this.applyChanges();
			this.init();
			this.setFocused(null);
		} else if (key == GLFW.GLFW_KEY_F1 && this.showHideButton != null) {
			this.guiHidden = !guiHidden;
			this.init();
		}
		return this.guiHidden || super.keyPressed(key, j, k);
	}

	// =========================================================================
	// File drop
	// =========================================================================

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (this.optionMenuOpen) onOptionMenuFilesDrop(paths);
		else onPackListFilesDrop(paths);
	}

	public void onPackListFilesDrop(List<Path> paths) {
		List<Path> packs = paths.stream().filter(Iris::isValidShaderpack).toList();

		for (Path pack : packs) {
			String fileName = pack.getFileName().toString();
			try {
				Iris.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
			} catch (FileAlreadyExistsException e) {
				this.notificationDialog = Component.translatable(
								"options.iris.shaderPackSelection.copyErrorAlreadyExists", fileName)
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
				this.notificationDialogTimer = 100;
				if (this.shaderPackList != null) this.shaderPackList.refresh();
				return;
			} catch (IOException e) {
				Iris.logger.warn("Error copying dragged shader pack", e);
				this.notificationDialog = Component.translatable(
								"options.iris.shaderPackSelection.copyError", fileName)
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
				this.notificationDialogTimer = 100;
				if (this.shaderPackList != null) this.shaderPackList.refresh();
				return;
			}
		}

		if (this.shaderPackList != null) this.shaderPackList.refresh();

		if (packs.isEmpty()) {
			if (paths.size() == 1) {
				this.notificationDialog = Component.translatable(
								"options.iris.shaderPackSelection.failedAddSingle",
								paths.getFirst().getFileName().toString())
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			} else {
				this.notificationDialog = Component.translatable(
								"options.iris.shaderPackSelection.failedAdd")
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			}
		} else if (packs.size() == 1) {
			String packName = packs.getFirst().getFileName().toString();
			this.notificationDialog = Component.translatable(
							"options.iris.shaderPackSelection.addedPack", packName)
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
			if (this.shaderPackList != null) this.shaderPackList.select(packName);
		} else {
			this.notificationDialog = Component.translatable(
							"options.iris.shaderPackSelection.addedPacks", packs.size())
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
		}
		this.notificationDialogTimer = 100;
	}

	public void displayNotification(Component component) {
		this.notificationDialog = component;
		this.notificationDialogTimer = 100;
	}

	public void onOptionMenuFilesDrop(List<Path> paths) {
		if (paths.size() != 1) {
			this.notificationDialog = Component.translatable(
							"options.iris.shaderPackOptions.tooManyFiles")
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			this.notificationDialogTimer = 100;
			return;
		}
		this.importPackOptions(paths.getFirst());
	}

	public void importPackOptions(Path settingFile) {
		try (InputStream in = Files.newInputStream(settingFile)) {
			Properties properties = new Properties();
			properties.load(in);
			Iris.queueShaderPackOptionsFromProperties(properties);
			this.notificationDialog = Component.translatable(
							"options.iris.shaderPackOptions.importedSettings",
							settingFile.getFileName().toString())
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
			this.notificationDialogTimer = 100;
			if (this.navigation != null) this.navigation.refresh();
		} catch (Exception e) {
			Iris.logger.error("Error importing shader settings file \"" + settingFile + "\"", e);
			this.notificationDialog = Component.translatable(
							"options.iris.shaderPackOptions.failedImport",
							settingFile.getFileName().toString())
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			this.notificationDialogTimer = 100;
		}
	}

	// =========================================================================
	// Apply / close
	// =========================================================================

	@Override
	public void onClose() {
		if (!dropChanges) applyChanges();
		else discardChanges();

		try {
			if (shaderPackList != null) shaderPackList.close();
		} catch (IOException e) {
			Iris.logger.error("Failed to safely close shaderpack selection!", e);
		}
		this.minecraft.setScreen(parent);
	}

	private void dropChangesAndClose() {
		dropChanges = true;
		onClose();
	}

	public void applyChanges() {
		if (this.shaderPackList == null) return;

		ShaderPackSelectionList.BaseEntry base = this.shaderPackList.getSelected();
		boolean enabled = this.shaderPackList.getTopButtonRow().shadersEnabled;

		// 1. Get the config instance early
		IrisConfig config = Iris.getIrisConfig();
		boolean previousShadersEnabled = config.areShadersEnabled();

		if (enabled != previousShadersEnabled) {
			IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		if (!(base instanceof ShaderPackSelectionList.ShaderPackEntry entry)) {
			// Even if no pack is selected, we should save the 'enabled' state
			saveConfigSilently(config);
			return;
		}

		this.shaderPackList.setApplied(entry);
		String name = entry.getPackName();

		if (!name.equals(Iris.getCurrentPackName())) {
			Iris.clearShaderPackOptionQueue();
		}

		String previousPackName = config.getShaderPackName().orElse(null);

		if (!name.equals(previousPackName)
				|| !Iris.getShaderPackOptionQueue().isEmpty()
				|| Iris.shouldResetShaderPackOptionsOnNextReload()) {
			config.setShaderPackName(name);
			IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		// 2. Finalize the save to iris.properties
		saveConfigSilently(config);

		refreshForChangedPack();
	}

	/** * Helper method to handle the save without cluttering applyChanges
	 */
	private void saveConfigSilently(IrisConfig config) {
		try {
			config.save();
		} catch (java.io.IOException e) {
			// Use your logger or System.err since we're porting
			System.err.println("ForgeOculus: Failed to save configuration! " + e.getMessage());
		}
	}
	private void discardChanges() {
		Iris.clearShaderPackOptionQueue();
	}

	private void openShaderPackFolder() {
		CompletableFuture.runAsync(
				() -> Util.getPlatform().openUri(Iris.getShaderpacksDirectoryManager().getDirectoryUri()));
	}

	// =========================================================================
	// Hover / comment helpers
	// =========================================================================

	public void setElementHoveredStatus(AbstractElementWidget<?> widget, boolean hovered) {
		if (hovered && widget != this.hoveredElement) {
			this.hoveredElement = widget;
			if (widget instanceof CommentedElementWidget<?> cw) {
				this.hoveredElementCommentTitle = cw.getCommentTitle();
				Optional<Component> commentBody = cw.getCommentBody();
				if (commentBody.isEmpty()) {
					this.hoveredElementCommentBody.clear();
				} else {
					String raw = commentBody.get().getString();
					if (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
					List<MutableComponent> split = Arrays.stream(raw.split("\\. [ ]*"))
							.map(Component::literal).toList();
					this.hoveredElementCommentBody = new ArrayList<>();
					for (MutableComponent text : split) {
						this.hoveredElementCommentBody.addAll(
								this.font.split(text, COMMENT_PANEL_WIDTH - 8));
					}
				}
			} else {
				this.hoveredElementCommentTitle = Optional.empty();
				this.hoveredElementCommentBody.clear();
			}
			this.hoveredElementCommentTimer = 0;
		} else if (!hovered && widget == this.hoveredElement) {
			this.hoveredElement = null;
			this.hoveredElementCommentTitle = Optional.empty();
			this.hoveredElementCommentBody.clear();
			this.hoveredElementCommentTimer = 0;
		}
	}

	public boolean isDisplayingComment() {
		return this.hoveredElementCommentTimer > 20
				&& this.hoveredElementCommentTitle.isPresent()
				&& !this.hoveredElementCommentBody.isEmpty();
	}

	public Button getBottomRowOption() {
		return openFolderButton;
	}
}