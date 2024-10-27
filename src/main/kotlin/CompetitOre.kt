import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandIssuer
import co.aikar.commands.PaperCommandManager
import co.aikar.commands.RegisteredCommand
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.onarandombox.MultiverseCore.MultiverseCore
import com.plotsquared.bukkit.util.BukkitSetupUtils
import com.plotsquared.core.PlotAPI
import com.plotsquared.core.PlotSquared
import com.plotsquared.core.configuration.ConfigurationNode
import com.plotsquared.core.configuration.ConfigurationUtil.*
import com.plotsquared.core.configuration.caption.StaticCaption
import com.plotsquared.core.plot.BlockBucket
import com.plotsquared.core.plot.PlotAreaTerrainType
import com.plotsquared.core.plot.PlotAreaType
import com.plotsquared.core.plot.PlotId
import com.plotsquared.core.plot.flag.GlobalFlagContainer
import com.plotsquared.core.setup.PlotAreaBuilder
import com.plotsquared.core.setup.SettingsNodesWrapper
import com.sk89q.worldedit.world.block.BlockTypes
import commands.CompetitOreException
import commands.CompetitionCommand
import commands.CompetitorCompletionHandler
import entity.*
import manager.CompetitoreCalculator
import manager.PlotEvent
import manager.Sql
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.util.logging.Level

class CompetitOre : JavaPlugin() {
    var plotApi = PlotAPI()
    var config = loadConfig()
    val database = Sql(
        config.competitorDatabase.host,
        config.competitorDatabase.port,
        config.competitorDatabase.database,
        config.competitorDatabase.username,
        config.competitorDatabase.password
    )
    var activeEvent: Event? = null
    lateinit var luckPerms: LuckPerms
    lateinit var core: MultiverseCore

    override fun onEnable() {
        luckPerms = LuckPermsProvider.get()
        database.initTables()
        plotApi.registerListener(PlotEvent(this))
        GlobalFlagContainer.getInstance().addFlag(FinishedFlag(true))
        PaperCommandManager(this).apply {
            commandConditions.addCondition("ongoingevent") {
                activeEvent ?: throw CompetitOreException("The competition has not started yet.")
            }
            commandContexts.registerIssuerOnlyContext(Team::class.java) { context ->
                val latestTeam = database.getActiveTeamOf(context.player.uniqueId) ?:
                    throw CompetitOreException("You are not part of the competition.")
                latestTeam
            }
            commandCompletions.registerAsyncCompletion("competitors", CompetitorCompletionHandler(this@CompetitOre))
            commandCompletions.registerCompletion("finished") { listOf("finished", "unfinished") }
            registerCommand(CompetitionCommand(this@CompetitOre))
            setDefaultExceptionHandler(::handleCommandException, false)
        }
        luckPerms.contextManager.registerCalculator(CompetitoreCalculator(this))
        activeEvent = database.getActiveEvent()
        core = server.pluginManager.getPlugin("Multiverse-Core") as MultiverseCore
    }

    override fun onDisable() {

    }

    fun reload() {
        config = loadConfig()
    }

    fun startEvent() {
        server.worlds.forEach {
            if (it.name.startsWith("competition_")) {
                it.players.forEach { player -> player.teleport(server.getWorld("world")!!.spawnLocation) }
                core.mvWorldManager.removeWorldFromConfig(it.name)
                plotApi.plotSquared.plotAreaManager.removeWorld(it.name)
            }
        }
        activeEvent = database.insertEvent(
            "n/a",
            LocalDateTime.now(),
            config.event.teamSize
        )
        ensureCompetitionWorld(activeEvent!!.key)
        server.onlinePlayers.forEach {
            it.sendCompetition("The ${activeEvent!!.name} competition has started!")
            it.sendCompetition("Join the competition by running \"/comp enter\".")
        }
    }

    fun stopEvent() {
        val competitors = database.getTeamsByEvent(activeEvent!!.id)!!.flatMap { it.members }
        competitors.forEach {
            server.getPlayer(it)?.let { player ->
                player.sendCompetition("The competition has ended!")
                player.sendCompetition("Judging will now begin and the results are to be announced shortly.")
            }
        }
        database.endEvent(activeEvent!!.id)
        activeEvent = null
    }

