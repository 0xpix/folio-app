package com.pix.folio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.view.WindowCompat
import com.pix.folio.R
import com.pix.folio.model.AppFontChoice

val FolioInk = Color(0xFF111111)
val FolioPaper = Color(0xFFF6F4EF)
val FolioPaperDark = Color(0xFF0D0D0D)
val FolioMuted = Color(0xFF77746E)
val FolioGain = Color(0xFF2C6E55)
val FolioLoss = Color(0xFFB2473F)

private val Light = lightColorScheme(
    primary = FolioInk,
    onPrimary = FolioPaper,
    background = FolioPaper,
    onBackground = FolioInk,
    surface = FolioPaper,
    onSurface = FolioInk,
    surfaceVariant = Color(0xFFEAE7E1),
    onSurfaceVariant = Color(0xFF706D67),
    outlineVariant = Color(0x1F111111),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF4F1EA),
    onPrimary = FolioInk,
    background = FolioPaperDark,
    onBackground = Color(0xFFF4F1EA),
    surface = FolioPaperDark,
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFFAAA69F),
    outlineVariant = Color(0x2FFFFFFF),
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
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography().withFamily(folioFontFamily(fontChoice)),
        content = content,
    )
}
