package sk.hypercube.cinematicstudioaddon.record

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.actor.ItemCodec
import sk.hypercube.cinematicstudioaddon.actor.TrackMode
import java.util.Locale
import java.util.UUID

/**
 * Captures a player's per-tick state into a [TrackMode.RECORDED] [ActorTrack].
 *
 * TODO(capture): also record swing/use animations + equipment changes via event listeners, and map
 * more poses (swimming, gliding, sleeping). Position is sampled here every tick.
 */
class ActorRecorder(private val plugin: Plugin) {

    private class Session(
        val trackId: String,
        val frames: MutableList<ActorFrame>,
        val appearance: ActorAppearance,
        var task: BukkitTask?
    )

    private val sessions = HashMap<UUID, Session>()

    fun isRecording(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    /** Begins recording [player] into a track named [trackId]. Returns false if already recording. */
    fun start(player: Player, trackId: String): Boolean {
        if (isRecording(player)) return false
        val session = Session(trackId, mutableListOf(), captureAppearance(player), null)
        session.task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val loc = player.location
            session.frames += ActorFrame(
                x = loc.x, y = loc.y, z = loc.z,
                yaw = loc.yaw, pitch = loc.pitch, headYaw = loc.yaw,
                pose = if (player.isSneaking) ActorPose.SNEAKING else ActorPose.STANDING,
                flags = buildSet { if (player.isGlowing) add(ActorFlag.GLOWING) }
            )
            player.sendActionBar(recordingHud(session.frames.size))
        }, 0L, 1L)
        sessions[player.uniqueId] = session
        return true
    }

    private fun recordingHud(ticks: Int): Component {
        val text = String.format(Locale.US, "§c● REC §8| §f%d ticks §7(§f%.1fs§7) §8| §7press §fF §7to stop", ticks, ticks / 20.0)
        return LegacyComponentSerializer.legacySection().deserialize(text)
    }

    /** Stops recording [player] and returns the captured track, or null if not recording. */
    fun stop(player: Player): ActorTrack? {
        val session = sessions.remove(player.uniqueId) ?: return null
        session.task?.cancel()
        player.sendActionBar(Component.empty())
        return ActorTrack(
            id = session.trackId,
            mode = TrackMode.RECORDED,
            length = session.frames.size,
            frames = session.frames,
            appearance = session.appearance
        )
    }

    /** Snapshots the player's skin and worn equipment so the actor looks like them. */
    private fun captureAppearance(player: Player): ActorAppearance {
        val textures = player.playerProfile.properties.firstOrNull { it.name == "textures" }
        val eq = player.equipment
        return ActorAppearance(
            entityType = "PLAYER",
            displayName = player.name,
            skinTextureValue = textures?.value,
            skinSignature = textures?.signature,
            equipment = ActorEquipment(
                mainHand = ItemCodec.encode(eq?.itemInMainHand),
                offHand = ItemCodec.encode(eq?.itemInOffHand),
                helmet = ItemCodec.encode(eq?.helmet),
                chestplate = ItemCodec.encode(eq?.chestplate),
                leggings = ItemCodec.encode(eq?.leggings),
                boots = ItemCodec.encode(eq?.boots)
            )
        )
    }

    fun cancelAll() {
        sessions.values.forEach { it.task?.cancel() }
        sessions.clear()
    }
}
