package sk.hypercube.cinematicstudioaddon.actor

/** What kind of entity an actor is rendered as. Extend as backends gain support. */
enum class ActorEntityType { PLAYER, ZOMBIE, ARMOR_STAND }

/** Persistent body pose, applied via entity metadata. */
enum class ActorPose { STANDING, SNEAKING, SWIMMING, SLEEPING, SITTING, FALL_FLYING }

/** Persistent boolean states, applied via entity metadata. */
enum class ActorFlag { ON_FIRE, GLOWING, INVISIBLE, USING_ITEM }

/** One-shot animations, sent on the exact tick a frame is shown (animation / status packets). */
enum class ActorAnimation { SWING_MAIN_HAND, SWING_OFF_HAND, TAKE_DAMAGE, CRITICAL_HIT, MAGIC_CRITICAL_HIT }

/** Interpolation curve between keyframes. */
enum class Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }
