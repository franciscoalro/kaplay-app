package com.kaplay.app.core.tracking

import com.kaplay.app.features.simkl.SimklAuthRepository
import com.kaplay.app.features.simkl.SimklMutationRepository
import com.kaplay.app.features.simkl.SimklLibraryRepository
import com.kaplay.app.features.simkl.SimklProgressRepository
import com.kaplay.app.features.simkl.SimklTrackingLibraryProvider
import com.kaplay.app.features.simkl.SimklTrackingProgressProvider
import com.kaplay.app.features.simkl.SimklWatchedSyncAdapter
import com.kaplay.app.features.simkl.SimklSyncRepository
import com.kaplay.app.features.tracking.TrackingProviderRegistry
import com.kaplay.app.features.mdblist.MdbListTracker
import com.kaplay.app.features.trakt.TraktAuthRepository
import com.kaplay.app.features.trakt.TraktScrobbleRepository
import com.kaplay.app.features.trakt.TraktTrackingLibraryProvider
import com.kaplay.app.features.trakt.TraktTrackingProgressProvider
import com.kaplay.app.features.watching.sync.TraktWatchedSyncAdapter

fun ensureTrackingProvidersRegistered() {
    MdbListTracker.register()
    TraktAuthRepository.descriptor
    TraktScrobbleRepository.ensureRegistered()
    SimklAuthRepository.descriptor
    SimklSyncRepository.state
    SimklLibraryRepository.uiState
    SimklProgressRepository.uiState
    SimklMutationRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerLibraryProvider(SimklTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerWatchedProvider(SimklWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
    TrackingProviderRegistry.registerProgressProvider(SimklTrackingProgressProvider)
}
