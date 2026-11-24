package com.elementalmc.kingdoms;

import com.elementalmc.kingdoms.menu.BareCommandListener;
import com.elementalmc.kingdoms.menu.ChatPrompts;
import com.elementalmc.kingdoms.menu.MenuCommand;
import com.elementalmc.kingdoms.menu.Menus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.kingdoms.addons.Addon;
import org.kingdoms.commands.KingdomsCommand;
import org.kingdoms.commands.KingdomsCommandHandler;
import org.kingdoms.gui.GUIConfig;
import com.elementalmc.kingdoms.restyle.GuiRestyler;
import com.elementalmc.kingdoms.restyle.ThemeInstaller;
import com.elementalmc.kingdoms.menu.HelpCleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import java.io.File;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ElementalMC additions for KingdomsX. Right now: one central /k menu that reaches every kingdom feature.
 * Registered as a Kingdoms addon so /k reload reloads it too, following the pattern of the official addons.
 */
public final class ElementalKingdoms extends JavaPlugin implements Addon {
    private static ElementalKingdoms instance;

    private Menus menus;
    private ChatPrompts prompts;
    private boolean enabled;

    public ElementalKingdoms() {
        instance = this;
    }

    public static ElementalKingdoms get() {
        return instance;
    }

    @Override
    public void onEnable() {
        if (!isKingdomsEnabled()) {
            getLogger().severe("Kingdoms plugin didn't load correctly. Disabling...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        reloadConfig();

        prompts = new ChatPrompts(this);
        menus = new Menus(this, prompts);
        Bukkit.getPluginManager().registerEvents(prompts, this);
        Bukkit.getPluginManager().registerEvents(new BareCommandListener(this), this);
        registerKingdomsCommand();

        // A plugin manager reload leaves the old instance in Kingdoms' addon registry.
        try {
            Addon stale = org.kingdoms.addons.AddonRegistry.getAddon(getAddonName());
            if (stale != null && stale != this) org.kingdoms.addons.AddonRegistry.unregister(stale);
        } catch (RuntimeException e) {
            getLogger().warning("Could not clear an old addon registration: " + e);
        }
        registerAddon();
        // Registers guis/menu/*.yml from this jar and writes them to plugins/Kingdoms/guis/<lang>/menu/.
        GUIConfig.loadInternalGUIs(this);
        enabled = true;
        // After startup, once Kingdoms has written all of its GUI files.
        Bukkit.getScheduler().runTask(this, () -> {
            restyleKingdomsGuisOnce();
            HelpCleaner.hide(getConfig().getStringList("hide-from-help"), getLogger());
        });
    }

    @Override
    public void onDisable() {
        if (!enabled) return;
        if (prompts != null) prompts.clear();
        signalDisable();
        unregisterKingdomsCommand();
        try {
            if (org.kingdoms.addons.AddonRegistry.getAddon(getAddonName()) == this) org.kingdoms.addons.AddonRegistry.unregister(this);
        } catch (RuntimeException e) {
            getLogger().warning("Could not unregister the addon from Kingdoms: " + e);
        }
        enabled = false;
    }

    /** Called by Kingdoms' own /k reload, which also rebuilds its command tree. */
    @Override
    public void reloadAddon() {
        if (!enabled) return;
        reloadConfig();
        registerKingdomsCommand();
        HelpCleaner.hide(getConfig().getStringList("hide-from-help"), getLogger());
    }

    /**
     * One time (or again after deleting the marker file): restyle the stock KingdomsX GUI files. See
     * {@link GuiRestyler}. Every changed file is backed up first, so it can be undone by copying them back.
     */
    private void restyleKingdomsGuisOnce() {
        if (!getConfig().getBoolean("restyle-kingdoms-guis", true)) return;
        Path marker = getDataFolder().toPath().resolve("guis-restyled.txt");
        if (Files.exists(marker)) return;
        org.bukkit.plugin.Plugin kingdoms = Bukkit.getPluginManager().getPlugin("Kingdoms");
        if (kingdoms == null) return;
        Path guis = kingdoms.getDataFolder().toPath().resolve("guis");
        if (!Files.isDirectory(guis)) return;
        if (getConfig().getBoolean("modern-colors", true)) {
            try {
                List<String> macros = ThemeInstaller.install(kingdoms.getDataFolder().toPath().resolve("config.yml"));
                if (!macros.isEmpty()) getLogger().info("Switched the Kingdoms colour macros " + macros
                        + " to the ElementalMC palette (original: plugins/Kingdoms/config.yml.before-theme)."
                        + " Takes effect after /k reload or a restart.");
            } catch (IOException | RuntimeException e) {
                getLogger().warning("Could not update the Kingdoms colour macros: " + e);
            }
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path backup = getDataFolder().toPath().resolve("gui-backups").resolve(stamp);
        try {
            GuiRestyler.Result result = GuiRestyler.restyle(guis, backup, List.of("menu"));
            for (String error : result.errors()) getLogger().warning("Could not restyle " + error);
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Restyled " + result.changed() + " of " + result.files()
                    + " KingdomsX GUI files on " + stamp + ".\nOriginals: gui-backups/" + stamp
                    + "\nDelete this file and restart to restyle again (for example after a KingdomsX update"
                    + " adds new GUI files).\n", StandardCharsets.UTF_8);
            getLogger().info("Restyled " + result.changed() + " KingdomsX GUI files (originals in gui-backups/" + stamp + ").");
            if (result.changed() > 0) GUIConfig.reloadAll();
        } catch (IOException | RuntimeException | LinkageError e) {
            getLogger().warning("Could not restyle the KingdomsX GUIs: " + e);
        }
    }

    private void registerKingdomsCommand() {
        unregisterKingdomsCommand();
        try {
            new MenuCommand(menus);
        } catch (RuntimeException | LinkageError e) {
            getLogger().warning("Could not register /k menu: " + e);
        }
    }

    /**
     * Removes every /k menu entry left by this plugin, including one from an older copy loaded by a plugin
     * manager reload. Matched by class name because a reloaded plugin has a new class loader.
     */
    private void unregisterKingdomsCommand() {
        String ours = MenuCommand.class.getName();
        Set<KingdomsCommand> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (Map<String, KingdomsCommand> commands : KingdomsCommandHandler.getCommands().values()) {
                commands.values().removeIf(c -> {
                    if (c == null || !c.getClass().getName().equals(ours)) return false;
                    removed.add(c);
                    return true;
                });
            }
        } catch (RuntimeException e) {
            getLogger().warning("Could not remove the old /k menu command: " + e);
        }
        for (KingdomsCommand c : removed) {
            try {
                c.unregisterPermissions();
            } catch (RuntimeException ignored) {
                // permissions already gone
            }
        }
    }

    public Menus menus() {
        return menus;
    }

    /** A message from config.yml "messages", with &amp; colours and {key} replacements. */
    public String message(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public String getAddonName() {
        return "elemental-kingdoms";
    }

    @Override
    public File getFile() {
        return super.getFile();
    }
}
