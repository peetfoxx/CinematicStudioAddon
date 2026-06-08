package sk.hypercube.cinematicstudioaddon.actor

/** Equipment slots, each a serialized item (null = empty). Serialization format TBD at impl time. */
data class ActorEquipment(
    val mainHand: String? = null,
    val offHand: String? = null,
    val helmet: String? = null,
    val chestplate: String? = null,
    val leggings: String? = null,
    val boots: String? = null
)

/** Static, non-animated properties of an actor: how it looks. */
data class ActorAppearance(
    /** Bukkit [org.bukkit.entity.EntityType] name (e.g. "PLAYER", "ZOMBIE", "ARMOR_STAND"). */
    val entityType: String = "PLAYER",
    /** Profile name for PLAYER actors; custom name tag for other entity types. */
    val displayName: String? = null,
    /** Base64 "textures" property value for PLAYER actors (from a Mojang profile). */
    val skinTextureValue: String? = null,
    val skinSignature: String? = null,
    /**
     * ModelEngine model (blueprint) id. When set and ModelEngine is installed, the actor is rendered
     * as that model on a real base entity (visible to everyone nearby) instead of a packet entity.
     */
    val model: String? = null,
    /**
     * Each slot is an item spec: a captured base64 ItemStack, an ItemsAdder id
     * (`itemsadder:namespace:id` or `ia:...`), or a material name (`mc:DIAMOND_SWORD` or `STONE`).
     * Resolved by [ItemResolver].
     */
    val equipment: ActorEquipment = ActorEquipment()
)
