package com.elementalmc.kingdoms.restyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Swaps KingdomsX's stock colour macros ({$p} dark green, {$s} gold, ...) in plugins/Kingdoms/config.yml
 * (placeholders.variables) for the ElementalMC palette, so every Kingdoms message and GUI gets the new look
 * at once. A macro is only changed while it still has its stock value, so anything the server owner set
 * by hand is kept. The original file is copied to config.yml.before-theme first.
 */
public final class ThemeInstaller {
    /** macro -> { stock value, new value }, values as they appear between the quotes. */
    public static final Map<String, String[]> PALETTE = new LinkedHashMap<>();

    static {
        PALETTE.put("p", new String[]{"&2", "{#FFD166}"});           // primary: warm gold
        PALETTE.put("sp", new String[]{"&9", "{#7FDBFF}"});          // secondary primary: sky blue
        PALETTE.put("s", new String[]{"&6", "&f"});                  // secondary: white values
        PALETTE.put("e", new String[]{"&c", "{#EF476F}"});           // error
        PALETTE.put("es", new String[]{"&e", "{#FFB3C1}"});          // error secondary
        PALETTE.put("err-sign", new String[]{"&8&l[&4&l!&8&l]", "{#EF476F}⚠"});
        PALETTE.put("info-sign", new String[]{"&8&l[&9&l!&8&l]", "{#7FDBFF}ℹ"});
        PALETTE.put("arrow", new String[]{"&8➔", "&8›"});
        PALETTE.put("point", new String[]{"&8➤", "&8»"});
        PALETTE.put("dot", new String[]{"&7●", "&8•"});
    }

    private static final Pattern MACRO = Pattern.compile("^(\\s{4})([A-Za-z0-9_-]+):(\\s*)(['\"])(.*?)\\4(.*)$");

    private ThemeInstaller() {}

    /** Returns the macros that were changed. Writes nothing when none needs changing. */
    public static List<String> install(Path kingdomsConfig) throws IOException {
        String text = Files.readString(kingdomsConfig, StandardCharsets.UTF_8);
        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        boolean trailing = text.endsWith("\n");
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (trailing) lines.remove(lines.size() - 1);

        int placeholders = indexOfTopLevel(lines, "placeholders");
        if (placeholders < 0) return List.of();
        int variables = -1;
        for (int i = placeholders + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (isTopLevel(l)) break;
            if (l.startsWith("  variables:")) {
                variables = i;
                break;
            }
        }
        if (variables < 0) return List.of();

        List<String> changed = new ArrayList<>();
        for (int i = variables + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (isTopLevel(l) || (l.startsWith("  ") && !l.startsWith("   ") && !l.trim().startsWith("#") && !l.isBlank())) break;
            Matcher m = MACRO.matcher(l);
            if (!m.matches()) continue;
            String[] swap = PALETTE.get(m.group(2));
            if (swap == null || !m.group(5).equals(swap[0])) continue;
            lines.set(i, m.group(1) + m.group(2) + ":" + m.group(3) + m.group(4) + swap[1] + m.group(4) + m.group(6));
            changed.add(m.group(2));
        }
        if (changed.isEmpty()) return changed;

        Path backup = kingdomsConfig.resolveSibling(kingdomsConfig.getFileName() + ".before-theme");
        if (!Files.exists(backup)) Files.copy(kingdomsConfig, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Files.writeString(kingdomsConfig, String.join(eol, lines) + (trailing ? eol : ""), StandardCharsets.UTF_8);
        return changed;
    }

    private static boolean isTopLevel(String line) {
        return !line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#");
    }

    private static int indexOfTopLevel(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(key + ":")) return i;
        return -1;
    }
}
