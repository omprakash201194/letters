package com.ogautam.letters.data.entity

/**
 * The eight moods from the web app, in display order. The slug is what persists;
 * emoji and label are presentation and may change without a migration.
 */
enum class Mood(val slug: String, val emoji: String, val label: String) {
    GRATEFUL("grateful", "🙏", "Grateful"),
    HOPEFUL("hopeful", "🌱", "Hopeful"),
    LOVE("love", "❤️", "Love"),
    NOSTALGIC("nostalgic", "🌙", "Nostalgic"),
    PROUD("proud", "⭐", "Proud"),
    SAD("sad", "💧", "Sad"),
    ANGRY("angry", "🔥", "Angry"),
    LONELY("lonely", "🕊️", "Lonely");

    companion object {
        private val bySlug = entries.associateBy(Mood::slug)

        /** Unknown slugs resolve to null rather than throwing — the column is free-form text. */
        fun fromSlug(slug: String?): Mood? = slug?.let(bySlug::get)
    }
}
