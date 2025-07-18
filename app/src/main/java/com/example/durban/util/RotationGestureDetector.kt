package com.example.durban.util

import android.view.MotionEvent
import kotlin.math.atan2

class RotationGestureDetector(private val mListener: OnRotationGestureListener?) {
    private var fX = 0f
    private var fY = 0f
    private var sX = 0f
    private var sY = 0f
    private var mPointerIndex1 = 0
    private var mPointerIndex2 = 0
    private var mAngle = 0f
    private var mIsFirstTouch = false

    init {
        mPointerIndex1 = INVALID_POINTER_INDEX
        mPointerIndex2 = INVALID_POINTER_INDEX
    }

    companion object {
        private const val INVALID_POINTER_INDEX = -1

        open class SimpleOnRotationGestureListener : OnRotationGestureListener {
            override fun onRotation(rotationDetector: RotationGestureDetector?): Boolean {
                return false
            }
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sX = event.x
                sY = event.y
                mPointerIndex1 = event.findPointerIndex(event.getPointerId(0))
                mAngle = 0f
                mIsFirstTouch = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                fX = event.x
                fY = event.y
                mPointerIndex2 = event.findPointerIndex(event.getPointerId(event.actionIndex))
                mAngle = 0f
                mIsFirstTouch = true
            }
            MotionEvent.ACTION_MOVE -> if (mPointerIndex1 != INVALID_POINTER_INDEX && mPointerIndex2 != INVALID_POINTER_INDEX && event.pointerCount > mPointerIndex2) {
                val nsX = event.getX(mPointerIndex1)
                val nsY = event.getY(mPointerIndex1)
                val nfX = event.getX(mPointerIndex2)
                val nfY = event.getY(mPointerIndex2)
                if (mIsFirstTouch) {
                    mAngle = 0f
                    mIsFirstTouch = false
                } else {
                    calculateAngleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY)
                }
                mListener?.onRotation(this)
                fX = nfX
                fY = nfY
                sX = nsX
                sY = nsY
            }
            MotionEvent.ACTION_UP -> mPointerIndex1 = INVALID_POINTER_INDEX
            MotionEvent.ACTION_POINTER_UP -> mPointerIndex2 = INVALID_POINTER_INDEX
        }
        return true
    }

    private fun calculateAngleBetweenLines(fx1: Float, fy1: Float, fx2: Float, fy2: Float, sx1: Float, sy1: Float, sx2: Float, sy2: Float): Float {
        return calculateAngleDelta(
            Math.toDegrees(atan2((fy1 - fy2).toDouble(), (fx1 - fx2).toDouble()).toFloat().toDouble()).toFloat(),
            Math.toDegrees(atan2((sy1 - sy2).toDouble(), (sx1 - sx2).toDouble()).toFloat().toDouble()).toFloat())
    }

    private fun calculateAngleDelta(angleFrom: Float, angleTo: Float): Float {
        mAngle = angleTo % 360.0f - angleFrom % 360.0f
        if (mAngle < -180.0f) {
            mAngle += 360.0f
        } else if (mAngle > 180.0f) {
            mAngle -= 360.0f
        }
        return mAngle
    }

    fun getAngle(): Float {
        return mAngle
    }

    interface OnRotationGestureListener {
        fun onRotation(rotationDetector: RotationGestureDetector?): Boolean
    }

}