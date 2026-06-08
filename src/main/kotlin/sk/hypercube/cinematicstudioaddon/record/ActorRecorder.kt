package sk.hypercube.cinematicstudioaddon.record

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.actor.TrackMode
import java.util.UUID

/**
 * Captures a player's per-tick state into a [TrackMode.RECORDED] [ActorTrack].
 *
 * TODO(capture): also record swing/use animations + equipment changes via event listeners, and map
 * more poses (swimming, gliding, sleeping). Position is sampled here every tick.
 */
class ActorRecorder(private val plugin: Plugin) {

    private class Session(val trackId: String, val frames: MutableList<ActorFrame>, var task: BukkitTask?)

    private val sessions = HashMap<UUID, Session>()

    fun isRecording(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    /** Begins recording [player] into a track named [trackId]. Returns false if already recording. */
    fun start(player: Player, trackId: String): Boolean {
        if (isRecording(player)) return false
        val session = Session(trackId, mutableListOf(), null)
        session.task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val loc = player.location
            session.frames += ActorFrame(
                x = loc.x, y = loc.y, z = loc.z,
                yaw = loc.yaw, pitch = loc.pitch, headYaw = loc.yaw,
                pose = if (player.isSneaking) ActorPose.SNEAKING else ActorPose.STANDING,
                flags = buildSet { if (player.isGlowing) add(ActorFlag.GLOWING) }
            )
        }, 0L, 1L)
        sessions[player.uniqueId] = session
        return true
    }

    /** Stops recording [player] and returns the captured track, or null if not recording. */
    fun stop(player: Player): ActorTrack? {
        val session = sessions.remove(player.uniqueId) ?: return null
        session.task?.cancel()
        return ActorTrack(
            id = session.trackId,
            mode = TrackMode.RECORDED,
            length = session.frames.size,
            frames = session.frames
        )
    }

    fun cancelAll() {
        sessions.values.forEach { it.task?.cancel() }
        sessions.clear()
    }
}
