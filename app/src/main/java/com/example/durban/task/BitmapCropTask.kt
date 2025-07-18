package com.example.durban.task

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.os.AsyncTask
import androidx.core.graphics.scale
import com.example.durban.callback.BitmapCropCallback
import com.example.durban.error.StorageError
import com.example.durban.model.CropParameters
import com.example.durban.model.ImageState
import com.example.durban.util.FileUtils
import com.example.durban.util.ImageHeaderParser
import com.yanzhenjie.loading.dialog.LoadingDialog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BitmapCropTask(context: Context, private var mViewBitmap: Bitmap?, imageState: ImageState, cropParameters: CropParameters, private var mCallback: BitmapCropCallback?) : AsyncTask<Unit, Unit, BitmapCropTask.Companion.PathWorkerResult>() {
    private var mCompressQuality = 0
    private var mCroppedImageWidth = 0
    private var mCroppedImageHeight = 0
    private var mMaxResultImageSizeX = 0
    private var mMaxResultImageSizeY = 0
    private var mCurrentScale = 0f
    private var mCurrentAngle = 0f
    private var mInputImagePath: String? = null
    private var mOutputDirectory: String? = null
    private var mCropRect: RectF? = null
    private var mCurrentImageRect: RectF? = null
    private var mCompressFormat: CompressFormat? = null
    private var mDialog: LoadingDialog? = null

    companion object {
        data class PathWorkerResult(var path: String? = null, var exception: Exception? = null)
    }

    init {
        mDialog = LoadingDialog(context)
        mCropRect = imageState.mCropRect
        mCurrentImageRect = imageState.mCurrentImageRect
        mCurrentScale = imageState.mCurrentScale
        mCurrentAngle = imageState.mCurrentAngle
        mMaxResultImageSizeX = cropParameters.mMaxResultImageSizeX
        mMaxResultImageSizeY = cropParameters.mMaxResultImageSizeY
        mCompressFormat = cropParameters.mCompressFormat
        mCompressQuality = cropParameters.mCompressQuality
        mInputImagePath = cropParameters.mImagePath
        mOutputDirectory = cropParameters.mImageOutputPath
    }

    @Deprecated("Deprecated in Java")
    override fun onPreExecute() {
        mDialog?.let { if (!it.isShowing) it.show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(result: PathWorkerResult?) {
        mDialog?.let { if (it.isShowing) it.dismiss() }
        if (mCallback != null) {
            if (result?.exception == null) {
                mCallback?.onBitmapCropped(result?.path.orEmpty(), mCroppedImageWidth, mCroppedImageHeight)
            } else {
                mCallback?.onCropFailure(result.exception ?: Throwable("exception is null"))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg p0: Unit?): PathWorkerResult {
        try {
            val imagePath: String = crop()
            return PathWorkerResult(imagePath, null)
        } catch (e: Exception) {
            return PathWorkerResult(null, e)
        }
    }

    @SuppressLint("ExifInterface")
    @Throws(Exception::class)
    private fun crop(): String {
        FileUtils.validateDirectory(mOutputDirectory.orEmpty())
        val fileName: String = FileUtils.randomImageName(mCompressFormat)
        val outputImagePath = File(mOutputDirectory, fileName).absolutePath
        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            val cropWidth = (mCropRect?.width() ?: 0f) / mCurrentScale
            val cropHeight = (mCropRect?.height() ?: 0f) / mCurrentScale
            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {
                val scaleX = mMaxResultImageSizeX / cropWidth
                val scaleY = mMaxResultImageSizeY / cropHeight
                val resizeScale = min(scaleX, scaleY)
                val resizedBitmap = mViewBitmap?.let {
                    it.scale(Math.round(it.width * resizeScale), Math.round(it.height * resizeScale), false)
                }
                if (mViewBitmap != resizedBitmap) mViewBitmap?.recycle()
                mViewBitmap = resizedBitmap
                mCurrentScale /= resizeScale
            }
        }
        // Rotate if needed
        if (mCurrentAngle != 0f) {
            val tempMatrix = Matrix()
            tempMatrix.setRotate(mCurrentAngle, ((mViewBitmap?.width ?: 0) / 2).toFloat(), ((mViewBitmap?.height ?: 0) / 2).toFloat())
            val rotatedBitmap = mViewBitmap?.let {
                Bitmap.createBitmap(it, 0, 0, it.width, it.height, tempMatrix, true)
            }
            if (mViewBitmap != rotatedBitmap) mViewBitmap?.recycle()
            mViewBitmap = rotatedBitmap
        }
        val cropOffsetX = Math.round(((mCropRect?.left ?: 0f) - (mCurrentImageRect?.left ?: 0f)) / mCurrentScale)
        val cropOffsetY = Math.round(((mCropRect?.top ?: 0f) - (mCurrentImageRect?.top ?: 0f)) / mCurrentScale)
        mCroppedImageWidth = Math.round((mCropRect?.width() ?: 0f) / mCurrentScale)
        mCroppedImageHeight = Math.round((mCropRect?.height() ?: 0f) / mCurrentScale)
        val shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight)
        if (shouldCrop) {
            val croppedBitmap = mViewBitmap?.let {
                Bitmap.createBitmap(it, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight)
            }
            var outputStream: OutputStream? = null
            try {
                outputStream = FileOutputStream(outputImagePath)
                mCompressFormat?.let { croppedBitmap?.compress(it, mCompressQuality, outputStream) }
            } catch (e: java.lang.Exception) {
                throw StorageError("")
            } finally {
                croppedBitmap?.recycle()
                FileUtils.close(outputStream)
            }
            if (mCompressFormat == CompressFormat.JPEG) {
                val originalExif = ExifInterface(mInputImagePath.orEmpty())
                ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, outputImagePath)
            }
        } else {
            FileUtils.copyFile(mInputImagePath, outputImagePath)
        }
        mViewBitmap?.let { if (!it.isRecycled) it.recycle() }
        return outputImagePath
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private fun shouldCrop(width: Int, height: Int): Boolean {
        var pixelError = 1
        pixelError += Math.round(max(width, height) / 1000f)
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) || abs((mCropRect?.left ?: 0f) - (mCurrentImageRect?.left ?: 0f)) > pixelError || abs((mCropRect?.top ?: 0f) - (mCurrentImageRect?.top ?: 0f)) > pixelError || abs((mCropRect?.bottom ?: 0f) - (mCurrentImageRect?.bottom ?: 0f)) > pixelError || abs((mCropRect?.right ?: 0f) - (mCurrentImageRect?.right ?: 0f)) > pixelError
    }

}