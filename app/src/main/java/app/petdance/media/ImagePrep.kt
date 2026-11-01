package app.petdance.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val OUT_W = 720
private const val OUT_H = 1280
private const val MAX_BYTES = 1_572_864

fun decodeSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    if (width <= 0 || height <= 0) return 1
    while (max(width, height) / sample > maxSide) sample *= 2
    return sample
}

fun decodeOriented(context: Context, uri: Uri, maxSide: Int = 2560): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法读取这张照片")
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IllegalArgumentException("无法读取这张照片")
    val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).rotationDegrees
    } ?: 0
    if (degrees == 0) return decoded
    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated != decoded) decoded.recycle()
    return rotated
}

fun exportPortraitJpeg(
    source: Bitmap,
    frameWidth: Float,
    frameHeight: Float,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
): ByteArray {
    if (frameWidth <= 1f || frameHeight <= 1f) throw IllegalArgumentException("裁剪区域还没准备好")
    val scale = max(frameWidth / source.width, frameHeight / source.height) * userScale.coerceAtLeast(1f)
    val displayW = source.width * scale
    val displayH = source.height * scale
    val maxX = ((displayW - frameWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((displayH - frameHeight) / 2f).coerceAtLeast(0f)
    val left = (frameWidth - displayW) / 2f + offsetX.coerceIn(-maxX, maxX)
    val top = (frameHeight - displayH) / 2f + offsetY.coerceIn(-maxY, maxY)
    val srcLeft = ((0f - left) / scale).coerceIn(0f, (source.width - 1).toFloat())
    val srcTop = ((0f - top) / scale).coerceIn(0f, (source.height - 1).toFloat())
    val srcRight = ((frameWidth - left) / scale).coerceIn(srcLeft + 1f, source.width.toFloat())
    val srcBottom = ((frameHeight - top) / scale).coerceIn(srcTop + 1f, source.height.toFloat())
    val output = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        source,
        Rect(srcLeft.roundToInt(), srcTop.roundToInt(), srcRight.roundToInt(), srcBottom.roundToInt()),
        Rect(0, 0, OUT_W, OUT_H),
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    )
    var quality = 90
    var bytes = compress(output, quality)
    while (bytes.size > MAX_BYTES && quality > 50) {
        quality -= 5
        bytes = compress(output, quality)
    }
    output.recycle()
    if (bytes.size > MAX_BYTES) throw IllegalArgumentException("照片仍然超过 1.5MB，请换一张")
    return bytes
}

private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}
