package com.hfremovalsadelaide.invoices.jobs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Turns finger strokes (in the on-screen box's coordinates) into a PNG. A blank box produces nothing. */
object SignatureRenderer {
    private const val OUT_W = 600
    private const val OUT_H = 240
    /** Stay well under the backend's 300 KB limit. */
    const val MAX_BYTES = 250_000

    fun hasInk(strokes: List<List<Pair<Float, Float>>>) = strokes.any { it.size >= 2 }

    fun toPngBase64(strokes: List<List<Pair<Float, Float>>>, boxWidth: Float, boxHeight: Float): String {
        if (!hasInk(strokes) || boxWidth <= 0f || boxHeight <= 0f) return ""
        var scale = 1f
        while (true) {
            val png = render(strokes, boxWidth, boxHeight, scale)
            if (png.size <= MAX_BYTES || scale <= 0.25f) return Base64.encodeToString(png, Base64.NO_WRAP)
            scale /= 2f
        }
    }

    private fun render(strokes: List<List<Pair<Float, Float>>>, boxWidth: Float, boxHeight: Float, scale: Float): ByteArray {
        val w = (OUT_W * scale).toInt()
        val h = (OUT_H * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f * scale; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val sx = w / boxWidth
        val sy = h / boxHeight
        strokes.filter { it.size >= 2 }.forEach { stroke ->
            val path = Path().apply {
                moveTo(stroke.first().first * sx, stroke.first().second * sy)
                stroke.drop(1).forEach { lineTo(it.first * sx, it.second * sy) }
            }
            canvas.drawPath(path, paint)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
