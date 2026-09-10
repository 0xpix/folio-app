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
    val Gain = Color(0xFF2C6E55)
    val Loss = Color(0xFFB2473F)
}

// Semantic finance tokens are defined once here so screens never invent their own gain/loss colors.
val FolioGain: Color = FolioPalette.Gain
val FolioLoss: Color = FolioPalette.Loss

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