    private fun ensureCompetitionWorld(name: String) {
        if (server.worlds.firstOrNull { it.name == name } != null) {
            return
        }
        fun String.block() = BlockBucket(BlockTypes.get("minecraft:$this")!!)
        val configurationNodes = arrayOf(
            ConfigurationNode("plot.height", config.plotSettings.plot.height, StaticCaption.of(""), INTEGER),
            ConfigurationNode("plot.size", config.plotSettings.plot.size, StaticCaption.of(""), INTEGER),
            ConfigurationNode("road.width", config.plotSettings.road.width, StaticCaption.of(""), INTEGER),
            ConfigurationNode("road.height", config.plotSettings.road.height, StaticCaption.of(""), INTEGER),
            ConfigurationNode("wall.height", config.plotSettings.wall.height, StaticCaption.of(""), INTEGER),
            ConfigurationNode("plot.bedrock", config.plotSettings.plot.bedrock, StaticCaption.of(""), BOOLEAN),
            ConfigurationNode("plot.create_signs", config.plotSettings.plot.useSigns, StaticCaption.of(""), BOOLEAN),
            ConfigurationNode("wall.place_top_block", config.plotSettings.wall.placeTopBlock, StaticCaption.of(""), BOOLEAN),
            ConfigurationNode("world.border", config.plotSettings.world.border, StaticCaption.of(""), BOOLEAN),
            ConfigurationNode("plot.filling", config.plotSettings.plot.filling.block(), StaticCaption.of(""), BLOCK_BUCKET),
            ConfigurationNode("plot.floor", config.plotSettings.plot.floor.block(), StaticCaption.of(""), BLOCK_BUCKET),
            ConfigurationNode("wall.block", config.plotSettings.wall.block.block(), StaticCaption.of(""), BLOCK_BUCKET),
            ConfigurationNode("wall.block_claimed", config.plotSettings.wall.blockClaimed.block(), StaticCaption.of(""), BLOCK_BUCKET),
            ConfigurationNode("road.block", config.plotSettings.road.block.block(), StaticCaption.of(""), BLOCK_BUCKET),
            ConfigurationNode("wall.filling", config.plotSettings.wall.filling.block(), StaticCaption.of(""), BLOCK_BUCKET)
        )
        val plotAreaBuilder = PlotAreaBuilder.newBuilder().apply {
            generatorName("PlotSquared")
            plotAreaType(PlotAreaType.NORMAL)
            terrainType(PlotAreaTerrainType.NONE)
            worldName(name)
            areaName(name)
            minimumId(PlotId.of(Int.MIN_VALUE, Int.MIN_VALUE))
            maximumId(PlotId.of(Int.MAX_VALUE, Int.MAX_VALUE))
            settingsNodesWrapper(SettingsNodesWrapper(configurationNodes, null))
        }
        server.scheduler.runTaskLater(this, Runnable {
            PlotSquared.platform().injector().getInstance(BukkitSetupUtils::class.java).setupWorld(plotAreaBuilder)
        }, 50L)
        server.scheduler.runTaskLater(this, Runnable {
            val eventWorld = server.getWorld(name)
            if (eventWorld == null) {
                logger.log(Level.SEVERE, "World $name has not loaded in time to set the following game rules:")
                config.gameRules.forEach {
                    this.logger.log(Level.INFO, "\t${it.key}: ${it.value}")
                }
                return@Runnable
            }
            config.gameRules.forEach {
                @Suppress("UNCHECKED_CAST")
                val gameRule = GameRule.getByName(it.key) as GameRule<Any>?
                if (gameRule == null) {
                    logger.log(Level.WARNING, "Invalid game rule ${it.key}")
                } else {
                    try {
                        eventWorld.setGameRule(gameRule, it.value)
                        logger.log(Level.INFO, "Set GameRule ${gameRule.name} with a value of ${it.value}")
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Unable to set game rule ${it.key}: ${it.value}")
                    }
                }
            }
            core.mvWorldManager.getMVWorld(eventWorld).setGameMode(GameMode.CREATIVE)
            eventWorld.time = 6000L
        }, 300L)
    }

    private fun loadConfig(): CompetitOreConfig {
        if (!dataFolder.exists()) {
            logger.info("No resource directory found, creating directory")
            dataFolder.mkdir()
        }
        val configFile = File(dataFolder, "config.yml")
        // does not overwrite or throw
        configFile.createNewFile()
        val mapper = ObjectMapper(YAMLFactory())
        val tree = mapper.readTree(configFile)
        val config = mapper.treeToValue(tree, CompetitOreConfig::class.java)
        logger.info("Loaded config.yml")
        return config
    }

    private fun handleCommandException(
        command: BaseCommand,
        registeredCommand: RegisteredCommand<*>,
        sender: CommandIssuer,
        args: List<String>,
        throwable: Throwable
    ): Boolean {
        val exception = throwable as? CompetitOreException ?: run {
            logger.log(Level.SEVERE, "Error while executing command", throwable)
            return false
        }
        val message = exception.message ?: "Something went wrong!"
        val player = server.getPlayer(sender.uniqueId)!!
        player.sendCompetitionError(message)
        return true
    }
}
