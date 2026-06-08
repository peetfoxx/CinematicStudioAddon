package sk.hypercube.cinematicstudioaddon.record

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import sk.hypercube.cinematicstudioaddon.actor.ActorState
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
            cfg.set("appearance.entityType", a.entityType)
            cfg.set("appearance.displayName", a.displayName)
            cfg.set("appearance.skinTextureValue", a.skinTextureValue)
            cfg.set("appearance.skinSignature", a.skinSignature)
            writeEquipment(cfg, "appearance.equipment", a.equipment)
        }
        track.states.forEach { (name, state) ->
            val base = "states.$name"
            state.flags?.let { cfg.set("$base.flags", it.map { f -> f.name }) }
            cfg.set("$base.name", state.name)
            cfg.set("$base.animation", state.animation)
            state.equipment?.let { writeEquipment(cfg, "$base.equipment", it) }
        }
        track.timeline.forEach { (tick, name) -> cfg.set("timeline.$tick", name) }
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
                entityType = cfg.getString("appearance.entityType") ?: "PLAYER",
                displayName = cfg.getString("appearance.displayName"),
                skinTextureValue = cfg.getString("appearance.skinTextureValue"),
                skinSignature = cfg.getString("appearance.skinSignature"),
                equipment = readEquipment(cfg, "appearance.equipment")
            )
        } else null

        val states = HashMap<String, ActorState>()
        cfg.getConfigurationSection("states")?.getKeys(false)?.forEach { name ->
            val base = "states.$name"
            val flags = cfg.getStringList("$base.flags")
                .mapNotNull { runCatching { ActorFlag.valueOf(it) }.getOrNull() }
                .toSet().ifEmpty { null }
            val equipment = if (cfg.isConfigurationSection("$base.equipment")) readEquipment(cfg, "$base.equipment") else null
            states[name] = ActorState(
                flags = flags,
                name = cfg.getString("$base.name"),
                equipment = equipment,
                animation = cfg.getString("$base.animation")
            )
        }

        val timeline = HashMap<Int, String>()
        cfg.getConfigurationSection("timeline")?.getKeys(false)?.forEach { key ->
            val tick = key.toIntOrNull() ?: return@forEach
            cfg.getString("timeline.$key")?.let { timeline[tick] = it }
        }

        return when (mode) {
            TrackMode.RECORDED -> ActorTrack(id, mode, length, frames = cfg.getStringList("frames").map { decodeFrame(it) }, appearance = appearance, states = states, timeline = timeline)
            TrackMode.KEYFRAMED -> ActorTrack(id, mode, length, keyframes = cfg.getStringList("keyframes").map { decodeKeyframe(it) }, appearance = appearance, states = states, timeline = timeline)
        }
    }

    private fun writeEquipment(cfg: YamlConfiguration, base: String, eq: ActorEquipment) {
        cfg.set("$base.mainHand", eq.mainHand)
        cfg.set("$base.offHand", eq.offHand)
        cfg.set("$base.helmet", eq.helmet)
        cfg.set("$base.chestplate", eq.chestplate)
        cfg.set("$base.leggings", eq.leggings)
        cfg.set("$base.boots", eq.boots)
    }

    private fun readEquipment(cfg: YamlConfiguration, base: String): ActorEquipment = ActorEquipment(
        mainHand = cfg.getString("$base.mainHand"),
        offHand = cfg.getString("$base.offHand"),
        helmet = cfg.getString("$base.helmet"),
        chestplate = cfg.getString("$base.chestplate"),
        leggings = cfg.getString("$base.leggings"),
        boots = cfg.getString("$base.boots")
    )

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
