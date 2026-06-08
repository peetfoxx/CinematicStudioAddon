package sk.hypercube.cinematicstudioaddon.actor.packet

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle

/**
 * ProtocolLib-based backend: each actor is a per-viewer client-side fake entity.
 *
 * Construct this only when ProtocolLib is present (it touches ProtocolLib classes eagerly).
 */
class PacketActorBackend(private val plugin: Plugin) : ActorBackend {

    private val protocol: ProtocolManager = ProtocolLibrary.getProtocolManager()

    override fun spawn(viewers: Collection<Player>, appearance: ActorAppearance, initial: ActorFrame): ActorHandle {
        val actor = PacketActor(plugin, protocol, viewers.toMutableSet(), appearance, initial)
        actor.spawn()
        return actor
    }
}
