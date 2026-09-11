package com.pix.folio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.view.WindowCompat
import com.pix.folio.R
import com.pix.folio.model.AppFontChoice

private object FolioPalette {
    val Ink = Color(0xFF111111)
    val Paper = Color(0xFFF6F4EF)
    val PaperDark = Color(0xFF0D0D0D)
    val LightSurfaceVariant = Color(0xFFEAE7E1)
    val LightMuted = Color(0xFF706D67)
    val LightOutline = Color(0x1F111111)
    val DarkInk = Color(0xFFF4F1EA)
    val DarkSurfaceVariant = Color(0xFF1B1B1B)
    val DarkMuted = Color(0xFFAAA69F)
    val DarkOutline = Color(0x2FFFFFFF)

    // Finance semantics. Green/red are intentionally reserved for change and performance,
    // not ordinary cash, spending, or contribution values.
    val LightGain = Color(0xFF216B49)
    val LightGainContainer = Color(0xFFDCEFE4)
    val LightLoss = Color(0xFFB13D36)
    val LightLossContainer = Color(0xFFF6DFDC)
    val DarkGain = Color(0xFF73D9A1)
    val DarkGainContainer = Color(0xFF173E2C)
    val DarkLoss = Color(0xFFFF8F86)
    val DarkLossContainer = Color(0xFF4C2422)
}

// Legacy semantic tokens used by older screens. New screens should prefer the adaptive
// MaterialTheme tertiary/error roles below so contrast follows light/dark mode automatically.
val FolioGain: Color = FolioPalette.LightGain
val FolioLoss: Color = FolioPalette.LightLoss

private val Light = lightColorScheme(
    primary = FolioPalette.Ink,
    onPrimary = FolioPalette.Paper,
    background = FolioPalette.Paper,
    onBackground = FolioPalette.Ink,
    surface = FolioPalette.Paper,
    onSurface = FolioPalette.Ink,
    surfaceVariant = FolioPalette.LightSurfaceVariant,
    onSurfaceVariant = FolioPalette.LightMuted,
    outlineVariant = FolioPalette.LightOutline,
    tertiary = FolioPalette.LightGain,
    onTertiary = Color.White,
    tertiaryContainer = FolioPalette.LightGainContainer,
    onTertiaryContainer = FolioPalette.LightGain,
    error = FolioPalette.LightLoss,
    onError = Color.White,
    errorContainer = FolioPalette.LightLossContainer,
    onErrorContainer = FolioPalette.LightLoss,
)

private val Dark = darkColorScheme(
    primary = FolioPalette.DarkInk,
    onPrimary = FolioPalette.Ink,
    background = FolioPalette.PaperDark,
    onBackground = FolioPalette.DarkInk,
    surface = FolioPalette.PaperDark,
    onSurface = FolioPalette.DarkInk,
    surfaceVariant = FolioPalette.DarkSurfaceVariant,
    onSurfaceVariant = FolioPalette.DarkMuted,
    outlineVariant = FolioPalette.DarkOutline,
    tertiary = FolioPalette.DarkGain,
    onTertiary = FolioPalette.PaperDark,
    tertiaryContainer = FolioPalette.DarkGainContainer,
    onTertiaryContainer = FolioPalette.DarkGain,
    error = FolioPalette.DarkLoss,
    onError = FolioPalette.PaperDark,
    errorContainer = FolioPalette.DarkLossContainer,
    onErrorContainer = FolioPalette.DarkLoss,
)

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val PixelifyFamily = FontFamily(
    androidx.compose.ui.text.googlefonts.Font(
        googleFont = GoogleFont("Pixelify Sans"),
        fontProvider = GoogleFontsProvider,
    )
)

fun folioFontFamily(choice: AppFontChoice): FontFamily = when (choice) {
    AppFontChoice.PIXELIFY -> PixelifyFamily
    AppFontChoice.SYSTEM -> FontFamily.Default
    AppFontChoice.MONO -> FontFamily.Monospace
    AppFontChoice.SERIF -> FontFamily.Serif
}

private fun Typography.withFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun FolioTheme(
    fontChoice: AppFontChoice = AppFontChoice.PIXELIFY,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }

    val scheme = if (dark) Dark else Light
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography().withFamily(folioFontFamily(fontChoice)),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
            content = content,
        )
    }
}
