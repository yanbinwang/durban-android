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
import android.content.res.TypedArray
import android.graphics.Bitmap.CompressFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.IntRange
import com.example.durban.R
import com.example.durban.callback.BitmapCropCallback
import com.example.durban.callback.CropBoundsChangeListener
import com.example.durban.model.CropParameters
import com.example.durban.model.ExifInfo
import com.example.durban.model.ImageState
import com.example.durban.task.BitmapCropTask
import com.example.durban.util.CubicEasing
import com.example.durban.util.RectUtils
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * <p>
 * This class adds crop feature, methods to draw crop guidelines, and keep image in correct state.
 * Also it extends parent class methods to add checks for scale; animating zoom in/out.
 * </p>
 * Update by Yan Zhenjie on 2017/5/23.
 */
open class CropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : TransformImageView(context, attrs, defStyleAttr) {
    private var mMaxResultImageSizeX = 0
    private var mMaxResultImageSizeY = 0
    private var mTargetAspectRatio = 0f
    private var mMaxScale = 0f
    private var mMinScale = 0f
    private var mMaxScaleMultiplier = DEFAULT_MAX_SCALE_MULTIPLIER
    private var mImageToWrapCropBoundsAnimDuration = DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION.toLong()
    private var mWrapCropBoundsRunnable: Runnable? = null
    private var mZoomImageToPositionRunnable: Runnable? = null
    private var mCropBoundsChangeListener: CropBoundsChangeListener? = null
    private val mCropRect = RectF()
    private val mTempMatrix = Matrix()

    companion object {
        const val DEFAULT_MAX_BITMAP_SIZE = 0
        const val DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = 500
        const val DEFAULT_MAX_SCALE_MULTIPLIER = 10.0f
        const val SOURCE_IMAGE_ASPECT_RATIO = 0f
        const val DEFAULT_ASPECT_RATIO = SOURCE_IMAGE_ASPECT_RATIO
    }

    /**
     * Cancels all current animations and sets image to fill crop area (without animation).
     * Then creates and executes [BitmapCropTask] with proper parameters.
     */
    fun cropAndSaveImage(compressFormat: CompressFormat, compressQuality: Int, cropCallback: BitmapCropCallback?) {
        cancelAllAnimations()
        setImageToWrapCropBounds(false)
        val imageState = ImageState(mCropRect, RectUtils.trapToRect(mCurrentImageCorners), getCurrentScale(), getCurrentAngle())
        val cropParameters = CropParameters(mMaxResultImageSizeX, mMaxResultImageSizeY, compressFormat, compressQuality, getImagePath().orEmpty(), getOutputDirectory().orEmpty(), getExifInfo() ?: ExifInfo(0, 0, 0))
        BitmapCropTask(context, getViewBitmap(), imageState, cropParameters, cropCallback).execute()
    }

    /**
     * @return - maximum scale value for current image and crop ratio
     */
    fun getMaxScale(): Float {
        return mMaxScale
    }

    /**
     * @return - minimum scale value for current image and crop ratio
     */
    fun getMinScale(): Float {
        return mMinScale
    }

    /**
     * @return - aspect ratio for crop bounds
     */
    fun getTargetAspectRatio(): Float {
        return mTargetAspectRatio
    }

    /**
     * Updates current crop rectangle with given. Also recalculates image properties and position
     * to fit new crop rectangle.
     *
     * @param cropRect - new crop rectangle
     */
    fun setCropRect(cropRect: RectF) {
        mTargetAspectRatio = cropRect.width() / cropRect.height()
        mCropRect[cropRect.left - paddingLeft, cropRect.top - paddingTop, cropRect.right - paddingRight] = cropRect.bottom - paddingBottom
        calculateImageScaleBounds()
        setImageToWrapCropBounds()
    }

    /**
     * This method sets aspect ratio for crop bounds.
     * If [.SOURCE_IMAGE_ASPECT_RATIO] value is passed - aspect ratio is calculated
     * based on current image width and height.
     *
     * @param targetAspectRatio - aspect ratio for image crop (e.g. 1.77(7) for 16:9)
     */
    fun setTargetAspectRatio(targetAspectRatio: Float) {
        val mDrawable = drawable
        if (mDrawable == null) {
            mTargetAspectRatio = targetAspectRatio
            return
        }
        mTargetAspectRatio = if (targetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
            mDrawable.intrinsicWidth / mDrawable.intrinsicHeight.toFloat()
        } else {
            targetAspectRatio
        }
        if (mCropBoundsChangeListener != null) {
            mCropBoundsChangeListener?.onCropAspectRatioChanged(mTargetAspectRatio)
        }
    }

