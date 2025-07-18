package com.example.durban.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.AsyncTask
import com.example.durban.callback.BitmapLoadCallback
import com.example.durban.model.ExifInfo
import com.example.durban.util.BitmapLoadUtils
import com.yanzhenjie.loading.dialog.LoadingDialog

class BitmapLoadTask(context: Context, private var mRequiredWidth: Int, private var mRequiredHeight: Int, private var mCallback: BitmapLoadCallback?) : AsyncTask<String, Unit, BitmapLoadTask.Companion.BitmapWorkerResult>() {
    private var mDialog: LoadingDialog? = null

    companion object {
        data class BitmapWorkerResult(var bitmapResult: Bitmap? = null, var exifInfo: ExifInfo? = null)
    }

    init {
        mDialog = LoadingDialog(context)
    }

    @Deprecated("Deprecated in Java")
    override fun onPreExecute() {
        mDialog?.let { if (!it.isShowing) it.show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(result: BitmapWorkerResult?) {
        mDialog?.let { if (it.isShowing) it.dismiss() }
        if (result?.bitmapResult == null) {
            mCallback?.onFailure()
        } else {
            mCallback?.onSuccessfully(result.bitmapResult, result.exifInfo)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: String?): BitmapWorkerResult {
        val imagePath = params[0]
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imagePath, options)
        if (options.outWidth == -1 || options.outHeight == -1) return BitmapWorkerResult(null, null)
        options.inSampleSize = BitmapLoadUtils.calculateInSampleSize(options, mRequiredWidth, mRequiredHeight)
        options.inJustDecodeBounds = false
        var decodeSampledBitmap: Bitmap? = null
        var decodeAttemptSuccess = false
        while (!decodeAttemptSuccess) {
            try {
                decodeSampledBitmap = BitmapFactory.decodeFile(imagePath, options)
                decodeAttemptSuccess = true
            } catch (error: Throwable) {
                options.inSampleSize *= 2
            }
        }
        val exifOrientation = BitmapLoadUtils.getExifOrientation(imagePath.orEmpty())
        val exifDegrees = BitmapLoadUtils.exifToDegrees(exifOrientation)
        val exifTranslation = BitmapLoadUtils.exifToTranslation(exifOrientation)
        val exifInfo = ExifInfo(exifOrientation, exifDegrees, exifTranslation)
        val matrix = Matrix()
        if (exifDegrees != 0) matrix.preRotate(exifDegrees.toFloat())
        if (exifTranslation != 1) matrix.postScale(exifTranslation.toFloat(), 1f)
        if (!matrix.isIdentity) return BitmapWorkerResult(decodeSampledBitmap?.let { BitmapLoadUtils.transformBitmap(it, matrix) }, exifInfo)
        return BitmapWorkerResult(decodeSampledBitmap, exifInfo)
    }

}