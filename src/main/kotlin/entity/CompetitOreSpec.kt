package entity

import com.uchuhimo.konf.ConfigSpec

object CompetitOreSpec : ConfigSpec("") {
    object Ranks: ConfigSpec() {
        val competitor by optional("competitor_set")
        val competitorWorldedit by optional("competitor_worldedit_set")
        val competitionHost by optional("competition_host_set")
        val competitionJudge by optional("competition_judge_set")
        val worldeditMinimumRank by optional("builder")
    }
    object CompetitorDatabase : ConfigSpec() {
        val username by optional("comptest")
        val password by optional("comptest")
        val database by optional("competitore_1")
        val host by optional("localhost")
        val port by optional(3306)
    }
    object Event : ConfigSpec() {
        object Start : ConfigSpec() {
            val dayOfWeek by optional("FRIDAY")
            val hour by optional(15)
            val minute by optional(0)
        }
        val length by optional(48)
        val teamSize by optional(1)
    }
    object PlotSettings : ConfigSpec() {
        object Plot : ConfigSpec() {
            val height by optional(3)
            val size by optional(124)
            val filling by optional("dirt")
            val floor by optional("grass_block")
            val bedrock by optional(false)
            val useSigns by optional(false)
        }
        object Wall : ConfigSpec() {
            val block by optional("red_concrete")
            val blockClaimed by optional("lime_concrete")
            val placeTopBlock by optional(true)
            val filling by optional("quartz_block")
            val height by optional(2)
        }
        object Road : ConfigSpec() {
            val width by optional(4)
            val height by optional(3)
            val block by optional("quartz_block")
        }
        object World : ConfigSpec() {
            val border by optional(true)
        }
    }
    val gameRules by optional(mapOf(
        "doDaylightCycle" to false,
        "doWeatherCycle" to false
    ))
    val timeUntil by optional(mapOf(
        864000 to "12 Hours",
        14400 to "2 Hours",
        72000 to "1 Hour",
        36000 to "30 Minutes",
        18000 to "15 Minutes",
        6000 to "5 Minutes",
        1200 to "1 Minute",
        600 to "30 Seconds",
        200 to "10 Seconds",
        100 to "5 Seconds",
        80 to "4 Seconds",
        60 to "3 Seconds",
        40 to "2 Seconds",
        20 to "1 Second"
    ))
    val timeLeft by optional(mapOf(
        864000 to "12 Hours",
        14400 to "2 Hours",
        72000 to "1 Hour",
        36000 to "30 Minutes",
        18000 to "15 Minutes",
        6000 to "5 Minutes",
        1200 to "1 Minute",
        600 to "30 Seconds",
        200 to "10 Seconds",
        100 to "5 Seconds",
        80 to "4 Seconds",
        60 to "3 Seconds",
        40 to "2 Seconds",
        20 to "1 Second"
    ))
}
