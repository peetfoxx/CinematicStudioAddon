package sk.hypercube.cinematicstudioaddon.actor

/**
 * A named, on-demand change applied to a live actor. Each field is optional; null means "leave
 * unchanged". States are defined in a track's `states:` section and triggered either by the track's
 * `timeline:` (tick -> state name) or by `/cinaddon state <track> <player> <state>` (e.g. from a
 * CinematicStudio COMMAND node).
 */
data class ActorState(
    /** Overlay flags (glowing/invisible/on-fire) merged on top of the per-tick frame flags. */
    val flags: Set<ActorFlag>? = null,
    /** Custom name tag (mobs) / overrides nothing visible for players. */
    val name: String? = null,
    /** Equipment override (item specs resolved by [ItemResolver]). */
    val equipment: ActorEquipment? = null,
    /** ModelEngine animation name. Reserved for the ModelEngine integration. */
    val animation: String? = null
)
