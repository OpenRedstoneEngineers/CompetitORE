package entity

data class RanksConfig(
    val competitor: String = "competitor_set",
    val competitionJudge: String = "competition_judge_set"
)

data class DatabaseConfig(
    val username: String = "comptest",
    val password: String = "comptest",
    val database: String = "competitore_1",
    val host: String = "localhost",
    val port: Int = 3306
)

data class StartConfig(
    val dayOfWeek: String = "FRIDAY",
    val hour: Int = 12,
    val minute: Int = 0
)

data class EventConfig(
    val start: StartConfig = StartConfig(),
    val length: Int = 48,
    val teamSize: Int = 1
)

data class PlotConfig(
    val height: Int = 3,
    val size: Int = 124,
    val filling: String = "dirt",
    val floor: String = "grass_block",
    val bedrock: Boolean = false,
    val useSigns: Boolean = false
)

data class WallConfig(
    val block: String = "red_concrete",
    val blockClaimed: String = "lime_concrete",
    val placeTopBlock: Boolean = true,
    val filling: String = "quartz_block",
    val height: Int = 2
)

data class RoadConfig(
    val width: Int = 4,
    val height: Int = 3,
    val block: String = "quartz_block"
)

data class WorldConfig(
    val border: Boolean = true
)

data class PlotSettingsConfig(
    val plot: PlotConfig = PlotConfig(),
    val wall: WallConfig = WallConfig(),
    val road: RoadConfig = RoadConfig(),
    val world: WorldConfig = WorldConfig()
)

data class CompetitOreConfig(
    val ranks: RanksConfig = RanksConfig(),
    val competitorDatabase: DatabaseConfig = DatabaseConfig(),
    val event: EventConfig = EventConfig(),
    val plotSettings: PlotSettingsConfig = PlotSettingsConfig(),
    val serverName: String = "competition",
    val gameRules: Map<String, Boolean> = mapOf(
        "doDaylightCycle" to false,
        "doWeatherCycle" to false,
        "keepInventory" to true
    )
)
