package sk.hypercube.cinematicstudioaddon.scene

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEntityType
import java.io.File

/** Persists [Scene]s to human-editable `scenes/<id>.yml` files. */
class SceneStorage(plugin: Plugin) {

    private val dir = File(plugin.dataFolder, "scenes").apply { mkdirs() }

    fun loadAll(): List<Scene> =
        dir.listFiles { f -> f.isFile && f.extension == "yml" }
            ?.mapNotNull { runCatching { load(it) }.getOrNull() }
            ?: emptyList()

    fun save(scene: Scene) {
        val cfg = YamlConfiguration()
        cfg.set("id", scene.id)
        cfg.set("world", scene.world)
        cfg.set("cinematic", scene.cinematic)
        cfg.set("actorCameraOffsetTicks", scene.actorCameraOffsetTicks)
        cfg.set("trackable", scene.trackable)
        cfg.set("actors", scene.actors.map { actorToMap(it) })
        cfg.save(File(dir, "${scene.id.lowercase()}.yml"))
    }

    fun delete(id: String): Boolean = File(dir, "${id.lowercase()}.yml").delete()

    private fun load(file: File): Scene {
        val cfg = YamlConfiguration.loadConfiguration(file)
        val actors = cfg.getMapList("actors").map { mapToActor(it) }
        return Scene(
            id = cfg.getString("id") ?: file.nameWithoutExtension,
            world = cfg.getString("world") ?: "world",
            cinematic = cfg.getString("cinematic"),
            actors = actors,
            actorCameraOffsetTicks = cfg.getInt("actorCameraOffsetTicks"),
            trackable = cfg.getBoolean("trackable")
        )
    }

    private fun actorToMap(actor: SceneActor): Map<String, Any?> = mapOf(
        "track" to actor.trackId,
        "startTick" to actor.startTick,
        "origin" to mapOf(
            "mode" to actor.origin.mode.name,
            "x" to actor.origin.anchorX,
            "y" to actor.origin.anchorY,
            "z" to actor.origin.anchorZ,
            "yaw" to actor.origin.anchorYaw.toDouble()
        ),
        "appearance" to mapOf(
            "entityType" to actor.appearance.entityType.name,
            "displayName" to actor.appearance.displayName,
            "skinTextureValue" to actor.appearance.skinTextureValue,
            "skinSignature" to actor.appearance.skinSignature
        )
    )

    private fun mapToActor(map: Map<*, *>): SceneActor {
        val origin = (map["origin"] as? Map<*, *>)?.let {
            SceneOrigin(
                mode = OriginMode.valueOf(it["mode"] as? String ?: OriginMode.ABSOLUTE.name),
                anchorX = (it["x"] as? Number)?.toDouble() ?: 0.0,
                anchorY = (it["y"] as? Number)?.toDouble() ?: 0.0,
                anchorZ = (it["z"] as? Number)?.toDouble() ?: 0.0,
                anchorYaw = (it["yaw"] as? Number)?.toFloat() ?: 0f
            )
        } ?: SceneOrigin()
        val appearance = (map["appearance"] as? Map<*, *>)?.let {
            ActorAppearance(
                entityType = ActorEntityType.valueOf(it["entityType"] as? String ?: ActorEntityType.PLAYER.name),
                displayName = it["displayName"] as? String,
                skinTextureValue = it["skinTextureValue"] as? String,
                skinSignature = it["skinSignature"] as? String
            )
        } ?: ActorAppearance()
        return SceneActor(
            trackId = map["track"] as? String ?: error("actor entry missing 'track'"),
            appearance = appearance,
            startTick = (map["startTick"] as? Number)?.toInt() ?: 0,
            origin = origin
        )
    }
}
