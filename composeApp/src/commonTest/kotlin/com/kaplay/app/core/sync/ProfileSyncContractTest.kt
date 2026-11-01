package com.kaplay.app.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSyncContractTest {
    @Test
    fun `contract preserves profile identity and sync error state`() {
        val view = profileSyncContractView(
            profileIndex = 2,
            state = KAPlaySyncState(
                status = KAPlaySyncStatus.ERROR,
                errorCode = "PGRST202",
            ),
        )

        assertEquals(2, view.profileIndex)
        assertEquals(ProfileSyncContractStatus.ERROR, view.status)
        assertEquals("PGRST202", view.errorCode)
    }

    @Test
    fun `missing state starts in loading`() {
        assertEquals(
            ProfileSyncContractStatus.LOADING,
            profileSyncContractView(1, null).status,
        )
    }
}
