# Actor Layer — Design

Replacement for CinematicStudio's unreliable built-in actors: a self-contained actor system that
we fully own, synchronized to cinematic playback.

## Decisions

| Topic | Choice |
| --- | --- |
| Backend | **Packet NPCs via ProtocolLib** — per-viewer client-side fake entities |
| Visibility | **Per-viewer only** — actors are sent only to the scene's viewer(s) |
| Animation source | **Both** — live recording *and* keyframe authoring |

## Core idea: we own the clock

There is no CinematicStudio API to read its playback state, so our actors cannot *follow* its
timeline. Instead, because we start the cinematic ourselves (`CinematicBridge.playFor`), we know
**tick 0** and drive our own actor timeline on the same server-tick stream. Both the camera and the
actors advance together, so drift is bounded to packet timing, not clock skew. A per-scene
`actorCameraOffsetTicks` lets us nudge alignment if needed.

## Data model

- **`ActorFrame`** — immutable per-tick state: position, yaw/pitch/headYaw, `ActorPose`,
  `ActorFlag`s, one-shot `ActorAnimation`s.
- **`ActorAppearance`** — entity type, display name, player skin (texture+signature), equipment.
- **`ActorTrack`** — an animation. `RECORDED` tracks store one frame per tick; `KEYFRAMED` tracks
  store sparse keyframes and interpolate (`frameAt(tick)` handles both; angle-aware lerp + easing).
- **`Scene`** — pairs an optional CinematicStudio cinematic with a list of `SceneActor`
  (track + appearance + startTick + origin). `trackable` reserved for the future navigation feature.

## Runtime

- **`ActorBackend` / `ActorHandle`** — backend-agnostic spawn/update/animate/despawn. First impl:
  `PacketActorBackend`.
- **`SceneSession`** — drives one scene for a fixed viewer set. A 1-tick repeating task advances each
  actor by `(currentTick - startTick)` into its track, spawning on first frame and despawning at end.
  Self-cleans when all actors finish or all viewers leave.
- **`SceneManager`** — owns scenes/tracks/sessions. `play(scene, viewers)` starts the CinematicStudio
  cinematic via the bridge *and* a `SceneSession`, on the same tick. Hooks `PlayerQuitEvent`.
- **`ActorRecorder`** — captures a player's per-tick state into a `RECORDED` track.

## Packet backend (PacketActorBackend) — implementation notes

Per actor: allocate a unique entity id + UUID. For `PLAYER` actors send `PLAYER_INFO` (add) →
spawn → `ENTITY_METADATA` (pose/flags) → `ENTITY_EQUIPMENT`. Each tick: `ENTITY_TELEPORT` (or
`REL_ENTITY_MOVE_LOOK`) + `ENTITY_HEAD_ROTATION`. One-shot `ANIMATION`/`ENTITY_STATUS` for
swings/damage. On despawn: `ENTITY_DESTROY` (+ `PLAYER_INFO` remove). Packet shapes are
version-guarded for the target MC version. Packets are sent only to the session's viewers.

## Persistence (planned)

- `scenes/<scene>.yml` — scene definitions (human-editable).
- `actortracks/<track>.json` — recorded/keyframed tracks (compact; per-tick frames).

## Commands (planned, extend `/cinaddon`)

```
/cinaddon actor record <track>     # start recording your movement
/cinaddon actor stop               # save the recording
/cinaddon scene create <scene> [cinematic]
/cinaddon scene addactor <scene> <track> [x y z]
/cinaddon scene play <scene> <player|@a>
```

## Build roadmap

1. ✅ `CinematicBridge` console play-for-player (shipped).
2. ✅ Skeleton — data model + engine interfaces + session/manager wiring.
3. ✅ `PacketActorBackend` for PLAYER actors — per-viewer spawn (skin), relative movement, head
   rotation, sneaking/glow/fire/invisible, swing animations. **Needs in-game testing on 1.21.4.**
4. `ActorRecorder` capture + JSON track persistence + `/cinaddon actor` commands.
5. `SceneManager` persistence + `/cinaddon scene` commands; wire `play` to bridge + session.
6. Remaining packet work: equipment, full poses (swimming/sleeping/sitting), hurt/crit animations,
   large-jump teleport polish, non-PLAYER entity types; keyframe authoring; `actorCameraOffsetTicks`.
