import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.formatting.text.TextComponent
import com.sk89q.worldedit.util.formatting.text.format.TextColor
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration
import entity.Event
import entity.Team
import net.luckperms.api.model.user.User
import net.luckperms.api.model.user.UserManager
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.query.QueryOptions
import org.bukkit.entity.Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.*
//import java.time.Duration // 2020 - 12 - 30 : Today, pauk wrote a semicolon here. Never forget.import java.time.temporal.TemporalAdjusters.firstInMonth

val Event.name: String get() = LocalDateTime.ofInstant(start, ZoneOffset.ofHours(-5)).month.name.toLowerCase().capitalize() +
        " ${LocalDateTime.ofInstant(start, ZoneOffset.ofHours(-5)).year}"
val Event.key: String get() = name.toLowerCase().replace(' ', '_')

fun Instant.prettyPrint(offset: ZoneOffset = ZoneOffset.ofHours(-5)): String {
    val dateTime = LocalDateTime.ofInstant(this, offset)
    return dateTime.month.name.toLowerCase().capitalize() +
        " ${dateTime.dayOfMonth}," +
        " ${dateTime.year}" +
        " at ${dateTime.hour}:${dateTime.minute}:${dateTime.second}" +
        " ${if (offset.id == "Z") "UTC" else offset.id}"
}

fun Instant.countdown(to: Instant): String {
    var duration = Duration.between(this, to)
    val days = duration.toDays()
    duration = duration.minusDays(days)
    val hours = duration.toHours()
    duration = duration.minusHours(hours)
    val minutes = duration.toMinutes()
    duration = duration.minusMinutes(minutes)
    val seconds = duration.toMillis() / 1000
    return buildString {
        if (days != 0L) {
            append("$days days, ")
        }
        if (hours != 0L) {
            append("$hours hours, ")
        }
        if (minutes != 0L) {
            append("$minutes minutes, ")
        }
        if (this.isEmpty()) {
            append("$seconds seconds")
        } else {
            append("and $seconds seconds")
        }
    }
}

fun UUID.toBin() : ByteArray {
    val uuidBytes = ByteArray(16)
    ByteBuffer.wrap(uuidBytes)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
    return uuidBytes
}

fun ByteArray.toUuid() : UUID {
    val wrap = ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN)
    val msb = wrap.long
    val lsb = wrap.long
    return UUID(msb, lsb)
}

fun Team.getPlot(competitOre: CompetitOre) =
    competitOre.plotApi.getPlotAreas(
        competitOre.activeEvent!!.key
    ).first().plots.first {
        (it.id.getX() == this.plotX) && (it.id.getY() == this.plotY)
    }

fun Player.sendCompetitionError(message: String) =
    BukkitAdapter.adapt(this).print(
        TextComponent.of("[").color(TextColor.DARK_GRAY)
            .append(TextComponent.of("Competition").color(TextColor.GRAY))
            .append(TextComponent.of("] [").color(TextColor.DARK_GRAY))
            .append(TextComponent.of("!").color(TextColor.RED).decoration(TextDecoration.BOLD, true))
            .append(TextComponent.of("] ").color(TextColor.DARK_GRAY))
            .append(TextComponent.of(message).color(TextColor.GRAY))
    )

fun Player.sendCompetition(message: String) =
    BukkitAdapter.adapt(this).print(
        TextComponent.of("[").color(TextColor.DARK_GRAY)
            .append(TextComponent.of("Competition").color(TextColor.GRAY))
            .append(TextComponent.of("]").color(TextColor.DARK_GRAY))
            .append(TextComponent.of(" $message").color(TextColor.GRAY))
    )

fun getNextEventStartTime(dayOfWeek: String, hour: Int, minute: Int): LocalDateTime {
    val now = LocalDateTime.now()
    val day = DayOfWeek.valueOf(dayOfWeek.toUpperCase())
    val firstFriday = LocalDateTime.of(
        now.year,
        now.month,
        now.with(TemporalAdjusters.firstInMonth(day)).dayOfMonth,
        hour,
        minute
    )
    return if (now.isAfter(firstFriday)) {
        firstFriday.plusMonths(1).with(TemporalAdjusters.firstInMonth(day))
    } else {
        firstFriday
    }
}

fun List<UUID>.filterWorldEditUsers(userManager: UserManager, rankName: String) = this
    .filter { user ->
        userManager.getUser(user)
            ?.getInheritedGroups(QueryOptions.nonContextual())
            ?.any { group -> group.name == rankName } == true
    }

fun User.removeGroupNode(userManager: UserManager, name: String) {
    this.data().remove(InheritanceNode.builder(name).build())
    userManager.saveUser(this)
}

fun User.addGroupNode(userManager: UserManager, name: String) {
    this.data().add(InheritanceNode.builder(name).build())
    userManager.saveUser(this)
}
