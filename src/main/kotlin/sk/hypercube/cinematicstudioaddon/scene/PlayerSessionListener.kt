package sk.hypercube.cinematicstudioaddon.scene

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/** Cleans up actor sessions when a viewer disconnects. */
class PlayerSessionListener(private val sceneManager: SceneManager) : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sceneManager.handleQuit(event.player)
    }
}
