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
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.example.durban.R
import com.example.durban.callback.CropBoundsChangeListener
import com.example.durban.callback.OverlayViewChangeListener

class CropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    private var mGestureCropImageView: GestureCropImageView? = null
    private var mViewOverlay: OverlayView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.durban_crop_view, this, true)
        mGestureCropImageView = findViewById(R.id.image_view_crop)
        mViewOverlay = findViewById(R.id.view_overlay)
        context.withStyledAttributes(attrs, R.styleable.durban_CropView) {
            mViewOverlay?.processStyledAttributes(this)
            mGestureCropImageView?.processStyledAttributes(this)
        }
        setListenersToViews()
    }

    private fun setListenersToViews() {
        mGestureCropImageView?.setCropBoundsChangeListener(object : CropBoundsChangeListener {
            override fun onCropAspectRatioChanged(cropRatio: Float) {
                mViewOverlay?.setTargetAspectRatio(cropRatio)
            }
        })
        mViewOverlay?.setOverlayViewChangeListener(object : OverlayViewChangeListener {
            override fun onCropRectUpdated(cropRect: RectF?) {
                cropRect ?: return
                mGestureCropImageView?.setCropRect(cropRect)
            }
        })
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    fun getCropImageView(): GestureCropImageView? {
        return mGestureCropImageView
    }

    fun getOverlayView(): OverlayView? {
        return mViewOverlay
    }

}