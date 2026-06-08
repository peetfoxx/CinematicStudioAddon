package sk.hypercube.cinematicstudioaddon.record

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEntityType
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
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
        track.appearance?.let { a ->
            cfg.set("appearance.entityType", a.entityType.name)
            cfg.set("appearance.displayName", a.displayName)
            cfg.set("appearance.skinTextureValue", a.skinTextureValue)
            cfg.set("appearance.skinSignature", a.skinSignature)
            cfg.set("appearance.equipment.mainHand", a.equipment.mainHand)
            cfg.set("appearance.equipment.offHand", a.equipment.offHand)
            cfg.set("appearance.equipment.helmet", a.equipment.helmet)
            cfg.set("appearance.equipment.chestplate", a.equipment.chestplate)
            cfg.set("appearance.equipment.leggings", a.equipment.leggings)
            cfg.set("appearance.equipment.boots", a.equipment.boots)
        }
        cfg.save(File(dir, "${track.id.lowercase()}.yml"))
    }

    fun delete(id: String): Boolean = File(dir, "${id.lowercase()}.yml").delete()

    private fun load(file: File): ActorTrack {
        val cfg = YamlConfiguration.loadConfiguration(file)
        val id = cfg.getString("id") ?: file.nameWithoutExtension
        val mode = TrackMode.valueOf(cfg.getString("mode") ?: TrackMode.RECORDED.name)
        val length = cfg.getInt("length")
        val appearance = if (cfg.isConfigurationSection("appearance")) {
            ActorAppearance(
                entityType = ActorEntityType.valueOf(cfg.getString("appearance.entityType") ?: ActorEntityType.PLAYER.name),
                displayName = cfg.getString("appearance.displayName"),
                skinTextureValue = cfg.getString("appearance.skinTextureValue"),
                skinSignature = cfg.getString("appearance.skinSignature"),
                equipment = ActorEquipment(
                    mainHand = cfg.getString("appearance.equipment.mainHand"),
                    offHand = cfg.getString("appearance.equipment.offHand"),
                    helmet = cfg.getString("appearance.equipment.helmet"),
                    chestplate = cfg.getString("appearance.equipment.chestplate"),
                    leggings = cfg.getString("appearance.equipment.leggings"),
                    boots = cfg.getString("appearance.equipment.boots")
                )
            )
        } else null
        return when (mode) {
            TrackMode.RECORDED -> ActorTrack(id, mode, length, frames = cfg.getStringList("frames").map { decodeFrame(it) }, appearance = appearance)
            TrackMode.KEYFRAMED -> ActorTrack(id, mode, length, keyframes = cfg.getStringList("keyframes").map { decodeKeyframe(it) }, appearance = appearance)
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
