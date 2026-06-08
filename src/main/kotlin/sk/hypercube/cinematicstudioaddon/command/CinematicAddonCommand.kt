package sk.hypercube.cinematicstudioaddon.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon
import sk.hypercube.cinematicstudioaddon.scene.Scene
import sk.hypercube.cinematicstudioaddon.scene.SceneActor
import java.io.File

/**
 * `/cinaddon` — top-level command.
 *
 *  play  <cinematic> <player|@a>          play a CinematicStudio cinematic for player(s)
 *  stop  <cinematic> <player|@a>          stop a cinematic for player(s)
 *  actor record <track>                   start recording your movement (players only)
 *  actor stop                             save the recording
 *  actor list | delete <track>
 *  scene create <scene> [cinematic]       create a scene in your current world (players only)
 *  scene delete <scene> | list | info <scene>
 *  scene addactor <scene> <track> [startTick]
 *  scene removeactor <scene> <index>
 *  scene play <scene> <player|@a>         play cinematic + actors together
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
            "actor" -> handleActor(sender, label, args)
            "scene" -> handleScene(sender, label, args)
            else -> sendUsage(sender, label)
        }
        return true
    }

    // --- play / stop a bare cinematic -----------------------------------------------------------

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
        val targets = resolveTargets(sender, args[2]) ?: return
        val cinematic = args[1]
        var ok = 0
        for (t in targets) {
            val success = if (action == "play") plugin.bridge.playFor(t, cinematic) else plugin.bridge.stopFor(t, cinematic)
            if (success) ok++
        }
        val verb = if (action == "play") "Playing" else "Stopping"
        sender.sendMessage("§a$verb §e$cinematic§a for §e$ok§a player(s).")
    }

    // --- actor recording ------------------------------------------------------------------------

    private fun handleActor(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "record" -> {
                val player = sender as? Player ?: return sender.sendMessage("§cOnly players can record.")
                val track = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label actor record <track>")
                if (plugin.actorRecorder.start(player, track)) {
                    sender.sendMessage("§aRecording track §e$track§a. Move around, then §e/$label actor stop§a.")
                } else {
                    sender.sendMessage("§cYou are already recording. Use §e/$label actor stop§c first.")
                }
            }
            "stop" -> {
                val player = sender as? Player ?: return sender.sendMessage("§cOnly players can record.")
                val track = plugin.actorRecorder.stop(player)
                    ?: return sender.sendMessage("§cYou are not recording.")
                plugin.sceneManager.saveTrack(track)
                sender.sendMessage("§aSaved track §e${track.id}§a (§e${track.length}§a ticks ≈ §e${track.length / 20}§as).")
            }
            "list" -> {
                val ids = plugin.sceneManager.trackIds()
                sender.sendMessage(if (ids.isEmpty()) "§7No tracks recorded yet." else "§6Tracks: §f${ids.joinToString(", ")}")
            }
            "delete" -> {
                val track = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label actor delete <track>")
                if (plugin.sceneManager.deleteTrack(track)) sender.sendMessage("§aDeleted track §e$track§a.")
                else sender.sendMessage("§cNo such track: §e$track§c.")
            }
            else -> {
                sender.sendMessage("§e/$label actor record <track> §7- start recording your movement")
                sender.sendMessage("§e/$label actor stop §7- save the recording")
                sender.sendMessage("§e/$label actor list §7| §edelete <track>")
            }
        }
    }

    // --- scenes ---------------------------------------------------------------------------------

    private fun handleScene(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "create" -> {
                val player = sender as? Player ?: return sender.sendMessage("§cOnly players can create scenes (world is taken from your location).")
                val id = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label scene create <scene> [cinematic]")
                if (plugin.sceneManager.getScene(id) != null) return sender.sendMessage("§cScene §e$id§c already exists.")
                val cinematic = args.getOrNull(3)
                plugin.sceneManager.saveScene(Scene(id = id, world = player.world.name, cinematic = cinematic))
                sender.sendMessage("§aCreated scene §e$id§a${if (cinematic != null) " (cinematic §e$cinematic§a)" else ""}.")
            }
            "delete" -> {
                val id = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label scene delete <scene>")
                if (plugin.sceneManager.deleteScene(id)) sender.sendMessage("§aDeleted scene §e$id§a.")
                else sender.sendMessage("§cNo such scene: §e$id§c.")
            }
            "list" -> {
                val ids = plugin.sceneManager.sceneIds()
                sender.sendMessage(if (ids.isEmpty()) "§7No scenes yet." else "§6Scenes: §f${ids.joinToString(", ")}")
            }
            "info" -> {
                val scene = args.getOrNull(2)?.let { plugin.sceneManager.getScene(it) }
                    ?: return sender.sendMessage("§cUsage: §e/$label scene info <scene>")
                sender.sendMessage("§6Scene §e${scene.id}§7 (world §f${scene.world}§7)")
                sender.sendMessage("§7  cinematic: §f${scene.cinematic ?: "none"}  §7trackable: §f${scene.trackable}  §7offset: §f${scene.actorCameraOffsetTicks}")
                if (scene.actors.isEmpty()) sender.sendMessage("§7  actors: none")
                else scene.actors.forEachIndexed { i, a ->
                    sender.sendMessage("§7  [$i] track §f${a.trackId}§7 start §f${a.startTick}§7 origin §f${a.origin.mode}")
                }
            }
            "addactor" -> {
                val scene = args.getOrNull(2)?.let { plugin.sceneManager.getScene(it) }
                    ?: return sender.sendMessage("§cUsage: §e/$label scene addactor <scene> <track> [startTick]")
                val trackId = args.getOrNull(3) ?: return sender.sendMessage("§cUsage: §e/$label scene addactor <scene> <track> [startTick]")
                if (plugin.sceneManager.getTrack(trackId) == null) return sender.sendMessage("§cNo such track: §e$trackId§c.")
                val startTick = args.getOrNull(4)?.toIntOrNull() ?: 0
                val updated = scene.copy(actors = scene.actors + SceneActor(trackId = trackId, startTick = startTick))
                plugin.sceneManager.saveScene(updated)
                sender.sendMessage("§aAdded actor (track §e$trackId§a, start §e$startTick§a) to scene §e${scene.id}§a.")
            }
            "removeactor" -> {
                val scene = args.getOrNull(2)?.let { plugin.sceneManager.getScene(it) }
                    ?: return sender.sendMessage("§cUsage: §e/$label scene removeactor <scene> <index>")
                val index = args.getOrNull(3)?.toIntOrNull()
                    ?: return sender.sendMessage("§cUsage: §e/$label scene removeactor <scene> <index>")
                if (index !in scene.actors.indices) return sender.sendMessage("§cIndex out of range (0..${scene.actors.size - 1}).")
                val updated = scene.copy(actors = scene.actors.filterIndexed { i, _ -> i != index })
                plugin.sceneManager.saveScene(updated)
                sender.sendMessage("§aRemoved actor §e[$index]§a from scene §e${scene.id}§a.")
            }
            "play" -> {
                val scene = args.getOrNull(2)?.let { plugin.sceneManager.getScene(it) }
                    ?: return sender.sendMessage("§cUsage: §e/$label scene play <scene> <player|@a>")
                val target = args.getOrNull(3) ?: return sender.sendMessage("§cUsage: §e/$label scene play <scene> <player|@a>")
                val targets = resolveTargets(sender, target) ?: return
                plugin.sceneManager.play(scene, targets)
                sender.sendMessage("§aPlaying scene §e${scene.id}§a for §e${targets.size}§a player(s).")
            }
            else -> {
                sender.sendMessage("§e/$label scene create <scene> [cinematic] §7| §edelete <scene> §7| §elist §7| §einfo <scene>")
                sender.sendMessage("§e/$label scene addactor <scene> <track> [startTick] §7| §eremoveactor <scene> <index>")
                sender.sendMessage("§e/$label scene play <scene> <player|@a>")
            }
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Resolves a player target arg (`@a`/`--all` = everyone online). Messages and returns null on failure. */
    private fun resolveTargets(sender: CommandSender, arg: String): List<Player>? {
        if (arg.equals("@a", true) || arg.equals("--all", true)) {
            val all = Bukkit.getOnlinePlayers().toList()
            if (all.isEmpty()) { sender.sendMessage("§cNo players online."); return null }
            return all
        }
        val player = Bukkit.getPlayerExact(arg)
        if (player == null) { sender.sendMessage("§cPlayer '§e$arg§c' is not online."); return null }
        return listOf(player)
    }

    private fun sendUsage(sender: CommandSender, label: String) {
        sender.sendMessage("§6CinematicStudioAddon")
        sender.sendMessage("§e/$label play|stop <cinematic> <player|@a> §7- direct cinematic playback")
        sender.sendMessage("§e/$label actor ... §7- record movement tracks")
        sender.sendMessage("§e/$label scene ... §7- build & play scenes (cinematic + actors)")
    }

    // --- tab completion -------------------------------------------------------------------------

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if (!sender.hasPermission(permission)) return mutableListOf()
        val out: List<String> = when (args[0].lowercase()) {
            "play", "stop" -> when (args.size) {
                1 -> listOf("play", "stop")
                2 -> listCinematics()
                3 -> playerTargets()
                else -> emptyList()
            }
            "actor" -> when (args.size) {
                1 -> listOf("actor")
                2 -> listOf("record", "stop", "list", "delete")
                3 -> if (args[1].equals("delete", true)) plugin.sceneManager.trackIds() else emptyList()
                else -> emptyList()
            }
            "scene" -> when (args.size) {
                1 -> listOf("scene")
                2 -> listOf("create", "delete", "list", "info", "addactor", "removeactor", "play")
                3 -> when (args[1].lowercase()) {
                    "delete", "info", "addactor", "removeactor", "play" -> plugin.sceneManager.sceneIds()
                    else -> emptyList()
                }
                4 -> when (args[1].lowercase()) {
                    "create" -> listCinematics()
                    "addactor" -> plugin.sceneManager.trackIds()
                    "play" -> playerTargets()
                    else -> emptyList()
                }
                else -> emptyList()
            }
            else -> if (args.size == 1) listOf("play", "stop", "actor", "scene") else emptyList()
        }
        val prefix = args.last()
        return out.filter { it.startsWith(prefix, ignoreCase = true) }.toMutableList()
    }

    private fun playerTargets(): List<String> = Bukkit.getOnlinePlayers().map { it.name } + "@a"

    /**
     * Best-effort listing of CinematicStudio cinematic names for tab completion, read from its data
     * folder. There is no public API to enumerate them, so this reads on-disk files defensively.
     */
    private fun listCinematics(): List<String> {
        val cs = Bukkit.getPluginManager().getPlugin("CinematicStudio") ?: return emptyList()
        val dir = listOf(File(cs.dataFolder, "cinematics"), File(cs.dataFolder, "Cinematics"))
            .firstOrNull { it.isDirectory } ?: return emptyList()
        return dir.listFiles()
            ?.map { if (it.isFile && it.name.contains('.')) it.name.substringBeforeLast('.') else it.name }
            ?.distinct()?.sorted() ?: emptyList()
    }
}
