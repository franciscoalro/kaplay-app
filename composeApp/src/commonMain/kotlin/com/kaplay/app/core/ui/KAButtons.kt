package com.kaplay.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KAPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .heightIn(min = 48.dp),
        shape = KAShapes.Button,
        contentPadding = PaddingValues(horizontal = KASpacing.LG),
        colors = ButtonDefaults.buttonColors(
            containerColor = KAColors.Primary,
            contentColor = Color.White,
            disabledContainerColor = if (isLoading) KAColors.Primary else KAColors.Primary.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.55f),
        ),
    ) {
        if (isLoading) {
            KAPlayLoadingIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
            )
        } else {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun KASecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .heightIn(min = 48.dp),
        shape = KAShapes.Button,
        contentPadding = PaddingValues(horizontal = KASpacing.LG),
        colors = ButtonDefaults.buttonColors(
            containerColor = KAColors.SurfaceVariant,
            contentColor = Color.White,
            disabledContainerColor = KAColors.SurfaceVariant.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
fun KATextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = KAColors.PrimaryLight,
    underlined: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, color = KAColors.PrimaryLight),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = KASpacing.SM, vertical = KASpacing.XS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.45f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (underlined) TextDecoration.Underline else null,
        )
    }
}
