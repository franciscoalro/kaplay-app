package com.kaplay.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.kaplay.app.core.ui.CustomThemeColors
import com.kaplay.app.core.ui.KAPlayInputField
import com.kaplay.app.core.ui.KAPlayModalBottomSheet
import com.kaplay.app.core.ui.KAPlayPrimaryButton
import com.kaplay.app.core.ui.KAPlayTokens
import com.kaplay.app.core.ui.dismissKAPlayBottomSheet
import com.kaplay.app.core.ui.formatHexColor
import com.kaplay.app.core.ui.kaplay
import com.kaplay.app.core.ui.parseHexColor
import kotlinx.coroutines.launch
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.action_cancel
import kaplay.composeapp.generated.resources.custom_theme_color_number
import kaplay.composeapp.generated.resources.custom_theme_hex_error
import kaplay.composeapp.generated.resources.custom_theme_hex_title
import kaplay.composeapp.generated.resources.custom_theme_mode_gradient
import kaplay.composeapp.generated.resources.custom_theme_mode_solid
import kaplay.composeapp.generated.resources.custom_theme_save
import kaplay.composeapp.generated.resources.custom_theme_solid_subtitle
import kaplay.composeapp.generated.resources.custom_theme_solid_title
import kaplay.composeapp.generated.resources.custom_theme_subtitle
import kaplay.composeapp.generated.resources.custom_theme_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomThemeEditor(
    initialColors: CustomThemeColors,
    allowGradient: Boolean,
    onSave: (CustomThemeColors) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.kaplay
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var encodedColors by rememberSaveable(allowGradient) { mutableStateOf(initialColors.encode()) }
    var gradientEnabled by rememberSaveable(allowGradient) {
        mutableStateOf(allowGradient && !initialColors.isSolid)
    }
    var selectedIndex by rememberSaveable(gradientEnabled) { mutableStateOf(0) }
    val draftColors = CustomThemeColors.decode(encodedColors)
    val colors = if (gradientEnabled) draftColors else CustomThemeColors.solid(draftColors.second)
    var hexCode by rememberSaveable(selectedIndex, gradientEnabled) {
        mutableStateOf(formatHexColor(colors.colors[selectedIndex]))
    }
    val validHex = parseHexColor(hexCode) != null
    fun updateColor(color: Int) {
        encodedColors = draftColors.withColor(if (gradientEnabled) selectedIndex else 1, color).encode()
    }
    fun dismiss() {
        scope.launch { dismissKAPlayBottomSheet(sheetState, onDismiss) }
    }

    KAPlayModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        fullHeight = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)
                .imePadding().navigationBarsPadding()
                .padding(horizontal = tokens.spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s12),
        ) {
            Text(
                text = stringResource(
                    if (gradientEnabled) Res.string.custom_theme_title else Res.string.custom_theme_solid_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s16),
            ) {
                if (allowGradient) {
                    FlowRow(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s8),
                    ) {
                        listOf(true, false).forEach { gradient ->
                            FilterChip(
                                selected = gradientEnabled == gradient,
                                onClick = { gradientEnabled = gradient },
                                label = {
                                    Text(stringResource(
                                        if (gradient) Res.string.custom_theme_mode_gradient else Res.string.custom_theme_mode_solid,
                                    ))
                                },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        if (gradientEnabled) Res.string.custom_theme_subtitle else Res.string.custom_theme_solid_subtitle,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
                CustomThemePreview(colors)
                if (gradientEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s8)) {
                        colors.colors.forEachIndexed { index, color ->
                            ThemeColorSlot(
                                index = index,
                                color = color,
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                ThemeColorPicker(
                    color = colors.colors[selectedIndex],
                    colorIndex = selectedIndex,
                    onColorChanged = {
                        updateColor(it)
                        hexCode = formatHexColor(it)
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s8)) {
                    val hexLabel = stringResource(Res.string.custom_theme_hex_title)
                    Text(
                        text = hexLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.textPrimary,
                    )
                    KAPlayInputField(
                        value = hexCode,
                        onValueChange = { value ->
                            hexCode = value
                            parseHexColor(value)?.let(::updateColor)
                        },
                        placeholder = "#B75AFF",
                        modifier = Modifier.semantics { contentDescription = hexLabel },
                    )
                    if (!validHex) {
                        Text(
                            text = stringResource(Res.string.custom_theme_hex_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.danger,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(bottom = KAPlayTokens.Space.s12),
                horizontalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s12),
            ) {
                TextButton(onClick = ::dismiss, modifier = Modifier.height(KAPlayTokens.Space.s48 + KAPlayTokens.Space.s4)) {
                    Text(stringResource(Res.string.action_cancel))
                }
                KAPlayPrimaryButton(
                    text = stringResource(Res.string.custom_theme_save),
                    modifier = Modifier.weight(1f),
                    enabled = validHex,
                    onClick = {
                        onSave(colors)
                        dismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSlot(
    index: Int,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.kaplay
    Column(
        modifier = modifier
            .clip(tokens.shapes.compactCard)
            .border(
                tokens.borders.medium,
                if (selected) tokens.colors.textPrimary else tokens.colors.borderDefault,
                tokens.shapes.compactCard,
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(KAPlayTokens.Space.s8),
        verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s6),
    ) {
        Box(
            Modifier.fillMaxWidth().height(KAPlayTokens.Space.s32)
                .clip(tokens.shapes.compactCard).background(Color(color or 0xFF000000.toInt())),
        )
        Text(
            text = stringResource(Res.string.custom_theme_color_number, index + 1),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = formatHexColor(color),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
