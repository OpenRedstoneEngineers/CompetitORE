import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandIssuer
import co.aikar.commands.PaperCommandManager
import co.aikar.commands.RegisteredCommand
import com.plotsquared.core.PlotAPI
import com.plotsquared.core.PlotSquared
import com.plotsquared.core.configuration.ConfigurationNode
import com.plotsquared.core.configuration.ConfigurationUtil.*
import com.plotsquared.core.configuration.caption.Caption
import com.plotsquared.core.configuration.caption.LocaleHolder
import com.plotsquared.core.configuration.caption.TranslatableCaption
import com.plotsquared.core.plot.BlockBucket
import com.plotsquared.core.plot.PlotAreaTerrainType
import com.plotsquared.core.plot.PlotAreaType
import com.plotsquared.core.plot.PlotId
import com.plotsquared.core.plot.flag.GlobalFlagContainer
import com.plotsquared.core.setup.PlotAreaBuilder
import com.plotsquared.core.setup.SettingsNodesWrapper
import com.plotsquared.core.util.SetupUtils
import com.sk89q.worldedit.world.block.BlockTypes
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.yaml
import com.uchuhimo.konf.source.yaml.toYaml
import commands.CompetitOreException
import commands.CompetitionCommand
import commands.CompetitorCompletionHandler
import entity.*
import manager.PlotEvent
import manager.Sql
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.context.ContextSet
import net.luckperms.api.context.DefaultContextKeys
import net.luckperms.api.context.ImmutableContextSet
import org.bukkit.GameRule
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.logging.Level

