package sk.hypercube.cinematicstudioaddon.actor

import org.bukkit.entity.Player

/**
 * Spawns and drives actors. Implementations decide *how* (ProtocolLib packets, Citizens, ...).
 * Actors are per-viewer: an actor is only ever shown to the viewers it was spawned for.
 */
interface ActorBackend {
    fun spawn(viewers: Collection<Player>, appearance: ActorAppearance, initial: ActorFrame): ActorHandle
}

/** A live actor instance. */
interface ActorHandle {
    /** Move/rotate/repose the actor to match [frame]. Called every tick while active. */
    fun update(frame: ActorFrame)

    /** Fire one-shot animations for the current tick. */
    fun playAnimations(animations: Set<ActorAnimation>)

    fun addViewer(player: Player)
    fun removeViewer(player: Player)

    /** Remove the actor for all viewers. */
    fun despawn()
}
