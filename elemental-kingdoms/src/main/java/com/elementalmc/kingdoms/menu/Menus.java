package com.elementalmc.kingdoms.menu;

import com.elementalmc.kingdoms.ElementalKingdoms;
import org.bukkit.entity.Player;
import org.kingdoms.commands.CommandContext;
import org.kingdoms.commands.KingdomsCommand;
import org.kingdoms.commands.KingdomsCommandHandler;
import org.kingdoms.commands.KingdomsParentCommand;
import org.kingdoms.constants.group.Kingdom;
import org.kingdoms.constants.group.Nation;
import org.kingdoms.constants.player.KingdomPlayer;
import org.kingdoms.constants.player.StandardKingdomPermission;
import org.kingdoms.gui.GUIAccessor;
import org.kingdoms.gui.GUIConfig;
import org.kingdoms.gui.InteractiveGUI;
import org.kingdoms.main.Kingdoms;
import org.kingdoms.managers.buildings.structures.NexusManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Opens the menu pages (KingdomsX GUI files under guis/&lt;lang&gt;/menu/) and does what their buttons ask:
 * open a nexus page directly, run a Kingdoms command, or ask for chat input first.
 */
public final class Menus {
    /** Folder of the menu pages inside the Kingdoms GUI folder, and in this jar's guis/ folder. */
    public static final String FOLDER = "menu/";
    /** Pages shipped with the plugin, for tab completion. Server owners can add more files. */
    private static final List<String> PAGES = List.of("main", "start", "members", "land", "buildings", "economy",
            "diplomacy", "war", "nation", "settings");
    public static final List<String> TARGETS = List.of("nexus", "members", "ranks", "settings", "turrets", "structures",
            "misc-upgrades", "champion-upgrades", "shields", "relations", "banners", "resource-points", "chest",
            "nation-nexus", "mailbox", "logs");
    private static final Pattern SAFE_PAGE = Pattern.compile("[a-z0-9_-]+");

    private final ElementalKingdoms plugin;
    private final ChatPrompts prompts;

    public Menus(ElementalKingdoms plugin, ChatPrompts prompts) {
        this.plugin = plugin;
        this.prompts = prompts;
    }

    public List<String> pageNames() {
        List<String> out = new ArrayList<>(PAGES);
        out.add("open");
        out.add("close");
        return out;
    }

    /** The main page for kingdom members, the start page for everyone else. */
    public void openMain(Player player) {
        openPage(player, hasKingdom(player) ? "main" : "start");
    }

    public void openPage(Player player, String page) {
        String name = page.toLowerCase(Locale.ROOT);
        if (!SAFE_PAGE.matcher(name).matches()) {
            player.sendMessage(plugin.message("unknown-page", "page", page));
            return;
        }
        // Kingdom-only pages fall back to the start page instead of showing empty numbers.
        if (!name.equals("start") && !hasKingdom(player)) name = "start";

        InteractiveGUI gui;
        try {
            gui = GUIAccessor.prepare(player, FOLDER + name);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not open menu page " + name + " for " + player.getName() + ": " + e);
            gui = null;
        }
        if (gui == null) {
            player.sendMessage(plugin.message("unknown-page", "page", name));
            return;
        }
        gui.open();
    }

    /**
     * Opens a nexus page directly, without going through the nexus first. KingdomsX checks the kingdom rank
     * permission itself (TURRETS, SETTINGS, EDIT_RANKS, ...) and tells the player when it's missing.
     */
    public void openTarget(Player player, String target) {
        String t = target.toLowerCase(Locale.ROOT);
        Kingdom kingdom = kingdom(player);
        if (kingdom == null) {
            player.sendMessage(plugin.message("no-kingdom"));
            return;
        }
        // Pages that take a "configure before open" callback get a back button to the menu page they came from.
        Consumer<InteractiveGUI> backTo = backButton(player, pageFor(t));
        NexusManager nexus = new NexusManager(kingdom, player);
        switch (t) {
            case "nexus" -> nexus.openNexus();
            case "members" -> nexus.openMembers(0);
            case "ranks" -> nexus.openRanks();
            case "settings" -> nexus.openSettings();
            case "turrets" -> nexus.openTurrets();
            case "structures" -> nexus.openStructures();
            case "misc-upgrades" -> nexus.openMiscUpgrades();
            case "champion-upgrades" -> nexus.openChampionUpgrades();
            case "shields" -> nexus.openShields();
            case "relations" -> nexus.openRelations(backTo);
            case "banners" -> nexus.openBanners(backTo);
            case "resource-points" -> nexus.openRp(backTo);
            case "chest" -> nexus.openNexusChest();
            case "nation-nexus" -> {
                Nation nation = kingdom.getNation();
                if (nation == null) {
                    player.sendMessage(plugin.message("no-nation"));
                    return;
                }
                new NexusManager(nation, player).nationalNexus();
            }
            // Not nexus methods, but registered by KingdomsX as "independent" GUIs (what /k gui opens).
            case "mailbox" -> openIndependent(player, "mails/mailbox", StandardKingdomPermission.READ_MAILS);
            case "logs" -> openIndependent(player, "structures/nexus/logs", StandardKingdomPermission.VIEW_LOGS);
            default -> player.sendMessage(plugin.message("unknown-target", "target", target));
        }
    }

