package sk.hypercube.cinematicstudioaddon.modelengine

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/** Holds the reflective ModelEngine objects for one actor. */
class MegModel(val modeledEntity: Any, val activeModel: Any)

/**
 * Reflection-only bridge to ModelEngine (MEG R4). No compile-time dependency, so the plugin builds
 * and runs without MEG; every call fails gracefully (logs, returns null/no-op) if the API differs.
 *
 * NOTE: MEG's API has shifted across major versions — these calls are best-effort and want in-game
 * verification against your installed MEG version.
 */
object ModelEngineHook {

    fun isAvailable(): Boolean =
        Bukkit.getPluginManager().getPlugin("ModelEngine")?.isEnabled == true &&
            runCatching { Class.forName("com.ticxo.modelengine.api.ModelEngineAPI") }.isSuccess

    /** Creates a modeled entity on [base] and adds the model [modelId]. Returns null on failure. */
    fun applyModel(base: Entity, modelId: String, plugin: Plugin): MegModel? = try {
        val api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI")
        val modeled = api.getMethod("createModeledEntity", Entity::class.java).invoke(null, base)
        val active = api.getMethod("createActiveModel", String::class.java).invoke(null, modelId)
        when {
            modeled == null -> { plugin.logger.warning("ModelEngine: createModeledEntity returned null."); null }
            active == null -> { plugin.logger.warning("ModelEngine: unknown model id '$modelId'."); null }
            else -> {
                val addModel = modeled.javaClass.methods.firstOrNull { it.name == "addModel" }
                if (addModel != null) {
                    val args: Array<Any> = if (addModel.parameterCount == 2) arrayOf(active, true) else arrayOf(active)
                    addModel.invoke(modeled, *args)
                }
                modeled.javaClass.methods
                    .firstOrNull { it.name == "setBaseEntityVisible" && it.parameterCount == 1 }
                    ?.invoke(modeled, false)
                MegModel(modeled, active)
            }
        }
    } catch (ex: Exception) {
        plugin.logger.warning("ModelEngine integration error applying '$modelId': ${ex.message}")
        null
    }

    /** Plays a model animation by name. */
    fun playAnimation(model: MegModel, animation: String, plugin: Plugin) {
        try {
            val handler = model.activeModel.javaClass.getMethod("getAnimationHandler").invoke(model.activeModel)
                ?: return
            val play5 = handler.javaClass.methods.firstOrNull { it.name == "playAnimation" && it.parameterCount == 5 }
            if (play5 != null) {
                // playAnimation(name, lerpIn, lerpOut, speed, force)
                play5.invoke(handler, animation, 0.2, 0.2, 1.0, true)
            } else {
                handler.javaClass.methods.firstOrNull { it.name == "playAnimation" && it.parameterCount == 1 }
                    ?.invoke(handler, animation)
            }
        } catch (ex: Exception) {
            plugin.logger.warning("ModelEngine: failed to play animation '$animation': ${ex.message}")
        }
    }

    /** Removes the model from its base entity. */
    fun destroy(model: MegModel, plugin: Plugin) {
        try {
            model.modeledEntity.javaClass.methods
                .firstOrNull { it.name == "destroy" && it.parameterCount == 0 }
                ?.invoke(model.modeledEntity)
        } catch (ex: Exception) {
            plugin.logger.warning("ModelEngine: failed to destroy model: ${ex.message}")
        }
    }
}
