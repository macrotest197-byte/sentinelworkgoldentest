package com.sentinel.recaudacion.data.settings

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Administrator-only import for the friendly name, independent of Telegram credentials. */
object DeviceProvisioning {
    const val FILE_NAME = "sentinel_device_provision.json"

    sealed interface Result {
        data object NotFound : Result
        data class Imported(val name: String) : Result
        data object Invalid : Result
    }

    fun provisioningFile(context: Context): File =
        context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) } ?: File(context.filesDir, FILE_NAME)

    fun parse(json: String): String? = runCatching {
        JSONObject(json).optString("deviceName", "").trim().takeIf(String::isNotBlank)?.take(40)
    }.getOrNull()

    fun importIfPresent(context: Context, store: DeviceIdentityStore): Result {
        val file = provisioningFile(context)
        if (!file.exists()) return Result.NotFound
        val name = runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull()
            ?: return Result.Invalid
        store.setAdministratorName(name)
        return if (file.delete()) Result.Imported(name) else Result.Invalid
    }
}
