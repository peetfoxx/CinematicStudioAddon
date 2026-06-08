package sk.hypercube.cinematicstudioaddon.actor.packet

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.comphenix.protocol.wrappers.WrappedSignedProperty
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import java.util.EnumSet
import java.util.UUID

/**
 * A single packet-based PLAYER actor, rendered only for its viewers as a client-side fake entity.
 *
 * Target: Minecraft 1.20.5 - 1.21.x.
 * - Movement uses REL_ENTITY_MOVE_LOOK (stable across versions); jumps beyond a short delta
 *   (~8 blocks/tick) fall back to a destroy + respawn at the new position.
 * - The skin-parts metadata index (17) and shared-flags index (0) are the 1.20.5+ values.
 *
 * All Bukkit/packet work happens on the main thread (the SceneSession tick task), so no
 * synchronization is needed on the viewer set.
 */
class PacketActor(
    private val plugin: Plugin,
    private val protocol: ProtocolManager,
    private val viewers: MutableSet<Player>,
    private val appearance: ActorAppearance,
    initial: ActorFrame
) : ActorHandle {

    private val entityId = EntityIds.next()
    private val uuid: UUID = UUID.randomUUID()
    private val profile: WrappedGameProfile = buildProfile()
    private val byteSerializer = WrappedDataWatcher.Registry.get(java.lang.Byte::class.java)

    private var lastFrame: ActorFrame = initial
    private var lastStateByte: Byte = stateByte(initial)
    private var spawned = false

    // --- lifecycle ------------------------------------------------------------------------------

    fun spawn() {
        viewers.toList().forEach { spawnFor(it, lastFrame) }
        spawned = true
    }

    private fun spawnFor(viewer: Player, frame: ActorFrame) {
        send(viewer, addPlayerInfoPacket())
        send(viewer, spawnPacket(frame))
        send(viewer, metadataPacket(stateByte(frame)))
        send(viewer, headRotationPacket(angle(frame.headYaw)))
    }

    override fun despawn() {
        if (!spawned) return
        val destroy = destroyPacket()
        val remove = removePlayerInfoPacket()
        viewers.toList().forEach { send(it, destroy); send(it, remove) }
        spawned = false
    }

    override fun addViewer(player: Player) {
        if (!viewers.add(player)) return
        if (spawned) spawnFor(player, lastFrame)
    }

    override fun removeViewer(player: Player) {
        if (!viewers.remove(player)) return
        if (spawned) {
            send(player, destroyPacket())
            send(player, removePlayerInfoPacket())
        }
    }

    // --- per-tick updates -----------------------------------------------------------------------

    override fun update(frame: ActorFrame) {
        if (!spawned) return

        val dx = ((frame.x - lastFrame.x) * 4096).toInt()
        val dy = ((frame.y - lastFrame.y) * 4096).toInt()
        val dz = ((frame.z - lastFrame.z) * 4096).toInt()
        val shortRange = Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()

        if (dx in shortRange && dy in shortRange && dz in shortRange) {
            val move = moveLookPacket(dx.toShort(), dy.toShort(), dz.toShort(), angle(frame.yaw), angle(frame.pitch))
            viewers.toList().forEach { send(it, move) }
        } else {
            // Too far for a relative move: snap by respawning at the new location.
            viewers.toList().forEach { send(it, destroyPacket()); spawnFor(it, frame) }
        }

        val head = headRotationPacket(angle(frame.headYaw))
        viewers.toList().forEach { send(it, head) }

        val newState = stateByte(frame)
        if (newState != lastStateByte) {
            lastStateByte = newState
            val meta = metadataPacket(newState)
            viewers.toList().forEach { send(it, meta) }
        }

        lastFrame = frame
    }

    override fun playAnimations(animations: Set<ActorAnimation>) {
        if (!spawned) return
        for (animation in animations) {
            val id = when (animation) {
                ActorAnimation.SWING_MAIN_HAND -> 0
                ActorAnimation.SWING_OFF_HAND -> 3
                // TODO(impl): TAKE_DAMAGE / CRITICAL_HIT / MAGIC_CRITICAL_HIT via ENTITY_STATUS or the
                //  1.19.4+ hurt-animation packet.
                else -> null
            } ?: continue
            val packet = animationPacket(id)
            viewers.toList().forEach { send(it, packet) }
        }
    }

    // --- packet builders ------------------------------------------------------------------------

    private fun addPlayerInfoPacket(): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO)
        packet.playerInfoActions.write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER))
        val data = PlayerInfoData(
            profile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
            WrappedChatComponent.fromText(profile.name)
        )
        // 1.19.3+: the data list lives at index 1 of the data-list modifier.
        packet.playerInfoDataLists.write(1, listOf(data))
        return packet
    }

    private fun removePlayerInfoPacket(): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE)
        packet.getUUIDLists().write(0, listOf(uuid))
        return packet
    }

    private fun spawnPacket(frame: ActorFrame): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY)
        packet.integers.write(0, entityId)
        packet.getUUIDs().write(0, uuid)
        packet.entityTypeModifier.write(0, EntityType.PLAYER)
        packet.doubles.write(0, frame.x).write(1, frame.y).write(2, frame.z)
        packet.bytes.write(0, angle(frame.pitch)).write(1, angle(frame.yaw))
        return packet
    }

    private fun metadataPacket(state: Byte): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, entityId)
        val values = listOf(
            WrappedDataValue(0, byteSerializer, state),            // shared entity flags
            WrappedDataValue(17, byteSerializer, 0x7F.toByte())    // displayed skin parts (all layers)
        )
        packet.dataValueCollectionModifier.write(0, values)
        return packet
    }

    private fun moveLookPacket(dx: Short, dy: Short, dz: Short, yaw: Byte, pitch: Byte): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK)
        packet.integers.write(0, entityId)
        packet.shorts.write(0, dx).write(1, dy).write(2, dz)
        packet.bytes.write(0, yaw).write(1, pitch)
        packet.booleans.write(0, true)
        return packet
    }

    private fun headRotationPacket(yaw: Byte): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION)
        packet.integers.write(0, entityId)
        packet.bytes.write(0, yaw)
        return packet
    }

    private fun animationPacket(animationId: Int): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.ANIMATION)
        packet.integers.write(0, entityId).write(1, animationId)
        return packet
    }

    private fun destroyPacket(): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY)
        packet.intLists.write(0, listOf(entityId))
        return packet
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun send(viewer: Player, packet: PacketContainer) {
        try {
            protocol.sendServerPacket(viewer, packet)
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to send ${packet.type} to ${viewer.name}: ${ex.message}")
        }
    }

    private fun buildProfile(): WrappedGameProfile {
        val profile = WrappedGameProfile(uuid, sanitizeName(appearance.displayName))
        val texture = appearance.skinTextureValue
        if (texture != null) {
            profile.properties.put("textures", WrappedSignedProperty("textures", texture, appearance.skinSignature))
        }
        return profile
    }

    private fun sanitizeName(displayName: String?): String {
        val base = (displayName ?: "Actor").replace(Regex("[^A-Za-z0-9_]"), "").ifEmpty { "Actor" }
        return if (base.length > 16) base.substring(0, 16) else base
    }

    /** Shared-entity-flags metadata byte (index 0) for the given frame. */
    private fun stateByte(frame: ActorFrame): Byte {
        var bits = 0
        if (ActorFlag.ON_FIRE in frame.flags) bits = bits or 0x01
        if (frame.pose == ActorPose.SNEAKING) bits = bits or 0x02
        if (ActorFlag.INVISIBLE in frame.flags) bits = bits or 0x20
        if (ActorFlag.GLOWING in frame.flags) bits = bits or 0x40
        // TODO(impl): USING_ITEM (hand-state index 8) and full poses (swimming/sleeping via pose index 6).
        return bits.toByte()
    }

    /** Degrees -> Minecraft protocol angle byte (256 units per full turn). */
    private fun angle(degrees: Float): Byte = Math.round(degrees * 256f / 360f).toByte()
}
