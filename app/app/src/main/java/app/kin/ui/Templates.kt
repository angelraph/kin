package app.kin.ui

/** A ready-made way to use a circle. Every template runs on the same on-chain program. */
data class CircleTemplate(
    val id: String,
    /** One or two words, for filter rows. */
    val short: String,
    val title: String,
    val tagline: String,
    val example: String,
    val name: String,
    /** Whole tokens each member pays per round. */
    val contribution: Int,
    val members: Int,
    val bondMultiple: Int,
    val periodSecs: Long,
    val onlyReliable: Boolean = false,
    val randomOrder: Boolean = true,
    val seekerOnly: Boolean = false,
)

object Templates {
    private const val DAY = 86_400L
    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY

    val all: List<CircleTemplate> = listOf(
        CircleTemplate(
            id = "ajo",
            short = "Savings",
            title = "Weekly savings circle",
            tagline = "The classic ajo, susu or tanda. Everyone saves together and each person takes a turn.",
            example = "5 friends, 50 a week, one lump sum of 250 every week",
            name = "Weekly savers",
            contribution = 50, members = 5, bondMultiple = 2, periodSecs = WEEK,
        ),
        CircleTemplate(
            id = "rent",
            short = "Rent",
            title = "Rent and bills",
            tagline = "Housemates take turns receiving a month of rent, so the big bill is never a scramble.",
            example = "4 housemates, 200 a month, an 800 rent pot on rotation",
            name = "Rent pot",
            contribution = 200, members = 4, bondMultiple = 1, periodSecs = MONTH,
            randomOrder = false,
        ),
        CircleTemplate(
            id = "business",
            short = "Business",
            title = "Business capital",
            tagline = "Traders and freelancers pool cash to restock, buy equipment, or hire, without a bank.",
            example = "6 traders, 100 a month, 600 of working capital each month",
            name = "Capital club",
            contribution = 100, members = 6, bondMultiple = 2, periodSecs = MONTH,
            onlyReliable = true,
        ),
        CircleTemplate(
            id = "emergency",
            short = "Emergency",
            title = "Emergency fund pact",
            tagline = "A safety net that only takes in people with a clean record, backed by a double bond.",
            example = "8 people, 25 a month, 200 ready for whoever needs it first",
            name = "Safety net",
            contribution = 25, members = 8, bondMultiple = 3, periodSecs = MONTH,
            onlyReliable = true,
        ),
        CircleTemplate(
            id = "trip",
            short = "Trip",
            title = "Trip or group gift",
            tagline = "Save toward a shared goal on a short cycle and settle up without chasing anyone.",
            example = "6 people, 20 a week, 120 released every week",
            name = "Trip fund",
            contribution = 20, members = 6, bondMultiple = 1, periodSecs = WEEK,
        ),
        CircleTemplate(
            id = "sprint",
            short = "Sprint",
            title = "Seven day sprint",
            tagline = "A small daily habit. Put in a little each day and get a payout within the week.",
            example = "7 people, 5 a day, 35 paid out daily",
            name = "7 day sprint",
            contribution = 5, members = 7, bondMultiple = 1, periodSecs = DAY,
        ),
        CircleTemplate(
            id = "seeker",
            short = "Seeker crew",
            title = "Seeker crew",
            tagline = "Only wallets holding a Seeker Genesis Token can join. One verified person per device.",
            example = "4 Seeker owners, 10 a week, checked on-chain when joining",
            name = "Seeker crew",
            contribution = 10, members = 4, bondMultiple = 2, periodSecs = WEEK,
            seekerOnly = true,
        ),
        CircleTemplate(
            id = "live",
            short = "Test run",
            title = "One minute test run",
            tagline = "Watch a whole circle finish in minutes. Ideal for trying Kin or recording a demo.",
            example = "3 wallets, 1 a minute, every rule enforced at real speed",
            name = "Test run",
            contribution = 1, members = 3, bondMultiple = 2, periodSecs = 60L,
        ),
    )

    fun byId(id: String): CircleTemplate? = all.firstOrNull { it.id == id }
}
