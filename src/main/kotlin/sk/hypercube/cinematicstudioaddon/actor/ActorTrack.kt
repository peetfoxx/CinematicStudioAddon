package sk.hypercube.cinematicstudioaddon.actor

enum class TrackMode { RECORDED, KEYFRAMED }

/** A single authored keyframe with an easing curve toward the *next* keyframe. */
data class Keyframe(val tick: Int, val frame: ActorFrame, val easing: Easing = Easing.LINEAR)

/**
 * A complete actor animation.
 *
 * - [TrackMode.RECORDED]: [frames] holds one frame per tick (index == tick).
 * - [TrackMode.KEYFRAMED]: [keyframes] holds sparse keyframes; [frameAt] interpolates between them.
 */
class ActorTrack(
    val id: String,
    val mode: TrackMode,
    val length: Int,
    val frames: List<ActorFrame> = emptyList(),
    val keyframes: List<Keyframe> = emptyList(),
    /** Appearance captured from the recording player (skin + equipment), used as the default when
     *  this track is added to a scene. Null for hand-authored tracks. */
    val appearance: ActorAppearance? = null
) {
    /** Resolves the actor's frame at [tick], or null if outside the track. */
    fun frameAt(tick: Int): ActorFrame? {
        if (tick < 0 || tick >= length) return null
        return when (mode) {
            TrackMode.RECORDED -> frames.getOrNull(tick)
            TrackMode.KEYFRAMED -> sampleKeyframes(tick)
        }
    }

    private fun sampleKeyframes(tick: Int): ActorFrame? {
        if (keyframes.isEmpty()) return null
        val prev = keyframes.lastOrNull { it.tick <= tick } ?: keyframes.first()
        val next = keyframes.firstOrNull { it.tick > tick } ?: return prev.frame
        val span = (next.tick - prev.tick).coerceAtLeast(1)
        val t = applyEasing(next.easing, (tick - prev.tick).toFloat() / span)
        return interpolate(prev.frame, next.frame, t)
    }

    companion object {
        fun applyEasing(easing: Easing, t: Float): Float = when (easing) {
            Easing.LINEAR -> t
            Easing.EASE_IN -> t * t
            Easing.EASE_OUT -> 1f - (1f - t) * (1f - t)
            Easing.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
        }

        /**
         * Position/rotation are interpolated; discrete state (pose/flags) snaps to the source frame
         * and one-shot animations only fire on exact keyframe ticks (so they aren't replayed mid-tween).
         */
        fun interpolate(a: ActorFrame, b: ActorFrame, t: Float): ActorFrame = ActorFrame(
            x = lerp(a.x, b.x, t),
            y = lerp(a.y, b.y, t),
            z = lerp(a.z, b.z, t),
            yaw = lerpAngle(a.yaw, b.yaw, t),
            pitch = lerpAngle(a.pitch, b.pitch, t),
            headYaw = lerpAngle(a.headYaw, b.headYaw, t),
            pose = a.pose,
            flags = a.flags,
            animations = emptySet()
        )

        private fun lerp(a: Double, b: Double, t: Float): Double = a + (b - a) * t

        /** Shortest-path angle interpolation in degrees. */
        private fun lerpAngle(a: Float, b: Float, t: Float): Float {
            var delta = (b - a) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return a + delta * t
        }
    }
}
