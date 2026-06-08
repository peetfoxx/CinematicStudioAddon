package sk.hypercube.cinematicstudioaddon.scene

import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance

enum class OriginMode {
    /** Track coordinates are absolute world coordinates. */
    ABSOLUTE,
    /** Track coordinates are offsets from an anchor (lets a scene be replayed anywhere). */
    ANCHORED
}

/** How a track's recorded coordinates map into the world at playback time. */
data class SceneOrigin(
    val mode: OriginMode = OriginMode.ABSOLUTE,
    val anchorX: Double = 0.0,
    val anchorY: Double = 0.0,
    val anchorZ: Double = 0.0,
    val anchorYaw: Float = 0f
)

/** One actor within a scene: which track, how it looks, when it starts, how its coords map. */
data class SceneActor(
    val trackId: String,
    val appearance: ActorAppearance = ActorAppearance(),
    val startTick: Int = 0,
    val origin: SceneOrigin = SceneOrigin()
)

/**
 * A scene pairs an optional CinematicStudio cinematic (camera / sound / particles, played via the
 * bridge) with our own actors (driven by a [SceneSession]).
 */
data class Scene(
    val id: String,
    val world: String,
    /** CinematicStudio cinematic to play for viewers alongside the actors; null = actors only. */
    val cinematic: String? = null,
    val actors: List<SceneActor> = emptyList(),
    /** Nudge actors vs. the CinematicStudio camera if they feel ahead/behind (ticks, may be negative). */
    val actorCameraOffsetTicks: Int = 0,
    /** Reserved for the planned quest-navigation feature. */
    val trackable: Boolean = false
)
