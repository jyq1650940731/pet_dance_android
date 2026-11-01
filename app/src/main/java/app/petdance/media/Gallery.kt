package app.petdance.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

fun saveVideoToGallery(context: Context, file: File) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/pet_dance")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val uri = resolver.insert(collection, values) ?: error("无法写入相册")
    resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        ?: error("无法写入相册")
    if (Build.VERSION.SDK_INT >= 29) {
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
