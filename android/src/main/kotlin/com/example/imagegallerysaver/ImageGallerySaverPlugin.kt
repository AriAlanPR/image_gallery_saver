package com.example.imagegallerysaver

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class ImageGallerySaverPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "image_gallery_saver")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "saveImageToGallery" -> {
                val image = call.argument<ByteArray>("imageBytes")
                val quality = call.argument<Int>("quality") ?: 100
                val name = call.argument<String>("name")
                
                if (image != null) {
                    result.success(saveImageToGallery(BitmapFactory.decodeByteArray(image, 0, image.size), quality, name))
                }
            }
            "saveFileToGallery" -> {
                val path = call.argument<String>("file")
                val name = call.argument<String>("name")
                
                if (path != null) {
                    result.success(saveFileToGallery(path, name))
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, quality: Int, name: String?): Map<String, Any> {
        return try {
            val fileName = name ?: System.currentTimeMillis().toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                context.contentResolver.let { resolver ->
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { imageUri ->
                        resolver.openOutputStream(imageUri)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                        }
                        mapOf("isSuccess" to true, "filePath" to imageUri.toString())
                    } ?: mapOf("isSuccess" to false, "errorMessage" to "Failed to create new MediaStore record")
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, "$fileName.jpg")
                image.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                }
                mapOf("isSuccess" to true, "filePath" to image.absolutePath)
            }
        } catch (e: IOException) {
            mapOf("isSuccess" to false, "errorMessage" to e.toString())
        }
    }

    private fun saveFileToGallery(path: String, name: String?): Map<String, Any> {
        return try {
            val file = File(path)
            val fileName = name ?: file.name
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                context.contentResolver.let { resolver ->
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { fileUri ->
                        resolver.openOutputStream(fileUri)?.use { outputStream ->
                            FileInputStream(file).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        mapOf("isSuccess" to true, "filePath" to fileUri.toString())
                    } ?: mapOf("isSuccess" to false, "errorMessage" to "Failed to create new MediaStore record")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destination = File(downloadsDir, fileName)
                file.copyTo(destination, true)
                mapOf("isSuccess" to true, "filePath" to destination.absolutePath)
            }
        } catch (e: IOException) {
            mapOf("isSuccess" to false, "errorMessage" to e.toString())
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "*/*"
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
