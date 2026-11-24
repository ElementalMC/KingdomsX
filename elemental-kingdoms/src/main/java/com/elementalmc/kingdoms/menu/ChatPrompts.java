package com.elementalmc.kingdoms.menu;

import com.elementalmc.kingdoms.ElementalKingdoms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * "Type it in chat" prompts for menu buttons that need a name or an amount (create, invite, ally, deposit...).
 * The next chat message of that player is taken as the answer and never shown in public chat.
 */
public final class ChatPrompts implements Listener {
    private record Pending(Consumer<String> answer, BukkitTask timeout) {}

    private final ElementalKingdoms plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompts(ElementalKingdoms plugin) {
        this.plugin = plugin;
    }

    /** Asks the prompt with this id from config.yml "prompts", then hands "run" with {input} filled in. */
    public void ask(Player player, String id, Consumer<String> run) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("prompts." + id);
        if (section == null) {
            player.sendMessage(plugin.message("unknown-prompt", "prompt", id));
            return;
        }
        String template = section.getString("run", "");
        String message = section.getString("message", "&7Type your answer in chat, or &ccancel&7.");
        player.closeInventory();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        UUID id0 = player.getUniqueId();
        long ticks = Math.max(5, plugin.getConfig().getLong("prompt-timeout-seconds", 60)) * 20L;
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id0) != null) {
                Player p = Bukkit.getPlayer(id0);
                if (p != null) p.sendMessage(plugin.message("prompt-timeout"));
            }
        }, ticks);
        Pending old = pending.put(id0, new Pending(input -> run.accept(template.replace("{input}", input)), timeout));
        if (old != null) old.timeout().cancel();
    }

    // LOWEST so chat formatters and chat channels (including Kingdoms' own) never see the answer.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending p = pending.remove(event.getPlayer().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        p.timeout().cancel();
        String input = event.getMessage().trim();
        Player player = event.getPlayer();
        String cancelWord = plugin.getConfig().getString("prompt-cancel-word", "cancel");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (input.isEmpty() || input.equalsIgnoreCase(cancelWord)) {
                player.sendMessage(plugin.message("prompt-cancelled"));
                return;
            }
            p.answer().accept(input);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pending p = pending.remove(event.getPlayer().getUniqueId());
        if (p != null) p.timeout().cancel();
    }

    public void clear() {
        for (Pending p : pending.values()) p.timeout().cancel();
        pending.clear();
    }
}
