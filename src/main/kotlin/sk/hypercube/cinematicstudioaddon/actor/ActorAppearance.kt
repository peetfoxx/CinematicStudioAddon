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
    val entityType: ActorEntityType = ActorEntityType.PLAYER,
    val displayName: String? = null,
    /** Base64 "textures" property value for PLAYER actors (from a Mojang profile). */
    val skinTextureValue: String? = null,
    val skinSignature: String? = null,
    val equipment: ActorEquipment = ActorEquipment()
)
