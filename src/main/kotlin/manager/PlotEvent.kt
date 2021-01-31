package manager

import CompetitOre
import com.google.common.eventbus.Subscribe
import com.plotsquared.core.events.PlayerEnterPlotEvent

class PlotEvent(val competitOre: CompetitOre) {
    @Subscribe
    fun onPlayerEnterPlot(event: PlayerEnterPlotEvent) {
        if (!event.plot.hasOwner()) return
        competitOre.server.onlinePlayers.first {
            event.plotPlayer.uuid == it.uniqueId
        }.sendTitle(
            "",
            event.plot.alias,
            5,
            20,
            5
        )
    }
}
