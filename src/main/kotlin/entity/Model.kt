package entity

import com.plotsquared.core.configuration.caption.TranslatableCaption
import com.plotsquared.core.plot.flag.types.BooleanFlag
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.Instant
import java.util.*

object Sql {
    object Event : Table("comp_event") {
        val id = integer("event_id").autoIncrement()
        val description = varchar("event_description", 128)
        val team_size = integer("event_team_size").default(1)
        val start = datetime("event_start")
        val end = datetime("event_end").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Team : Table("comp_team") {
        val id = integer("team_id").autoIncrement()
        val plot_x = integer("team_plot_x")
        val plot_y = integer("team_plot_y")
        val is_finished = bool("team_is_finished").default(false)
        val event_id = (integer("team_event_id") references Event.id)
        override val primaryKey = PrimaryKey(id)
    }

    object Competitor : Table("comp_competitor") {
        val uuid = binary("comp_uuid", 16)
        val team_id = (integer("comp_team_id") references Team.id)
        override val primaryKey = PrimaryKey(uuid, team_id)
    }
}

class FinishedFlag(value: Boolean) : BooleanFlag<FinishedFlag>(value, TranslatableCaption.of("flag.flag_desc")) {
    override fun flagOf(value: Boolean): FinishedFlag = FinishedFlag(value)
}

data class Event(
    val id: Int,
    val description: String,
    val teamSize: Int,
    val start: Instant,
    val end: Instant? = null,
)

data class Team(
    val id: Int,
    val plotX: Int,
    val plotY: Int,
    val event: Event,
    val isFinished: Boolean,
    val members: List<UUID>
)
