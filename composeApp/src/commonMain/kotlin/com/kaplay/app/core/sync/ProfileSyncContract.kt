package com.kaplay.app.core.sync

internal enum class ProfileSyncContractStatus {
    LOADING,
    SYNCING,
    SYNCED,
    PENDING,
    OFFLINE,
    ERROR,
}

internal data class ProfileSyncContractView(
    val userId: String,
    val profileIndex: Int,
    val status: ProfileSyncContractStatus,
    val errorCode: String? = null,
)

internal fun profileSyncContractView(
    userId: String,
    profileIndex: Int,
    state: KAPlaySyncState?,
): ProfileSyncContractView {
    val status = when (state?.status) {
        null -> ProfileSyncContractStatus.LOADING
        KAPlaySyncStatus.SYNCING -> ProfileSyncContractStatus.SYNCING
        KAPlaySyncStatus.SYNCED -> ProfileSyncContractStatus.SYNCED
        KAPlaySyncStatus.PENDING -> ProfileSyncContractStatus.PENDING
        KAPlaySyncStatus.OFFLINE -> ProfileSyncContractStatus.OFFLINE
        KAPlaySyncStatus.ERROR -> ProfileSyncContractStatus.ERROR
    }
    return ProfileSyncContractView(
        userId = userId,
        profileIndex = profileIndex,
        status = status,
        errorCode = state?.errorCode,
    )
}
