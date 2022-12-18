package com.example.androidstorage.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import com.example.androidstorage.model.InternalStoragePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

suspend fun Context.deletePhotoFromInternalStorage(filename: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            deleteFile(filename)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

suspend fun Context.loadPhotosFromInternalStorage(): List<InternalStoragePhoto> {
    return withContext(Dispatchers.IO) {
        val listFiles = filesDir.listFiles()
        listFiles?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
            val bytes = it.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            InternalStoragePhoto(it.name, bitmap)
        } ?: listOf()
    }
}

suspend fun Context.savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            openFileOutput("$filename.jpg", AppCompatActivity.MODE_PRIVATE).use { outputStream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("can't save bitmap to $filename'")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}