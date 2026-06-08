package sk.hypercube.cinematicstudioaddon.record

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.actor.Easing
import sk.hypercube.cinematicstudioaddon.actor.Keyframe
import sk.hypercube.cinematicstudioaddon.actor.TrackMode
import java.io.File

/**
 * Persists [ActorTrack]s to `actortracks/<id>.yml`. Frames are stored as compact delimited strings
 * (one per list entry) to keep per-tick recordings small and fast to (de)serialize.
 */
class TrackStorage(plugin: Plugin) {

    private val dir = File(plugin.dataFolder, "actortracks").apply { mkdirs() }

    fun loadAll(): List<ActorTrack> =
        dir.listFiles { f -> f.isFile && f.extension == "yml" }
            ?.mapNotNull { runCatching { load(it) }.getOrNull() }
            ?: emptyList()

    fun save(track: ActorTrack) {
        val cfg = YamlConfiguration()
        cfg.set("id", track.id)
        cfg.set("mode", track.mode.name)
        cfg.set("length", track.length)
        when (track.mode) {
            TrackMode.RECORDED -> cfg.set("frames", track.frames.map { encodeFrame(it) })
            TrackMode.KEYFRAMED -> cfg.set("keyframes", track.keyframes.map { encodeKeyframe(it) })
        }
        cfg.save(File(dir, "${track.id.lowercase()}.yml"))
    }

    fun delete(id: String): Boolean = File(dir, "${id.lowercase()}.yml").delete()

    private fun load(file: File): ActorTrack {
        val cfg = YamlConfiguration.loadConfiguration(file)
        val id = cfg.getString("id") ?: file.nameWithoutExtension
        val mode = TrackMode.valueOf(cfg.getString("mode") ?: TrackMode.RECORDED.name)
        val length = cfg.getInt("length")
        return when (mode) {
            TrackMode.RECORDED -> ActorTrack(id, mode, length, frames = cfg.getStringList("frames").map { decodeFrame(it) })
            TrackMode.KEYFRAMED -> ActorTrack(id, mode, length, keyframes = cfg.getStringList("keyframes").map { decodeKeyframe(it) })
        }
    }

    companion object {
        // Frame:    x;y;z;yaw;pitch;headYaw;pose;flagsCsv;animsCsv
        // Keyframe: tick|easing|<frame>
        fun encodeFrame(f: ActorFrame): String = listOf(
            f.x, f.y, f.z, f.yaw, f.pitch, f.headYaw,
            f.pose.name,
            f.flags.joinToString(",") { it.name },
            f.animations.joinToString(",") { it.name }
        ).joinToString(";")

        fun decodeFrame(s: String): ActorFrame {
            val p = s.split(";")
            return ActorFrame(
                x = p[0].toDouble(), y = p[1].toDouble(), z = p[2].toDouble(),
                yaw = p[3].toFloat(), pitch = p[4].toFloat(), headYaw = p[5].toFloat(),
                pose = ActorPose.valueOf(p[6]),
                flags = parseEnumCsv(p.getOrNull(7)) { ActorFlag.valueOf(it) },
                animations = parseEnumCsv(p.getOrNull(8)) { ActorAnimation.valueOf(it) }
            )
        }

        private fun encodeKeyframe(k: Keyframe): String = "${k.tick}|${k.easing.name}|${encodeFrame(k.frame)}"

        private fun decodeKeyframe(s: String): Keyframe {
            val p = s.split("|", limit = 3)
            return Keyframe(p[0].toInt(), decodeFrame(p[2]), Easing.valueOf(p[1]))
        }

        private fun <T> parseEnumCsv(csv: String?, parse: (String) -> T): Set<T> =
            csv?.split(",")?.filter { it.isNotBlank() }?.mapTo(HashSet()) { parse(it) } ?: emptySet()
    }
}
