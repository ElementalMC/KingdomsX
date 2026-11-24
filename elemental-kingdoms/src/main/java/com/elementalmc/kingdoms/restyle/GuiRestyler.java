package com.elementalmc.kingdoms.restyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Gives the stock KingdomsX GUI files a cleaner, consistent look without touching what they do:
 * <ul>
 *   <li>titles lose the old {@code -=[ ... ]=-} / {@code -=( ... )=-} frames and mixed colours and become plain
 *       dark grey text, like vanilla inventories;</li>
 *   <li>purely decorative glass panes (an option with a blank name) become black, so every GUI shares one
 *       background instead of red/black/blue patterns.</li>
 * </ul>
 * Option keys, conditions, placeholders, slots and lore are left exactly as they are, so every button keeps
 * working. Line based on purpose: the files keep their comments and layout. Each changed file is first copied
 * to the backup folder with the same relative path.
 */
public final class GuiRestyler {
    private static final Pattern TITLE = Pattern.compile("^title:\\s*(['\"]?)(.*)\\1\\s*$");
    private static final Pattern FRAME = Pattern.compile("-=[\\[(]\\s*|\\s*[\\])]=-");
    private static final Pattern COLOR = Pattern.compile("\\{\\$[a-zA-Z0-9_-]+}|\\{#[0-9a-fA-F]{3,6}}|&[0-9a-fk-orA-FK-OR]|&#[0-9a-fA-F]{6}");
    private static final Pattern OPTION_KEY = Pattern.compile("^  ([^\\s#'\"][^:]*|'[^']*'|\"[^\"]*\"):\\s*(#.*)?$");
    private static final Pattern NAME = Pattern.compile("^    name:\\s*(.*)$");
    private static final Pattern MATERIAL = Pattern.compile("^(    material:\\s*)([A-Z_]+_STAINED_GLASS_PANE)\\s*$");
    private static final Pattern BLANK_VALUE = Pattern.compile("^(''|\"\"|' '|\" \"|)\\s*(#.*)?$");
    public static final String BACKGROUND = "BLACK_STAINED_GLASS_PANE";

    private GuiRestyler() {}

    public record Result(int files, int changed, List<String> errors) {}

    /** Restyles every .yml under {@code guis}, except the folders in {@code skip} (relative, like "menu"). */
    public static Result restyle(Path guis, Path backup, List<String> skip) throws IOException {
        int files = 0, changed = 0;
        List<String> errors = new ArrayList<>();
        List<Path> all;
        try (Stream<Path> walk = Files.walk(guis)) {
            all = walk.filter(p -> p.toString().endsWith(".yml") && Files.isRegularFile(p)).toList();
        }
        for (Path file : all) {
            String rel = guis.relativize(file).toString().replace('\\', '/');
            if (skipped(rel, skip)) continue;
            files++;
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                // Keep the file's own line endings (KingdomsX writes CRLF on Windows-made configs).
                String eol = text.contains("\r\n") ? "\r\n" : "\n";
                boolean trailing = text.endsWith("\n");
                List<String> before = List.of(text.split("\r?\n", -1));
                if (trailing) before = before.subList(0, before.size() - 1);
                List<String> after = restyle(before);
                if (after.equals(before)) continue;
                Path copy = backup.resolve(rel);
                Files.createDirectories(copy.getParent());
                if (!Files.exists(copy)) Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES);
                Files.writeString(file, String.join(eol, after) + (trailing ? eol : ""), StandardCharsets.UTF_8);
                changed++;
            } catch (IOException | RuntimeException e) {
                errors.add(rel + ": " + e);
            }
        }
        return new Result(files, changed, errors);
    }

    private static boolean skipped(String rel, List<String> skip) {
        // guis/<lang>/<folder>/...
        int slash = rel.indexOf('/');
        String inLang = slash < 0 ? rel : rel.substring(slash + 1);
        for (String s : skip) if (inLang.startsWith(s + "/")) return true;
        return false;
    }

    public static List<String> restyle(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            String title = restyleTitle(out.get(i));
            if (title != null) out.set(i, title);
        }
        darkenDecoration(out);
        return out;
    }

    /** Returns the new title line, or null if the line isn't a title that needs changing. */
    static String restyleTitle(String line) {
        Matcher m = TITLE.matcher(line);
        if (!m.matches()) return null;
        String raw = m.group(2);
        // "title: chest" style values are not display text.
        if (raw.matches("[a-z_]+")) return null;
        String text = FRAME.matcher(raw).replaceAll("");
        text = COLOR.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ").trim();
        if (text.isEmpty()) return null;
        String result = "title: \"&8" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return result.equals(line) ? null : result;
    }

    /** Top-level options (two spaces in) with a blank name and a glass pane material get the shared background. */
    private static void darkenDecoration(List<String> lines) {
        boolean inOptions = false;
        int start = -1;
        for (int i = 0; i <= lines.size(); i++) {
            String line = i < lines.size() ? lines.get(i) : null;
            if (line != null && !line.isEmpty() && !line.startsWith(" ") && !line.startsWith("#")) {
                if (inOptions && start >= 0) darkenBlock(lines, start, i);
                start = -1;
                inOptions = line.startsWith("options:");
                continue;
            }
            if (line == null) {
                if (inOptions && start >= 0) darkenBlock(lines, start, i);
                break;
            }
            if (inOptions && OPTION_KEY.matcher(line).matches()) {
                if (start >= 0) darkenBlock(lines, start, i);
                start = i;
            }
        }
    }

    private static void darkenBlock(List<String> lines, int from, int to) {
        boolean blankName = false;
        int material = -1;
        for (int i = from + 1; i < to; i++) {
            String l = lines.get(i);
            Matcher n = NAME.matcher(l);
            if (n.matches() && BLANK_VALUE.matcher(n.group(1).trim()).matches()) blankName = true;
            if (MATERIAL.matcher(l).matches()) material = i;
        }
        if (!blankName || material < 0) return;
        Matcher m = MATERIAL.matcher(lines.get(material));
        if (m.matches() && !m.group(2).toUpperCase(Locale.ROOT).equals(BACKGROUND)) {
            lines.set(material, m.group(1) + BACKGROUND);
        }
    }
}
