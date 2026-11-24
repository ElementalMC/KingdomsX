# ElementalKingdoms

ElementalMC's additions for KingdomsX. The KingdomsX core is closed source (this repository only holds its
configs, GUIs and the official addons), so everything here is an addon that runs next to the server's
KingdomsX jar and uses its API.

## What it does (1.0.0)

- **One central menu.** `/k menu`, or just `/k`, opens a tree-style menu that reaches every kingdom feature:

  ```
  Kingdom (kingdom name, level, members, lands, RP, bank, your rank)
  ├─ Members      members list, ranks & permissions, invite, join requests, invite codes, tpa, kick, leave
  ├─ Land         claim, unclaim, auto claim, map, borders, plots, claim list, set home, home, unclaim all
  ├─ Buildings    turrets, structures, champion upgrades, kingdom upgrades
  ├─ Economy      resource points, withdraw RP, bank deposit / withdraw, nexus chest, donate
  ├─ Nexus        the classic nexus GUI
  ├─ Diplomacy    ally, truce, enemy, neutral, relation rules, relation requests, mailbox, top kingdoms
  ├─ War          invade, shields, PvP, outpost events, surrender
  ├─ Nation       create / join, or nation nexus, info, invite, spawn, leave
  ├─ Settings     kingdom settings, flag, colour, rename, tag, lore, language, audit logs, disband
  └─ Home · Close · Map
  ```

  Players without a kingdom get a start page instead: create, invites, join, ask to join, top kingdoms,
  map, language. Buttons that need a name or amount ask for it in chat (type `cancel` to stop).
  Nexus pages open directly (no need to stand at the nexus) and KingdomsX still checks the rank
  permission for each one.
- **A cleaner look for the stock GUIs (one time, on the first start).** Titles lose the old
  `-=[ Title ]=-` frames and become plain dark text, and decorative glass panes share one black background.
  Only looks change; option keys, conditions and slots are untouched. Originals go to
  `plugins/ElementalKingdoms/gui-backups/<date>/`.
- **Modern colours.** KingdomsX's stock colour macros (`{$p}` dark green, `{$s}` gold, ...) in
  `plugins/Kingdoms/config.yml` become the ElementalMC palette, so messages and GUIs match the menu.
  Only macros still at their stock value are changed. Original: `config.yml.before-theme`.
- **A shorter `/k help`.** Commands the menu covers (`/k gui`, `/k invites`, `/k joinrequests`, ...) are
  hidden from the help list (`hide-from-help` in config.yml). They keep working when typed.

## Menu files

The pages are normal KingdomsX GUI files, written to `plugins/Kingdoms/guis/<lang>/menu/*.yml`, so they
can be restyled like any other Kingdoms GUI. A button runs commands as the player:

| Command | Does |
|---|---|
| `k menu` | main page (or the start page without a kingdom) |
| `k menu page <name>` | `menu/<name>.yml` |
| `k menu open <target>` | nexus, members, ranks, settings, turrets, structures, misc-upgrades, champion-upgrades, shields, relations, banners, resource-points, chest, nation-nexus, mailbox, logs |
| `k menu run <command> [args]` | any Kingdoms command by internal name (works in every language), e.g. `k menu run unclaim all` |
| `k menu prompt <id>` | asks in chat, then runs the prompt from config.yml |
| `k menu close` | closes the menu |

## Building

KingdomsX isn't on Maven Central in the version the server runs, so install the server's jar once:

```
mvn install:install-file -Dfile=KingdomsX-1.17.28.jar -DgroupId=com.github.cryptomorin -DartifactId=kingdoms -Dversion=1.17.28 -Dpackaging=jar
mvn package
```

The jar is `target/ElementalKingdoms-1.0.0.jar`. Put it in `plugins/` next to KingdomsX and restart.

## Undo

- Menu: remove the jar. `/k` shows the help again.
- GUI restyle: copy `plugins/ElementalKingdoms/gui-backups/<date>/` back over `plugins/Kingdoms/guis/`.
- Colours: rename `plugins/Kingdoms/config.yml.before-theme` back to `config.yml`.
- Set `restyle-kingdoms-guis: false` and `modern-colors: false` before the first start to skip both.
