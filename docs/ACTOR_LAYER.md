# Actor Layer — Design

Replacement for CinematicStudio's unreliable built-in actors: a self-contained, per-viewer actor
system that we fully own.

## Decisions

| Topic | Choice |
| --- | --- |
| Backend | **Packet NPCs via ProtocolLib** — per-viewer client-side fake entities |
| Visibility | **Per-viewer only** — actors are sent only to the target viewer(s) |
| Triggering | **CinematicStudio COMMAND node** — `cinaddon spawnactors <track> %player%` |

## How it ties into a cinematic

There is no CinematicStudio API, so we don't inject actors into its timeline. Instead the cinematic
itself triggers us: add a CinematicStudio **COMMAND node** at the tick where the actor should appear:

```yaml
type: COMMAND
command: cinaddon spawnactors guard %player%
console: true
```

`console: true` (always has permission) + `%player%` (the viewing player) means each viewer gets
their own actor, sequenced by the cinematic's own timeline. There is **no "scene" concept** — one
command node per actor; the cinematic editor does the sequencing.

## Data model

- **`ActorFrame`** — immutable per-tick state: position, yaw/pitch/headYaw, `ActorPose`,
  `ActorFlag`s, one-shot `ActorAnimation`s.
- **`ActorAppearance`** — entity type, display name, player skin (texture+signature), equipment.
  Captured from the recording player and stored on the track.
- **`ActorTrack`** — an animation. `RECORDED` tracks store one frame per tick; `KEYFRAMED` tracks
  store sparse keyframes and interpolate (`frameAt(tick)` handles both; angle-aware lerp + easing).
  Carries the captured `ActorAppearance`.

## Runtime

- **`ActorBackend` / `ActorHandle`** — backend-agnostic spawn/update/animate/despawn. Impl:
  `PacketActorBackend` (`PacketActor`).
- **`ActorSession`** — plays one track for a viewer set, from tick 0. A 1-tick repeating task
  advances the track, spawning on the first frame and despawning at the end. Self-cleans when the
  track ends or all viewers leave.
- **`ActorManager`** — owns tracks + active sessions. `spawn(track, viewers)` starts an
  `ActorSession`. Hooks `PlayerQuitEvent` for cleanup.
- **`ActorRecorder`** — captures a player's per-tick state (+ skin + worn equipment) into a track.

## Packet backend (PacketActor) — notes

Per actor: allocate a unique entity id + UUID. For `PLAYER` actors send `PLAYER_INFO` (ADD_PLAYER,
not listed) → `SPAWN_ENTITY` → `ENTITY_METADATA` (skin parts 0x7F @ index 17 + flags) →
`ENTITY_EQUIPMENT`. Each tick: `REL_ENTITY_MOVE_LOOK` (respawn fallback for >8 block jumps) +
`ENTITY_HEAD_ROTATION`. One-shot `ANIMATION` for swings. On despawn: `ENTITY_DESTROY` +
`PLAYER_INFO_REMOVE`. Targets 1.20.5–1.21.x. Packets are sent only to the session's viewers.

## Persistence

- `actortracks/<track>.yml` — tracks; each frame is one compact delimited string
  (`x;y;z;yaw;pitch;headYaw;pose;flags;anims`), plus the captured `appearance` (skin + equipment).

## Build roadmap

1. ✅ `CinematicBridge` console play-for-player.
2. ✅ Data model + engine.
3. ✅ `PacketActor` for PLAYER actors — per-viewer spawn (skin), relative movement, head rotation,
   sneaking/glow/fire/invisible, swing animations, equipment. **Needs in-game testing on 1.21.4.**
4. ✅ `ActorRecorder` capture (movement + skin + equipment) + persistence + `/cinaddon actor`.
5. ✅ `spawnactors` command + `ActorManager` (scenes removed; COMMAND-node driven).
6. Remaining: per-frame equipment/item-use, full poses (swimming/sleeping/sitting), hurt/crit
   animations, non-PLAYER entity types, keyframe authoring.
