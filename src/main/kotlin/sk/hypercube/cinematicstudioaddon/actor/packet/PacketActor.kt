package sk.hypercube.cinematicstudioaddon.actor.packet

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.Pair
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.comphenix.protocol.wrappers.WrappedSignedProperty
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import sk.hypercube.cinematicstudioaddon.actor.ActorAnimation
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
import sk.hypercube.cinematicstudioaddon.actor.ActorFlag
import sk.hypercube.cinematicstudioaddon.actor.ActorFrame
import sk.hypercube.cinematicstudioaddon.actor.ActorHandle
import sk.hypercube.cinematicstudioaddon.actor.ActorPose
import sk.hypercube.cinematicstudioaddon.actor.ItemResolver
import java.util.EnumSet
import java.util.Optional
import java.util.UUID

/**
 * A single packet-based actor (PLAYER or any other entity type), rendered only for its viewers.
 *
 * Target: Minecraft 1.20.5 - 1.21.x. Movement uses REL_ENTITY_MOVE_LOOK; jumps beyond a short delta
 * (~8 blocks/tick) fall back to destroy + respawn. Shared-data metadata indices used: 0 flags,
 * 2 custom name, 3 custom-name-visible, 17 skin parts (PLAYER only).
 *
 * All work happens on the main thread (the ActorSession tick task), so the viewer set needs no locks.
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
    private val isPlayer = appearance.entityType.equals("PLAYER", ignoreCase = true)
    private val bukkitType = resolveEntityType(appearance.entityType)
    private val profile: WrappedGameProfile? = if (isPlayer) buildProfile() else null

    private val byteSerializer = WrappedDataWatcher.Registry.get(java.lang.Byte::class.java)
    private val boolSerializer = WrappedDataWatcher.Registry.get(java.lang.Boolean::class.java)
    private val optChatSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer(true)

    private var lastFrame: ActorFrame = initial
    private var lastStateByte: Byte = stateByte(initial.flags, initial.pose)
    private var overlayFlags: Set<ActorFlag> = emptySet()
    private var currentName: String? = appearance.displayName
    private var currentEquipment: ActorEquipment = appearance.equipment
    private var spawned = false

    // --- lifecycle ------------------------------------------------------------------------------

    fun spawn() {
        viewers.toList().forEach { spawnFor(it, lastFrame) }
        spawned = true
    }

    private fun spawnFor(viewer: Player, frame: ActorFrame) {
        if (isPlayer) send(viewer, addPlayerInfoPacket())
        send(viewer, spawnPacket(frame))
        send(viewer, metadataPacket(frame))
        equipmentPacket()?.let { send(viewer, it) }
        send(viewer, headRotationPacket(angle(frame.headYaw)))
    }

    override fun despawn() {
        if (!spawned) return
        val destroy = destroyPacket()
        val remove = if (isPlayer) removePlayerInfoPacket() else null
        viewers.toList().forEach { v -> send(v, destroy); remove?.let { send(v, it) } }
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
            if (isPlayer) send(player, removePlayerInfoPacket())
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
            viewers.toList().forEach { send(it, destroyPacket()); spawnFor(it, frame) }
        }

        val head = headRotationPacket(angle(frame.headYaw))
        viewers.toList().forEach { send(it, head) }

        val newState = stateByte(frame.flags + overlayFlags, frame.pose)
        if (newState != lastStateByte) {
            lastStateByte = newState
            sendMetadata(frame)
        }

        lastFrame = frame
    }

    override fun playAnimations(animations: Set<ActorAnimation>) {
        if (!spawned) return
        for (animation in animations) {
            val id = when (animation) {
                ActorAnimation.SWING_MAIN_HAND -> 0
                ActorAnimation.SWING_OFF_HAND -> 3
                else -> null
            } ?: continue
            val packet = animationPacket(id)
            viewers.toList().forEach { send(it, packet) }
        }
    }

    override fun playModelAnimation(animation: String) {
        // Packet actors have no model; ModelEngine animations only apply to ModelEngineActor.
    }

    // --- state changes --------------------------------------------------------------------------

    override fun setEquipment(equipment: ActorEquipment) {
        currentEquipment = equipment
        if (spawned) equipmentPacket()?.let { p -> viewers.toList().forEach { send(it, p) } }
    }

    override fun setName(name: String?) {
        currentName = name
        if (spawned) sendMetadata(lastFrame)
    }

    override fun setFlagOverlay(flags: Set<ActorFlag>) {
        overlayFlags = flags
        lastStateByte = stateByte(lastFrame.flags + overlayFlags, lastFrame.pose)
        if (spawned) sendMetadata(lastFrame)
    }

    private fun sendMetadata(frame: ActorFrame) {
        val packet = metadataPacket(frame)
        viewers.toList().forEach { send(it, packet) }
    }

    // --- packet builders ------------------------------------------------------------------------

    private fun addPlayerInfoPacket(): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO)
        packet.playerInfoActions.write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER))
        val data = PlayerInfoData(
            profile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
            WrappedChatComponent.fromText(profile?.name ?: "Actor")
        )
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
        packet.entityTypeModifier.write(0, bukkitType)
        packet.doubles.write(0, frame.x).write(1, frame.y).write(2, frame.z)
        packet.bytes.write(0, angle(frame.pitch)).write(1, angle(frame.yaw))
        return packet
    }

    private fun metadataPacket(frame: ActorFrame): PacketContainer {
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, entityId)
        val values = ArrayList<WrappedDataValue>()
        values.add(WrappedDataValue(0, byteSerializer, stateByte(frame.flags + overlayFlags, frame.pose)))
        val nameComponent: Optional<Any> = currentName?.let { Optional.of(WrappedChatComponent.fromText(it).handle) } ?: Optional.empty()
        values.add(WrappedDataValue(2, optChatSerializer, nameComponent))
        values.add(WrappedDataValue(3, boolSerializer, currentName != null))
        if (isPlayer) values.add(WrappedDataValue(17, byteSerializer, 0x7F.toByte())) // displayed skin parts
        packet.dataValueCollectionModifier.write(0, values)
        return packet
    }

    /** Builds an ENTITY_EQUIPMENT packet for all non-empty slots, or null if the actor is unequipped. */
    private fun equipmentPacket(): PacketContainer? {
        val pairs = ArrayList<Pair<EnumWrappers.ItemSlot, ItemStack>>()
        fun add(slot: EnumWrappers.ItemSlot, spec: String?) {
            ItemResolver.resolve(spec)?.let { pairs.add(Pair(slot, it)) }
        }
        add(EnumWrappers.ItemSlot.HEAD, currentEquipment.helmet)
        add(EnumWrappers.ItemSlot.CHEST, currentEquipment.chestplate)
        add(EnumWrappers.ItemSlot.LEGS, currentEquipment.leggings)
        add(EnumWrappers.ItemSlot.FEET, currentEquipment.boots)
        add(EnumWrappers.ItemSlot.MAINHAND, currentEquipment.mainHand)
        add(EnumWrappers.ItemSlot.OFFHAND, currentEquipment.offHand)
        if (pairs.isEmpty()) return null
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT)
        packet.integers.write(0, entityId)
        packet.slotStackPairLists.write(0, pairs)
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

    private fun resolveEntityType(name: String): EntityType =
        runCatching { EntityType.valueOf(name.uppercase()) }.getOrElse {
            plugin.logger.warning("Unknown actor entity type '$name', falling back to PLAYER.")
            EntityType.PLAYER
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

    /** Shared-entity-flags metadata byte (index 0). */
    private fun stateByte(flags: Set<ActorFlag>, pose: ActorPose): Byte {
        var bits = 0
        if (ActorFlag.ON_FIRE in flags) bits = bits or 0x01
        if (pose == ActorPose.SNEAKING) bits = bits or 0x02
        if (ActorFlag.INVISIBLE in flags) bits = bits or 0x20
        if (ActorFlag.GLOWING in flags) bits = bits or 0x40
        return bits.toByte()
    }

    /** Degrees -> Minecraft protocol angle byte (256 units per full turn). */
    private fun angle(degrees: Float): Byte = Math.round(degrees * 256f / 360f).toByte()
}
