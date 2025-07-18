package com.example.durban.callback

interface BitmapCropCallback {

    fun onBitmapCropped(imagePath: String, imageWidth: Int, imageHeight: Int)

    fun onCropFailure(t: Throwable)

}