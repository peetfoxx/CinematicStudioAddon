package sk.hypercube.cinematicstudioaddon

import org.bukkit.plugin.java.JavaPlugin
import sk.hypercube.cinematicstudioaddon.command.CinematicAddonCommand
import sk.hypercube.cinematicstudioaddon.actor.ActorTrack
import sk.hypercube.cinematicstudioaddon.playback.ActorManager
import sk.hypercube.cinematicstudioaddon.playback.QuitListener
import sk.hypercube.cinematicstudioaddon.record.ActorRecorder
import sk.hypercube.cinematicstudioaddon.record.RecordingListener

/**
 * CinematicStudioAddon - an open-source companion plugin for LoneDev's CinematicStudio.
 *
 * It does NOT decompile, modify or bundle any part of CinematicStudio. It only drives the
 * plugin through its public command interface, so it works with a legitimately purchased copy.
 *
 * Feature 1 (shipped): make `play`/`stop` reliably target a specific player even when the
 * trigger comes from the console or a command block. CinematicStudio binds playback to the
 * command sender, so we dispatch its command *as the target player* (see [CinematicBridge]).
 *
 * Feature 2 (in progress): a self-contained, per-viewer actor layer (packet NPCs). Actors are
 * recorded as tracks and spawned directly for player(s) — typically from a CinematicStudio COMMAND
 * node (`cinaddon spawnactors <track> %player%`), so the cinematic's timeline does the sequencing.
 * See docs/ACTOR_LAYER.md.
 */
class CinematicStudioAddon : JavaPlugin() {

    lateinit var bridge: CinematicBridge
        private set

    lateinit var actorManager: ActorManager
        private set

    lateinit var actorRecorder: ActorRecorder
        private set

    override fun onEnable() {
        bridge = CinematicBridge(this)
        actorManager = ActorManager(this)
        actorRecorder = ActorRecorder(this)
        actorManager.loadAll()

        val command = getCommand("cinaddon")
        if (command == null) {
            logger.severe("Command 'cinaddon' is missing from plugin.yml - disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        val executor = CinematicAddonCommand(this)
        command.setExecutor(executor)
        command.tabCompleter = executor

        server.pluginManager.registerEvents(QuitListener(actorManager), this)
        server.pluginManager.registerEvents(RecordingListener(this), this)

        if (!bridge.isCinematicStudioPresent()) {
            logger.warning("CinematicStudio is not installed or not enabled. Commands will report an error until it is available.")
        }

        logger.info("CinematicStudioAddon enabled.")
    }

    override fun onDisable() {
        if (::actorManager.isInitialized) actorManager.shutdown()
        if (::actorRecorder.isInitialized) actorRecorder.cancelAll()
        logger.info("CinematicStudioAddon disabled.")
    }

    /** Stops the player's recording and persists the captured track. Returns null if not recording. */
    fun finishRecording(player: org.bukkit.entity.Player): ActorTrack? {
        val track = actorRecorder.stop(player) ?: return null
        actorManager.saveTrack(track)
        return track
    }
}
