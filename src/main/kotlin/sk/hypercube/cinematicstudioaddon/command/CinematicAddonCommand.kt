package sk.hypercube.cinematicstudioaddon.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon
import java.io.File

/**
 * `/cinaddon play|stop <cinematic> <player|@a>`
 *
 * Works from any sender - console, command block, or player - and always targets the named
 * player(s) rather than the sender.
 */
class CinematicAddonCommand(private val plugin: CinematicStudioAddon) : CommandExecutor, TabCompleter {

    private val permission = "cinematicstudioaddon.admin"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender, label)
            return true
        }

        when (args[0].lowercase()) {
            "play", "stop" -> handlePlayStop(sender, label, args)
            else -> sendUsage(sender, label)
        }
        return true
    }

    private fun handlePlayStop(sender: CommandSender, label: String, args: Array<out String>) {
        val action = args[0].lowercase()

        if (args.size < 3) {
            sender.sendMessage("§cUsage: §e/$label $action <cinematic> <player|@a>")
            return
        }
        if (!plugin.bridge.isCinematicStudioPresent()) {
            sender.sendMessage("§cCinematicStudio is not installed or not enabled on this server.")
            return
        }

        val cinematic = args[1]
        val targetArg = args[2]

        val targets: List<Player> = if (targetArg.equals("@a", true) || targetArg.equals("--all", true)) {
            Bukkit.getOnlinePlayers().toList()
        } else {
            val player = Bukkit.getPlayerExact(targetArg)
            if (player == null) {
                sender.sendMessage("§cPlayer '§e$targetArg§c' is not online.")
                return
            }
            listOf(player)
        }

        if (targets.isEmpty()) {
            sender.sendMessage("§cNo matching online players.")
            return
        }

        var succeeded = 0
        for (target in targets) {
            val ok = if (action == "play") {
                plugin.bridge.playFor(target, cinematic)
            } else {
                plugin.bridge.stopFor(target, cinematic)
            }
            if (ok) succeeded++
        }

        val verb = if (action == "play") "Playing" else "Stopping"
        sender.sendMessage("§a$verb §e$cinematic§a for §e$succeeded§a player(s).")
    }

    private fun sendUsage(sender: CommandSender, label: String) {
        sender.sendMessage("§6CinematicStudioAddon")
        sender.sendMessage("§e/$label play <cinematic> <player|@a> §7- play a cinematic for player(s)")
        sender.sendMessage("§e/$label stop <cinematic> <player|@a> §7- stop a cinematic for player(s)")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): MutableList<String> {
        if (!sender.hasPermission(permission)) return mutableListOf()

        return when (args.size) {
            1 -> listOf("play", "stop").filter { it.startsWith(args[0], true) }.toMutableList()
            2 -> listCinematics().filter { it.startsWith(args[1], true) }.toMutableList()
            3 -> (Bukkit.getOnlinePlayers().map { it.name } + "@a")
                .filter { it.startsWith(args[2], true) }
                .toMutableList()
            else -> mutableListOf()
        }
    }

    /**
     * Best-effort listing of cinematic names for tab completion, read from CinematicStudio's data
     * folder. CinematicStudio has no public API to enumerate cinematics, so this reads the on-disk
     * files defensively; if the layout differs or isn't found, completion is simply empty.
     */
    private fun listCinematics(): List<String> {
        val cs = Bukkit.getPluginManager().getPlugin("CinematicStudio") ?: return emptyList()
        val candidates = listOf(
            File(cs.dataFolder, "cinematics"),
            File(cs.dataFolder, "Cinematics")
        )
        val dir = candidates.firstOrNull { it.isDirectory } ?: return emptyList()

        return dir.listFiles()
            ?.map { if (it.isFile && it.name.contains('.')) it.name.substringBeforeLast('.') else it.name }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }
}
