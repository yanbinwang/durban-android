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
package com.example.durban.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.IntRange
import androidx.appcompat.widget.AppCompatImageView
import com.example.durban.callback.BitmapLoadCallback
import com.example.durban.model.ExifInfo
import com.example.durban.task.BitmapLoadTask
import com.example.durban.util.BitmapLoadUtils
import com.example.durban.util.FastBitmapDrawable
import com.example.durban.util.RectUtils
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * <p>
 * This class provides base logic to setup the image, transform it with matrix (move, scale, rotate),
 * and methods to get current matrix state.
 * </p>
 * Update by Yan Zhenjie on 2017/5/23.
 */
open class TransformImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var mMaxBitmapSize = 0
    private var mImagePath: String? = null
    private var mOutputDirectory: String? = null
    private var mInitialImageCorners: FloatArray? = null
    private var mInitialImageCenter: FloatArray? = null
    private var mExifInfo: ExifInfo? = null
    private val mMatrixValues = FloatArray(MATRIX_VALUES_COUNT)

    protected var mThisWidth = 0
    protected var mThisHeight = 0
    protected var mBitmapDecoded = false
    protected var mBitmapLaidOut = false
    protected var mCurrentImageMatrix = Matrix()
    protected var mTransformImageListener: TransformImageListener? = null
    protected val mCurrentImageCorners = FloatArray(RECT_CORNER_POINTS_COORDS)
    protected val mCurrentImageCenter = FloatArray(RECT_CENTER_POINT_COORDS)

    companion object {
        private const val RECT_CORNER_POINTS_COORDS = 8
        private const val RECT_CENTER_POINT_COORDS = 2
        private const val MATRIX_VALUES_COUNT = 9
        private const val TAG = "TransformImageView"
    }

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed || (mBitmapDecoded && !mBitmapLaidOut)) {
            mThisWidth = (width - paddingRight) - paddingLeft
            mThisHeight = (height - paddingBottom) - paddingTop
            onImageLaidOut()
        }
    }

    override fun setScaleType(scaleType: ScaleType?) {
        if (scaleType === ScaleType.MATRIX) {
            super.setScaleType(scaleType)
        } else {
            Log.w(TAG, "Invalid ScaleType. Only ScaleType.MATRIX can be used")
        }
    }

    override fun setImageBitmap(bm: Bitmap?) {
        setImageDrawable(FastBitmapDrawable(bm))
    }

    override fun setImageMatrix(matrix: Matrix?) {
        super.setImageMatrix(matrix)
        mCurrentImageMatrix.set(matrix)
        updateCurrentImagePoints()
    }

    /**
     * This method updates current image corners and center points that are stored in
     * [.mCurrentImageCorners] and [.mCurrentImageCenter] arrays.
     * Those are used for several calculations.
     */
    private fun updateCurrentImagePoints() {
        mCurrentImageMatrix.mapPoints(mCurrentImageCorners, mInitialImageCorners)
        mCurrentImageMatrix.mapPoints(mCurrentImageCenter, mInitialImageCenter)
    }

    /**
     * When image is laid out [.mInitialImageCenter] and [.mInitialImageCenter]
     * must be set.
     */
    open protected fun onImageLaidOut() {
        val drawable = drawable ?: return
        val w = drawable.intrinsicWidth.toFloat()
        val h = drawable.intrinsicHeight.toFloat()
        Log.d(TAG, String.format("Image size: [%d:%d]", w.toInt(), h.toInt()))
        val initialImageRect = RectF(0f, 0f, w, h)
        mInitialImageCorners = RectUtils.getCornersFromRect(initialImageRect)
        mInitialImageCenter = RectUtils.getCenterFromRect(initialImageRect)
        mBitmapLaidOut = true
        if (mTransformImageListener != null) {
            mTransformImageListener?.onLoadComplete()
        }
    }

    /**
     * This method returns Matrix value for given index.
     *
     * @param matrix     - valid Matrix object
     * @param valueIndex - index of needed value. See [Matrix.MSCALE_X] and others.
     * @return - matrix value for index
     */
    protected fun getMatrixValue(matrix: Matrix, @IntRange(from = 0, to = MATRIX_VALUES_COUNT.toLong()) valueIndex: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[valueIndex]
    }

    /**
     * This method logs given matrix X, Y, scale, and angle values.
     * Can be used for debug.
     */
    @Suppress("unused")
    protected fun printMatrix(logPrefix: String, matrix: Matrix) {
        val x = getMatrixValue(matrix, Matrix.MTRANS_X)
        val y = getMatrixValue(matrix, Matrix.MTRANS_Y)
        val rScale = getMatrixScale(matrix)
        val rAngle = getMatrixAngle(matrix)
        Log.d(TAG, "$logPrefix: matrix: { x: $x, y: $y, scale: $rScale, angle: $rAngle }")
    }

    /**
     * This method translates current image.
     *
     * @param deltaX - horizontal shift
     * @param deltaY - vertical shift
     */
    fun postTranslate(deltaX: Float, deltaY: Float) {
        if (deltaX != 0f || deltaY != 0f) {
            mCurrentImageMatrix.postTranslate(deltaX, deltaY)
            setImageMatrix(mCurrentImageMatrix)
        }
    }

    /**
     * This method scales current image.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    open fun postScale(deltaScale: Float, px: Float, py: Float) {
        if (deltaScale != 0f) {
            mCurrentImageMatrix.postScale(deltaScale, deltaScale, px, py)
            setImageMatrix(mCurrentImageMatrix)
            if (mTransformImageListener != null) {
                mTransformImageListener?.onScale(getMatrixScale(mCurrentImageMatrix))
            }
        }
    }

    /**
     * This method rotates current image.
     *
     * @param deltaAngle - rotation angle
     * @param px         - rotation center X
     * @param py         - rotation center Y
     */
    fun postRotate(deltaAngle: Float, px: Float, py: Float) {
        if (deltaAngle != 0f) {
            mCurrentImageMatrix.postRotate(deltaAngle, px, py)
            setImageMatrix(mCurrentImageMatrix)
            if (mTransformImageListener != null) {
                mTransformImageListener?.onRotate(getMatrixAngle(mCurrentImageMatrix))
            }
        }
    }

    fun getViewBitmap(): Bitmap? {
        return if (drawable == null || drawable !is FastBitmapDrawable) null
        else (drawable as FastBitmapDrawable).getBitmap()
    }

    fun getImagePath(): String? {
        return mImagePath
    }

    fun getOutputDirectory(): String? {
        return mOutputDirectory
    }

    fun getExifInfo(): ExifInfo? {
        return mExifInfo
    }

    /**
     * @return - current image scale value.
     * [1.0f - for original image, 2.0f - for 200% scaled image, etc.]
     */
    fun getCurrentScale(): Float {
        return getMatrixScale(mCurrentImageMatrix)
    }

    /**
     * This method calculates scale value for given Matrix object.
     */
    fun getMatrixScale(matrix: Matrix): Float {
        return sqrt(getMatrixValue(matrix, Matrix.MSCALE_X).toDouble().pow(2.0) + getMatrixValue(matrix, Matrix.MSKEW_Y).toDouble().pow(2.0)).toFloat()
    }

    /**
     * @return - current image rotation angle.
     */
    fun getCurrentAngle(): Float {
        return getMatrixAngle(mCurrentImageMatrix)
    }

    /**
     * This method calculates rotation angle for given Matrix object.
     */
    fun getMatrixAngle(matrix: Matrix): Float {
        return -(atan2(getMatrixValue(matrix, Matrix.MSKEW_X).toDouble(), getMatrixValue(matrix, Matrix.MSCALE_X).toDouble()) * (180 / Math.PI)).toFloat()
    }

    fun getMaxBitmapSize(): Int {
        if (mMaxBitmapSize <= 0) {
            mMaxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(context)
        }
        return mMaxBitmapSize
    }

    fun setOutputDirectory(outputDirectory: String?) {
        this.mOutputDirectory = outputDirectory
    }

    /**
     * This method takes an Uri as a parameter, then calls method to decode it into Bitmap with specified size.
     *
     * @param inputImagePath - image Uri
     * @throws Exception - can throw exception if having problems with decoding Uri or OOM.
     */
    @Throws(Exception::class)
    fun setImagePath(inputImagePath: String) {
        this.mImagePath = inputImagePath
        val maxBitmapSize = getMaxBitmapSize()
        BitmapLoadTask(context, maxBitmapSize, maxBitmapSize, object : BitmapLoadCallback {
            override fun onSuccessfully(bitmap: Bitmap, exifInfo: ExifInfo) {
                mExifInfo = exifInfo
                mBitmapDecoded = true
                setImageBitmap(bitmap)
            }

            override fun onFailure() {
                if (mTransformImageListener != null) {
                    mTransformImageListener?.onLoadFailure()
                }
            }
        }).execute(inputImagePath)
    }

    /**
     * Setter for [.mMaxBitmapSize] value.
     * Be sure to call it before [.setImageURI] or other image setters.
     *
     * @param maxBitmapSize - max size for both width and height of exception that will be used in the view.
     */
    fun setMaxBitmapSize(maxBitmapSize: Int) {
        mMaxBitmapSize = maxBitmapSize
    }

    fun setTransformImageListener(transformImageListener: TransformImageListener) {
        mTransformImageListener = transformImageListener
    }

    /**
     * Interface for rotation and scale change notifying.
     */
    interface TransformImageListener {
        fun onLoadComplete()

        fun onLoadFailure()

        fun onRotate(currentAngle: Float)

        fun onScale(currentScale: Float)
    }

}