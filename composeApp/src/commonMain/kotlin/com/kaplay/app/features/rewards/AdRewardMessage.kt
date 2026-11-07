package com.kaplay.app.features.rewards

/**
 * Builds the message shown when a user taps the reward while its benefit is active.
 * The caller supplies the value read at tap time, rather than a cached UI label.
 */
internal fun activeAdRewardMessage(remainingMs: Long): String {
    val remainingText = when {
        remainingMs < 60_000L -> "menos de 1 minuto"
        remainingMs / 60_000L < 60L -> {
            val minutes = remainingMs / 60_000L
            "$minutes ${if (minutes == 1L) "minuto" else "minutos"}"
        }
        else -> {
            val totalMinutes = remainingMs / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            val hoursText = "$hours ${if (hours == 1L) "hora" else "horas"}"
            if (minutes == 0L) {
                hoursText
            } else {
                "$hoursText e $minutes ${if (minutes == 1L) "minuto" else "minutos"}"
            }
        }
    }
    return "Você já está sem anúncios. Restam $remainingText de benefício."
}
