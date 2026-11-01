package com.kaplay.app.features.rewards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression tests for Task 2 (rewards/profile isolation).
 *
 * Local reward storage must be scoped to the owning account AND the active
 * profile, mirroring the backend composite key (user_id, profile_index)
 * from supabase/migrations/20261028000000_ka_ad_rewards.sql. Without the
 * profile in the key, switching profiles leaks one profile's ad-free
 * balance into every other profile of the same account.
 */
class RewardStorageTest {

    @Test
    fun `same user different profiles use different storage keys`() {
        val user = "00000000-0000-0000-0000-000000000001"

        val keyProfile1 = RewardStorage.key(user, profileIndex = 1)
        val keyProfile2 = RewardStorage.key(user, profileIndex = 2)

        assertNotEquals(keyProfile1, keyProfile2)
    }

    @Test
    fun `different users same profile use different storage keys`() {
        val otherUser = "00000000-0000-0000-0000-000000000002"

        val keyUserAProfile1 = RewardStorage.key(otherUser, profileIndex = 2)
        val keyUserBProfile1 = RewardStorage.key("00000000-0000-0000-0000-000000000003", profileIndex = 2)

        assertNotEquals(keyUserAProfile1, keyUserBProfile1)
    }

    @Test
    fun `storage key is deterministic and orders userId then profileIndex`() {
        val user = "00000000-0000-0000-0000-000000000001"

        assertEquals(
            RewardStorage.key(user, profileIndex = 1),
            RewardStorage.key(user, profileIndex = 1),
        )
        assertEquals("ad_free_until_${user}_1", RewardStorage.key(user, profileIndex = 1))
    }
}
