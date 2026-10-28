package com.kaplay.app.features.rewards

import co.touchlab.kermit.Logger
import com.kaplay.app.core.auth.AuthRepository
import com.kaplay.app.core.auth.AuthState
import com.kaplay.app.core.network.SupabaseProvider
import com.kaplay.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal data class AdRewardState(
    val remainingMs: Long = 0L,
    val syncing: Boolean = false,
    val error: String? = null,
)

internal object AdRewardRepository {
    private const val TABLE = "ka_ad_reward_balances"
    private const val RPC = "ka_claim_ad_reward"
    private val log = Logger.withTag("AdRewardRepository")
    private val json = Json { ignoreUnknownKeys = true }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun currentState(): AdRewardState {
        val userId = remoteUserId() ?: return AdRewardState()
        val remaining = (RewardStorage.loadUntil(userId) - nowMs()).coerceAtLeast(0L)
        return AdRewardState(remainingMs = remaining)
    }

    fun remoteUserId(): String? = when (val auth = AuthRepository.state.value) {
        is AuthState.Authenticated -> auth.userId.takeIf { !auth.isAnonymous }
        else -> null
    }

    suspend fun refresh(): Result<Long> = withContext(Dispatchers.Default) {
        val userId = remoteUserId()
            ?: return@withContext Result.failure(IllegalStateException("Entre na sua conta KA Play para ganhar recompensas"))
        runCatching {
            val rows = SupabaseProvider.client.postgrest
                .from(TABLE)
                .select { filter { eq("user_id", userId) } }
                .decodeList<RewardBalanceRow>()
            val remoteUntil = rows.maxOfOrNull { parseTimestamp(it.expiresAt) } ?: 0L
            val localUntil = RewardStorage.loadUntil(userId)
            val finalUntil = maxOf(remoteUntil, localUntil)
            if (finalUntil > 0L) RewardStorage.saveUntil(userId, finalUntil)
            finalUntil
        }.onFailure { log.w(it) { "Reward refresh failed" } }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun claimAfterAd(): Result<Long> = withContext(Dispatchers.Default) {
        val userId = remoteUserId()
            ?: return@withContext Result.failure(IllegalStateException("Faça login para salvar a recompensa"))
        runCatching {
            val response = SupabaseProvider.client.postgrest.rpc(
                RPC,
                buildJsonObject {
                    put("p_profile_index", ProfileRepository.state.value.activeProfile?.profileIndex ?: 1)
                    put("p_event_id", Uuid.random().toString())
                },
            )
            val expiresAt = json.decodeFromString<RewardClaimResponse>(response.data).expiresAt
            val authoritativeUntil = parseTimestamp(expiresAt)
            RewardStorage.saveUntil(userId, authoritativeUntil)
            authoritativeUntil
        }.onFailure { log.w(it) { "Reward cloud sync failed; no reward granted" } }
    }

    private fun parseTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)

    @Serializable
    private data class RewardBalanceRow(
        @SerialName("expires_at") val expiresAt: String = "",
    )

    @Serializable
    private data class RewardClaimResponse(
        @SerialName("expires_at") val expiresAt: String,
    )
}
