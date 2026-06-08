# CinematicStudioAddon

An open-source companion plugin for [CinematicStudio](https://lonedev.gitbook.io/cinematicstudio) by LoneDev.

> **This is not a fork, crack, or modification of CinematicStudio.** It contains none of
> CinematicStudio's code. It drives the plugin purely through its public `/cin` command, so you
> must own a legitimate copy of CinematicStudio for this addon to do anything.

## Why

CinematicStudio binds cinematic playback to the *command sender*. That makes it awkward to start a
cinematic **for a specific player from the console**, a command block, or another plugin. This addon
fixes that: it dispatches CinematicStudio's command *as the target player*, so playback always lands
on the intended viewer no matter who triggered it.

## Features

- **`/cinaddon play <cinematic> <player>`** — play a cinematic for a specific player, from any
  sender (console / command block / player).
- **`/cinaddon stop <cinematic> <player>`** — stop a cinematic for a specific player.
- **`@a` / `--all`** as the target plays/stops for every online player.
- Tab completion for cinematic names (read best-effort from CinematicStudio's data folder) and
  online players.

### Planned

- A self-contained actor layer (Citizens / packet NPCs) synced to cinematic playback, to replace
  CinematicStudio's unreliable built-in actors.

## Commands & permissions

| Command | Description |
| --- | --- |
| `/cinaddon play <cinematic> <player\|@a>` | Play a cinematic for player(s) |
| `/cinaddon stop <cinematic> <player\|@a>` | Stop a cinematic for player(s) |

Aliases: `/csa`, `/cinematicaddon`

| Permission | Default | Description |
| --- | --- | --- |
| `cinematicstudioaddon.admin` | op | Use the addon commands |

## Requirements

- A server running Paper/Spigot (API 1.13+)
- [CinematicStudio](https://lonedev.gitbook.io/cinematicstudio) installed and enabled (soft
  dependency — the addon loads without it but commands will report an error until it is present)

## Building

This is a standalone Maven project (it does **not** depend on the HyperPlugins suite or HyperCore;
the Kotlin runtime is shaded in and relocated).

```bash
mvn clean package
```

The built jar is at `target/CinematicStudioAddon-1.0-SNAPSHOT.jar`.

## License

[MIT](LICENSE) © peetfoxx
