package com.example.durban.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class FastBitmapDrawable(b: Bitmap?) : Drawable() {
    private var mAlpha = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mBitmap: Bitmap? = null
    private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        mAlpha = 255
        setBitmap(b)
    }

    override fun draw(canvas: Canvas) {
        mBitmap?.let {
            if (!it.isRecycled) {
                canvas.drawBitmap(it, null, bounds, mPaint)
            }
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
        mAlpha = alpha
        mPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.setColorFilter(cf)
    }

    override fun getIntrinsicWidth(): Int {
        return mWidth
    }

    override fun getIntrinsicHeight(): Int {
        return mHeight
    }

    override fun getMinimumWidth(): Int {
        return mWidth
    }

    override fun getMinimumHeight(): Int {
        return mHeight
    }

    override fun setFilterBitmap(filterBitmap: Boolean) {
        mPaint.isFilterBitmap = filterBitmap
    }

    override fun getAlpha(): Int {
        return mAlpha
    }

    fun setBitmap(b: Bitmap?) {
        mBitmap = b
        if (b != null) {
            mWidth = mBitmap?.width ?: 0
            mHeight = mBitmap?.height ?: 0
        } else {
            mHeight = 0
            mWidth = mHeight
        }
    }

    fun getBitmap(): Bitmap? {
        return mBitmap
    }

}