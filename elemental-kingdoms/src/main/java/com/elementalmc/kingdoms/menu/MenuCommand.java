package com.elementalmc.kingdoms.menu;

import org.bukkit.entity.Player;
import org.kingdoms.commands.CommandContext;
import org.kingdoms.commands.CommandResult;
import org.kingdoms.commands.CommandTabContext;
import org.kingdoms.commands.KingdomsCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /k menu [page &lt;name&gt; | open &lt;target&gt; | run &lt;command...&gt; | prompt &lt;id&gt; | close].
 * Players normally only type /k menu (or just /k); the other forms are what the menu buttons run.
 */
public final class MenuCommand extends KingdomsCommand {
    private final Menus menus;

    public MenuCommand(Menus menus) {
        super("menu", true); // true = PermissionDefault.TRUE (kingdoms.command.menu)
        this.menus = menus;
    }

    @Override
    public CommandResult execute(CommandContext context) {
        if (!context.assertPlayer()) return CommandResult.FAILED;
        Player player = context.senderAsPlayer();
        String[] args = context.getArgs();
        if (args.length == 0) {
            menus.openMain(player);
            return CommandResult.SUCCESS;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "page" -> {
                if (rest.length == 0) menus.openMain(player);
                else menus.openPage(player, rest[0]);
            }
            case "open" -> {
                if (rest.length == 0) menus.openMain(player);
                else menus.openTarget(player, rest[0]);
            }
            case "run" -> {
                if (rest.length > 0) menus.runKingdomsCommand(player, rest);
            }
            case "prompt" -> {
                if (rest.length > 0) menus.prompt(player, rest[0]);
            }
            case "close" -> player.closeInventory();
            // "/k menu members" is a handy shortcut for "/k menu page members"
            default -> menus.openPage(player, sub);
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> tabComplete(CommandTabContext context) {
        String[] args = context.getArgs();
        if (args.length <= 1) return filter(menus.pageNames(), args.length == 0 ? "" : args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) return filter(Menus.TARGETS, args[1]);
        return emptyTab();
    }

    private static List<String> filter(List<String> options, String start) {
        String s = start.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(s)) out.add(o);
        return out;
    }
}
