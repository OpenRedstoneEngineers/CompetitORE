package manager

import entity.Event
import entity.Sql
import entity.Team
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import toBin
import toUuid
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class Sql(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    driver: String = "com.mysql.cj.jdbc.Driver"
) {

    private val zoneOffset = ZoneOffset.ofHours(-5)

    private val database = Database.connect(
        "jdbc:mysql://${host}:${port}/${database}",
        driver = driver,
        user = user,
        password = password
    )

    fun initTables() = transaction(database) {
        SchemaUtils.create(
            Sql.Event,
            Sql.Team,
            Sql.Competitor
        )
    }

    fun destroy() = transaction(database) {
        SchemaUtils.drop(
            Sql.Competitor,
            Sql.Team,
            Sql.Event
        )
    }

    fun insertEvent(eventDescription: String, eventStart: LocalDateTime, eventEnd: LocalDateTime, size: Int = 1): Event = transaction(database) {
        Event(
            Sql.Event.insert {
                it[description] = eventDescription
                it[team_size] = size
                it[start] = eventStart
                it[end] = eventEnd
            } get Sql.Event.id,
            eventDescription,
            size,
            eventStart.atOffset(zoneOffset).toInstant(),
            eventEnd.atOffset(zoneOffset).toInstant()
        )
    }

    fun updateEventSize(id: Int, size: Int) = transaction(database) {
        Sql.Event.update({
            Sql.Event.id eq id
        }) {
            it[team_size] = size
        }
    }

    fun updateEventDescription(id: Int, desc: String) = transaction(database) {
        Sql.Event.update({
            Sql.Event.id eq id
        }) {
            it[description] = desc
        }
    }

    fun getAllEvents(): List<Event> = transaction(database) {
        Sql.Event.selectAll().map {
            Event(
                it[Sql.Event.id],
                it[Sql.Event.description],
                it[Sql.Event.team_size],
                it[Sql.Event.start].atOffset(zoneOffset).toInstant(),
                it[Sql.Event.end].atOffset(zoneOffset).toInstant()
            )
        }
    }

    fun getEvent(id: Int): Event? = transaction(database) {
        Sql.Event.select {
            Sql.Event.id eq id
        }.firstOrNull()?.let {
            Event(
                it[Sql.Event.id],
                it[Sql.Event.description],
                it[Sql.Event.team_size],
                it[Sql.Event.start].atOffset(zoneOffset).toInstant(),
                it[Sql.Event.end].atOffset(zoneOffset).toInstant()
            )
        }
    }

    /* The event that is currently active, null if no event is active */
    fun getActiveEvent(): Event? = transaction(database) {
        Sql.Event.select {
            Sql.Event.start.less(CurrentDateTime()).and(
                Sql.Event.end greater CurrentDateTime()
            )
        }.firstOrNull()?.let {
            Event(
                it[Sql.Event.id],
                it[Sql.Event.description],
                it[Sql.Event.team_size],
                it[Sql.Event.start].atOffset(zoneOffset).toInstant(),
                it[Sql.Event.end].atOffset(zoneOffset).toInstant(),
            )
        }
    }

    /* The event that is currently active, next event if inactive */
    fun getNextEvent(): Event = transaction(database) {
        getEvent(
            Sql.Event.selectAll()
                .orderBy(Sql.Event.id to SortOrder.DESC)
                .first().let {
                    it[Sql.Event.id]
                }
        )!!
    }

    /* The event that is currently active, last event that took place if inactive */
    fun getLastOrActiveEvent(): Event = transaction(database) {
        Sql.Event.select {
            Sql.Event.start.less(CurrentDateTime())
        }.orderBy(Sql.Event.start to SortOrder.DESC).first().let {
            Event(
                it[Sql.Event.id],
                it[Sql.Event.description],
                it[Sql.Event.team_size],
                it[Sql.Event.start].atOffset(zoneOffset).toInstant(),
                it[Sql.Event.end].atOffset(zoneOffset).toInstant()
            )
        }
    }

    fun insertTeam(event: Event, plotX: Int, plotY: Int, members: List<UUID>): Team = transaction(database) {
        val teamId = Sql.Team.insert {
            it[event_id] = event.id
            it[plot_x] = plotX
            it[plot_y] = plotY
        } get Sql.Team.id
        Sql.Competitor.batchInsert(members) {
            this[Sql.Competitor.uuid] = it.toBin()
            this[Sql.Competitor.team_id] = teamId
        }
        Team(teamId, plotX, plotY, event, false, members)
    }

    fun getTeamOf(eventId: Int, x: Int, y: Int): Team? = transaction(database) {
        Sql.Team.select {
            (Sql.Team.event_id eq eventId) and (Sql.Team.plot_x eq x) and (Sql.Team.plot_y eq y)
        }.firstOrNull()?.let {
            Team(
                it[Sql.Team.id],
                it[Sql.Team.plot_x],
                it[Sql.Team.plot_y],
                getEvent(it[Sql.Team.event_id])!!,
                it[Sql.Team.is_finished],
                Sql.Competitor.select {
                    Sql.Competitor.team_id eq it[Sql.Team.id]
                }.map { competitor ->
                    competitor[Sql.Competitor.uuid].toUuid()
                }
            )
        }
    }

    fun getTeam(id: Int): Team? = transaction(database) {
        Sql.Team.select {
            Sql.Team.id eq id
        }.firstOrNull()?.let {
            Team(
                it[Sql.Team.id],
                it[Sql.Team.plot_x],
                it[Sql.Team.plot_y],
                getEvent(it[Sql.Team.event_id])!!,
                it[Sql.Team.is_finished],
                Sql.Competitor.select {
                    Sql.Competitor.team_id eq it[Sql.Team.id]
                }.map { competitor ->
                    competitor[Sql.Competitor.uuid].toUuid()
                }
            )
        }
    }

    fun getTeamsByEvent(id: Int): List<Team>? = transaction(database) {
        val event = getEvent(id) ?: return@transaction null
        Sql.Team.select {
            Sql.Team.event_id eq id
        }.map {
            Team(
                it[Sql.Team.id],
                it[Sql.Team.plot_x],
                it[Sql.Team.plot_y],
                event,
                it[Sql.Team.is_finished],
                Sql.Competitor.select {
                    Sql.Competitor.team_id eq it[Sql.Team.id]
                }.map { competitor ->
                    competitor[Sql.Competitor.uuid].toUuid()
                }
            )
        }
    }

    fun getActiveTeamOf(uuid: UUID): Team? = transaction(database) {
        Sql.Competitor.select {
            Sql.Competitor.uuid eq uuid.toBin()
        }.orderBy(Sql.Competitor.team_id to SortOrder.DESC).firstOrNull()?.let {
            val team = getTeam(it[Sql.Competitor.team_id]) ?: return@transaction null
            val now = Instant.now()
            if (now.isBefore(team.event.start) || now.isAfter(team.event.end)) {
                return@transaction null
            }
            team
        }
    }

    fun addToTeam(id: Int, user_id: UUID) = transaction(database) {
        Sql.Competitor.insert {
            it[uuid] = user_id.toBin()
            it[team_id] = id
        }
    }

    fun setTeamFinishedState(id: Int, state: Boolean) = transaction(database) {
        Sql.Team.update({ Sql.Team.id eq id}) {
            it[is_finished] = state
        }
    }

    fun removeFromTeam(id: Int, uuid: UUID) = transaction(database) {
        Sql.Competitor.deleteWhere { (Sql.Competitor.uuid eq uuid.toBin()) and (Sql.Competitor.team_id eq id) }
    }

    /** DAngerus funtion !*/
    fun deleteTeam(id: Int) = transaction(database) {
        Sql.Competitor.deleteWhere { Sql.Competitor.team_id eq id }
        Sql.Team.deleteWhere { Sql.Team.id eq id }
    }
}
