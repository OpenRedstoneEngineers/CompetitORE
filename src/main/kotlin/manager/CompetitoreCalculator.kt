package manager

import CompetitOre
import key
import net.luckperms.api.context.ContextCalculator
import net.luckperms.api.context.ContextConsumer
import net.luckperms.api.context.ContextSet
import net.luckperms.api.context.ImmutableContextSet
import org.bukkit.entity.Player

class CompetitoreCalculator(val competitOre: CompetitOre) : ContextCalculator<Player> {
    companion object {
        const val ACTIVE = "competitore:active"
    }

    override fun calculate(target: Player, consumer: ContextConsumer) {
        val activeInEvent = competitOre.database.getActiveTeamOf(target.uniqueId) != null
            && target.world.name == competitOre.activeEvent?.key
        consumer.accept(ACTIVE, activeInEvent.toString())
    }

    override fun estimatePotentialContexts(): ContextSet =
        ImmutableContextSet.builder()
            .add(ACTIVE, "true")
            .add(ACTIVE, "false")
            .build()
}
