package com.example.durban.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import com.example.durban.R
import com.example.durban.callback.OverlayViewChangeListener
import com.example.durban.util.RectUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * <p>
 * This view is used for drawing the overlay on top of the image. It may have frame, crop guidelines and dimmed area.
 * This must have LAYER_TYPE_SOFTWARE to draw itself properly.
 * </p>
 * Update by Yan Zhenjie on 2017/5/23.
 */
class OverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    @FreestyleMode
    private var mFreestyleCropMode = DEFAULT_FREESTYLE_CROP_MODE
    private var mCurrentTouchCornerIndex = -1
    private var mTouchPointThreshold = 0
    private var mCropGridRowCount = 0
    private var mCropGridColumnCount = 0
    private var mCropRectMinSize = 0
    private var mCropRectCornerTouchAreaLineLength = 0
    private var mDimmedColor = 0
    private var mTargetAspectRatio = 0f
    private var mPreviousTouchX = -1f
    private var mPreviousTouchY = -1f
    private var mShowCropFrame = false
    private var mShowCropGrid = false
    private var mCircleDimmedLayer = false
    private var mShouldSetupCropBounds = false
    private var mGridPoints: FloatArray? = null
    private var mCallback: OverlayViewChangeListener? = null
    private val mCropViewRect = RectF()
    private val mTempRect = RectF()
    private val mCircularPath = Path()
    private val mDimmedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mCropGridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mCropFramePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mCropFrameCornersPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    protected var mThisWidth = 0
    protected var mThisHeight = 0
    protected var mCropGridCorners: FloatArray? = null
    protected var mCropGridCenter: FloatArray? = null

    companion object {
        const val FREESTYLE_CROP_MODE_DISABLE = 0
        const val FREESTYLE_CROP_MODE_ENABLE = 1
        const val FREESTYLE_CROP_MODE_ENABLE_WITH_PASS_THROUGH = 2
        const val DEFAULT_FREESTYLE_CROP_MODE = FREESTYLE_CROP_MODE_DISABLE

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(FREESTYLE_CROP_MODE_DISABLE, FREESTYLE_CROP_MODE_ENABLE, FREESTYLE_CROP_MODE_ENABLE_WITH_PASS_THROUGH)
        annotation class FreestyleMode
    }

    init {
        mTouchPointThreshold = resources.getDimensionPixelSize(R.dimen.durban_dp_30)
        mCropRectMinSize = resources.getDimensionPixelSize(R.dimen.durban_dp_100)
        mCropRectCornerTouchAreaLineLength = resources.getDimensionPixelSize(R.dimen.durban_dp_10)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            mThisWidth = (width - paddingRight) - paddingLeft
            mThisHeight = (height - paddingBottom) - paddingTop
            if (mShouldSetupCropBounds) {
                mShouldSetupCropBounds = false
                setTargetAspectRatio(mTargetAspectRatio)
            }
        }
    }

    /**
     * Along with image there are dimmed layer, crop bounds and crop guidelines that must be drawn.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDimmedLayer(canvas)
        drawCropGrid(canvas)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (mCropViewRect.isEmpty || mFreestyleCropMode == FREESTYLE_CROP_MODE_DISABLE) {
            return false
        }
        event ?: return false
        var x = event.x
        var y = event.y
        if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            mCurrentTouchCornerIndex = getCurrentTouchIndex(x, y)
            val shouldHandle = mCurrentTouchCornerIndex != -1
            if (!shouldHandle) {
                mPreviousTouchX = -1f
                mPreviousTouchY = -1f
            } else if (mPreviousTouchX < 0) {
                mPreviousTouchX = x
                mPreviousTouchY = y
            }
            return shouldHandle
        }
        if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_MOVE) {
            if (event.pointerCount == 1 && mCurrentTouchCornerIndex != -1) {
                x = min(max(x, paddingLeft.toFloat()), (width - paddingRight).toFloat())
                y = min(max(y, paddingTop.toFloat()), (height - paddingBottom).toFloat())
                updateCropViewRect(x, y)
                mPreviousTouchX = x
                mPreviousTouchY = y
                return true
            }
        }
        if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            mPreviousTouchX = -1f
            mPreviousTouchY = -1f
            mCurrentTouchCornerIndex = -1
            if (mCallback != null) {
                mCallback?.onCropRectUpdated(mCropViewRect)
            }
        }
        return false
    }

    /**
     * * The order of the corners is:
     * 0------->1
     * ^        |
     * |   4    |
     * |        v
     * 3<-------2
     */
    private fun updateCropViewRect(touchX: Float, touchY: Float) {
        mTempRect.set(mCropViewRect)
        when (mCurrentTouchCornerIndex) {
            0 -> mTempRect[touchX, touchY, mCropViewRect.right] = mCropViewRect.bottom
            1 -> mTempRect[mCropViewRect.left, touchY, touchX] = mCropViewRect.bottom
            2 -> mTempRect[mCropViewRect.left, mCropViewRect.top, touchX] = touchY
            3 -> mTempRect[touchX, mCropViewRect.top, mCropViewRect.right] = touchY
            4 -> {
                mTempRect.offset(touchX - mPreviousTouchX, touchY - mPreviousTouchY)
                if (mTempRect.left > left && mTempRect.top > top && mTempRect.right < right && mTempRect.bottom < bottom) {
                    mCropViewRect.set(mTempRect)
                    updateGridPoints()
                    postInvalidate()
                }
                return
            }
        }
        val changeHeight = mTempRect.height() >= mCropRectMinSize
        val changeWidth = mTempRect.width() >= mCropRectMinSize
        mCropViewRect[if (changeWidth) mTempRect.left else mCropViewRect.left, if (changeHeight) mTempRect.top else mCropViewRect.top, if (changeWidth) mTempRect.right else mCropViewRect.right] = if (changeHeight) mTempRect.bottom else mCropViewRect.bottom
        if (changeHeight || changeWidth) {
            updateGridPoints()
            postInvalidate()
        }
    }

    /**
     * * The order of the corners in the float array is:
     * 0------->1
     * ^        |
     * |   4    |
     * |        v
     * 3<-------2
     *
     * @return - index of corner that is being dragged
     */
    private fun getCurrentTouchIndex(touchX: Float, touchY: Float): Int {
        var closestPointIndex = -1
        var closestPointDistance = mTouchPointThreshold.toDouble()
        var i = 0
        while (i < 8) {
            val distanceToCorner = sqrt((touchX - (mCropGridCorners?.get(i) ?: 0f)).toDouble().pow(2.0) + (touchY - (mCropGridCorners?.get(i + 1) ?: 0f)).toDouble().pow(2.0))
            if (distanceToCorner < closestPointDistance) {
                closestPointDistance = distanceToCorner
                closestPointIndex = i / 2
            }
            i += 2
        }
        if (mFreestyleCropMode == FREESTYLE_CROP_MODE_ENABLE && closestPointIndex < 0 && mCropViewRect.contains(touchX, touchY)) return 4
        return closestPointIndex
    }

    /**
     * This method draws dimmed area around the crop bounds.
     *
     * @param canvas - valid canvas object
     */
    protected fun drawDimmedLayer(canvas: Canvas) {
        canvas.withSave {
            if (mCircleDimmedLayer) clipPath(mCircularPath, Region.Op.DIFFERENCE)
            else clipRect(mCropViewRect, Region.Op.DIFFERENCE)
            drawColor(mDimmedColor)
        }
        if (mCircleDimmedLayer) { // Draw 1px stroke to fix antialias
            canvas.drawCircle(mCropViewRect.centerX(), mCropViewRect.centerY(), min(mCropViewRect.width(), mCropViewRect.height()) / 2f, mDimmedStrokePaint)
        }
    }

    /**
     * This method draws crop bounds (empty rectangle)
     * and crop guidelines (vertical and horizontal lines inside the crop bounds) if needed.
     *
     * @param canvas - valid canvas object
     */
    protected fun drawCropGrid(canvas: Canvas) {
        if (mShowCropGrid) {
            if (mGridPoints == null && !mCropViewRect.isEmpty) {
                mGridPoints = FloatArray((mCropGridRowCount) * 4 + (mCropGridColumnCount) * 4)
                var index = 0
                for (i in 0..<mCropGridRowCount) {
                    mGridPoints?.let {
                        it[index++] = mCropViewRect.left
                        it[index++] = (mCropViewRect.height() * ((i.toFloat() + 1.0f) / (mCropGridRowCount + 1).toFloat())) + mCropViewRect.top
                        it[index++] = mCropViewRect.right
                        it[index++] = (mCropViewRect.height() * ((i.toFloat() + 1.0f) / (mCropGridRowCount + 1).toFloat())) + mCropViewRect.top
                    }
                }
                for (i in 0..<mCropGridColumnCount) {
                    mGridPoints?.let {
                        it[index++] = ((mCropViewRect.width() * ((i.toFloat() + 1.0f) / (mCropGridColumnCount + 1).toFloat())) + mCropViewRect.left)
                        it[index++] = mCropViewRect.top
                        it[index++] = ((mCropViewRect.width() * ((i.toFloat() + 1.0f) / (mCropGridColumnCount + 1).toFloat())) + mCropViewRect.left)
                        it[index++] = mCropViewRect.bottom
                    }
                }
            }
            mGridPoints?.let { canvas.drawLines(it, mCropGridPaint) }
        }
        if (mShowCropFrame) canvas.drawRect(mCropViewRect, mCropFramePaint)
        if (mFreestyleCropMode != FREESTYLE_CROP_MODE_DISABLE) {
            canvas.withSave {
                mTempRect.set(mCropViewRect)
                mTempRect.inset(mCropRectCornerTouchAreaLineLength.toFloat(), -mCropRectCornerTouchAreaLineLength.toFloat())
                clipRect(mTempRect, Region.Op.DIFFERENCE)
                mTempRect.set(mCropViewRect)
                mTempRect.inset(-mCropRectCornerTouchAreaLineLength.toFloat(), mCropRectCornerTouchAreaLineLength.toFloat())
                clipRect(mTempRect, Region.Op.DIFFERENCE)
                drawRect(mCropViewRect, mCropFrameCornersPaint)
            }
        }
    }

    /**
     * This method extracts all needed values from the styled attributes.
     * Those are used to configure the view.
     */
    fun processStyledAttributes(a: TypedArray) {
        mCircleDimmedLayer = a.getBoolean(R.styleable.durban_CropView_durban_circle_dimmed_layer, false)
        mDimmedColor = a.getColor(R.styleable.durban_CropView_durban_dimmed_color, ContextCompat.getColor(context, R.color.durban_CropDimmed))
        mDimmedStrokePaint.color = mDimmedColor
        mDimmedStrokePaint.style = Paint.Style.STROKE
        mDimmedStrokePaint.strokeWidth = 1f
        initCropFrameStyle(a)
        mShowCropFrame = a.getBoolean(R.styleable.durban_CropView_durban_show_frame, true)
        initCropGridStyle(a)
        mShowCropGrid = a.getBoolean(R.styleable.durban_CropView_durban_show_grid, true)
    }

    /**
     * This method setups Paint object for the crop bounds.
     */
    private fun initCropFrameStyle(a: TypedArray) {
        val cropFrameStrokeSize = a.getDimensionPixelSize(R.styleable.durban_CropView_durban_frame_stroke_size, resources.getDimensionPixelSize(R.dimen.durban_dp_1))
        val cropFrameColor = a.getColor(R.styleable.durban_CropView_durban_frame_color, ContextCompat.getColor(context, R.color.durban_CropFrameLine))
        mCropFramePaint.strokeWidth = cropFrameStrokeSize.toFloat()
        mCropFramePaint.color = cropFrameColor
        mCropFramePaint.style = Paint.Style.STROKE
        mCropFrameCornersPaint.strokeWidth = (cropFrameStrokeSize * 3).toFloat()
        mCropFrameCornersPaint.color = cropFrameColor
        mCropFrameCornersPaint.style = Paint.Style.STROKE
    }

    /**
     * This method setups Paint object for the crop guidelines.
     */
    private fun initCropGridStyle(a: TypedArray) {
        val cropGridStrokeSize = a.getDimensionPixelSize(R.styleable.durban_CropView_durban_grid_stroke_size, resources.getDimensionPixelSize(R.dimen.durban_dp_1))
        val cropGridColor = a.getColor(R.styleable.durban_CropView_durban_grid_color, ContextCompat.getColor(context, R.color.durban_CropGridLine))
        mCropGridPaint.strokeWidth = cropGridStrokeSize.toFloat()
        mCropGridPaint.color = cropGridColor
        mCropGridRowCount = a.getInt(R.styleable.durban_CropView_durban_grid_row_count, 2)
        mCropGridColumnCount = a.getInt(R.styleable.durban_CropView_durban_grid_column_count, 2)
    }

    fun getCropViewRect(): RectF {
        return mCropViewRect
    }

    @FreestyleMode
    fun getFreestyleCropMode(): Int {
        return mFreestyleCropMode
    }

    /***
     * Please use the new method {@link #getFreestyleCropMode() getFreestyleCropMode} method as we have more than 1 freestyle
     * crop mode.
     */
    @Deprecated("")
    fun isFreestyleCropEnabled(): Boolean {
        return mFreestyleCropMode == FREESTYLE_CROP_MODE_ENABLE
    }

    /***
     * Please use the new method [setFreestyleCropMode][.setFreestyleCropMode] method as we have more than 1 freestyle
     * crop mode.
     */
    @Deprecated("")
    fun setFreestyleCropEnabled(freestyleCropEnabled: Boolean) {
        mFreestyleCropMode = if (freestyleCropEnabled) FREESTYLE_CROP_MODE_ENABLE else FREESTYLE_CROP_MODE_DISABLE
    }

    fun setFreestyleCropMode(@FreestyleMode mFreestyleCropMode: Int) {
        this.mFreestyleCropMode = mFreestyleCropMode
        postInvalidate()
    }

    /**
     * Setter for {@link #mCircleDimmedLayer} variable.
     *
     * @param circleDimmedLayer - set it to true if you want dimmed layer to be an circle
     */
    fun setCircleDimmedLayer(circleDimmedLayer: Boolean) {
        mCircleDimmedLayer = circleDimmedLayer
    }

    /**
     * Setter for crop grid rows count.
     * Resets [.mGridPoints] variable because it is not valid anymore.
     */
    fun setCropGridRowCount(@IntRange(from = 0) cropGridRowCount: Int) {
        mCropGridRowCount = cropGridRowCount
        mGridPoints = null
    }

    /**
     * Setter for crop grid columns count.
     * Resets [.mGridPoints] variable because it is not valid anymore.
     */
    fun setCropGridColumnCount(@IntRange(from = 0) cropGridColumnCount: Int) {
        mCropGridColumnCount = cropGridColumnCount
        mGridPoints = null
    }

    /**
     * Setter for [.mShowCropFrame] variable.
     *
     * @param showCropFrame - set to true if you want to see a crop frame rectangle on top of an image
     */
    fun setShowCropFrame(showCropFrame: Boolean) {
        mShowCropFrame = showCropFrame
    }

    /**
     * Setter for [.mShowCropGrid] variable.
     *
     * @param showCropGrid - set to true if you want to see a crop grid on top of an image
     */
    fun setShowCropGrid(showCropGrid: Boolean) {
        mShowCropGrid = showCropGrid
    }

    /**
     * Setter for [.mDimmedColor] variable.
     *
     * @param dimmedColor - desired color of dimmed area around the crop bounds
     */
    fun setDimmedColor(@ColorInt dimmedColor: Int) {
        mDimmedColor = dimmedColor
    }

    /**
     * Setter for crop frame stroke width
     */
    fun setCropFrameStrokeWidth(@IntRange(from = 0) width: Int) {
        mCropFramePaint.strokeWidth = width.toFloat()
    }

    /**
     * Setter for crop grid stroke width
     */
    fun setCropGridStrokeWidth(@IntRange(from = 0) width: Int) {
        mCropGridPaint.strokeWidth = width.toFloat()
    }

    /**
     * Setter for crop frame color
     */
    fun setCropFrameColor(@ColorInt color: Int) {
        mCropFramePaint.color = color
    }

    /**
     * Setter for crop grid color
     */
    fun setCropGridColor(@ColorInt color: Int) {
        mCropGridPaint.color = color
    }

    /**
     * This method sets aspect ratio for crop bounds.
     *
     * @param targetAspectRatio - aspect ratio for image crop (e.g. 1.77(7) for 16:9)
     */
    fun setTargetAspectRatio(targetAspectRatio: Float) {
        mTargetAspectRatio = targetAspectRatio
        if (mThisWidth > 0) {
            setupCropBounds()
            postInvalidate()
        } else {
            mShouldSetupCropBounds = true
        }
    }

    /**
     * This method setups crop bounds rectangles for given aspect ratio and view size.
     * [.mCropViewRect] is used to draw crop bounds - uses padding.
     */
    fun setupCropBounds() {
        val height = (mThisWidth / mTargetAspectRatio).toInt()
        if (height > mThisHeight) {
            val width = (mThisHeight * mTargetAspectRatio).toInt()
            val halfDiff = (mThisWidth - width) / 2
            mCropViewRect[(paddingLeft + halfDiff).toFloat(), paddingTop.toFloat(), (paddingLeft + width + halfDiff).toFloat()] = (paddingTop + mThisHeight).toFloat()
        } else {
            val halfDiff = (mThisHeight - height) / 2
            mCropViewRect[paddingLeft.toFloat(), (paddingTop + halfDiff).toFloat(), (paddingLeft + mThisWidth).toFloat()] = (paddingTop + height + halfDiff).toFloat()
        }
        if (mCallback != null) {
            mCallback?.onCropRectUpdated(mCropViewRect)
        }
        updateGridPoints()
    }

    private fun updateGridPoints() {
        mCropGridCorners = RectUtils.getCornersFromRect(mCropViewRect)
        mCropGridCenter = RectUtils.getCenterFromRect(mCropViewRect)
        mGridPoints = null
        mCircularPath.reset()
        mCircularPath.addCircle(mCropViewRect.centerX(), mCropViewRect.centerY(), min(mCropViewRect.width(), mCropViewRect.height()) / 2f, Path.Direction.CW)
    }

    fun setOverlayViewChangeListener(callback: OverlayViewChangeListener) {
        mCallback = callback
    }

}