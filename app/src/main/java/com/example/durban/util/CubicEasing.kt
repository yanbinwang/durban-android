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

object CubicEasing {

    @JvmStatic
    fun easeOut(time: Float, start: Float, end: Float, duration: Float): Float {
        var mTime = time
        return end * (((mTime / duration - 1.0f).also { mTime = it }) * mTime * mTime + 1.0f) + start
    }

    @JvmStatic
    fun easeInOut(time: Float, start: Float, end: Float, duration: Float): Float {
        var mTime = time
        return if ((duration / 2.0f.let { mTime /= it; mTime }) < 1.0f) end / 2.0f * mTime * mTime * mTime + start else end / 2.0f * (((2.0f.let { mTime -= it; mTime }) * mTime * mTime) + 2.0f) + start
    }

}