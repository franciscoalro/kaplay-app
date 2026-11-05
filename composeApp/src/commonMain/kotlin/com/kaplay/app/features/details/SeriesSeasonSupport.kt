package com.kaplay.app.features.details

internal const val SPECIALS_SEASON_NUMBER = 0

internal val metaVideoSeasonEpisodeComparator: Comparator<MetaVideo> =
    compareBy<MetaVideo>(
        { seasonSortKey(it.season) },
        { it.episode ?: Int.MAX_VALUE },
        { it.released ?: "" },
        { it.title },
    )

internal fun normalizeSeasonNumber(seasonNumber: Int?): Int =
    if (seasonNumber == null || seasonNumber <= SPECIALS_SEASON_NUMBER) {
        SPECIALS_SEASON_NUMBER
    } else {
        seasonNumber
    }

internal fun seasonSortKey(seasonNumber: Int?): Int =
    if (seasonNumber == null || seasonNumber <= SPECIALS_SEASON_NUMBER) {
        Int.MAX_VALUE
    } else {
        seasonNumber
    }

internal fun preferredEpisodeNumberForSeason(
    displayedSeasonNumber: Int,
    preferredSeasonNumber: Int?,
    preferredEpisodeNumber: Int?,
): Int? = preferredEpisodeNumber.takeIf { displayedSeasonNumber == preferredSeasonNumber }

internal fun MetaVideo.canonicalEpisodeKey(): String =
    when {
        season != null && episode != null -> "s${normalizeSeasonNumber(season)}e$episode"
        episode != null -> "e$episode"
        else -> id
    }

internal fun List<MetaVideo>.deduplicateCanonicalEpisodes(): List<MetaVideo> {
    if (size <= 1) return this
    val merged = linkedMapOf<String, MetaVideo>()
    for (video in this) {
        val key = video.canonicalEpisodeKey()
        val existing = merged[key]
        if (existing == null) {
            merged[key] = video
        } else {
            merged[key] = existing.copy(
                thumbnail = existing.thumbnail ?: video.thumbnail,
                overview = existing.overview ?: video.overview,
                released = existing.released ?: video.released,
                runtime = existing.runtime ?: video.runtime,
                rating = existing.rating ?: video.rating,
                streams = if (existing.streams.isNotEmpty()) existing.streams else video.streams,
            )
        }
    }
    return merged.values.toList()
}

internal fun MetaDetails.groupedEpisodesForDisplay(): Map<Int, List<MetaVideo>> {
    val numberedEpisodes = videos.filter { it.season != null || it.episode != null }
    return when {
        numberedEpisodes.isNotEmpty() -> numberedEpisodes
            .sortedWith(metaVideoSeasonEpisodeComparator)
            .deduplicateCanonicalEpisodes()
            .groupBy { normalizeSeasonNumber(it.season) }
        type != "series" && videos.isNotEmpty() -> mapOf(normalizeSeasonNumber(null) to videos.distinctBy { it.id })
        else -> emptyMap()
    }
}
