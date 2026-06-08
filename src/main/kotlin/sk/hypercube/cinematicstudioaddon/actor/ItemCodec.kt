package sk.hypercube.cinematicstudioaddon.actor

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Base64 (de)serialization of [ItemStack]s for storing actor equipment in config. */
object ItemCodec {
    fun encode(item: ItemStack?): String? {
        if (item == null || item.type.isAir) return null
        return try {
            val bytes = ByteArrayOutputStream()
            BukkitObjectOutputStream(bytes).use { it.writeObject(item) }
            Base64.getEncoder().encodeToString(bytes.toByteArray())
        } catch (ex: Exception) {
            null
        }
    }

    fun decode(data: String?): ItemStack? {
        if (data.isNullOrBlank()) return null
        return try {
            val bytes = Base64.getDecoder().decode(data)
            BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ItemStack }
        } catch (ex: Exception) {
            null
        }
    }
}
