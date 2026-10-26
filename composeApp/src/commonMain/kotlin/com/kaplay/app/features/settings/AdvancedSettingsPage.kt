package com.kaplay.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaplay.app.core.ui.DialogButton
import com.kaplay.app.core.ui.DialogButtons
import com.kaplay.app.core.ui.DialogButtonStyle
import com.kaplay.app.core.ui.DialogSurface
import com.kaplay.app.core.ui.KAPlayTokens
import com.kaplay.app.core.ui.kaplay
import com.kaplay.app.features.profiles.ProfileRepository
import com.kaplay.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.kaplay.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.launch
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.action_cancel
import kaplay.composeapp.generated.resources.settings_advanced_clear_cw_cache
import kaplay.composeapp.generated.resources.settings_advanced_clear_cw_cache_done
import kaplay.composeapp.generated.resources.settings_advanced_clear_cw_cache_subtitle
import kaplay.composeapp.generated.resources.settings_advanced_remember_last_profile
import kaplay.composeapp.generated.resources.settings_advanced_remember_last_profile_description
import kaplay.composeapp.generated.resources.settings_advanced_section_cache
import kaplay.composeapp.generated.resources.settings_advanced_section_diagnostics
import kaplay.composeapp.generated.resources.settings_advanced_section_startup
import kaplay.composeapp.generated.resources.settings_advanced_sentry_reports
import kaplay.composeapp.generated.resources.settings_advanced_sentry_reports_subtitle
import kaplay.composeapp.generated.resources.sentry_disable_dialog_subtitle
import kaplay.composeapp.generated.resources.sentry_disable_dialog_title
import kaplay.composeapp.generated.resources.sentry_enable_dialog_subtitle
import kaplay.composeapp.generated.resources.sentry_enable_dialog_title
import kaplay.composeapp.generated.resources.sentry_help_body
import kaplay.composeapp.generated.resources.sentry_help_title
import kaplay.composeapp.generated.resources.sentry_keep_enabled
import kaplay.composeapp.generated.resources.sentry_not_sent_body
import kaplay.composeapp.generated.resources.sentry_not_sent_title
import kaplay.composeapp.generated.resources.sentry_sent_body
import kaplay.composeapp.generated.resources.sentry_sent_title
import kaplay.composeapp.generated.resources.sentry_turn_off
import kaplay.composeapp.generated.resources.sentry_turn_on
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.advancedSettingsContent(
    isTablet: Boolean,
    rememberLastProfileEnabled: Boolean,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_startup),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_remember_last_profile),
                    description = stringResource(Res.string.settings_advanced_remember_last_profile_description),
                    checked = rememberLastProfileEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ProfileRepository::setRememberLastProfileEnabled,
                )
            }
        }
    }
    if (SentrySettingsRepository.isSupported) {
        item {
            val sentryEnabledFlow = remember {
                SentrySettingsRepository.ensureLoaded()
                SentrySettingsRepository.enabled
            }
            val sentryEnabled by sentryEnabledFlow.collectAsStateWithLifecycle()
            var showSentryDialog by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_diagnostics),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_sentry_reports),
                        description = stringResource(Res.string.settings_advanced_sentry_reports_subtitle),
                        checked = sentryEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { showSentryDialog = true },
                    )
                }
            }

            if (showSentryDialog) {
                SentrySettingsDialog(
                    enabled = sentryEnabled,
                    onConfirm = {
                        SentrySettingsRepository.setEnabled(!sentryEnabled)
                    },
                    onDismiss = {
                        showSentryDialog = false
                    },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_cache),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val scope = rememberCoroutineScope()
                var cleared by rememberSaveable { mutableStateOf(false) }
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_clear_cw_cache),
                    description = if (cleared) {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_done)
                    } else {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_subtitle)
                    },
                    isTablet = isTablet,
                    onClick = {
                        if (!cleared) {
                            ContinueWatchingEnrichmentCache.clearAll(ProfileRepository.activeProfileId)
                            cleared = true
                            scope.launch {
                                WatchProgressRepository.clearLocalAndForceSnapshotRefreshFromServer(
                                    ProfileRepository.activeProfileId,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SentrySettingsDialog(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.kaplay
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(
                        if (enabled) {
                            Res.string.sentry_disable_dialog_title
                        } else {
                            Res.string.sentry_enable_dialog_title
                        },
                    ),
    ) {
        Text(
            text = stringResource(
                if (enabled) {
                    Res.string.sentry_disable_dialog_subtitle
                } else {
                    Res.string.sentry_enable_dialog_subtitle
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textMuted,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s12),
        ) {
            SentryInfoSection(
                title = stringResource(Res.string.sentry_help_title),
                body = stringResource(Res.string.sentry_help_body),
            )
            SentryInfoSection(
                title = stringResource(Res.string.sentry_sent_title),
                body = stringResource(Res.string.sentry_sent_body),
            )
            SentryInfoSection(
                title = stringResource(Res.string.sentry_not_sent_title),
                body = stringResource(Res.string.sentry_not_sent_body),
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(
                        if (enabled) {
                            Res.string.sentry_keep_enabled
                        } else {
                            Res.string.action_cancel
                        },
                    ),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(
                        if (enabled) {
                            Res.string.sentry_turn_off
                        } else {
                            Res.string.sentry_turn_on
                        },
                    ),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun SentryInfoSection(
    title: String,
    body: String,
) {
    val tokens = MaterialTheme.kaplay
    Column(
        verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
    }
}
