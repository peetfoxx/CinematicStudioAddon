package sk.hypercube.cinematicstudioaddon.actor

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Resolves an equipment item spec into an [ItemStack]. Accepts, in priority order:
 *  - `itemsadder:<namespace:id>` or `ia:<namespace:id>` — resolved via ItemsAdder (reflection,
 *    soft dependency; returns null if ItemsAdder is absent or the id is unknown)
 *  - `mc:<MATERIAL>` or a bare material name (e.g. `DIAMOND_SWORD`)
 *  - otherwise a base64-encoded ItemStack (as captured by recording) via [ItemCodec]
 */
object ItemResolver {

    fun resolve(spec: String?): ItemStack? {
        if (spec.isNullOrBlank()) return null

        if (spec.startsWith("itemsadder:", true) || spec.startsWith("ia:", true)) {
            return resolveItemsAdder(spec.substringAfter(':'))
        }
        if (spec.startsWith("mc:", true)) {
            return Material.matchMaterial(spec.substringAfter(':'))?.let { ItemStack(it) }
        }
        // A bare material name (only if it isn't actually base64 that happens to match).
        Material.matchMaterial(spec)?.let { return ItemStack(it) }

        return ItemCodec.decode(spec)
    }

    private fun resolveItemsAdder(id: String): ItemStack? {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return null
        return try {
            val customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val instance = customStackClass.getMethod("getInstance", String::class.java).invoke(null, id)
                ?: return null
            customStackClass.getMethod("getItemStack").invoke(instance) as? ItemStack
        } catch (ex: Exception) {
            null
        }
    }
}
