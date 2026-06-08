package sk.hypercube.cinematicstudioaddon.actor

/**
 * Immutable snapshot of an actor's state for a single tick.
 *
 * Coordinates are interpreted according to the owning [sk.hypercube.cinematicstudioaddon.scene.SceneOrigin]
 * (absolute world coords, or offset from a scene anchor).
 */
data class ActorFrame(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val headYaw: Float = yaw,
    val pose: ActorPose = ActorPose.STANDING,
    val flags: Set<ActorFlag> = emptySet(),
    /** One-shot animations to fire on the tick this frame is shown. */
    val animations: Set<ActorAnimation> = emptySet()
)
