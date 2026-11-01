package com.kaplay.app.features.profiles

import com.kaplay.app.core.sync.KAPlaySyncState
import com.kaplay.app.core.sync.KAPlaySyncStatus
import com.kaplay.app.core.sync.profileSyncContractView
import com.kaplay.app.core.sync.ProfileSyncContractStatus
import com.kaplay.app.core.sync.ProfileSyncContractView
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSyncStatusPresenterTest {
    @Test
    fun `profile sync status distinguishes local cloud and reconnect states`() {
        assertEquals("Local", profileSyncStatusText(null, isCloudProfile = false))
        assertEquals("Selecione um perfil", profileSyncStatusText(null, isCloudProfile = true))
        assertEquals("Sincronizando na nuvem…", profileSyncStatusText(profileSyncContractView("user-a", 1, KAPlaySyncState(KAPlaySyncStatus.SYNCING)), true))
        assertEquals("Nuvem sincronizada", profileSyncStatusText(profileSyncContractView("user-a", 1, KAPlaySyncState(KAPlaySyncStatus.SYNCED)), true))
        assertEquals("Sem conexão • dados locais preservados", profileSyncStatusText(profileSyncContractView("user-a", 1, KAPlaySyncState(KAPlaySyncStatus.OFFLINE)), true))
        assertEquals("Falha na nuvem • reconectar", profileSyncStatusText(profileSyncContractView("user-a", 1, KAPlaySyncState(KAPlaySyncStatus.ERROR)), true))
    }

    @Test
    fun `loading sync state explains verification and offers retry only after an error`() {
        assertEquals(
            "Verificando sincronização…",
            profileSyncStatusText(
                ProfileSyncContractView(
                    userId = "user-a",
                    profileIndex = 1,
                    status = ProfileSyncContractStatus.LOADING,
                ),
                isCloudProfile = true,
            ),
        )
        assertEquals(null, profileSyncStatusAction(ProfileSyncContractStatus.LOADING))
        assertEquals("Tentar novamente", profileSyncStatusAction(ProfileSyncContractStatus.ERROR))
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