    fun getCropBoundsChangeListener(): CropBoundsChangeListener? {
        return mCropBoundsChangeListener
    }

    fun setCropBoundsChangeListener(cropBoundsChangeListener: CropBoundsChangeListener) {
        mCropBoundsChangeListener = cropBoundsChangeListener
    }

    /**
     * This method sets maximum width for resulting cropped image
     *
     * @param maxResultImageSizeX - size in pixels
     */
    fun setMaxResultImageSizeX(@IntRange(from = 10) maxResultImageSizeX: Int) {
        mMaxResultImageSizeX = maxResultImageSizeX
    }

    /**
     * This method sets maximum width for resulting cropped image
     *
     * @param maxResultImageSizeY - size in pixels
     */
    fun setMaxResultImageSizeY(@IntRange(from = 10) maxResultImageSizeY: Int) {
        mMaxResultImageSizeY = maxResultImageSizeY
    }

    /**
     * This method sets animation duration for image to wrap the crop bounds
     *
     * @param imageToWrapCropBoundsAnimDuration - duration in milliseconds
     */
    fun setImageToWrapCropBoundsAnimDuration(@IntRange(from = 100) imageToWrapCropBoundsAnimDuration: Long) {
        if (imageToWrapCropBoundsAnimDuration > 0) {
            mImageToWrapCropBoundsAnimDuration = imageToWrapCropBoundsAnimDuration
        } else {
            throw IllegalArgumentException("Animation duration cannot be negative value.")
        }
    }

    /**
     * This method sets multiplier that is used to calculate max image scale from min image scale.
     *
     * @param maxScaleMultiplier - (minScale * maxScaleMultiplier) = maxScale
     */
    fun setMaxScaleMultiplier(maxScaleMultiplier: Float) {
        mMaxScaleMultiplier = maxScaleMultiplier
    }

    /**
     * This method scales image down for given value related to image center.
     */
    fun zoomOutImage(deltaScale: Float) {
        zoomOutImage(deltaScale, mCropRect.centerX(), mCropRect.centerY())
    }

