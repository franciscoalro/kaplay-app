package com.kaplay.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.kaplay.app.R
import com.kaplay.app.features.simkl.SimklBrandAsset
import com.kaplay.app.features.simkl.simklBrandPainter
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.introdb_favicon
import kaplay.composeapp.generated.resources.rating_tmdb
import org.jetbrains.compose.resources.painterResource as composePainterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> composePainterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.Trakt -> painterResource(id = R.drawable.trakt_tv_favicon)
        IntegrationLogo.Simkl -> simklBrandPainter(SimklBrandAsset.Glyph)
        IntegrationLogo.MdbList -> painterResource(id = R.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> composePainterResource(Res.drawable.introdb_favicon)
    }
