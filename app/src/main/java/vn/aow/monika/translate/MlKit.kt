package vn.aow.monika.translate

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** OCR trên máy bằng Google ML Kit (bản qua Google Play Services: mô hình tải khi dùng lần đầu). */
object MlKit {
    /** Đọc chữ trong ảnh: mỗi khối chữ thành 1 đoạn (xuống dòng gộp thành dấu cách). */
    suspend fun read(bmp: Bitmap, src: String): List<String> {
        val rec = if (src == "ja") TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            rec.process(InputImage.fromBitmap(bmp, 0)).await().textBlocks
                .map { it.text.replace('\n', ' ').trim() }.filter { it.length >= 2 }
        } finally { rec.close() }
    }
}
