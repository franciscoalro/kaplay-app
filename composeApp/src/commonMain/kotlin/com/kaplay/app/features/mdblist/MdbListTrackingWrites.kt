package com.kaplay.app.features.mdblist

import com.kaplay.app.features.tracking.TrackingHistoryItem
import com.kaplay.app.features.tracking.TrackingHistoryWriter
import com.kaplay.app.features.tracking.TrackingMediaReference
import com.kaplay.app.features.tracking.TrackingProviderId
import com.kaplay.app.features.tracking.TrackingScrobbleAction
import com.kaplay.app.features.tracking.TrackingScrobbleEvent
import com.kaplay.app.features.tracking.TrackingScrobbler
import com.kaplay.app.features.tracking.TrackingSeekScrobblePolicy
import kotlinx.coroutines.CancellationException

class MdbListTrackingWrites(
    private val sync: MdbListSyncRepository,
    private val history: MdbListHistoryService,
    private val scrobble: MdbListScrobbleService,
) : TrackingHistoryWriter, TrackingScrobbler {
    override val providerId = TrackingProviderId.MDBLIST
    override val seekScrobblePolicy = TrackingSeekScrobblePolicy.STOP_AND_RESTART

    override suspend fun addToHistory(profileId: Int, items: Collection<TrackingHistoryItem>) =
        history.add(scope(profileId), items)

    override suspend fun removeFromHistory(profileId: Int, items: Collection<TrackingMediaReference>) =
        history.remove(scope(profileId), items)

    override suspend fun scrobble(profileId: Int, action: TrackingScrobbleAction, event: TrackingScrobbleEvent) =
        scrobble.scrobble(scope(profileId), action, event)

    private fun scope(profileId: Int): MdbListAuthScope = sync.currentScope().also {
        if (it.profileId != profileId) throw CancellationException("MDBList profile changed")
    }
}
