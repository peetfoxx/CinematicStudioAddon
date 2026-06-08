package sk.hypercube.cinematicstudioaddon.actor.packet

import java.util.concurrent.atomic.AtomicInteger

/**
 * Allocates entity ids for fake actors. Starts from a high base to avoid colliding with the
 * server's real entities (which count up from low values).
 */
internal object EntityIds {
    private val counter = AtomicInteger(2_000_000_000)
    fun next(): Int = counter.getAndIncrement()
}
