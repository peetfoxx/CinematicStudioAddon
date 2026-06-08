package sk.hypercube.cinematicstudioaddon.scene

import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.actor.packet.PacketActorBackend
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns scenes, tracks and active sessions. Playing a scene starts the CinematicStudio cinematic via
 * the bridge *and* a [SceneSession] for our actors, on the same tick.
 */
class SceneManager(private val plugin: CinematicStudioAddon) {

    private val scenes = ConcurrentHashMap<String, Scene>()
    private val tracks = ConcurrentHashMap<String, ActorTrack>()
    private val sessions = mutableListOf<SceneSession>()

    /**
     * Built lazily and only if ProtocolLib is present (the backend touches ProtocolLib classes
     * eagerly). Null means actors are unavailable; cinematics still play.
     */
    private val backend: ActorBackend? by lazy {
        if (plugin.server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true) {
            PacketActorBackend(plugin)
        } else {
            plugin.logger.warning("ProtocolLib not found - actor playback is disabled (cinematics still play).")
            null
        }
    }

    fun getScene(id: String): Scene? = scenes[id.lowercase()]
    fun getTrack(id: String): ActorTrack? = tracks[id.lowercase()]
    fun registerScene(scene: Scene) { scenes[scene.id.lowercase()] = scene }
    fun registerTrack(track: ActorTrack) { tracks[track.id.lowercase()] = track }

    /** Starts [scene] for [viewers]: cinematic (via bridge) + actor session, aligned on this tick. */
    fun play(scene: Scene, viewers: Collection<Player>) {
        scene.cinematic?.let { cinematic ->
            viewers.forEach { plugin.bridge.playFor(it, cinematic) }
        }
        if (scene.actors.isEmpty()) return
        val backend = this.backend
        if (backend == null) {
            plugin.logger.warning("Scene '${scene.id}' has actors but ProtocolLib is unavailable; skipping actors.")
            return
        }
        val session = SceneSession(
            plugin = plugin,
            scene = scene,
            viewers = viewers.toMutableSet(),
            backend = backend,
            trackResolver = { getTrack(it) },
            onComplete = { finished -> synchronized(sessions) { sessions.remove(finished) } }
        )
        synchronized(sessions) { sessions += session }
        session.start()
    }

    fun handleQuit(player: Player) {
        synchronized(sessions) { sessions.toList() }.forEach { it.removeViewer(player) }
    }

    fun shutdown() {
        synchronized(sessions) { sessions.toList() }.forEach { it.stop() }
    }

    // TODO(persistence): load scenes from scenes/*.yml and tracks from actortracks/*.json on enable;
    //   save on change. See ACTOR_LAYER.md roadmap steps 4-5.
}
