package com.pix.folio.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.pix.folio.R
import kotlin.math.roundToInt

/** Renders Google Fonts Noto Emoji as a caller-colored monochrome bitmap. */
object NotoEmojiRenderer {
    @Volatile private var cachedTypeface: Typeface? = null

    private fun typeface(context: Context): Typeface {
        cachedTypeface?.let { return it }
        val loaded = runCatching { ResourcesCompat.getFont(context, R.font.noto_emoji) }.getOrNull()
        if (loaded != null) cachedTypeface = loaded
        return loaded ?: Typeface.DEFAULT
    }

    fun render(context: Context, glyph: String, sizeDp: Int, colorArgb: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = colorArgb
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.72f
            typeface = typeface(context)
        }
        val fm = paint.fontMetrics
        val baseline = sizePx / 2f - (fm.ascent + fm.descent) / 2f
        Canvas(bitmap).drawText(glyph, sizePx / 2f, baseline, paint)
        return bitmap
    }
}
