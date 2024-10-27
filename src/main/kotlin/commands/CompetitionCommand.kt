package commands

import CompetitOre
import co.aikar.commands.BaseCommand
import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.CommandCompletions
import co.aikar.commands.annotation.*
import co.aikar.commands.annotation.Optional
import com.plotsquared.bukkit.util.BukkitUtil
import com.plotsquared.core.player.PlotPlayer
import com.plotsquared.core.plot.Plot
import com.plotsquared.core.plot.flag.implementations.BlockBurnFlag
import com.plotsquared.core.plot.flag.implementations.BlockIgnitionFlag
import com.plotsquared.core.plot.flag.implementations.ExplosionFlag
import com.plotsquared.core.plot.flag.implementations.ServerPlotFlag
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.formatting.component.InvalidComponentException
import com.sk89q.worldedit.util.formatting.component.PaginationBox
import com.sk89q.worldedit.util.formatting.text.Component
import com.sk89q.worldedit.util.formatting.text.TextComponent
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent
import com.sk89q.worldedit.util.formatting.text.format.TextColor
import entity.*
import getPlot
import key
import name
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import sendCompetition
import sendCompetitionError
import java.util.*
import kotlin.random.Random

@CommandAlias("comp")
@Description("A command to manage competitions")
class CompetitionCommand(private val competitOre: CompetitOre) : BaseCommand() {
    private data class Confirmation(val key: Int, val description: String, val onConfirm: () -> Unit)
    private val confirmations = mutableMapOf<UUID, Confirmation>()
    private fun withConfirmation(player: Player, description: String, onConfirm: () -> Unit) {
        if (player.uniqueId in confirmations) {
            throw CompetitOreException("Currently waiting on ${player.name} to confirm a previous action. Try again later.")
        }
        val confirm = Confirmation(Random.nextInt(), description, onConfirm)
        confirmations[player.uniqueId] = confirm
        competitOre.server.scheduler.runTaskLater(competitOre, Runnable {
            confirmations.remove(player.uniqueId, confirm)
        }, 600L)
    }

    @Default
    @CatchUnknown
    @Subcommand("info")
    fun info(player: Player) {
        if (competitOre.activeEvent != null) {
            player.sendCompetition("${competitOre.activeEvent!!.name} (${competitOre.activeEvent!!.description})")
        } else {
            player.sendCompetition("There is no active event.")
        }
    }

    @Subcommand("version")
    fun version(player: Player) {
        player.sendCompetition("Version ${competitOre.description.version}")
    }

    @Subcommand("cancel")
    @CommandAlias("cancel")
    fun cancel(player: Player) {
        val description =
            confirmations.remove(player.uniqueId)?.description ?: "nothing, because you have nothing to confirm"
        player.sendCompetition("Cancelled $description")
    }

    @Subcommand("confirm")
    @CommandAlias("confirm")
    @CommandPermission("competition.confirm")
    fun confirm(player: Player) {
        val state = confirmations.remove(player.uniqueId) ?: throw CompetitOreException("You have nothing to confirm.")
        state.onConfirm()
    }

    @Subcommand("home")
    @CommandPermission("competition.home")
    fun home(player: Player, team: entity.Team) {
        team.getPlot(competitOre).getCenter { center ->
            player.teleport(BukkitUtil.adapt(center), PlayerTeleportEvent.TeleportCause.COMMAND)
        }
        player.sendCompetition("You have been teleported to your competition plot.")
    }

    @Subcommand("view|visit")
    @CommandCompletion("@competitors")
    @CommandPermission("competition.view")
    fun view(player: Player, @Single target: String) {
        val applicableEvent = competitOre.activeEvent ?: competitOre.database.getLastOrActiveEvent()
        val competitorPlot = if (',' in target) {
            val coordinates = target.split(',')
            if (coordinates.size != 2) throw CompetitOreException("Invalid plot ID")
            val intCoordinates = try {
                coordinates.map { it.toInt() }
            } catch (e: NumberFormatException) {
                throw CompetitOreException("Invalid plot ID")
            }
            competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots
                .filter { it.owner == UUID(0, 0) }.firstOrNull {
                it.id.getX() == intCoordinates[0] && it.id.getY() == intCoordinates[1] && it.hasOwner()
            } ?: throw CompetitOreException("That plot does not have an associated team.")
        } else {
            val targetCompetitor = competitOre.server.offlinePlayers.firstOrNull { it.name == target }
                ?: throw CompetitOreException("Unrecognized user.")
            competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots
                .filter { it.owner == UUID(0, 0) }.firstOrNull {
                it.trusted.contains(targetCompetitor.uniqueId)
            } ?: throw CompetitOreException("That user is not competing or has not competed in the last competition.")
        }
        competitorPlot.getCenter { center ->
            player.teleport(BukkitUtil.adapt(center), PlayerTeleportEvent.TeleportCause.COMMAND)
        }
        player.sendCompetition("You are now viewing ${competitorPlot.alias}.")
    }

