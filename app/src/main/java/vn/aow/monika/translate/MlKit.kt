package vn.aow.monika.translate

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
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

/** OCR + dịch trên máy bằng Google ML Kit (mô hình dịch tải 1 lần ~30 MB mỗi ngôn ngữ). */
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

    suspend fun translate(lines: List<String>, src: String): List<String> {
        if (lines.isEmpty()) return emptyList()
        val from = TranslateLanguage.fromLanguageTag(src) ?: throw TranslateException("Ngôn ngữ nguồn chưa hỗ trợ")
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(TranslateLanguage.VIETNAMESE).build())
        return try {
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            lines.map { tr.translate(it).await() }
        } finally { tr.close() }
    }
}
