package net.irisshaders.iris;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cross-launcher Oculus installer.
 *
 * Supports: Official (Mojang/Microsoft) Launcher, Modrinth App, CurseForge App,
 * Prism Launcher, MultiMC, GDLauncher (legacy + Carbon best-effort), ATLauncher, Technic.
 *
 * NOTE: Requires Jankson on the classpath (implementation 'blue.endless:jankson:1.2.3').
 * Jankson tolerates JSON5-style quirks (comments, trailing commas) that occasionally show
 * up in hand-edited launcher_profiles.json files, which plain Gson chokes on.
 * Paths below reflect defaults as of mid-2025/2026; portable installs of Prism/MultiMC/
 * ATLauncher/GDLauncher can live anywhere, so a manual "Browse..." fallback is included.
 */
public class LaunchWarn {

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // Optional tweaks
            JFrame.setDefaultLookAndFeelDecorated(false);
            JDialog.setDefaultLookAndFeelDecorated(false);

        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ForgeOculus Installer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(480, 340);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout(10, 10));

            List<LauncherProfile> profiles = LauncherDetector.detectAll();

            DefaultComboBoxModel<LauncherProfile> model = new DefaultComboBoxModel<>();
            for (LauncherProfile p : profiles) model.addElement(p);

            JComboBox<LauncherProfile> profileBox = new JComboBox<>(model);

            JPanel top = new JPanel(new BorderLayout(5, 5));
            top.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
            top.add(new JLabel("Select your installation profile:"), BorderLayout.NORTH);
            top.add(profileBox, BorderLayout.CENTER);

            JLabel statusLabel = new JLabel(" ");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

            JButton browseBtn = new JButton("Browse for mods folder…");
            JButton installBtn = new JButton("Install Mod");
            JButton openFolderBtn = new JButton("Open Mods Folder");
            installBtn.setEnabled(!profiles.isEmpty());

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonRow.add(browseBtn);
            buttonRow.add(openFolderBtn);
            buttonRow.add(installBtn);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
            bottom.add(statusLabel, BorderLayout.NORTH);
            bottom.add(buttonRow, BorderLayout.SOUTH);

            if (profiles.isEmpty()) {
                statusLabel.setText("No launcher installations auto-detected. Use Browse to select a mods folder manually.");
            }

            browseBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select target 'mods' folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setFileFilter(new FileFilter() {
                    public boolean accept(File f) { return f.isDirectory(); }
                    public String getDescription() { return "Folders"; }
                });
                int result = chooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File chosen = chooser.getSelectedFile();
                    LauncherProfile custom = new LauncherProfile("Custom", chosen.getName(), chosen);
                    model.addElement(custom);
                    model.setSelectedItem(custom);
                    installBtn.setEnabled(true);
                }
            });

            openFolderBtn.addActionListener(e -> {
                LauncherProfile selected = (LauncherProfile) profileBox.getSelectedItem();
                if (selected == null) return;

                File modsDir = selected.modsDir;

                try {
                    if (!modsDir.exists()) {
                        modsDir.mkdirs();
                    }

                    if (!Desktop.isDesktopSupported()) {
                        JOptionPane.showMessageDialog(frame,
                                "Desktop operations not supported on this system.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    Desktop.getDesktop().open(modsDir);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not open folder:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            installBtn.addActionListener(e -> {
                LauncherProfile selected = (LauncherProfile) profileBox.getSelectedItem();
                if (selected == null) return;

                File jarFile = locateOculusJar();
                if (jarFile == null) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not find the Oculus jar next to the installer. Aborting.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    if (!selected.modsDir.exists() && !selected.modsDir.mkdirs()) {
                        throw new IOException("Could not create mods directory: " + selected.modsDir);
                    }
                    File dest = new File(selected.modsDir, jarFile.getName());
                    Files.copy(jarFile.toPath(), dest.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    statusLabel.setText("Installed to: " + selected.modsDir.getAbsolutePath());
                    JOptionPane.showMessageDialog(frame, "Installed to:\n" + dest.getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Install failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            frame.add(top, BorderLayout.CENTER);
            frame.add(bottom, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
    }

    /** Finds the Oculus jar sitting alongside the running installer jar. */
    private static File locateOculusJar() {
        try {
            File installerJar = new File(LaunchWarn.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = installerJar.getParentFile();
            if (dir == null) return null;
            File[] matches = dir.listFiles((d, name) ->
                    name.toLowerCase(Locale.ROOT).contains("oculus") && name.endsWith(".jar")
                            && !name.equals(installerJar.getName()));
            return (matches != null && matches.length > 0) ? matches[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------

    public static class LauncherProfile {
        final String launcher;
        final String name;
        final File modsDir;

        LauncherProfile(String launcher, String name, File modsDir) {
            this.launcher = launcher;
            this.name = name;
            this.modsDir = modsDir;
        }

        @Override
        public String toString() {
            return "[" + launcher + "] " + name;
        }
    }

    // ---------------------------------------------------------------------

    static class LauncherDetector {

        static List<LauncherProfile> detectAll() {
            List<LauncherProfile> all = new ArrayList<>();
            safeAdd(all, LaunchWarn.LauncherDetector::detectOfficial);
            safeAdd(all, LaunchWarn.LauncherDetector::detectModrinth);
            safeAdd(all, LaunchWarn.LauncherDetector::detectCurseForge);
            safeAdd(all, () -> detectMMCStyle("PrismLauncher", "Prism Launcher"));
            safeAdd(all, () -> detectMMCStyle("MultiMC", "MultiMC"));
            safeAdd(all, () -> detectMMCStyle("PolyMC", "PolyMC"));
            safeAdd(all, LaunchWarn.LauncherDetector::detectGDLauncher);
            safeAdd(all, LaunchWarn.LauncherDetector::detectATLauncher);
            safeAdd(all, LaunchWarn.LauncherDetector::detectTechnic);
            return all;
        }

        private interface Supplier<T> { T get(); }

        private static void safeAdd(List<LauncherProfile> out, Supplier<List<LauncherProfile>> fn) {
            try {
                List<LauncherProfile> result = fn.get();
                if (result != null) out.addAll(result);
            } catch (Exception ignored) {
                // A single launcher's odd/missing config shouldn't kill detection for the rest.
            }
        }

        // ---- Cross-platform base dirs ----

        private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        private static final boolean WINDOWS = OS.contains("win");
        private static final boolean MAC = OS.contains("mac");
        private static final String HOME = System.getProperty("user.home");

        /** e.g. %APPDATA% on Windows, ~/Library/Application Support on Mac, ~/.config on Linux */
        private static File appDataRoaming() {
            if (WINDOWS) {
                String appData = System.getenv("APPDATA");
                return new File(appData != null ? appData : HOME);
            } else if (MAC) {
                return new File(HOME, "Library/Application Support");
            } else {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                return new File(xdg != null ? xdg : HOME + "/.config");
            }
        }

        /** e.g. ~/.local/share on Linux, same as roaming on Windows/Mac */
        private static File appDataLocal() {
            if (WINDOWS) {
                String local = System.getenv("LOCALAPPDATA");
                return new File(local != null ? local : HOME);
            } else if (MAC) {
                return new File(HOME, "Library/Application Support");
            } else {
                String xdg = System.getenv("XDG_DATA_HOME");
                return new File(xdg != null ? xdg : HOME + "/.local/share");
            }
        }

        // ---- 1. Official Launcher ----

        private static List<LauncherProfile> detectOfficial() {
            List<LauncherProfile> out = new ArrayList<>();
            File mcDir;
            if (WINDOWS) mcDir = new File(appDataRoaming(), ".minecraft");
            else if (MAC) mcDir = new File(HOME, "Library/Application Support/minecraft");
            else mcDir = new File(HOME, ".minecraft");

            if (!mcDir.isDirectory()) return out;

            // Shared mods folder used by default (Forge/Fabric versions all share this
            // unless a profile sets a custom gameDir).
            File sharedMods = new File(mcDir, "mods");
            out.add(new LauncherProfile("Official Launcher", "Default (.minecraft/mods)", sharedMods));

            // Custom gameDir profiles from launcher_profiles.json
            File profilesJson = new File(mcDir, "launcher_profiles.json");
            if (profilesJson.isFile()) {
                try {
                    JsonObject root = readJson(profilesJson);
                    JsonElement profilesEl = root.get("profiles");
                    if (profilesEl instanceof JsonObject) {
                        JsonObject profs = (JsonObject) profilesEl;
                        for (String key : profs.keySet()) {
                            JsonElement pEl = profs.get(key);
                            if (!(pEl instanceof JsonObject)) continue;
                            JsonObject p = (JsonObject) pEl;
                            String gameDir = p.get(String.class, "gameDir");
                            if (gameDir != null) {
                                String name = p.get(String.class, "name");
                                out.add(new LauncherProfile("Official Launcher (custom dir)",
                                        name != null ? name : key, new File(gameDir, "mods")));
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
            return out;
        }

        // ---- 2. Modrinth App (Theseus) ----

        private static List<LauncherProfile> detectModrinth() {
            List<LauncherProfile> out = new ArrayList<>();
            File base = new File(appDataRoaming(), "com.modrinth.theseus");
            if (!base.isDirectory()) base = new File(appDataRoaming(), "ModrinthApp");
            File profilesDir = new File(base, "profiles");
            if (!profilesDir.isDirectory()) return out;

            File[] dirs = profilesDir.listFiles(File::isDirectory);
            if (dirs == null) return out;
            for (File dir : dirs) {
                // Modrinth profiles keep mods directly under the profile root (no .minecraft subfolder)
                out.add(new LauncherProfile("Modrinth App", dir.getName(), new File(dir, "mods")));
            }
            return out;
        }

        // ---- 3. CurseForge App ----

        private static List<LauncherProfile> detectCurseForge() {
            List<LauncherProfile> out = new ArrayList<>();
            List<File> candidates = new ArrayList<>();
            if (WINDOWS) {
                candidates.add(new File(HOME, "curseforge/minecraft/Instances"));
                candidates.add(new File(HOME, "Documents/Curse/Minecraft/Instances"));
            } else if (MAC) {
                candidates.add(new File(HOME, "Documents/curseforge/minecraft/Instances"));
            } else {
                candidates.add(new File(HOME, "curseforge/minecraft/Instances"));
            }

            for (File instancesDir : candidates) {
                if (!instancesDir.isDirectory()) continue;
                File[] dirs = instancesDir.listFiles(File::isDirectory);
                if (dirs == null) continue;
                for (File dir : dirs) {
                    out.add(new LauncherProfile("CurseForge App", dir.getName(), new File(dir, "mods")));
                }
            }
            return out;
        }

        // ---- 4. Prism / MultiMC / PolyMC (share the same instance layout) ----

        private static List<LauncherProfile> detectMMCStyle(String folderName, String displayName) {
            List<LauncherProfile> out = new ArrayList<>();
            List<File> candidates = new ArrayList<>();
            candidates.add(new File(appDataRoaming(), folderName));
            candidates.add(new File(appDataLocal(), folderName));
            if (!WINDOWS && !MAC) {
                candidates.add(new File(HOME, "." + folderName.toLowerCase(Locale.ROOT)));
            }

            for (File base : candidates) {
                File instancesDir = new File(base, "instances");
                if (!instancesDir.isDirectory()) continue;
                File[] dirs = instancesDir.listFiles(File::isDirectory);
                if (dirs == null) continue;
                for (File dir : dirs) {
                    File mcSub = new File(dir, ".minecraft");
                    File modsDir = mcSub.isDirectory() ? new File(mcSub, "mods") : new File(dir, "mods");
                    out.add(new LauncherProfile(displayName, dir.getName(), modsDir));
                }
            }
            return out;
        }

        // ---- 5. GDLauncher ----

        private static List<LauncherProfile> detectGDLauncher() {
            List<LauncherProfile> out = new ArrayList<>();

            // Legacy Electron-based GDLauncher
            File legacy = new File(HOME, ".gdlauncher/instances");
            addGdInstances(out, legacy, "GDLauncher");

            // GDLauncher Carbon (Rust/Tauri) — layout has changed across versions;
            // this covers the common default. Falls back silently if not present.
            File carbon = new File(appDataRoaming(), "gdlauncher_carbon/instances");
            addGdInstances(out, carbon, "GDLauncher Carbon");

            return out;
        }

        private static void addGdInstances(List<LauncherProfile> out, File instancesDir, String label) {
            if (!instancesDir.isDirectory()) return;
            File[] dirs = instancesDir.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                File mcSub = new File(dir, ".minecraft");
                File modsDir = mcSub.isDirectory() ? new File(mcSub, "mods") : new File(dir, "mods");
                out.add(new LauncherProfile(label, dir.getName(), modsDir));
            }
        }

        // ---- 6. ATLauncher ----

        private static List<LauncherProfile> detectATLauncher() {
            List<LauncherProfile> out = new ArrayList<>();
            List<File> candidates = new ArrayList<>();
            candidates.add(new File(HOME, "ATLauncher/instances"));
            candidates.add(new File(appDataRoaming(), "ATLauncher/instances"));

            for (File instancesDir : candidates) {
                if (!instancesDir.isDirectory()) continue;
                File[] dirs = instancesDir.listFiles(File::isDirectory);
                if (dirs == null) continue;
                for (File dir : dirs) {
                    out.add(new LauncherProfile("ATLauncher", dir.getName(), new File(dir, "mods")));
                }
            }
            return out;
        }

        // ---- 7. Technic Launcher ----

        private static List<LauncherProfile> detectTechnic() {
            List<LauncherProfile> out = new ArrayList<>();
            File base;
            if (WINDOWS) base = new File(appDataRoaming(), "Technic");
            else if (MAC) base = new File(HOME, "Library/Application Support/technic");
            else base = new File(HOME, ".technic");

            File modpacksDir = new File(base, "modpacks");
            if (!modpacksDir.isDirectory()) return out;
            File[] dirs = modpacksDir.listFiles(File::isDirectory);
            if (dirs == null) return out;
            for (File dir : dirs) {
                out.add(new LauncherProfile("Technic Launcher", dir.getName(), new File(dir, "mods")));
            }
            return out;
        }

        // ---- helpers ----

        private static JsonObject readJson(File file) throws IOException {
            // Jankson's load() throws a checked SyntaxError on malformed JSON5; the caller
            // wraps this in a broad try/catch so a single bad file doesn't kill detection.
            Jankson jankson = Jankson.builder().build();
            try {
                return jankson.load(file);
            } catch (Exception e) {
                throw new IOException("Failed to parse " + file, e);
            }
        }
    }
}