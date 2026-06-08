package sk.hypercube.cinematicstudioaddon.actor

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.packet.PacketActor
import sk.hypercube.cinematicstudioaddon.modelengine.ModelEngineActor
import sk.hypercube.cinematicstudioaddon.modelengine.ModelEngineHook

/**
 * Routes each actor to the right backend:
 *  - a [model][ActorAppearance.model] + ModelEngine present -> [ModelEngineActor] (real entity)
 *  - otherwise -> [PacketActor] (per-viewer packet entity, needs ProtocolLib)
 *
 * [ActorManager] verifies the relevant plugin is present before spawning, so the lazily-created
 * ProtocolLib manager is only touched on the packet path.
 */
class ActorBackendRouter(private val plugin: Plugin) : ActorBackend {

    private val protocol: ProtocolManager by lazy { ProtocolLibrary.getProtocolManager() }

    override fun spawn(viewers: Collection<Player>, appearance: ActorAppearance, initial: ActorFrame): ActorHandle {
        if (appearance.model != null && ModelEngineHook.isAvailable()) {
            return ModelEngineActor(plugin, appearance, viewers.toMutableSet(), initial).also { it.spawn() }
        }
        return PacketActor(plugin, protocol, viewers.toMutableSet(), appearance, initial).also { it.spawn() }
    }
}
