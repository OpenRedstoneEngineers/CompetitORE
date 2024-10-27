import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.formatting.text.TextComponent
import com.sk89q.worldedit.util.formatting.text.format.TextColor
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration
import entity.Event
import entity.Team
import org.bukkit.entity.Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.*
import java.util.*
//import java.time.Duration // 2020 - 12 - 30 : Today, pauk wrote a semicolon here. Never forget.import java.time.temporal.TemporalAdjusters.firstInMonth

val pluginZoneOffset: ZoneOffset = ZoneOffset.UTC

val Event.name: String get() = LocalDateTime.ofInstant(start, pluginZoneOffset).month.name.toLowerCase().capitalize() +
        " ${LocalDateTime.ofInstant(start, pluginZoneOffset).year}"
val Event.key: String get() = "competition_${id}"

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
