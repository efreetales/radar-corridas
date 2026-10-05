package com.taleco.radarcorridas

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lê o texto de uma imagem da tela, no próprio celular.
 * Devolve as linhas de cima para baixo, como aparecem na tela.
 */
object ScreenReader {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun read(bitmap: Bitmap, onDone: (List<String>) -> Unit) {
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks
                        .flatMap { it.lines }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .map { it.text }
                    onDone(lines)
                }
                .addOnFailureListener { onDone(emptyList()) }
        } catch (e: Exception) {
            onDone(emptyList())
        }
    }
}
