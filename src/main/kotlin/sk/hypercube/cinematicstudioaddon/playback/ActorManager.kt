package sk.hypercube.cinematicstudioaddon.playback

import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.actor.packet.PacketActorBackend
import sk.hypercube.cinematicstudioaddon.record.TrackStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns recorded actor tracks and active playback sessions. Actors are spawned directly for player(s)
 * (typically from a CinematicStudio COMMAND node) — there is no separate "scene" concept; the
 * cinematic's own timeline does the sequencing.
 */
class ActorManager(private val plugin: CinematicStudioAddon) {

    private val tracks = ConcurrentHashMap<String, ActorTrack>()
    private val trackStorage = TrackStorage(plugin)
    private val sessions = mutableListOf<ActorSession>()

    /**
     * Built lazily and only if ProtocolLib is present (the backend touches ProtocolLib classes
     * eagerly). Null means actor playback is unavailable.
     */
    private val backend: ActorBackend? by lazy {
        if (plugin.server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true) {
            PacketActorBackend(plugin)
        } else {
            plugin.logger.warning("ProtocolLib not found - actor playback is disabled.")
            null
        }
    }

    fun getTrack(id: String): ActorTrack? = tracks[id.lowercase()]
    fun trackIds(): List<String> = tracks.values.map { it.id }.sorted()

    /** Loads all persisted tracks from disk. Call once on enable. */
    fun loadAll() {
        trackStorage.loadAll().forEach { tracks[it.id.lowercase()] = it }
        plugin.logger.info("Loaded ${tracks.size} actor track(s).")
    }

    fun saveTrack(track: ActorTrack) {
        tracks[track.id.lowercase()] = track
        trackStorage.save(track)
    }

    fun deleteTrack(id: String): Boolean {
        val removed = tracks.remove(id.lowercase()) != null
        trackStorage.delete(id)
        return removed
    }

    /** Spawns [track]'s actor for [viewers], playing it once. Returns false if ProtocolLib is missing. */
    fun spawn(track: ActorTrack, viewers: Collection<Player>): Boolean {
        val backend = this.backend ?: return false
        val session = ActorSession(
            plugin = plugin,
            track = track,
            appearance = track.appearance ?: ActorAppearance(),
            viewers = viewers.toMutableSet(),
            backend = backend,
            onComplete = { finished -> synchronized(sessions) { sessions.remove(finished) } }
        )
        synchronized(sessions) { sessions += session }
        session.start()
        return true
    }

    fun stateNames(trackId: String): List<String> = getTrack(trackId)?.states?.keys?.sorted() ?: emptyList()

    /**
     * Applies a named state to the live actor(s) of [trackId] currently shown to any of [players].
     * Returns false if the track or state name is unknown.
     */
    fun applyState(trackId: String, players: Collection<Player>, stateName: String): Boolean {
        val track = getTrack(trackId) ?: return false
        val state = track.states[stateName] ?: return false
        val targets = players.toSet()
        synchronized(sessions) { sessions.toList() }
            .filter { it.trackId.equals(trackId, ignoreCase = true) && targets.any { p -> it.appliesTo(p) } }
            .forEach { it.applyState(state) }
        return true
    }

    fun handleQuit(player: Player) {
        synchronized(sessions) { sessions.toList() }.forEach { it.removeViewer(player) }
    }

    fun shutdown() {
        synchronized(sessions) { sessions.toList() }.forEach { it.stop() }
    }
}
