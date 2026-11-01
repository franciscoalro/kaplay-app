package com.kaplay.app.features.profiles

import com.kaplay.app.core.sync.KAPlaySyncState
import com.kaplay.app.core.sync.KAPlaySyncStatus
import com.kaplay.app.core.sync.ProfileSyncContractStatus
import com.kaplay.app.core.sync.ProfileSyncContractView
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSyncStatusPresenterTest {
    @Test
    fun `profile sync status distinguishes local cloud and reconnect states`() {
        assertEquals("Local", profileSyncStatusText(null, isCloudProfile = false))
        assertEquals("Sincronizando na nuvem…", profileSyncStatusText(KAPlaySyncState(KAPlaySyncStatus.SYNCING), true))
        assertEquals("Nuvem sincronizada", profileSyncStatusText(KAPlaySyncState(KAPlaySyncStatus.SYNCED), true))
        assertEquals("Sem conexão • dados locais preservados", profileSyncStatusText(KAPlaySyncState(KAPlaySyncStatus.OFFLINE), true))
        assertEquals("Falha na nuvem • reconectar", profileSyncStatusText(KAPlaySyncState(KAPlaySyncStatus.ERROR), true))
    }

    @Test
    fun `profile sync presenter consumes shared identity contract`() {
        assertEquals(
            "Falha na nuvem • reconectar",
            profileSyncStatusText(
                ProfileSyncContractView(
                    userId = "user-a",
                    profileIndex = 2,
                    status = ProfileSyncContractStatus.ERROR,
                    errorCode = "PGRST202",
                ),
                isCloudProfile = true,
            ),
        )
    }
}
