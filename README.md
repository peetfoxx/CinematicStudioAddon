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
| `/cinaddon spawnactors <track> <player\|@a>` | Spawn a recorded actor for player(s) |
| `/cinaddon state <track> <player\|@a> <state>` | Trigger a named actor state |

Aliases: `/csa`, `/cinematicaddon`

### Customizing an actor

A recorded track is saved to `actortracks/<track>.yml`. Edit the `appearance` section to change how
the actor looks, and add `states` / `timeline` to script changes during playback:

```yaml
appearance:
  entityType: PLAYER          # any Bukkit entity type: ZOMBIE, ARMOR_STAND, ...
  displayName: "Town Guard"   # profile name (players) / name tag (mobs)
  skinTextureValue: "..."     # captured from the recorder; editable
  model: "town_guard"         # ModelEngine model id (optional) — renders a custom model
  equipment:
    mainHand: "itemsadder:weapons:ruby_sword"   # ItemsAdder id
    helmet: "DIAMOND_HELMET"                     # material name
    # (recorded items are stored as base64 here automatically)

states:
  draw_weapon:
    flags: [GLOWING]
    name: "Town Guard (!)"
    equipment:
      mainHand: "ia:weapons:ruby_sword"
    animation: attack          # reserved for ModelEngine

timeline:                      # auto-apply states at track ticks
  40: draw_weapon
```

Item specs accept an ItemsAdder id (`itemsadder:ns:id` / `ia:ns:id`), a material
(`mc:DIAMOND_SWORD` or `DIAMOND_SWORD`), or a captured base64 ItemStack.

States can also be triggered live from a CinematicStudio **COMMAND node**:
`cinaddon state guard %player% draw_weapon` (`console: true`).

#### ModelEngine actors

Set `appearance.model` to a ModelEngine blueprint id to render the actor as a custom model. These
use a real (invisible) base entity, so — unlike packet actors — they are **visible to everyone
nearby, not per-viewer**. A state's `animation:` field plays the named MEG animation on the model.
ModelEngine is an optional soft dependency, hooked via reflection.

### Quick start: a custom actor

```
/cinaddon actor record guard           # walk the path you want the actor to take
/cinaddon actor stop                   # saves the track (with your skin + equipment)
```

Then add a **COMMAND node** in the CinematicStudio editor, at the tick where the actor should appear:

```yaml
type: COMMAND
command: cinaddon spawnactors guard %player%
console: true
```

Now whenever that cinematic plays for someone, the actor spawns for that exact viewer, sequenced by
the cinematic's own timeline. `console: true` means viewers don't need any permission.

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