    /**
     * Opens a GUI KingdomsX registered for /k gui. That command does no rank check, so it's done here with
     * the same permission the nexus button uses.
     */
    private void openIndependent(Player player, String key, StandardKingdomPermission permission) {
        KingdomPlayer kp = KingdomPlayer.getKingdomPlayer(player);
        if (kp == null || !kp.hasPermission(permission)) {
            permission.sendDeniedMessage(player);
            return;
        }
        Function<CommandContext, InteractiveGUI> opener = GUIConfig.getIndependentGuis().get(key);
        if (opener == null) {
            player.sendMessage(plugin.message("unknown-target", "target", key));
            return;
        }
        opener.apply(new CommandContext(Kingdoms.get(), menuCommand(), player, new String[0]));
    }

    /** Any registered command works as the context's command; /k menu is ours and always there. */
    private static KingdomsCommand menuCommand() {
        for (KingdomsCommand c : topLevelCommands()) {
            if (c instanceof MenuCommand) return c;
        }
        return topLevelCommands().iterator().next();
    }

    /** Which menu page a nexus page's back button returns to. */
    private static String pageFor(String target) {
        return switch (target) {
            case "relations" -> "diplomacy";
            case "banners" -> "settings";
            case "resource-points" -> "economy";
            default -> "main";
        };
    }

    private Consumer<InteractiveGUI> backButton(Player player, String page) {
        return gui -> {
            try {
                gui.push("back", () -> openPage(player, page));
            } catch (RuntimeException ignored) {
                // that GUI file has no "back" option
            }
        };
    }

    /**
     * Runs a Kingdoms command by its internal name, so menu files work whatever language the player uses.
     * {@code path} is the command path followed by its arguments, e.g. ["unclaim", "all"] or ["invite", "Steve"].
     * Goes through KingdomsCommandHandler.handle, which checks permissions, disabled worlds and cooldowns
     * exactly like typing the command.
     */
    public void runKingdomsCommand(Player player, String[] path) {
        KingdomsCommand command = null;
        int used = 0;
        Collection<KingdomsCommand> level = topLevelCommands();
        while (used < path.length) {
            KingdomsCommand next = byName(level, path[used]);
            if (next == null) break;
            command = next;
            used++;
            if (!(next instanceof KingdomsParentCommand parent)) break;
            level = children(parent);
        }
        if (command == null) {
            player.sendMessage(plugin.message("unknown-command", "command", String.join(" ", path)));
            return;
        }
        String[] args = Arrays.copyOfRange(path, used, path.length);
        KingdomsCommandHandler.handle(new CommandContext(Kingdoms.get(), command, player, args));
    }

    /** Same as {@link #runKingdomsCommand} for a whole command line, e.g. "invite Steve". */
    public void runKingdomsCommand(Player player, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;
        runKingdomsCommand(player, trimmed.split("\\s+"));
    }

    public void prompt(Player player, String id) {
        prompts.ask(player, id, input -> runKingdomsCommand(player, input));
    }

    private static Collection<KingdomsCommand> topLevelCommands() {
        List<KingdomsCommand> out = new ArrayList<>();
        for (Map<String, KingdomsCommand> perLanguage : KingdomsCommandHandler.getCommands().values()) {
            out.addAll(perLanguage.values());
        }
        return out;
    }

    private static Collection<KingdomsCommand> children(KingdomsParentCommand parent) {
        List<KingdomsCommand> out = new ArrayList<>();
        for (Map<String, KingdomsCommand> perLanguage : parent.getChildren().values()) out.addAll(perLanguage.values());
        return out;
    }

    private static KingdomsCommand byName(Collection<KingdomsCommand> commands, String name) {
        for (KingdomsCommand c : commands) {
            if (c != null && c.getName().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    private static Kingdom kingdom(Player player) {
        KingdomPlayer kp = KingdomPlayer.getKingdomPlayer(player);
        return kp == null ? null : kp.getKingdom();
    }

    private static boolean hasKingdom(Player player) {
        return kingdom(player) != null;
    }
}
