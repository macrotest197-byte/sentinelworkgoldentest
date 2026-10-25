package com.sentinel.recaudacion.ui.common

import android.content.Context
import android.net.Uri
import java.nio.charset.StandardCharsets

/** Lectura/escritura de texto UTF-8 sobre URIs del Storage Access Framework. */
object FileIo {

    fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(StandardCharsets.UTF_8))
        } != null
    } catch (_: Exception) {
        false
    }

    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
