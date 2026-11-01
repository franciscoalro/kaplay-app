package com.kaplay.app.features.profiles

import com.kaplay.app.core.sync.KAPlaySyncState
import com.kaplay.app.core.sync.KAPlaySyncStatus

internal fun profileSyncStatusText(
    syncState: KAPlaySyncState?,
    isCloudProfile: Boolean,
): String {
    if (!isCloudProfile) return "Local"
    return when (syncState?.status) {
        KAPlaySyncStatus.SYNCING -> "Sincronizando na nuvem…"
        KAPlaySyncStatus.SYNCED -> "Nuvem sincronizada"
        KAPlaySyncStatus.OFFLINE -> "Sem conexão • dados locais preservados"
        KAPlaySyncStatus.ERROR -> "Falha na nuvem • reconectar"
        KAPlaySyncStatus.PENDING -> "Alterações pendentes • sincronizar"
        null -> "Nuvem pronta"
    }
}
