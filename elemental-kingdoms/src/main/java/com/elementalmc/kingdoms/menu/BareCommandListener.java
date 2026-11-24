package com.elementalmc.kingdoms.menu;

import com.elementalmc.kingdoms.ElementalKingdoms;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.kingdoms.commands.KingdomsCommandHandler;

import java.util.Locale;
import java.util.Set;

/** Opens the menu when a player types just the Kingdoms main command (/k, /kingdoms, ...) with nothing after it. */
public final class BareCommandListener implements Listener {
    private final ElementalKingdoms plugin;

    public BareCommandListener(ElementalKingdoms plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("open-on-bare-command", true)) return;
        String message = event.getMessage().trim();
        if (message.length() < 2 || message.indexOf(' ') >= 0) return;
        String label = message.substring(1).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':'); // kingdoms:k
        if (colon >= 0) label = label.substring(colon + 1);
        Set<String> names;
        try {
            names = KingdomsCommandHandler.getMainCommandNames(true);
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(label)) {
                event.setCancelled(true);
                plugin.menus().openMain(event.getPlayer());
                return;
            }
        }
    }
}
