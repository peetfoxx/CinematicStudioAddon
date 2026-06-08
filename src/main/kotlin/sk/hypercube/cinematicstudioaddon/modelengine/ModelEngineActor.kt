package sk.hypercube.cinematicstudioaddon.modelengine

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle

/**
 * An actor rendered as a ModelEngine model on a real, invisible base entity (an armor-stand marker).
 *
 * Unlike packet actors, this is a real server-side entity, so it is **visible to everyone nearby**,
 * not per-viewer. The viewer set is still used for lifecycle (despawn when the triggering player
 * leaves). The model follows the base entity, which we teleport each tick.
 */
class ModelEngineActor(
    private val plugin: Plugin,
    private val appearance: ActorAppearance,
    private val viewers: MutableSet<Player>,
    private val initial: ActorFrame
) : ActorHandle {

    private val world = viewers.firstOrNull()?.world
    private var base: Entity? = null
    private var model: MegModel? = null
    private var spawned = false

    fun spawn() {
        val w = world ?: run {
            plugin.logger.warning("ModelEngine actor: no viewer world available, cannot spawn.")
            return
        }
        val loc = Location(w, initial.x, initial.y, initial.z, initial.yaw, initial.pitch)
        val stand = w.spawn(loc, ArmorStand::class.java) { s ->
            s.isMarker = true
            s.isVisible = false
            s.setGravity(false)
            s.isInvulnerable = true
            s.isSilent = true
            s.isPersistent = false
            s.isCollidable = false
        }
        base = stand
        appearance.displayName?.let { setName(it) }
        val modelId = appearance.model
        if (modelId != null) {
            model = ModelEngineHook.applyModel(stand, modelId, plugin)
            if (model == null) plugin.logger.warning("ModelEngine: could not apply model '$modelId'.")
        }
        spawned = true
    }

    override fun update(frame: ActorFrame) {
        if (!spawned) return
        val w = world ?: return
        base?.teleport(Location(w, frame.x, frame.y, frame.z, frame.yaw, frame.pitch))
    }

    override fun playAnimations(animations: Set<ActorAnimation>) {
        // Vanilla swing/hurt animations don't map onto MEG models; use named model animations via states.
    }

    override fun playModelAnimation(animation: String) {
        model?.let { ModelEngineHook.playAnimation(it, animation, plugin) }
    }

    override fun setEquipment(equipment: ActorEquipment) {
        // Models define their own visuals; vanilla equipment on the invisible base is not rendered.
    }

    override fun setName(name: String?) {
        val entity = base ?: return
        @Suppress("DEPRECATION")
        entity.customName = name
        entity.isCustomNameVisible = name != null
    }

    override fun setFlagOverlay(flags: Set<ActorFlag>) {
        base?.isGlowing = ActorFlag.GLOWING in flags
    }

    override fun addViewer(player: Player) {
        // ModelEngine actors are a real entity, visible to everyone nearby; nothing per-viewer to do.
    }

    override fun removeViewer(player: Player) {
        // See addViewer.
    }

    override fun despawn() {
        if (!spawned) return
        model?.let { ModelEngineHook.destroy(it, plugin) }
        base?.remove()
        base = null
        model = null
        spawned = false
    }
}
