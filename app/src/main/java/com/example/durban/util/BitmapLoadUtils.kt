/*
 * Copyright © Yan Zhenjie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.durban.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.media.ExifInterface
import android.view.WindowManager
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object BitmapLoadUtils {

    @JvmStatic
    fun transformBitmap(bitmap: Bitmap, transformMatrix: Matrix): Bitmap {
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transformMatrix, true)
        } catch (error: OutOfMemoryError) {
            bitmap
        }
    }

    @JvmStatic
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width lower or equal to the requested height and width.
            while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @JvmStatic
    fun getExifOrientation(imagePath: String): Int {
        var orientation = ExifInterface.ORIENTATION_UNDEFINED
        try {
            val stream: InputStream = FileInputStream(imagePath)
            orientation = ImageHeaderParser(stream).getOrientation()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return orientation
    }

    @JvmStatic
    fun exifToDegrees(exifOrientation: Int): Int {
        val rotation = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
        return rotation
    }

    @JvmStatic
    fun exifToTranslation(exifOrientation: Int): Int {
        val translation = when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> -1
            else -> 1
        }
        return translation
    }

    @JvmStatic
    fun calculateMaxBitmapSize(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = wm?.defaultDisplay
        val size = Point()
        display?.getSize(size)
        val width = size.x
        val height = size.y
        // Twice the device screen diagonal as default
        var maxBitmapSize = sqrt(width.toDouble().pow(2.0) + height.toDouble().pow(2.0)).toInt()
        // Check for max texture size via GL
        val maxTextureSize = EglUtils.getMaxTextureSize()
        if (maxTextureSize > 0) {
            maxBitmapSize = min(maxBitmapSize, maxTextureSize)
        }
        return maxBitmapSize
    }

}