package com.elementalmc.kingdoms.menu;

import org.kingdoms.commands.KingdomsCommand;
import org.kingdoms.commands.KingdomsCommandHandler;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Hides top-level Kingdoms commands from /k help (they keep working when typed). KingdomsX has a
 * per-command show-in-help-page option, but only in its own config; this keeps the list in one place and
 * re-applies it after every /k reload, which rebuilds the command objects.
 */
public final class HelpCleaner {
    private HelpCleaner() {}

    public static void hide(List<String> names, Logger log) {
        if (names.isEmpty()) return;
        Set<String> wanted = new HashSet<>();
        for (String n : names) wanted.add(n.toLowerCase(Locale.ROOT));
        Field field;
        try {
            field = KingdomsCommand.class.getDeclaredField("showInHelpPage");
            field.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Could not hide commands from /k help on this KingdomsX version: " + e);
            return;
        }
        int hidden = 0;
        for (Map<String, KingdomsCommand> commands : KingdomsCommandHandler.getCommands().values()) {
            for (KingdomsCommand c : commands.values()) {
                if (c == null || !wanted.contains(c.getName().toLowerCase(Locale.ROOT))) continue;
                try {
                    if (field.getBoolean(c)) {
                        field.setBoolean(c, false);
                        hidden++;
                    }
                } catch (IllegalAccessException | RuntimeException e) {
                    log.warning("Could not hide /k " + c.getName() + " from help: " + e);
                }
            }
        }
        if (hidden > 0) log.info("Hid " + hidden + " command entries from /k help (hide-from-help in config.yml).");
    }
}
