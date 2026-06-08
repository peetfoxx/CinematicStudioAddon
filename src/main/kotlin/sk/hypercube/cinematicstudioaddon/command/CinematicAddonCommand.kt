package sk.hypercube.cinematicstudioaddon.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import sk.hypercube.cinematicstudioaddon.CinematicStudioAddon
import sk.hypercube.cinematicstudioaddon.actor.ActorAppearance
import sk.hypercube.cinematicstudioaddon.actor.ActorEquipment
import java.io.File

/**
 * `/cinaddon` — top-level command.
 *
 *  play  <cinematic> <player|@a>          play a CinematicStudio cinematic for player(s)
 *  stop  <cinematic> <player|@a>          stop a cinematic for player(s)
 *  actor record <track>                   start recording your movement (players only)
 *  actor stop                             save the recording
 *  actor list | delete <track>
 *  spawnactors <track> <player|@a>        spawn a recorded actor for player(s)
 *
 * The intended way to put an actor in a cinematic is a CinematicStudio COMMAND node:
 *   command: cinaddon spawnactors <track> %player%   (console: true)
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
            "spawnactors" -> handleSpawnActors(sender, label, args)
            "state" -> handleState(sender, label, args)
            "reload" -> {
                val count = plugin.actorManager.reload()
                sender.sendMessage("§aReloaded §e$count§a actor track(s) from disk.")
            }
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
                    sender.sendMessage("§aRecording track §e$track§a. Move around, then press §eF§a (or §e/$label actor stop§a).")
                } else {
                    sender.sendMessage("§cYou are already recording. Press §eF§c or §e/$label actor stop§c first.")
                }
            }
            "stop" -> {
                val player = sender as? Player ?: return sender.sendMessage("§cOnly players can record.")
                val track = plugin.finishRecording(player)
                    ?: return sender.sendMessage("§cYou are not recording.")
                sender.sendMessage("§aSaved track §e${track.id}§a (§e${track.length}§a ticks ≈ §e${track.length / 20}§as).")
            }
            "list" -> {
                val ids = plugin.actorManager.trackIds()
                sender.sendMessage(if (ids.isEmpty()) "§7No tracks recorded yet." else "§6Tracks: §f${ids.joinToString(", ")}")
            }
            "delete" -> {
                val track = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label actor delete <track>")
                if (plugin.actorManager.deleteTrack(track)) sender.sendMessage("§aDeleted track §e$track§a.")
                else sender.sendMessage("§cNo such track: §e$track§c.")
            }
            "set" -> handleActorSet(sender, label, args)
            else -> {
                sender.sendMessage("§e/$label actor record <track> §7- start recording your movement")
                sender.sendMessage("§e/$label actor stop §7- save the recording")
                sender.sendMessage("§e/$label actor list §7| §edelete <track>")
                sender.sendMessage("§e/$label actor set <track> <field> <value> §7- customize an actor")
            }
        }
    }

    private val equipmentSlots = setOf("mainhand", "offhand", "helmet", "chestplate", "leggings", "boots")

    /** `/cinaddon actor set <track> <field> <value>` — customize a track's appearance. */
    private fun handleActorSet(sender: CommandSender, label: String, args: Array<out String>) {
        val trackId = args.getOrNull(2) ?: return sendSetUsage(sender, label)
        val track = plugin.actorManager.getTrack(trackId) ?: return sender.sendMessage("§cNo such track: §e$trackId§c.")
        val field = args.getOrNull(3)?.lowercase() ?: return sendSetUsage(sender, label)
        val rawValue = args.drop(4).joinToString(" ").ifBlank { null }
        val clearable = rawValue == null || rawValue.equals("none", ignoreCase = true)
        val value = if (clearable) null else rawValue

        val current = track.appearance ?: ActorAppearance()
        val updated: ActorAppearance = when (field) {
            "entitytype" -> {
                if (value == null) return sender.sendMessage("§cProvide an entity type, e.g. ZOMBIE.")
                if (runCatching { EntityType.valueOf(value.uppercase()) }.isFailure)
                    return sender.sendMessage("§cUnknown entity type: §e$value§c.")
                current.copy(entityType = value.uppercase())
            }
            "model" -> current.copy(model = value)
            "name" -> current.copy(displayName = value)
            "skin" -> {
                if (value == null) current.copy(skinTextureValue = null, skinSignature = null)
                else {
                    val source = Bukkit.getPlayerExact(value)
                        ?: return sender.sendMessage("§cPlayer '§e$value§c' is not online.")
                    val textures = source.playerProfile.properties.firstOrNull { it.name == "textures" }
                        ?: return sender.sendMessage("§cCould not read §e$value§c's skin.")
                    current.copy(skinTextureValue = textures.value, skinSignature = textures.signature)
                }
            }
            in equipmentSlots -> current.copy(equipment = setSlot(current.equipment, field, value))
            else -> return sendSetUsage(sender, label)
        }

        plugin.actorManager.saveTrack(track.withAppearance(updated))
        sender.sendMessage("§aSet §e$field§a on track §e$trackId§a to §f${value ?: "none"}§a.")
    }

    private fun setSlot(eq: ActorEquipment, slot: String, value: String?): ActorEquipment = when (slot) {
        "mainhand" -> eq.copy(mainHand = value)
        "offhand" -> eq.copy(offHand = value)
        "helmet" -> eq.copy(helmet = value)
        "chestplate" -> eq.copy(chestplate = value)
        "leggings" -> eq.copy(leggings = value)
        "boots" -> eq.copy(boots = value)
        else -> eq
    }

    private fun sendSetUsage(sender: CommandSender, label: String) {
        sender.sendMessage("§cUsage: §e/$label actor set <track> <field> <value>")
        sender.sendMessage("§7fields: §fentitytype, model, name, skin, mainhand, offhand, helmet, chestplate, leggings, boots")
        sender.sendMessage("§7use §fnone§7 as the value to clear a field; §fskin <player>§7 copies an online player's skin")
    }

    // --- spawn an actor (typically called from a CinematicStudio COMMAND node) -------------------

    private fun handleSpawnActors(sender: CommandSender, label: String, args: Array<out String>) {
        val trackId = args.getOrNull(1) ?: return sender.sendMessage("§cUsage: §e/$label spawnactors <track> <player|@a>")
        val track = plugin.actorManager.getTrack(trackId) ?: return sender.sendMessage("§cNo such track: §e$trackId§c.")
        val targetArg = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label spawnactors <track> <player|@a>")
        val targets = resolveTargets(sender, targetArg) ?: return
        if (!plugin.actorManager.spawn(track, targets)) {
            sender.sendMessage("§cActor playback is unavailable (ProtocolLib not installed).")
        }
        // Success is intentionally silent: this command is usually fired by a console COMMAND node
        // once per viewer, and we don't want to spam the console.
    }

    // --- trigger a named state on a live actor (typically from a COMMAND node) -------------------

    private fun handleState(sender: CommandSender, label: String, args: Array<out String>) {
        val trackId = args.getOrNull(1) ?: return sender.sendMessage("§cUsage: §e/$label state <track> <player|@a> <state>")
        if (plugin.actorManager.getTrack(trackId) == null) return sender.sendMessage("§cNo such track: §e$trackId§c.")
        val targetArg = args.getOrNull(2) ?: return sender.sendMessage("§cUsage: §e/$label state <track> <player|@a> <state>")
        val stateName = args.getOrNull(3) ?: return sender.sendMessage("§cUsage: §e/$label state <track> <player|@a> <state>")
        val targets = resolveTargets(sender, targetArg) ?: return
        if (!plugin.actorManager.applyState(trackId, targets, stateName)) {
            sender.sendMessage("§cNo such state '§e$stateName§c' on track §e$trackId§c.")
        }
        // Success is silent (typically fired by a console COMMAND node).
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
        sender.sendMessage("§e/$label actor record|stop|list|delete §7- manage movement tracks")
        sender.sendMessage("§e/$label spawnactors <track> <player|@a> §7- spawn a recorded actor")
        sender.sendMessage("§e/$label state <track> <player|@a> <state> §7- trigger an actor state")
        sender.sendMessage("§e/$label reload §7- re-read track files from disk")
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
                2 -> listOf("record", "stop", "list", "delete", "set")
                3 -> if (args[1].equals("delete", true) || args[1].equals("set", true)) plugin.actorManager.trackIds() else emptyList()
                4 -> if (args[1].equals("set", true))
                    listOf("entitytype", "model", "name", "skin") + equipmentSlots
                else emptyList()
                5 -> if (args[1].equals("set", true)) when (args[3].lowercase()) {
                    "entitytype" -> EntityType.values().map { it.name.lowercase() }
                    "skin" -> playerTargets().filterNot { it == "@a" } + "none"
                    else -> listOf("none")
                } else emptyList()
                else -> emptyList()
            }
            "spawnactors" -> when (args.size) {
                1 -> listOf("spawnactors")
                2 -> plugin.actorManager.trackIds()
                3 -> playerTargets()
                else -> emptyList()
            }
            "state" -> when (args.size) {
                1 -> listOf("state")
                2 -> plugin.actorManager.trackIds()
                3 -> playerTargets()
                4 -> plugin.actorManager.stateNames(args[1])
                else -> emptyList()
            }
            else -> if (args.size == 1) listOf("play", "stop", "actor", "spawnactors", "state", "reload") else emptyList()
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
