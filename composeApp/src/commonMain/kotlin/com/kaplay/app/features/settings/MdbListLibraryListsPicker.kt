package com.kaplay.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.DialogButton
import com.kaplay.app.core.ui.DialogButtons
import com.kaplay.app.core.ui.KAPlayBottomSheetDivider
import com.kaplay.app.core.ui.KAPlayLoadingIndicator
import com.kaplay.app.core.ui.KAPlayModalBottomSheet
import com.kaplay.app.core.ui.SurfaceEdge
import com.kaplay.app.core.ui.dismissKAPlayBottomSheet
import com.kaplay.app.core.ui.kaplay
import com.kaplay.app.features.mdblist.MdbListLibraryListOption
import com.kaplay.app.features.mdblist.localizedMdbListMessage
import kotlinx.coroutines.launch
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.action_close
import kaplay.composeapp.generated.resources.settings_mdblist_library_lists
import kaplay.composeapp.generated.resources.settings_mdblist_library_lists_description
import kaplay.composeapp.generated.resources.settings_mdblist_library_lists_empty
import org.jetbrains.compose.resources.stringResource

/** Multi-select counterpart of [TrackingAdaptivePicker]: one switch per MDBList list. */
@Composable
internal fun MdbListLibraryListsPicker(
    isTablet: Boolean,
    options: List<MdbListLibraryListOption>,
    isLoading: Boolean,
    onToggle: (MdbListLibraryListOption, onResult: (Throwable?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val toggle: (MdbListLibraryListOption) -> Unit = { option ->
        if (pendingKey == null) {
            pendingKey = option.key
            errorMessage = null
            onToggle(option) { error ->
                pendingKey = null
                errorMessage = error?.localizedMdbListMessage()
            }
        }
    }
    if (isTablet) {
        MdbListLibraryListsDialog(options, isLoading, pendingKey, errorMessage, toggle, onDismiss)
    } else {
        MdbListLibraryListsBottomSheet(options, isLoading, pendingKey, errorMessage, toggle, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListLibraryListsBottomSheet(
    options: List<MdbListLibraryListOption>,
    isLoading: Boolean,
    pendingKey: String?,
    errorMessage: String?,
    onToggle: (MdbListLibraryListOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch {
            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
        }
    }

    KAPlayModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
    ) {
        MdbListLibraryListsContent(
            options = options,
            isLoading = isLoading,
            pendingKey = pendingKey,
            errorMessage = errorMessage,
            isTablet = false,
            onToggle = onToggle,
            onDismiss = ::dismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListLibraryListsDialog(
    options: List<MdbListLibraryListOption>,
    isLoading: Boolean,
    pendingKey: String?,
    errorMessage: String?,
    onToggle: (MdbListLibraryListOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.kaplay
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = tokens.components.dialogMaxWidth),
            shape = tokens.shapes.dialog,
            color = tokens.colors.surfaceDialog,
            border = BorderStroke(tokens.borders.thin, SurfaceEdge),
        ) {
            MdbListLibraryListsContent(
                options = options,
                isLoading = isLoading,
                pendingKey = pendingKey,
                errorMessage = errorMessage,
                isTablet = true,
                onToggle = onToggle,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun MdbListLibraryListsContent(
    options: List<MdbListLibraryListOption>,
    isLoading: Boolean,
    pendingKey: String?,
    errorMessage: String?,
    isTablet: Boolean,
    onToggle: (MdbListLibraryListOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.kaplay
    val horizontalPadding = if (isTablet) tokens.spacing.dialogPadding else tokens.spacing.screenHorizontal
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = if (isTablet) tokens.spacing.dialogPadding else 12.dp,
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_mdblist_library_lists),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.settings_mdblist_library_lists_description),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        when {
            options.isNotEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, option ->
                    if (index > 0) {
                        KAPlayBottomSheetDivider()
                    }
                    // Changes save one at a time; the other switches stay usable once it finishes.
                    SettingsSwitchRow(
                        title = option.name,
                        checked = option.visible,
                        enabled = pendingKey == null,
                        isTablet = isTablet,
                        onCheckedChange = { onToggle(option) },
                    )
                }
            }
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                KAPlayLoadingIndicator(modifier = Modifier.size(24.dp))
            }
            else -> Text(
                text = stringResource(Res.string.settings_mdblist_library_lists_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
            )
        }

        if (isTablet) {
            DialogButtons(
                modifier = Modifier.padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = horizontalPadding,
                ),
            ) {
                DialogButton(
                    text = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}
