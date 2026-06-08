package sk.hypercube.cinematicstudioaddon

import org.bukkit.plugin.java.JavaPlugin
import sk.hypercube.cinematicstudioaddon.command.CinematicAddonCommand
import sk.hypercube.cinematicstudioaddon.record.ActorRecorder
import sk.hypercube.cinematicstudioaddon.scene.PlayerSessionListener
import sk.hypercube.cinematicstudioaddon.scene.SceneManager

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
 * Feature 2 (in progress): a self-contained, per-viewer actor layer (packet NPCs) synced to
 * cinematic playback, to replace CinematicStudio's unreliable built-in actors. See docs/ACTOR_LAYER.md.
 */
class CinematicStudioAddon : JavaPlugin() {

    lateinit var bridge: CinematicBridge
        private set

    lateinit var sceneManager: SceneManager
        private set

    lateinit var actorRecorder: ActorRecorder
        private set

    override fun onEnable() {
        bridge = CinematicBridge(this)
        sceneManager = SceneManager(this)
        actorRecorder = ActorRecorder(this)

        val command = getCommand("cinaddon")
        if (command == null) {
            logger.severe("Command 'cinaddon' is missing from plugin.yml - disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        val executor = CinematicAddonCommand(this)
        command.setExecutor(executor)
        command.tabCompleter = executor

        server.pluginManager.registerEvents(PlayerSessionListener(sceneManager), this)

        if (!bridge.isCinematicStudioPresent()) {
            logger.warning("CinematicStudio is not installed or not enabled. Commands will report an error until it is available.")
        }

        logger.info("CinematicStudioAddon enabled.")
    }

    override fun onDisable() {
        if (::sceneManager.isInitialized) sceneManager.shutdown()
        if (::actorRecorder.isInitialized) actorRecorder.cancelAll()
        logger.info("CinematicStudioAddon disabled.")
    }
}
