package com.kaplay.app.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.rewards.AdRewardState

internal data class HomeRewardBannerContent(
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val isActionEnabled: Boolean,
)

internal fun homeRewardBannerContent(
    requiresAuthentication: Boolean,
    rewardState: AdRewardState,
): HomeRewardBannerContent {
    val remainingMs = rewardState.remainingMs
    return when {
        requiresAuthentication -> HomeRewardBannerContent(
            title = "Ganhe 1 hora sem anúncios",
            subtitle = "Entre na sua conta para assistir a um anúncio",
            actionLabel = "Entrar",
            isActionEnabled = true,
        )

        rewardState.syncing -> HomeRewardBannerContent(
            title = "Atualizando seu benefício",
            subtitle = "Estamos sincronizando o tempo restante",
            actionLabel = "Aguarde",
            isActionEnabled = false,
        )

        remainingMs > 0L -> HomeRewardBannerContent(
            title = "1 hora sem anúncios ativa",
            subtitle = "Tempo restante: ${formatRewardRemaining(remainingMs)}",
            actionLabel = "Ativo",
            isActionEnabled = true,
        )

        rewardState.error != null -> HomeRewardBannerContent(
            title = "Benefício indisponível agora",
            subtitle = "Tente novamente em instantes",
            actionLabel = "Tentar novamente",
            isActionEnabled = true,
        )

        else -> HomeRewardBannerContent(
            title = "Ganhe 1 hora sem anúncios",
            subtitle = "Assista a um anúncio para liberar",
            actionLabel = "Liberar",
            isActionEnabled = true,
        )
    }
}

internal fun formatRewardRemaining(remainingMs: Long): String {
    val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(1L)
    return if (totalMinutes >= 60L) {
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}min"
    } else {
        "${totalMinutes}min"
    }
}

@Composable
internal fun HomeRewardBanner(
    rewardState: AdRewardState,
    requiresAuthentication: Boolean,
    isAuthenticationLoading: Boolean = false,
    onRequestAuthentication: () -> Unit,
    onShowRewarded: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (isAuthenticationLoading) {
        HomeRewardBannerContent(
            title = "Carregando sua conta",
            subtitle = "Aguarde enquanto verificamos sua sessão",
            actionLabel = "Aguarde",
            isActionEnabled = false,
        )
    } else {
        homeRewardBannerContent(requiresAuthentication, rewardState)
    }
    val onClick = when {
        isAuthenticationLoading -> ({ })
        requiresAuthentication -> onRequestAuthentication
        rewardState.remainingMs > 0L -> onShowRewarded
        rewardState.error != null -> onRetry
        else -> onShowRewarded
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        enabled = content.isActionEnabled,
        onClick = onClick,
        shape = KAShapes.Card,
        color = KAColors.SurfaceVariant,
        contentColor = KAColors.TextPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CardGiftcard,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = KAColors.Primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.subtitle,
                    color = KAColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = KAShapes.Button,
                color = if (content.isActionEnabled) KAColors.Primary else KAColors.Surface,
                contentColor = if (content.isActionEnabled) KAColors.OnPrimary else KAColors.TextSecondary,
            ) {
                Text(
                    text = content.actionLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
