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

import android.graphics.Bitmap.CompressFormat
import com.example.durban.error.StorageError
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

object FileUtils {
    private val random = Random()

    @JvmStatic
    @Throws(StorageError::class)
    fun validateDirectory(path: String) {
        val file = File(path)
        try {
            if (file.isFile) file.delete()
            if (!file.exists()) file.mkdirs()
        } catch (e: Exception) {
            throw StorageError("Directory creation failed.")
        }
    }

    @JvmStatic
    fun randomImageName(format: CompressFormat?): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmSSS", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate) + random.nextInt(9000) + "." + format
    }

    @JvmStatic
    @Throws(StorageError::class)
    fun copyFile(pathFrom: String?, pathTo: String) {
        try {
            val inputStream = FileInputStream(pathFrom)
            val outputStream = FileOutputStream(pathTo)
            var len: Int
            val buffer = ByteArray(2048)
            while ((inputStream.read(buffer).also { len = it }) != -1) outputStream.write(buffer, 0, len)
        } catch (e: IOException) {
            throw StorageError(e)
        }
    }

    @JvmStatic
    fun close(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (ignored: IOException) {
            }
        }
    }

}