package sk.hypercube.cinematicstudioaddon.playback

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle
import sk.hypercube.cinematicstudioaddon.actor.ActorState
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack

/**
 * Plays a single [ActorTrack] for a fixed set of viewers, starting now (tick 0 of the track).
 *
 * Timing relative to a cinematic is handled by *when* the spawn command fires (e.g. a CinematicStudio
 * COMMAND node at a given tick), so the session itself just advances the track from its first frame.
 * Self-cleans when the track ends or all viewers leave.
 */
class ActorSession(
    private val plugin: Plugin,
    private val track: ActorTrack,
    private val appearance: ActorAppearance,
    private val viewers: MutableSet<Player>,
    private val backend: ActorBackend,
    private val onComplete: (ActorSession) -> Unit
) {
    private var tick = 0
    private var task: BukkitTask? = null
    private var handle: ActorHandle? = null
    private var stopped = false

    val trackId: String get() = track.id

    fun appliesTo(player: Player): Boolean = viewers.contains(player)

    fun start() {
        if (track.length <= 0 || viewers.isEmpty()) {
            onComplete(this)
            return
        }
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 0L, 1L)
    }

    private fun tick() {
        val frame = track.frameAt(tick)
        if (frame == null) {
            stop()
            return
        }
        val current = handle
        if (current == null) {
            handle = backend.spawn(viewers, appearance, frame)
        } else {
            current.update(frame)
            if (frame.animations.isNotEmpty()) current.playAnimations(frame.animations)
        }
        // Auto-apply any state scheduled for this tick.
        track.timeline[tick]?.let { name -> track.states[name]?.let { applyState(it) } }
        tick++
    }

    /** Applies a state to the live actor (no-op if not yet spawned). */
    fun applyState(state: ActorState) {
        val h = handle ?: return
        state.flags?.let { h.setFlagOverlay(it) }
        state.name?.let { h.setName(it) }
        state.equipment?.let { h.setEquipment(it) }
        state.animation?.let { h.playModelAnimation(it) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        task?.cancel()
        task = null
        handle?.despawn()
        handle = null
        onComplete(this)
    }

    fun removeViewer(player: Player) {
        if (!viewers.remove(player)) return
        handle?.removeViewer(player)
        if (viewers.isEmpty()) stop()
    }
}
