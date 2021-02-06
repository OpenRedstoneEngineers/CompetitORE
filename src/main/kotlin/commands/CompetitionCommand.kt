package commands

import CompetitOre
import co.aikar.commands.BaseCommand
import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.CommandCompletions
import co.aikar.commands.annotation.*
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
import countdown
import entity.*
import getPlot
import key
import name
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import prettyPrint
import sendCompetition
import sendCompetitionError
import java.time.Duration
import java.time.Instant

@CommandAlias("competition|comp")
@Description("A command to manage competitions")
class CompetitionCommand(private val competitOre: CompetitOre) : BaseCommand() {
    @Default @CatchUnknown
    @Subcommand("info")
    fun info(player: Player) {
        if (competitOre.activeEvent != null) {
            player.sendCompetition("${competitOre.activeEvent!!.name} (${competitOre.activeEvent!!.description})")
        } else {
            time(player)
        }
    }
    @Subcommand("version")
    fun version(player: Player) {
        player.sendCompetition("Version ${competitOre.description.version}")
    }
    @Subcommand("cancel")
    fun cancel(player: Player) {
        val state = competitOre.confirmStates.remove(player)
        val type = when (state?.action) {
            is Confirm.Invite -> "an invite"
            Confirm.Leave -> "team leaving"
            Confirm.StartEvent -> "starting the event"
            Confirm.StopEvent -> "stopping the event"
            null -> "nothing, because you have nothing to confirm"
        }
        player.sendCompetition("Cancelled $type")
    }
    @Subcommand("confirm")
    @CommandPermission("competition.confirm")
    fun confirm(player: Player) {
        val state = competitOre.confirmStates[player] ?: throw CompetitOreException("You have nothing to confirm.")
        competitOre.confirmStates.remove(player, state)
        when (val action = state.action) {
            is Confirm.Invite -> {
                val teamId = action.teamId
                val team = competitOre.database.getTeam(teamId)
                    ?: throw CompetitOreException("Invalid team ID. (How did this happen?)")
                if (team.members.size == team.event.teamSize) {
                    throw CompetitOreException("The team you are trying to join has already reached its size limit.")
                }
                competitOre.database.addToTeam(teamId, player.uniqueId)
                val teamPlot = team.getPlot(competitOre)
                val existingMembers = teamPlot.alias.split(", ")
                teamPlot.alias = (existingMembers + player.name).sorted().joinToString(", ")
                teamPlot.addTrusted(player.uniqueId)
                teamPlot.getCenter { center ->
                    player.teleport(BukkitUtil.getLocation(center), PlayerTeleportEvent.TeleportCause.COMMAND)
                }
                competitOre.addCompetitorRank(listOf(player.uniqueId))
                player.sendCompetition("You have joined the team along with ${existingMembers.joinToString(", ")}.")
            }
            is Confirm.Leave -> {
                val team = competitOre.database.getActiveTeamOf(player.uniqueId)
                    ?: throw CompetitOreException("You are not a member of a team. (How did this happen?)")
                val teamPlot = team.getPlot(competitOre)
                teamPlot.deletePlot(null)
                competitOre.database.deleteTeam(team.id)
                competitOre.removeCompetitorRank(listOf(player.uniqueId))
                player.sendCompetition("You have successfully left the competition.")
            }
            is Confirm.StartEvent -> {
                // TODO 1.1
            }
            is Confirm.StopEvent -> {
                // TODO 1.1
            }
        }
    }
    @Subcommand("home")
    @CommandPermission("competition.home")
    fun home(player: Player, team: entity.Team) {
        team.getPlot(competitOre).getCenter { center ->
            player.teleport(BukkitUtil.getLocation(center), PlayerTeleportEvent.TeleportCause.COMMAND)
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
            competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots.firstOrNull {
                it.id.getX() == intCoordinates[0] && it.id.getY() == intCoordinates[1] && it.hasOwner()
            } ?: throw CompetitOreException("That plot does not have an associated team.")
        } else {
            val targetCompetitor = competitOre.server.offlinePlayers.firstOrNull { it.name == target }
                ?: throw CompetitOreException("Unrecognized user.")
            competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots.firstOrNull {
                it.trusted.contains(targetCompetitor.uniqueId)
            } ?: throw CompetitOreException("That user is not competing or has not competed in the last competition.")
        }
        competitorPlot.getCenter { center ->
            player.teleport(BukkitUtil.getLocation(center), PlayerTeleportEvent.TeleportCause.COMMAND)
        }
        player.sendCompetition("You are now viewing ${competitorPlot.alias}.")
    }
    @Subcommand("list")
    @CommandPermission("competition.list")
    @CommandCompletion("@finished")
    fun list(player: Player, @Optional finished: String?, @Default("1") page: Int) {
        val isFinished = when (finished?.toLowerCase()){
            "finished" -> FinishedState.FINISHED
            "unfinished" -> FinishedState.UNFINISHED
            else -> FinishedState.EITHER
        }
        val applicableEvent = competitOre.activeEvent ?: competitOre.database.getLastOrActiveEvent()
        val filtered = competitOre.plotApi.getPlotAreas(applicableEvent.key).first().plots.filter {
            val plotTeam = competitOre.database.getTeamOf(applicableEvent.id, it.id.getX(), it.id.getY())!!
            when (isFinished) {
                FinishedState.FINISHED -> plotTeam.isFinished
                FinishedState.UNFINISHED -> !plotTeam.isFinished
                FinishedState.EITHER -> true
            }
        }
        val paginationBox = PlotsPaginationBox(filtered, isFinished, "/competition list ${isFinished.name.toLowerCase()} %page%")
        try {
            BukkitAdapter.adapt(player).print(paginationBox.create(page))
        } catch (e: InvalidComponentException) {
            player.sendCompetitionError("Invalid page number.")
        }
    }
    @Subcommand("time")
    @Conditions("ongoingevent")
    @CommandPermission("competition.time")
    fun time(player: Player) {
        val now = Instant.now()
        if (competitOre.activeEvent != null) {
            "The ${competitOre.activeEvent!!.name} competition has started. It will end in ${now.countdown(competitOre.activeEvent!!.end)}."
        } else {
            val currentEvent = competitOre.database.getNextEvent()
            if (Duration.between(now, currentEvent.start) > Duration.ofDays(4)) {
                "The ${currentEvent.name} competition is set to start at ${currentEvent.start.prettyPrint()}."
            } else {
                "The ${currentEvent.name} competition is set to start in ${now.countdown(currentEvent.start)}."
            }
        }.let { player.sendCompetition(it) }
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
            PlotPlayer.wrap(player.uniqueId),
            null
        )
        competitOre.database.insertTeam(
            competitOre.activeEvent!!,
            firstUnclaimed.id.getX(),
            firstUnclaimed.id.getY(),
            listOf(player.uniqueId)
        )
        competitOre.addCompetitorRank(listOf(player.uniqueId))
        firstUnclaimed.apply {
            claim(PlotPlayer.wrap(player.uniqueId), false, null)
            setFlag(ServerPlotFlag.SERVER_PLOT_TRUE)
            setFlag(ExplosionFlag.EXPLOSION_TRUE)
            setFlag(BlockBurnFlag.BLOCK_BURN_TRUE)
            setFlag(BlockIgnitionFlag.BLOCK_IGNITION_FALSE)
            setFlag(FinishedFlag(false))
            alias = player.name
            addTrusted(player.uniqueId)
            getCenter { center ->
                player.teleport(BukkitUtil.getLocation(center), PlayerTeleportEvent.TeleportCause.COMMAND)
            }
        }
        player.sendCompetition("You have successfully entered the competition.")
    }
    @Subcommand("leave")
    @Conditions("ongoingevent")
    @CommandPermission("competition.leave")
    fun leave(player: Player, team: entity.Team) {
        if (team.members.size == 1) {
            val confirmLeave = confirmationStateOf(Confirm.Leave)
            competitOre.confirmStates[player] = confirmLeave
            competitOre.server.scheduler.runTaskLater(competitOre, Runnable {
                competitOre.confirmStates.remove(player, confirmLeave)
            }, 600L)
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
        competitOre.removeCompetitorRank(listOf(player.uniqueId))
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
            val targetPlayer = competitOre.server.getPlayer(target) ?:
                throw CompetitOreException("Player $target is offline.")
            if (competitOre.database.getActiveTeamOf(targetPlayer.uniqueId) != null) {
                throw CompetitOreException("User $target is already a member of a team.")
            }
            if (competitOre.confirmStates[targetPlayer] != null) {
                throw CompetitOreException("Currently waiting on $target to confirm a previous action. Try again later.")
            }
            val confirmInvite = confirmationStateOf(Confirm.Invite(team.id))
            competitOre.confirmStates[targetPlayer] = confirmInvite
            competitOre.server.scheduler.runTaskLater(competitOre, Runnable {
                competitOre.confirmStates.remove(targetPlayer, confirmInvite)
            }, 600L)
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
            player.sendCompetition("Once the build is completed, submit it by running \"/competition team finish\".")
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
    inner class Event: BaseCommand() {
        @Subcommand("judges")
        inner class Judge: BaseCommand() {
            @Subcommand("add")
            @CommandCompletion("@players")
            fun add(player: Player, @Single target: String) {
                val targetPlayer = competitOre.server.getPlayer(target) ?:
                    throw CompetitOreException("Player $target is offline.")
                competitOre.addRank(
                    listOf(targetPlayer.uniqueId),
                    competitOre.config[CompetitOreSpec.Ranks.competitionJudge],
                    competitOre.competitonContext
                )
                player.sendCompetition("Player $target has been added to judging.")
            }
            @Subcommand("remove")
            @CommandCompletion("@players")
            fun remove(player: Player, @Single target: String) {
                competitOre.server.offlinePlayers.firstOrNull {
                    it.name == target
                }?.let {
                    competitOre.removeRank(
                        listOf(it.uniqueId),
                        competitOre.config[CompetitOreSpec.Ranks.competitionJudge],
                        competitOre.competitonContext
                    )
                    player.sendCompetition("Player $target has been removed from judging.")
                } ?: player.sendCompetition("Player $target is not recognized by the server.")
            }
            @Subcommand("clear")
            fun clear(player: Player) {
                competitOre.removeRank(
                    competitOre.luckPerms.userManager.loadedUsers.map { it.uniqueId },
                    competitOre.config[CompetitOreSpec.Ranks.competitionJudge]
                )
                player.sendCompetition("Judges have been cleared.")
                player.sendCompetition("Note: the clearing may have been incomplete.")
                player.sendCompetition("Run \"/lp group ${competitOre.config[CompetitOreSpec.Ranks.competitionJudge]} listmembers\" for any unresolved players.")
            }
        }
        @Subcommand("start")
        fun start(player: Player) {
            if (competitOre.activeEvent != null) {
                throw CompetitOreException("There is already an active competition.")
            }
            val confirmStart = confirmationStateOf(Confirm.StartEvent)
            competitOre.confirmStates[player] = confirmStart
            competitOre.server.scheduler.runTaskLater(competitOre, Runnable {
                competitOre.confirmStates.remove(player, confirmStart)
            }, 600L)
            player.sendCompetition("You have requested to start the next competition.")
            player.sendCompetition("Type \"/confirm\" to accept the invitation.")
        }
        @Subcommand("stop")
        @Conditions("ongoingevent")
        fun stop(player: Player) {
            val confirmStop = confirmationStateOf(Confirm.StopEvent)
            competitOre.confirmStates[player] = confirmStop
            competitOre.server.scheduler.runTaskLater(competitOre, Runnable {
                competitOre.confirmStates.remove(player, confirmStop)
            }, 600L)
            player.sendCompetition("You have requested to stop the currently active competition.")
            player.sendCompetition("Type \"/confirm\" to accept the invitation.")
        }
        @Subcommand("teamsize")
        @CommandCompletion("@range:1-5")
        fun teamSize(player: Player, @Single size: Int) {
            val currentEvent = competitOre.database.getNextEvent()
            competitOre.database.updateEventSize(currentEvent.id, size)
            player.sendCompetition("Updated ${currentEvent.name} with team size: $size")
        }
        @Subcommand("description")
        fun description(player: Player, description: String) {
            val currentEvent = competitOre.database.getNextEvent()
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
                    .clickEvent(ClickEvent.runCommand("/competition view ${applicablePlot.id.getX()},${applicablePlot.id.getY()}"))
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