    @Subcommand("list")
    @CommandPermission("competition.list")
    @CommandCompletion("@finished")
    fun list(player: Player, @Optional finished: String?, @Default("1") page: Int) {
        val isFinished = when (finished?.toLowerCase()) {
            "finished" -> FinishedState.FINISHED
            "unfinished" -> FinishedState.UNFINISHED
            else -> FinishedState.EITHER
        }
        val applicableEvent = competitOre.activeEvent ?: competitOre.database.getLastOrActiveEvent()
        val filtered = competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots
            .filter { it.owner == UUID(0, 0) }
            .filter {
            val plotTeam = competitOre.database.getTeamOf(applicableEvent.id, it.id.getX(), it.id.getY())!!
            when (isFinished) {
                FinishedState.FINISHED -> plotTeam.isFinished
                FinishedState.UNFINISHED -> !plotTeam.isFinished
                FinishedState.EITHER -> true
            }
        }
        val paginationBox =
            PlotsPaginationBox(filtered, isFinished, "/comp list ${isFinished.name.toLowerCase()} %page%")
        try {
            BukkitAdapter.adapt(player).print(paginationBox.create(page))
        } catch (e: InvalidComponentException) {
            player.sendCompetitionError("Invalid page number.")
        }
    }

    @Subcommand("enter")
    @Conditions("ongoingevent")
    @CommandPermission("competition.enter")
    fun enter(player: Player) {
        if (competitOre.database.getActiveTeamOf(player.uniqueId) != null) {
            throw CompetitOreException("You are already a member of a team.")
        }
        val firstUnclaimed = competitOre.plotApi.getPlotAreas(
            competitOre.activeEvent!!.key
        ).first().getNextFreePlot(
            PlotPlayer.from(player),
            null
        )
        competitOre.database.insertTeam(
            competitOre.activeEvent!!,
            firstUnclaimed.id.getX(),
            firstUnclaimed.id.getY(),
            listOf(player.uniqueId)
        )
        firstUnclaimed.apply {
            claim(PlotPlayer.from(player), false, null, true, false)
            setFlag(ServerPlotFlag.SERVER_PLOT_TRUE)
            setFlag(ExplosionFlag.EXPLOSION_TRUE)
            setFlag(BlockBurnFlag.BLOCK_BURN_TRUE)
            setFlag(BlockIgnitionFlag.BLOCK_IGNITION_FALSE)
            setFlag(FinishedFlag(false))
            alias = player.name
            addTrusted(player.uniqueId)
            getCenter { center ->
                player.teleport(BukkitUtil.adapt(center), PlayerTeleportEvent.TeleportCause.COMMAND)
            }
        }
        player.sendCompetition("You have successfully entered the competition.")
    }

    @Subcommand("leave")
    @Conditions("ongoingevent")
    @CommandPermission("competition.leave")
    fun leave(player: Player, team: entity.Team) {
        if (team.members.size == 1) {
            withConfirmation(player, "team leaving") {
                // NOTE!!! this prevents rare cases where the confirmation outlives the current event or something
                val activeTeam = competitOre.database.getActiveTeamOf(player.uniqueId)
                    ?: throw CompetitOreException("You are not a member of a team. (How did this happen?)")
                val teamPlot = activeTeam.getPlot(competitOre)
                teamPlot.plotModificationManager.deletePlot(PlotPlayer.from(player), null)
                competitOre.database.deleteTeam(activeTeam.id)
                player.sendCompetition("You have successfully left the competition.")
            }
            player.sendCompetition("Since you are the last member of your team, your team plot will also be deleted.")
            player.sendCompetition("Type \"/confirm\" to confirm.")
            return
        }
        val teamPlot = team.getPlot(competitOre)
        if (teamPlot.owner == player.uniqueId) {
            teamPlot.setOwner(team.members.first { it != player.uniqueId })
        }
        val existingMembers = teamPlot.alias.split(", ")
        teamPlot.alias = (existingMembers - player.name).sorted().joinToString(", ")
        teamPlot.removeTrusted(player.uniqueId)
        competitOre.database.removeFromTeam(team.id, player.uniqueId)
        player.sendCompetition("You have successfully left the competition.")
    }