class CompetitOre : JavaPlugin() {
    var plotApi = PlotAPI()
    var config = loadConfig()
    val serverContext get() = ImmutableContextSet.builder().add(DefaultContextKeys.SERVER_KEY, config[CompetitOreSpec.serverName])
    val competitonContext get() = serverContext.add(DefaultContextKeys.WORLD_KEY, database.getLastOrActiveEvent().key).build()
    val database = Sql(
        config[CompetitOreSpec.CompetitorDatabase.host],
        config[CompetitOreSpec.CompetitorDatabase.port],
        config[CompetitOreSpec.CompetitorDatabase.database],
        config[CompetitOreSpec.CompetitorDatabase.username],
        config[CompetitOreSpec.CompetitorDatabase.password]
    )
    var activeEvent: Event? = null
    lateinit var luckPerms: LuckPerms

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
        if (database.getAllEvents().isEmpty()) {
            addNextEvent()
        }
        server.scheduler.scheduleSyncRepeatingTask(this, {
            val event = database.getActiveEvent()
            if (activeEvent == null && event != null) {
                startEvent(event)
            } else if (activeEvent != null && event == null) {
                stopEvent()
            }
        }, 0L, 20L)
    }

    override fun onDisable() {
        //database.destroy()
    }

    fun reload() {
        config = loadConfig(reloaded = true)
    }

    private fun startEvent(event: Event) {
        // TODO 1.1: Figure out how to unload the world.
        ensureCompetitionWorld(event.key)
        activeEvent = event
        server.onlinePlayers.forEach {
            it.sendCompetition("The ${activeEvent!!.name} competition has started!")
            it.sendCompetition("Join the competition by running \"/comp enter\".")
        }
    }

    private fun addNextEvent() {
        val nextStart = getNextEventStartTime(
            config[CompetitOreSpec.Event.Start.dayOfWeek],
            config[CompetitOreSpec.Event.Start.hour],
            config[CompetitOreSpec.Event.Start.minute]
        )
        database.insertEvent(
            "n/a",
            nextStart,
            nextStart.plusHours(config[CompetitOreSpec.Event.length].toLong()),
            config[CompetitOreSpec.Event.teamSize]
        )
    }

    private fun stopEvent() {
        val competitors = database.getTeamsByEvent(activeEvent!!.id)!!.flatMap { it.members }
        competitors.forEach {
            server.getPlayer(it)?.let { player ->
                player.sendCompetition("The competition has ended!")
                player.sendCompetition("Judging will now begin and the results are to be announced shortly.")
            }
        }
        removeCompetitorRank(competitors)
        activeEvent = null
        addNextEvent()
    }

    private fun ensureCompetitionWorld(name: String) {
        if (server.worlds.firstOrNull { it.name == name } != null) {
            return
        }
        fun String.block() = BlockBucket(BlockTypes.get("minecraft:$this")!!)
        val configurationNodes = arrayOf(
            ConfigurationNode("plot.height", config[CompetitOreSpec.PlotSettings.Plot.height], TranslatableCaption.of("setup.plot_height"), INTEGER),
            ConfigurationNode("plot.size", config[CompetitOreSpec.PlotSettings.Plot.size], TranslatableCaption.of("setup.plot_width"), INTEGER),
            ConfigurationNode("road.width", config[CompetitOreSpec.PlotSettings.Road.width], TranslatableCaption.of("setup.road_width"), INTEGER),
            ConfigurationNode("road.height", config[CompetitOreSpec.PlotSettings.Road.height], TranslatableCaption.of("setup.road_height"), INTEGER),
            ConfigurationNode("wall.height", config[CompetitOreSpec.PlotSettings.Wall.height], TranslatableCaption.of("setup.wall_height"), INTEGER),
            ConfigurationNode("plot.bedrock", config[CompetitOreSpec.PlotSettings.Plot.bedrock], TranslatableCaption.of("setup.bedrock_boolean"), BOOLEAN),
            ConfigurationNode("plot.create_signs", config[CompetitOreSpec.PlotSettings.Plot.useSigns], TranslatableCaption.of("setup.bedrock_boolean"), BOOLEAN),
            ConfigurationNode("wall.place_top_block", config[CompetitOreSpec.PlotSettings.Wall.placeTopBlock], TranslatableCaption.of("setup.top_block_boolean"), BOOLEAN),
            ConfigurationNode("world.border", config[CompetitOreSpec.PlotSettings.World.border], TranslatableCaption.of("setup.bedrock_boolean"), BOOLEAN),
            ConfigurationNode("plot.filling", config[CompetitOreSpec.PlotSettings.Plot.filling].block(), TranslatableCaption.of("setup.plot_block"), BLOCK_BUCKET),
            ConfigurationNode("plot.floor", config[CompetitOreSpec.PlotSettings.Plot.floor].block(), TranslatableCaption.of("setup.plot_block_floor"), BLOCK_BUCKET),
            ConfigurationNode("wall.block", config[CompetitOreSpec.PlotSettings.Wall.block].block(), TranslatableCaption.of("setup.top_wall_block"), BLOCK_BUCKET),
            ConfigurationNode("wall.block_claimed", config[CompetitOreSpec.PlotSettings.Wall.blockClaimed].block(), TranslatableCaption.of("setup.wall_block_claimed"), BLOCK_BUCKET),
            ConfigurationNode("road.block", config[CompetitOreSpec.PlotSettings.Road.block].block(), TranslatableCaption.of("setup.road_block"), BLOCK_BUCKET),
            ConfigurationNode("wall.filling", config[CompetitOreSpec.PlotSettings.Wall.filling].block(), TranslatableCaption.of("setup.wall_filling_block"), BLOCK_BUCKET)
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
        plotAreaBuilder.settingsNodesWrapper().settingsNodes
        val manager = SetupUtils.manager
        manager.setupWorld(plotAreaBuilder)
        server.scheduler.runTaskLater(this, Runnable {
            val eventWorld = server.getWorld(name)
            if (eventWorld == null) {
                logger.log(Level.SEVERE, "World $name has not loaded in time to set the following game rules:")
                config[CompetitOreSpec.gameRules].forEach {
                    this.logger.log(Level.INFO, "\t${it.key}: ${it.value}")
                }
                return@Runnable
            }
            config[CompetitOreSpec.gameRules].forEach {
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
            eventWorld.time = 6000L
        }, 300L)
    }

    private fun loadConfig(reloaded: Boolean = false): Config {
        if (!dataFolder.exists()) {
            logger.log(Level.INFO, "No resource directory found, creating directory")
            dataFolder.mkdir()
        }
        val configFile = File(dataFolder, "config.yml")
        val loadedConfig = if (!configFile.exists()) {
            logger.log(Level.INFO, "No config file found, generating from default config.yml")
            configFile.createNewFile()
            Config { addSpec(CompetitOreSpec) }
        } else {
            Config { addSpec(CompetitOreSpec) }.from.yaml.watchFile(configFile)
        }
        loadedConfig.toYaml.toFile(configFile)
        logger.log(Level.INFO, "${if (reloaded) "Rel" else "L"}oaded config.yml")
        return loadedConfig
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

    fun addCompetitorRank(users: List<UUID>) {
        val worldEditUsers = users.filterWorldEditUsers(
            luckPerms.userManager,
            config[CompetitOreSpec.Ranks.worldeditMinimumRank]
        )
        val basicUsers = users.minus(worldEditUsers)
        addRank(
            worldEditUsers,
            config[CompetitOreSpec.Ranks.competitorWorldedit],
            competitonContext
        )
        addRank(
            basicUsers,
            config[CompetitOreSpec.Ranks.competitor],
            competitonContext
        )
    }

    fun removeCompetitorRank(users: List<UUID>) {
        val worldEditUsers = users.filterWorldEditUsers(
            luckPerms.userManager,
            config[CompetitOreSpec.Ranks.worldeditMinimumRank]
        )
        val basicUsers = users.minus(worldEditUsers)
        removeRank(
            worldEditUsers,
            config[CompetitOreSpec.Ranks.competitorWorldedit],
            competitonContext
        )
        removeRank(
            basicUsers,
            config[CompetitOreSpec.Ranks.competitor],
            competitonContext
        )
    }

    fun addRank(users: List<UUID>, rankName: String, context: ContextSet = ImmutableContextSet.empty()) {
        val userManager = luckPerms.userManager
        users.forEach {
            val player = userManager.getUser(it)
            if (player != null) {
                player.addGroupNode(userManager, rankName, context)
            } else {
                userManager.loadUser(it).thenApplyAsync { user ->
                    user.addGroupNode(userManager, rankName, context)
                }
            }
        }
    }

    fun removeRank(users: List<UUID>, rankName: String, context: ContextSet = ImmutableContextSet.empty()) {
        val userManager = luckPerms.userManager
        users.forEach {
            val player = userManager.getUser(it)
            if (player != null) {
                println("User $it loaded, removing rank.")
                player.removeGroupNode(userManager, rankName, context)
            } else {
                println("User $it not loaded...")
                userManager.loadUser(it).thenApplyAsync { user ->
                    println("User $it loaded, removing rank.")
                    user.removeGroupNode(userManager, rankName, context)
                }
            }
        }
    }
}
