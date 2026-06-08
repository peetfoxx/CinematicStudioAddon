package sk.hypercube.cinematicstudioaddon.playback

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/** Cleans up active actor sessions when a viewer disconnects. */
class QuitListener(private val actorManager: ActorManager) : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        actorManager.handleQuit(event.player)
    }
}