    @Subcommand("team")
    @Conditions("ongoingevent")
    @CommandPermission("competition.team")
    inner class Team : BaseCommand() {
        @Subcommand("add")
        @CommandCompletion("@players")
        fun add(player: Player, team: entity.Team, @Single target: String) {
            if (team.members.size == team.event.teamSize) {
                throw CompetitOreException("Your team is already full.")
            }
            val targetPlayer =
                competitOre.server.getPlayer(target) ?: throw CompetitOreException("Player $target is offline.")
            if (competitOre.database.getActiveTeamOf(targetPlayer.uniqueId) != null) {
                throw CompetitOreException("User $target is already a member of a team.")
            }
            withConfirmation(targetPlayer, "an invite") {
                // NOTE!!! same as leave, tis is to refresh the Team
                val team = competitOre.database.getTeam(team.id)
                    ?: throw CompetitOreException("Invalid team ID. (How did this happen?)")
                if (team.members.size == team.event.teamSize) {
                    throw CompetitOreException("The team you are trying to join has already reached its size limit.")
                }
                competitOre.database.addToTeam(team.id, targetPlayer.uniqueId)
                val teamPlot = team.getPlot(competitOre)
                val existingMembers = teamPlot.alias.split(", ")
                teamPlot.alias = (existingMembers + targetPlayer.name).sorted().joinToString(", ")
                teamPlot.addTrusted(targetPlayer.uniqueId)
                teamPlot.getCenter { center ->
                    targetPlayer.teleport(BukkitUtil.adapt(center), PlayerTeleportEvent.TeleportCause.COMMAND)
                }
                targetPlayer.sendCompetition("You have joined the team along with ${existingMembers.joinToString(", ")}.")
            }
            targetPlayer.sendCompetition("You have been invited by ${player.name} to join their team.")
            targetPlayer.sendCompetition("Type \"/confirm\" to accept the invitation.")
            player.sendCompetition("Invite sent to ${targetPlayer.name} to join your team.")
        }

        @Subcommand("finish")
        fun finish(player: Player, team: entity.Team) {
            val teamPlot = team.getPlot(competitOre)
            teamPlot.setFlag(FinishedFlag(true))
            competitOre.database.setTeamFinishedState(team.id, true)
            player.sendCompetition("You have labeled your competition build as finished.")
            player.sendCompetition("You can still make changes to your build during this time.")
        }

        @Subcommand("unfinish")
        fun unfinish(player: Player, team: entity.Team) {
            val teamPlot = team.getPlot(competitOre)
            teamPlot.setFlag(FinishedFlag(false))
            competitOre.database.setTeamFinishedState(team.id, false)
            player.sendCompetition("You have labeled your competition build as unfinished.")
            player.sendCompetition("Once the build is completed, submit it by running \"/comp team finish\".")
        }
    }

    @Subcommand("reload")
    @CommandPermission("competition.reload")
    fun reload(player: Player) {
        competitOre.reload()
        player.sendCompetition("${competitOre.description.name} has been reloaded.")
    }

