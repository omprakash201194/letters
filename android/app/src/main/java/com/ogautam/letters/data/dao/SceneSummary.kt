package com.ogautam.letters.data.dao

import java.time.Instant

/** Projection for the scenes list — avoids loading every message to show two counts. */
data class SceneSummary(
    val id: String,
    val name: String,
    val characterCount: Int,
    val messageCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
