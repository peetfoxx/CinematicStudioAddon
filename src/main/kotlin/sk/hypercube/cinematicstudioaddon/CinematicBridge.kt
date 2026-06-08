package sk.hypercube.cinematicstudioaddon

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Thin, license-safe bridge to CinematicStudio. We never touch its internals - we only invoke
 * its public `/cin` command.
 */
class CinematicBridge(private val plugin: CinematicStudioAddon) {

    /** CinematicStudio's command node, required by the sender for `/cin` to run. */
    private val cinematicStudioPermission = "cinematicstudio.command"

    fun isCinematicStudioPresent(): Boolean =
        plugin.server.pluginManager.getPlugin("CinematicStudio")?.isEnabled == true

    /**
     * Plays a cinematic FOR [player], regardless of who triggered it (console, command block, etc.).
     *
     * CinematicStudio plays for whoever sent the command, so we dispatch the command *as the target
     * player*. That sidesteps any quirk in its optional `[player]` argument handling and guarantees
     * the cinematic lands on the intended viewer.
     */
    fun playFor(player: Player, cinematic: String): Boolean =
        dispatchAsPlayer(player, "cin play $cinematic")

    /** Stops a cinematic for [player], using the same as-the-player dispatch trick. */
    fun stopFor(player: Player, cinematic: String): Boolean =
        dispatchAsPlayer(player, "cin stop $cinematic")

    /**
     * Dispatches [command] with [player] as the sender. A temporary permission attachment grants
     * the CinematicStudio command node for the duration of the dispatch only, so this works even if
     * the player would not normally have permission to run `/cin` themselves.
     */
    private fun dispatchAsPlayer(player: Player, command: String): Boolean {
        if (!isCinematicStudioPresent()) return false

        val attachment = player.addAttachment(plugin)
        return try {
            attachment.setPermission(cinematicStudioPermission, true)
            Bukkit.dispatchCommand(player, command)
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to dispatch '$command' as ${player.name}: ${ex.message}")
            false
        } finally {
            player.removeAttachment(attachment)
        }
    }
}
