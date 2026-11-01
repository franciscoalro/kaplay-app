package com.kaplay.app.features.profiles

import com.kaplay.app.core.sync.ProfileSyncContractView
import com.kaplay.app.core.sync.ProfileSyncContractStatus

internal fun profileSyncStatusText(
    contractView: ProfileSyncContractView?,
    isCloudProfile: Boolean,
): String {
    if (!isCloudProfile) return "Local"
    if (contractView == null) return "Selecione um perfil"
    return when (contractView.status) {
        ProfileSyncContractStatus.SYNCING -> "Sincronizando na nuvem…"
        ProfileSyncContractStatus.SYNCED -> "Nuvem sincronizada"
        ProfileSyncContractStatus.OFFLINE -> "Sem conexão • dados locais preservados"
        ProfileSyncContractStatus.ERROR -> "Falha na nuvem • reconectar"
        ProfileSyncContractStatus.PENDING -> "Alterações pendentes • sincronizar"
        ProfileSyncContractStatus.LOADING -> "Nuvem pronta"
    }
}