    /**
     * This method scales image down for given value related given coords (x, y).
     */
    fun zoomOutImage(scale: Float, centerX: Float, centerY: Float) {
        if (scale >= getMinScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY)
        }
    }

    /**
     * This method scales image up for given value related to image center.
     */
    fun zoomInImage(deltaScale: Float) {
        zoomInImage(deltaScale, mCropRect.centerX(), mCropRect.centerY())
    }

    /**
     * This method scales image up for given value related to given coords (x, y).
     */
    fun zoomInImage(scale: Float, centerX: Float, centerY: Float) {
        if (scale <= getMaxScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY)
        }
    }

    /**
     * This method changes image scale for given value related to point (px, py) but only if
     * resulting scale is in min/max bounds.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    override fun postScale(deltaScale: Float, px: Float, py: Float) {
        if (deltaScale > 1 && getCurrentScale() * deltaScale <= getMaxScale()) {
            super.postScale(deltaScale, px, py)
        } else if (deltaScale < 1 && getCurrentScale() * deltaScale >= getMinScale()) {
            super.postScale(deltaScale, px, py)
        }
    }

    /**
     * This method rotates image for given angle related to the image center.
     *
     * @param deltaAngle - angle to rotate
     */
    fun postRotate(deltaAngle: Float) {
        postRotate(deltaAngle, mCropRect.centerX(), mCropRect.centerY())
    }

    /**
     * This method cancels all current Runnable objects that represent animations.
     */
    fun cancelAllAnimations() {
        removeCallbacks(mWrapCropBoundsRunnable)
        removeCallbacks(mZoomImageToPositionRunnable)
    }

    fun setImageToWrapCropBounds() {
        setImageToWrapCropBounds(true)
    }

    /**
     * If image doesn't fill the crop bounds it must be translated and scaled properly to fill those.
     *
     *
     * Therefore this method calculates delta X, Y and scale values and passes them to the
     * [WrapCropBoundsRunnable] which animates image.
     * Scale value must be calculated only if image won't fill the crop bounds after it's translated to the
     * crop bounds rectangle center. Using temporary variables this method checks this case.
     */
    fun setImageToWrapCropBounds(animate: Boolean) {
        if (mBitmapLaidOut && !isImageWrapCropBounds()) {
            val currentX = mCurrentImageCenter[0]
            val currentY = mCurrentImageCenter[1]
            val currentScale = getCurrentScale()
            var deltaX = mCropRect.centerX() - currentX
            var deltaY = mCropRect.centerY() - currentY
            var deltaScale = 0f
            mTempMatrix.reset()
            mTempMatrix.setTranslate(deltaX, deltaY)
            val tempCurrentImageCorners = mCurrentImageCorners.copyOf(mCurrentImageCorners.size)
            mTempMatrix.mapPoints(tempCurrentImageCorners)
            val willImageWrapCropBoundsAfterTranslate = isImageWrapCropBounds(tempCurrentImageCorners)
            if (willImageWrapCropBoundsAfterTranslate) {
                val imageIndents = calculateImageIndents()
                deltaX = -(imageIndents[0] + imageIndents[2])
                deltaY = -(imageIndents[1] + imageIndents[3])
            } else {
                val tempCropRect = RectF(mCropRect)
                mTempMatrix.reset()
                mTempMatrix.setRotate(getCurrentAngle())
                mTempMatrix.mapRect(tempCropRect)
                val currentImageSides = RectUtils.getRectSidesFromCorners(mCurrentImageCorners)
                deltaScale = max(tempCropRect.width() / currentImageSides[0], tempCropRect.height() / currentImageSides[1])
                deltaScale = deltaScale * currentScale - currentScale
            }
            if (animate) {
                post(WrapCropBoundsRunnable(this, mImageToWrapCropBoundsAnimDuration, currentX, currentY, deltaX, deltaY, currentScale, deltaScale, willImageWrapCropBoundsAfterTranslate).also { mWrapCropBoundsRunnable = it })
            } else {
                postTranslate(deltaX, deltaY)
                if (!willImageWrapCropBoundsAfterTranslate) {
                    zoomInImage(currentScale + deltaScale, mCropRect.centerX(), mCropRect.centerY())
                }
            }
        }
    }

    /**
     * First, un-rotate image and crop rectangles (make image rectangle axis-aligned).
     * Second, calculate deltas between those rectangles sides.
     * Third, depending on delta (its sign) put them or zero inside an array.
     * Fourth, using Matrix, rotate back those points (indents).
     *
     * @return - the float array of image indents (4 floats) - in this order [left, top, right, bottom]
     */
    private fun calculateImageIndents(): FloatArray {
        mTempMatrix.reset()
        mTempMatrix.setRotate(-getCurrentAngle())
        val unRotatedImageCorners = mCurrentImageCorners.copyOf(mCurrentImageCorners.size)
        val unRotatedCropBoundsCorners = RectUtils.getCornersFromRect(mCropRect)
        mTempMatrix.mapPoints(unRotatedImageCorners)
        mTempMatrix.mapPoints(unRotatedCropBoundsCorners)
        val unRotatedImageRect = RectUtils.trapToRect(unRotatedImageCorners)
        val unRotatedCropRect = RectUtils.trapToRect(unRotatedCropBoundsCorners)
        val deltaLeft = unRotatedImageRect.left - unRotatedCropRect.left
        val deltaTop = unRotatedImageRect.top - unRotatedCropRect.top
        val deltaRight = unRotatedImageRect.right - unRotatedCropRect.right
        val deltaBottom = unRotatedImageRect.bottom - unRotatedCropRect.bottom
        val indents = FloatArray(4)
        indents[0] = if (deltaLeft > 0) deltaLeft else 0f
        indents[1] = if (deltaTop > 0) deltaTop else 0f
        indents[2] = if (deltaRight < 0) deltaRight else 0f
        indents[3] = if (deltaBottom < 0) deltaBottom else 0f
        mTempMatrix.reset()
        mTempMatrix.setRotate(getCurrentAngle())
        mTempMatrix.mapPoints(indents)
        return indents
    }

    /**
     * When image is laid out it must be centered properly to fit current crop bounds.
     */
    override fun onImageLaidOut() {
        super.onImageLaidOut()
        val mDrawable = drawable ?: return
        val drawableWidth = mDrawable.intrinsicWidth.toFloat()
        val drawableHeight = mDrawable.intrinsicHeight.toFloat()
        if (mTargetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
            mTargetAspectRatio = drawableWidth / drawableHeight
        }
        val height = (mThisWidth / mTargetAspectRatio) as Int
        if (height > mThisHeight) {
            val width = (mThisHeight * mTargetAspectRatio) as Int
            val halfDiff = (mThisWidth - width) / 2
            mCropRect[halfDiff.toFloat(), 0f, (width + halfDiff).toFloat()] = mThisHeight.toFloat()
        } else {
            val halfDiff = (mThisHeight - height) / 2
            mCropRect[0f, halfDiff.toFloat(), mThisWidth.toFloat()] = (height + halfDiff).toFloat()
        }
        calculateImageScaleBounds(drawableWidth, drawableHeight)
        setupInitialImagePosition(drawableWidth, drawableHeight)
        if (mCropBoundsChangeListener != null) {
            mCropBoundsChangeListener?.onCropAspectRatioChanged(mTargetAspectRatio)
        }
        if (mTransformImageListener != null) {
            mTransformImageListener?.onScale(getCurrentScale())
            mTransformImageListener?.onRotate(getCurrentAngle())
        }
    }

    /**
     * This method checks whether current image fills the crop bounds.
     */
    protected fun isImageWrapCropBounds(): Boolean {
        return isImageWrapCropBounds(mCurrentImageCorners)
    }

    /**
     * This methods checks whether a rectangle that is represented as 4 corner points (8 floats)
     * fills the crop bounds rectangle.
     *
     * @param imageCorners - corners of a rectangle
     * @return - true if it wraps crop bounds, false - otherwise
     */
    protected fun isImageWrapCropBounds(imageCorners: FloatArray): Boolean {
        mTempMatrix.reset()
        mTempMatrix.setRotate(-getCurrentAngle())
        val unRotatedImageCorners = imageCorners.copyOf(imageCorners.size)
        mTempMatrix.mapPoints(unRotatedImageCorners)
        val unRotatedCropBoundsCorners = RectUtils.getCornersFromRect(mCropRect)
        mTempMatrix.mapPoints(unRotatedCropBoundsCorners)
        return RectUtils.trapToRect(unRotatedImageCorners).contains(RectUtils.trapToRect(unRotatedCropBoundsCorners))
    }

    /**
     * This method changes image scale (animating zoom for given duration), related to given center (x,y).
     *
     * @param scale      - target scale
     * @param centerX    - scale center X
     * @param centerY    - scale center Y
     * @param durationMs - zoom animation duration
     */
    protected fun zoomImageToPosition(scale: Float, centerX: Float, centerY: Float, durationMs: Long) {
        var mScale = scale
        if (mScale > getMaxScale()) {
            mScale = getMaxScale()
        }
        val oldScale = getCurrentScale()
        val deltaScale = mScale - oldScale
        post(ZoomImageToPosition(this, durationMs, oldScale, deltaScale, centerX, centerY).also { mZoomImageToPositionRunnable = it })
    }

    private fun calculateImageScaleBounds() {
        val mDrawable = drawable ?: return
        calculateImageScaleBounds(mDrawable.intrinsicWidth.toFloat(), mDrawable.intrinsicHeight.toFloat())
    }

    /**
     * This method calculates image minimum and maximum scale values for current [.mCropRect].
     *
     * @param drawableWidth  - image width
     * @param drawableHeight - image height
     */
    private fun calculateImageScaleBounds(drawableWidth: Float, drawableHeight: Float) {
        val widthScale = min(mCropRect.width() / drawableWidth, mCropRect.width() / drawableHeight)
        val heightScale = min(mCropRect.height() / drawableHeight, mCropRect.height() / drawableWidth)
        mMinScale = min(widthScale, heightScale)
        mMaxScale = mMinScale * mMaxScaleMultiplier
    }

    /**
     * This method calculates initial image position so it is positioned properly.
     * Then it sets those values to the current image matrix.
     *
     * @param drawableWidth  - image width
     * @param drawableHeight - image height
     */
    private fun setupInitialImagePosition(drawableWidth: Float, drawableHeight: Float) {
        val cropRectWidth = mCropRect.width()
        val cropRectHeight = mCropRect.height()
        val widthScale = mCropRect.width() / drawableWidth
        val heightScale = mCropRect.height() / drawableHeight
        val initialMinScale = max(widthScale, heightScale)
        val tw = (cropRectWidth - drawableWidth * initialMinScale) / 2.0f + mCropRect.left
        val th = (cropRectHeight - drawableHeight * initialMinScale) / 2.0f + mCropRect.top
        mCurrentImageMatrix.reset()
        mCurrentImageMatrix.postScale(initialMinScale, initialMinScale)
        mCurrentImageMatrix.postTranslate(tw, th)
        imageMatrix = mCurrentImageMatrix
    }

    /**
     * This method extracts all needed values from the styled attributes.
     * Those are used to configure the view.
     */
    fun processStyledAttributes(a: TypedArray) {
        val targetAspectRatioX = abs(a.getFloat(R.styleable.durban_CropView_durban_aspect_ratio_x, DEFAULT_ASPECT_RATIO))
        val targetAspectRatioY = abs(a.getFloat(R.styleable.durban_CropView_durban_aspect_ratio_y, DEFAULT_ASPECT_RATIO))
        mTargetAspectRatio = if (targetAspectRatioX == SOURCE_IMAGE_ASPECT_RATIO || targetAspectRatioY == SOURCE_IMAGE_ASPECT_RATIO) {
            SOURCE_IMAGE_ASPECT_RATIO
        } else {
            targetAspectRatioX / targetAspectRatioY
        }
    }

    /**
     * This Runnable is used to animate an image so it fills the crop bounds entirely.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie [.cancelAllAnimations] method
     * or when certain conditions inside [WrapCropBoundsRunnable.run] method are triggered.
     */
    private inner class WrapCropBoundsRunnable(cropImageView: CropImageView, private val mDurationMs: Long, private val mOldX: Float, private val mOldY: Float, private val mCenterDiffX: Float, private val mCenterDiffY: Float, private val mOldScale: Float, private val mDeltaScale: Float, private val mWillBeImageInBoundsAfterTranslate: Boolean) : Runnable {
        private val mCropImageView = WeakReference(cropImageView)
        private val mStartTime = System.currentTimeMillis()

        override fun run() {
            val cropImageView = mCropImageView.get() ?: return
            val now = System.currentTimeMillis()
            val currentMs = min(mDurationMs, now - mStartTime).toFloat()
            val newX = CubicEasing.easeOut(currentMs, 0f, mCenterDiffX, mDurationMs.toFloat())
            val newY = CubicEasing.easeOut(currentMs, 0f, mCenterDiffY, mDurationMs.toFloat())
            val newScale = CubicEasing.easeInOut(currentMs, 0f, mDeltaScale, mDurationMs.toFloat())
            if (currentMs < mDurationMs) {
                cropImageView.postTranslate(newX - (cropImageView.mCurrentImageCenter[0] - mOldX), newY - (cropImageView.mCurrentImageCenter[1] - mOldY))
                if (!mWillBeImageInBoundsAfterTranslate) {
                    cropImageView.zoomInImage(mOldScale + newScale, cropImageView.mCropRect.centerX(), cropImageView.mCropRect.centerY())
                }
                if (!cropImageView.isImageWrapCropBounds()) {
                    cropImageView.post(this)
                }
            }
        }
    }

    /**
     * This Runnable is used to animate an image zoom.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie [.cancelAllAnimations] method
     * or when certain conditions inside [ZoomImageToPosition.run] method are triggered.
     */
    private inner class ZoomImageToPosition(cropImageView: CropImageView, private val mDurationMs: Long, private val mOldScale: Float, private val mDeltaScale: Float, private val mDestX: Float, private val mDestY: Float) : Runnable {
        private val mCropImageView = WeakReference(cropImageView)
        private val mStartTime = System.currentTimeMillis()

        override fun run() {
            val cropImageView = mCropImageView.get() ?: return
            val now = System.currentTimeMillis()
            val currentMs = min(mDurationMs, now - mStartTime).toFloat()
            val newScale: Float = CubicEasing.easeInOut(currentMs, 0f, mDeltaScale, mDurationMs.toFloat())
            if (currentMs < mDurationMs) {
                cropImageView.zoomInImage(mOldScale + newScale, mDestX, mDestY)
                cropImageView.post(this)
            } else {
                cropImageView.setImageToWrapCropBounds()
            }
        }
    }

}