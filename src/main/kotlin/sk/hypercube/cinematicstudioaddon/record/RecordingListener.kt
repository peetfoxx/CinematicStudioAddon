package sk.hypercube.cinematicstudioaddon.record

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon

/** Lets a recording player press F (swap-hand key) to stop and save the recording. */
class RecordingListener(private val plugin: CinematicStudioAddon) : Listener {

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!plugin.actorRecorder.isRecording(player)) return
        event.isCancelled = true // don't actually swap items
        val track = plugin.finishRecording(player) ?: return
        player.sendMessage("§aSaved track §e${track.id}§a (§e${track.length}§a ticks ≈ §e${track.length / 20}§as).")
    }
}
