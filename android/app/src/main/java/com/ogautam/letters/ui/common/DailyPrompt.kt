package com.ogautam.letters.ui.common

import java.time.Clock
import java.time.Instant

/**
 * The same twenty prompts as the web app, indexed by whole days since the epoch, so the
 * prompt rotates at UTC midnight and repeats every twenty days. Everyone sees the same
 * one on the same day — that was the point of it, and it survives having no accounts.
 */
object DailyPrompt {

    val PROMPTS: List<String> = listOf(
        "Write to someone you haven't spoken to in over a year.",
        "Write to your past self at age 16.",
        "Write to someone who changed you without knowing it.",
        "Write to a place you'll never visit again.",
        "Write what you wish you'd said at exactly the right moment.",
        "Write to someone who believed in you before you did.",
        "Write to the version of yourself you're afraid to become.",
        "Write to someone you've forgiven but never told.",
        "Write to yourself, ten years from now.",
        "Write to the person who taught you what love really means.",
        "Write about a memory no one else remembers but you.",
        "Write to someone you lost too soon.",
        "Write to the child you once were.",
        "Write about the apology you still owe.",
        "Write to the stranger whose kindness you never forgot.",
        "Write to someone whose face you can't quite remember anymore.",
        "Write to a future child or grandchild who doesn't exist yet.",
        "Write to the friend you drifted from without a reason.",
        "Write to yourself on the hardest day of last year.",
        "Write to someone who never got to see who you became.",
    )

    private const val MILLIS_PER_DAY = 86_400_000L

    fun forInstant(now: Instant): String =
        PROMPTS[(Math.floorDiv(now.toEpochMilli(), MILLIS_PER_DAY) % PROMPTS.size).toInt()]

    fun today(clock: Clock = Clock.systemUTC()): String = forInstant(Instant.now(clock))
}
