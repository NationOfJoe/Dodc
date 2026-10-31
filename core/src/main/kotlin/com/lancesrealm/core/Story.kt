package com.lancesrealm.core

/** The painted backdrop a story panel is shown against. */
enum class Scene {
    THRONE, RIVALS, HIGHMERE, MESSENGER, PLAGUE, POOR_HARVEST, GOOD_HARVEST, FIRE, MERCENARIES,
    KNIGHT, TOWER, REBELS, TRUCE, TAUNT, REUNION, CORONATION, FALL,
}

enum class EventKind {
    INTRO, PLAGUE, POOR_HARVEST, GOOD_HARVEST, FIRE, MERCENARIES, WANDERING_KNIGHT, TRUCE_OFFER,
    TAUNT, REBELLION, SIBLING_TAKEN, SIBLING_MOVED, SIBLING_FREED, ENDING,
}

/**
 * One illustrated story panel. Events without [choices] have already been applied when created;
 * events with choices wait for [GameState.resolveEvent].
 */
class StoryEvent(
    val kind: EventKind,
    val title: String,
    val text: String,
    val scene: Scene,
    val lordId: Int = -1,
    val territoryId: Int = -1,
    val amount: Int = 0,
    val cost: Int = 0,
    val choices: List<String> = emptyList(),
)

object Story {
    const val SIBLING = "Tamsin"

    private val errantKnights = listOf(
        "Sir Brannoc of the Marches", "Dame Oriel Fenwick", "Sir Caddoc the Grey",
        "Dame Hesper Vane", "Sir Lowen Ashgrove",
    )

    fun errantKnight(index: Int) = errantKnights[index % errantKnights.size]

    fun intro(hero: Hero): List<StoryEvent> = listOf(
        StoryEvent(
            EventKind.INTRO, "The Empty Throne",
            "In the high hall of Valdria the throne stands empty. Old King Aldwin is dead, and with him " +
                "the last of his line. The crown waits on its cushion for whoever is strong enough to take it.",
            Scene.THRONE,
        ),
        StoryEvent(
            EventKind.INTRO, "Three Pretenders",
            "Baron Vask of Ravensgate, hard as northern iron. Duke Morlan of Duskhold, patient and cold. " +
                "Countess Ysolde of Saltmarsh Keep, whose smile has ended more lords than any sword.",
            Scene.RIVALS,
        ),
        StoryEvent(
            EventKind.INTRO, "The Keeper of Highmere",
            "And you: ${hero.displayName}, ${hero.epithet}, holding Highmere in the green south-west with your " +
                "sister $SIBLING at your side. Win land by the sword, gold in the lists and glory in daring raids. " +
                "Take all three rival castles and the crown is yours. Lose Highmere and all is lost.",
            Scene.HIGHMERE,
        ),
    )

    fun taunt(lordId: Int, territory: String): String = when (lordId) {
        1 -> "\"You call that a border? My men barely broke a sweat taking $territory. Send me better next time.\""
        2 -> "\"$territory has a wiser lord now. Do try to keep hold of the rest.\""
        else -> "\"How careless of you to leave $territory so poorly guarded. Never mind, I shall take good care of it.\""
    }

    fun victory(state: GameState): StoryEvent = StoryEvent(
        EventKind.ENDING, "Long Live the King!",
        "The last rival banner is lowered and the lords of Valdria kneel in the high hall. " +
            "${state.hero.displayName} takes up the crown " +
            (if (state.siblingFreed) "with $SIBLING beside the throne. " else "alone, for $SIBLING never came home. ") +
            "After ${state.year} years of war, the realm is whole again.",
        Scene.CORONATION,
    )

    fun defeat(state: GameState): StoryEvent = StoryEvent(
        EventKind.ENDING, "Highmere Has Fallen",
        "Smoke rises over Highmere and your banner is torn from its tower. " +
            "Another will sit upon the throne of Valdria, and the songs will not remember ${state.hero.displayName}.",
        Scene.FALL,
    )
}
