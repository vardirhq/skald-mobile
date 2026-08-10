package no.vardir.skald.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import android.graphics.Bitmap

/**
 * The pairing QR, rendered on the device.
 *
 * GESH deliberately returns a string rather than an image, because the content
 * key has to be appended as a `#k=` fragment first — and that fragment is the
 * half the relay must never receive. Drawing it locally is what keeps that true.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    /** Always high contrast: a themed QR is a QR that does not scan. */
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    val bitmap: ImageBitmap? = remember(content, foreground, background) {
        runCatching { encode(content, 512, foreground.toArgb(), background.toArgb()) }.getOrNull()
    }

    Box(modifier.background(background).padding(12.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Pairing code",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
            )
        }
    }
}

private fun encode(content: String, pixels: Int, fg: Int, bg: Int): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val row = IntArray(matrix.width)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) row[x] = if (matrix.get(x, y)) fg else bg
        bitmap.setPixels(row, 0, matrix.width, 0, y, matrix.width, 1)
    }
    return bitmap.asImageBitmap()
}