    @Subcommand("event")
    @CommandPermission("competition.manage")
    inner class Event : BaseCommand() {
        @Subcommand("judges")
        inner class Judge : BaseCommand() {
            @Subcommand("add")
            @CommandCompletion("@players")
            fun add(player: Player, @Single target: String) {
                val targetPlayer =
                    competitOre.server.getPlayer(target) ?: throw CompetitOreException("Player $target is offline.")
                player.sendCompetition("Player $target has been added to judging.")
            }

            @Subcommand("remove")
            @CommandCompletion("@players")
            fun remove(player: Player, @Single target: String) {
                competitOre.server.offlinePlayers.firstOrNull {
                    it.name == target
                }?.let {
                    player.sendCompetition("Player $target has been removed from judging.")
                } ?: player.sendCompetition("Player $target is not recognized by the server.")
            }

            @Subcommand("clear")
            fun clear(player: Player) {
                player.sendCompetition("Judges have been cleared.")
                player.sendCompetition("Note: the clearing may have been incomplete.")
                player.sendCompetition("Run \"/lp group ${competitOre.config.ranks.competitionJudge} listmembers\" for any unresolved players.")
            }
        }

        @Subcommand("start")
        fun start(player: Player) {
            withConfirmation(player, "starting the event") {
                competitOre.startEvent()
            }
            player.sendCompetition("You have requested to start the next competition.")
            player.sendCompetition("Run \"/confirm\" to confirm.")
        }

        @Subcommand("stop")
        @Conditions("ongoingevent")
        fun stop(player: Player) {
            withConfirmation(player, "stopping the event") {
                competitOre.stopEvent()
            }
            player.sendCompetition("You have requested to stop the currently active competition.")
            player.sendCompetition("Run \"/confirm\" to confirm.")
        }

        @Subcommand("teamsize")
        @Conditions("ongoingevent")
        @CommandCompletion("@range:1-5")
        fun teamSize(player: Player, @Single size: Int) {
            val currentEvent = competitOre.database.getActiveEvent()!!  // There is a better way to pass this in but eh.
            competitOre.database.updateEventSize(currentEvent.id, size)
            player.sendCompetition("Updated ${currentEvent.name} with team size: $size")
        }

        @Subcommand("description")
        @Conditions("ongoingevent")
        fun description(player: Player, description: String) {
            val currentEvent = competitOre.database.getActiveEvent()!!
            if (competitOre.activeEvent != null) {
                competitOre.activeEvent = competitOre.activeEvent!!.copy(description = description)
            }
            competitOre.database.updateEventDescription(currentEvent.id, description)
            player.sendCompetition("Updated ${currentEvent.name} with description: \"${description}\"")
        }

        @Subcommand("winner")
        fun winner(player: Player) {
            //val currentPlot = PlotPlayer.wrap(player.uniqueId).currentPlot
            player.sendCompetition("MANUAL: Copy the winner plot to a showcase plot")
            //TODO 1.1: Move plot to a showcase plot
        }
    }
}

class CompetitorCompletionHandler(private val competitOre: CompetitOre) :
    CommandCompletions.AsyncCommandCompletionHandler<BukkitCommandCompletionContext> {
        override fun getCompletions(context: BukkitCommandCompletionContext): Collection<String> =
            competitOre.plotApi.allPlots
                .flatMap { it.trusted }
                .mapNotNull { competitOre.server.getOfflinePlayer(it).name }
                .toSet()
    }

class PlotsPaginationBox(private val plots: List<Plot>, finishingState: FinishedState, command: String) :
    PaginationBox(
        "${ChatColor.LIGHT_PURPLE} ${if (finishingState != FinishedState.EITHER) {
            finishingState.name.toLowerCase().capitalize()
        } else {
            "Plots"
        }}",
        command
    ) {

    init {
        setComponentsPerPage(7)
    }

    override fun getComponent(number: Int): Component {
        if (number > plots.size) throw IllegalArgumentException("Invalid location index.")
        val applicablePlot = plots[number]
        return TextComponent.of("").color(TextColor.GRAY)
            .append(
                TextComponent.of(" ${number+1} ")
                    .clickEvent(ClickEvent.runCommand("/comp view ${applicablePlot.id.getX()},${applicablePlot.id.getY()}"))
                    .hoverEvent(HoverEvent.showText(TextComponent.of("Visit")))
                    .color(TextColor.RED)
            )
            .append(TextComponent.of("|"))
            .append(TextComponent.of(" ${applicablePlot.alias}").color(TextColor.YELLOW))
    }

    override fun getComponentsSize(): Int = plots.size

    override fun create(page: Int): Component {
        super.getContents().append(TextComponent.of("Total Plots: ${plots.size}").color(TextColor.GRAY))
            .append(TextComponent.newline())
        return super.create(page)
    }
}

class CompetitOreException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
