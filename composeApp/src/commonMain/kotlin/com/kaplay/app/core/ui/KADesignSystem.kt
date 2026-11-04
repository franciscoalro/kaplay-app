package com.kaplay.app.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object KAColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF19191F)
    val SurfaceVariant = Color(0xFF23232B)
    val SurfaceElevated = Color(0xFF2C2C35)

    val Primary = Color(0xFF9729D7)
    val PrimaryPressed = Color(0xFF7B20B4)
    val PrimaryLight = Color(0xFFC477E8)

    val OnPrimary = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA3A3AD)
    val TextMuted = Color(0xFF777781)

    val Outline = Color(0xFF393941)
    val Success = Color(0xFF36B57B)
    val Warning = Color(0xFFE7B34D)
    val Error = Color(0xFFE46770)
}

object KASpacing {
    val XS: Dp = 4.dp
    val SM: Dp = 8.dp
    val MD: Dp = 12.dp
    val LG: Dp = 16.dp
    val XL: Dp = 20.dp
    val XXL: Dp = 24.dp
    val Section: Dp = 28.dp
    val Space8: Dp = 32.dp
    val Space10: Dp = 40.dp
}

object KAShapes {
    val ButtonRadius: Dp = 10.dp
    val FieldRadius: Dp = 12.dp
    val CardRadius: Dp = 10.dp
    val PosterRadius: Dp = 8.dp
    val BannerRadius: Dp = 12.dp
    val ChipRadius: Dp = 8.dp
    val ModalRadius: Dp = 16.dp

    val Button = RoundedCornerShape(ButtonRadius)
    val Field = RoundedCornerShape(FieldRadius)
    val Card = RoundedCornerShape(CardRadius)
    val Poster = RoundedCornerShape(PosterRadius)
    val Banner = RoundedCornerShape(BannerRadius)
    val Chip = RoundedCornerShape(ChipRadius)
    val Modal = RoundedCornerShape(ModalRadius)
}
