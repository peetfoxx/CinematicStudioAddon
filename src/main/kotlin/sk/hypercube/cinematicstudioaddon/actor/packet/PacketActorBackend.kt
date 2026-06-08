package sk.hypercube.cinematicstudioaddon.actor.packet

import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle

/**
 * ProtocolLib-based backend: each actor is a client-side fake entity sent only to its viewers.
 *
 * TODO(impl): allocate a unique entity id + UUID. For PLAYER actors send PLAYER_INFO (add) ->
 * spawn -> ENTITY_METADATA (pose/flags) -> ENTITY_EQUIPMENT. Each tick send ENTITY_TELEPORT (or
 * REL_ENTITY_MOVE_LOOK) + ENTITY_HEAD_ROTATION. One-shot ANIMATION/ENTITY_STATUS for swings/damage.
 * On despawn send ENTITY_DESTROY (+ PLAYER_INFO remove). Version-guard packet shapes for the target
 * MC version. All packets go only to the handle's viewers.
 */
class PacketActorBackend : ActorBackend {
    override fun spawn(viewers: Collection<Player>, appearance: ActorAppearance, initial: ActorFrame): ActorHandle {
        TODO("Packet actor spawning not yet implemented (see ACTOR_LAYER.md roadmap step 3)")
    }
}
