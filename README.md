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
- **Custom actor layer** — record movement tracks and play them back as per-viewer packet NPCs,
  synced to a cinematic, replacing CinematicStudio's unreliable built-in actors (requires ProtocolLib).

### Planned

- Actor equipment, full poses, and hurt/crit animations; keyframe authoring; in-game GUI editor.

## Commands & permissions

| Command | Description |
| --- | --- |
| `/cinaddon play <cinematic> <player\|@a>` | Play a cinematic for player(s) |
| `/cinaddon stop <cinematic> <player\|@a>` | Stop a cinematic for player(s) |
| `/cinaddon actor record <track>` | Start recording your movement into a track |
| `/cinaddon actor stop` | Save the recording |
| `/cinaddon actor list` / `delete <track>` | Manage recorded tracks |
| `/cinaddon scene create <scene> [cinematic]` | Create a scene in your current world |
| `/cinaddon scene addactor <scene> <track> [startTick]` | Add a recorded actor to a scene |
| `/cinaddon scene removeactor <scene> <index>` | Remove an actor from a scene |
| `/cinaddon scene info <scene>` / `list` / `delete <scene>` | Manage scenes |
| `/cinaddon scene play <scene> <player\|@a>` | Play cinematic + actors together |

Aliases: `/csa`, `/cinematicaddon`

### Quick start: a custom actor

```
/cinaddon actor record intro_walk      # walk the path you want the actor to take
/cinaddon actor stop                   # saves the track
/cinaddon scene create intro my_cinematic
/cinaddon scene addactor intro intro_walk
/cinaddon scene play intro <player>    # cinematic camera + your actor, in sync
```

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
