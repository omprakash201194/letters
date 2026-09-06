package com.ogautam.letters.ui.scenes

import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneMessageEntity
import java.time.LocalTime

/**
 * A scene to render before there is any way to compose one. It exists to exercise the
 * renderer, so it deliberately contains the awkward cases: a run of consecutive messages
 * from one person (whose label and avatar must be suppressed after the first), a message
 * long enough to wrap against the 280dp cap, and a one-word reply that must not stretch.
 *
 * Delete this once the scene editor lands.
 */
object SampleScene {

    const val NAME = "The group chat"

    private const val SCENE_ID = "sample"
    private const val YOU = "you"
    private const val MEERA = "meera"
    private const val ARJUN = "arjun"

    private val youColor = CharacterPalette.colorForIndex(0)
    private val meeraColor = CharacterPalette.colorForIndex(1)
    private val arjunColor = CharacterPalette.colorForIndex(2)

    val messages: List<SceneMessageEntity> = buildList {
        add(message(MEERA, "Meera", meeraColor, "are you awake", "21:14", outgoing = false))
        add(message(MEERA, "Meera", meeraColor, "i have been staring at the ceiling for an hour", "21:14", outgoing = false))
        add(message(YOU, "You", youColor, "unfortunately yes", "21:15", outgoing = true))
        add(
            message(
                ARJUN, "Arjun", arjunColor,
                "wait why is everyone awake. it is a tuesday. some of us have a standup at nine that we have been pretending is optional",
                "21:16", outgoing = false,
            ),
        )
        add(message(YOU, "You", youColor, "it is not optional", "21:16", outgoing = true))
        add(message(MEERA, "Meera", meeraColor, "it is a little optional", "21:17", outgoing = false))
        add(message(ARJUN, "Arjun", arjunColor, "thank you", "21:17", outgoing = false))
        add(message(YOU, "You", youColor, "go to sleep, both of you", "21:18", outgoing = true))
        add(message(MEERA, "Meera", meeraColor, "no", "21:18", outgoing = false))
    }.mapIndexed { index, message -> message.copy(orderIndex = index) }

    private fun message(
        charId: String,
        charName: String,
        charColor: Int,
        text: String,
        time: String,
        outgoing: Boolean,
    ) = SceneMessageEntity(
        id = "$SCENE_ID-$charId-${text.hashCode()}",
        sceneId = SCENE_ID,
        charId = charId,
        charName = charName,
        charColor = charColor,
        text = text,
        time = LocalTime.parse(time),
        outgoing = outgoing,
        orderIndex = 0,
    )
}
