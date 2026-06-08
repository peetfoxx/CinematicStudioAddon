package sk.hypercube.cinematicstudioaddon.scene

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import sk.hypercube.cinematicstudioaddon.actor.ActorBackend
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack

/**
 * Drives one [Scene] for a fixed set of viewers.
 *
 * We own the clock: a 1-tick repeating task advances every actor by `(currentTick - startTick)`
 * into its track, spawning it on its first visible frame and despawning at the end. The
 * CinematicStudio camera is started separately by the caller at the same tick, so the two stay
 * aligned on the server tick stream. Self-cleans when all actors finish or all viewers leave.
 */
class SceneSession(
    private val plugin: Plugin,
    private val scene: Scene,
    private val viewers: MutableSet<Player>,
    private val backend: ActorBackend,
    private val trackResolver: (String) -> ActorTrack?,
    private val onComplete: (SceneSession) -> Unit
) {
    private var tick = 0
    private var task: BukkitTask? = null
    private var stopped = false
    private val running = mutableListOf<RunningActor>()

    private class RunningActor(val actor: SceneActor, val track: ActorTrack, var handle: ActorHandle?)

    fun start() {
        for (sceneActor in scene.actors) {
            val track = trackResolver(sceneActor.trackId)
            if (track == null) {
                plugin.logger.warning("Scene '${scene.id}': missing track '${sceneActor.trackId}', skipping actor.")
                continue
            }
            running += RunningActor(sceneActor, track, handle = null)
        }
        if (running.isEmpty()) {
            onComplete(this)
            return
        }
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 0L, 1L)
    }

    private fun tick() {
        for (ra in running) {
            // TODO(origin): for OriginMode.ANCHORED, transform frame coords by ra.actor.origin here.
            val local = tick - ra.actor.startTick
            val frame = ra.track.frameAt(local)
            when {
                frame == null && ra.handle != null -> { ra.handle?.despawn(); ra.handle = null }
                frame != null && ra.handle == null -> ra.handle = backend.spawn(viewers, ra.actor.appearance, frame)
                frame != null -> {
                    ra.handle?.update(frame)
                    if (frame.animations.isNotEmpty()) ra.handle?.playAnimations(frame.animations)
                }
            }
        }
        tick++
        if (running.all { tick - it.actor.startTick >= it.track.length }) stop()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        task?.cancel()
        task = null
        running.forEach { it.handle?.despawn(); it.handle = null }
        onComplete(this)
    }

    fun removeViewer(player: Player) {
        if (!viewers.remove(player)) return
        running.forEach { it.handle?.removeViewer(player) }
        if (viewers.isEmpty()) stop()
    }
}
