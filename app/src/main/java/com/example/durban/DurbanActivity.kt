package com.example.durban

import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.example.durban.callback.BitmapCropCallback
import com.example.durban.util.DurbanUtils
import com.example.durban.view.CropImageView.Companion.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION
import com.example.durban.view.CropImageView.Companion.DEFAULT_MAX_BITMAP_SIZE
import com.example.durban.view.CropImageView.Companion.DEFAULT_MAX_SCALE_MULTIPLIER
import com.example.durban.view.CropImageView.Companion.SOURCE_IMAGE_ASPECT_RATIO
import com.example.durban.view.CropView
import com.example.durban.view.GestureCropImageView
import com.example.durban.view.OverlayView
import com.example.durban.view.TransformImageView

/**
 * 读写权限的校验改为进页面之前/剔除冗余的校验逻辑
 */
class DurbanActivity : AppCompatActivity() {
    private var mStatusColor = 0
    private var mNavigationColor = 0
    private var mToolbarColor = 0
    private var mTitle: String? = null
    private var mGesture = 0
    private var mAspectRatio: FloatArray? = null
    private var mMaxWidthHeight: IntArray? = null
    private var mCompressFormat: CompressFormat? = null
    private var mCompressQuality = 0
    private var mOutputDirectory: String? = null
    private var mInputPathList: ArrayList<String>? = null
    private var mController: Controller? = null
    private var mCropView: CropView? = null
    private var mCropImageView: GestureCropImageView? = null
    private var mOutputPathList: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locale = Durban.getDurbanConfig()?.getLocale()
        DurbanUtils.applyLanguageForContext(this, locale)
        setContentView(R.layout.durban_activity_photobox)
        initArgument(intent)
        initFrameViews()
        initContentViews()
        initControllerViews()
        cropNextImage()
    }

    private fun initArgument(intent: Intent) {
        mStatusColor = ContextCompat.getColor(this, R.color.durban_ColorPrimaryDark)
        mToolbarColor = ContextCompat.getColor(this, R.color.durban_ColorPrimary)
        mNavigationColor = ContextCompat.getColor(this, R.color.durban_ColorPrimaryBlack)
        mStatusColor = intent.getIntExtra(Durban.KEY_INPUT_STATUS_COLOR, mStatusColor)
        mToolbarColor = intent.getIntExtra(Durban.KEY_INPUT_TOOLBAR_COLOR, mToolbarColor)
        mNavigationColor = intent.getIntExtra(Durban.KEY_INPUT_NAVIGATION_COLOR, mNavigationColor)
        mTitle = intent.getStringExtra(Durban.KEY_INPUT_TITLE)
        if (mTitle.isNullOrEmpty()) mTitle = getString(R.string.durban_title_crop)
        mGesture = intent.getIntExtra(Durban.KEY_INPUT_GESTURE, Durban.GESTURE_ALL)
        mAspectRatio = intent.getFloatArrayExtra(Durban.KEY_INPUT_ASPECT_RATIO)
        if (mAspectRatio == null) mAspectRatio = floatArrayOf(0f, 0f)
        mMaxWidthHeight = intent.getIntArrayExtra(Durban.KEY_INPUT_MAX_WIDTH_HEIGHT)
        if (mMaxWidthHeight == null) mMaxWidthHeight = intArrayOf(500, 500)
        val compressFormat = intent.getIntExtra(Durban.KEY_INPUT_COMPRESS_FORMAT, 0)
        mCompressFormat = if (compressFormat == Durban.COMPRESS_PNG) CompressFormat.PNG else CompressFormat.JPEG
        mCompressQuality = intent.getIntExtra(Durban.KEY_INPUT_COMPRESS_QUALITY, 90)
        mOutputDirectory = intent.getStringExtra(Durban.KEY_INPUT_DIRECTORY)
        if (mOutputDirectory.isNullOrEmpty()) mOutputDirectory = filesDir.absolutePath
        mInputPathList = intent.getStringArrayListExtra(Durban.KEY_INPUT_PATH_ARRAY)
        mController = intent.getParcelableExtra(Durban.KEY_INPUT_CONTROLLER)
        if (mController == null) mController = Controller.newBuilder().build()
        mOutputPathList = java.util.ArrayList()
    }

    private fun initFrameViews() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = mStatusColor
        window.navigationBarColor = mNavigationColor
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(mToolbarColor)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = mTitle
    }

    private fun initContentViews() {
        mCropView = findViewById(R.id.crop_view)
        mCropImageView = mCropView?.getCropImageView()
        mCropImageView?.setOutputDirectory(mOutputDirectory)
        mCropImageView?.setTransformImageListener(mImageListener)
        mCropImageView?.setScaleEnabled(mGesture == Durban.GESTURE_ALL || mGesture == Durban.GESTURE_SCALE)
        mCropImageView?.setRotateEnabled(mGesture == Durban.GESTURE_ALL || mGesture == Durban.GESTURE_ROTATE)
        // Durban image view options
        mCropImageView?.setMaxBitmapSize(DEFAULT_MAX_BITMAP_SIZE)
        mCropImageView?.setMaxScaleMultiplier(DEFAULT_MAX_SCALE_MULTIPLIER)
        mCropImageView?.setImageToWrapCropBoundsAnimDuration(DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION.toLong())
        // Overlay view options
        val overlayView = mCropView?.getOverlayView()
        overlayView?.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_DISABLE)
        overlayView?.setDimmedColor(ContextCompat.getColor(this, R.color.durban_CropDimmed))
        overlayView?.setCircleDimmedLayer(false)
        overlayView?.setShowCropFrame(true)
        overlayView?.setCropFrameColor(ContextCompat.getColor(this, R.color.durban_CropFrameLine))
        overlayView?.setCropFrameStrokeWidth(resources.getDimensionPixelSize(R.dimen.durban_dp_1))
        overlayView?.setShowCropGrid(true)
        overlayView?.setCropGridRowCount(2)
        overlayView?.setCropGridColumnCount(2)
        overlayView?.setCropGridColor(ContextCompat.getColor(this, R.color.durban_CropGridLine))
        overlayView?.setCropGridStrokeWidth(resources.getDimensionPixelSize(R.dimen.durban_dp_1))
        // Aspect ratio options
        mAspectRatio?.let {
            if (it[0] > 0 && it[1] > 0) {
                mCropImageView?.setTargetAspectRatio(it[0] / it[1])
            } else {
                mCropImageView?.setTargetAspectRatio(SOURCE_IMAGE_ASPECT_RATIO)
            }
        }
        // Result exception max size options
        mMaxWidthHeight?.let {
            if (it[0] > 0 && it[1] > 0) {
                mCropImageView?.setMaxResultImageSizeX(it[0])
                mCropImageView?.setMaxResultImageSizeY(it[1])
            }
        }
    }

    private fun initControllerViews() {
        val controllerRoot = findViewById<LinearLayout>(R.id.iv_controller_root)
        val rotationTitle = findViewById<TextView>(R.id.tv_controller_title_rotation)
        val rotationLeft = findViewById<FrameLayout>(R.id.layout_controller_rotation_left)
        val rotationRight = findViewById<FrameLayout>(R.id.layout_controller_rotation_right)
        val scaleTitle = findViewById<TextView>(R.id.tv_controller_title_scale)
        val scaleBig = findViewById<FrameLayout>(R.id.layout_controller_scale_big)
        val scaleSmall = findViewById<FrameLayout>(R.id.layout_controller_scale_small)
        controllerRoot.visibility = if (mController?.enable == true) View.VISIBLE else View.GONE
        rotationTitle.visibility = if (mController?.rotationTitle == true) View.VISIBLE else View.INVISIBLE
        rotationLeft.visibility = if (mController?.rotation == true) View.VISIBLE else View.GONE
        rotationRight.visibility = if (mController?.rotation == true) View.VISIBLE else View.GONE
        scaleTitle.visibility = if (mController?.scaleTitle == true) View.VISIBLE else View.INVISIBLE
        scaleBig.visibility = if (mController?.scale == true) View.VISIBLE else View.GONE
        scaleSmall.visibility = if (mController?.scale == true) View.VISIBLE else View.GONE
        if (mController?.rotationTitle != true && mController?.scaleTitle != true) findViewById<LinearLayout>(R.id.layout_controller_title_root).visibility = View.GONE
        if (mController?.rotation != true) rotationTitle.visibility = View.GONE
        if (mController?.scale != true) scaleTitle.visibility = View.GONE
        rotationLeft.setOnClickListener(it)
        rotationRight.setOnClickListener(it)
        scaleBig.setOnClickListener(it)
        scaleSmall.setOnClickListener(it)
    }

    override fun onStop() {
        super.onStop()
        if (mCropImageView != null) mCropImageView?.cancelAllAnimations()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.durban_menu_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_action_ok) {
            cropAndSaveImage()
        } else if (item.itemId == android.R.id.home) {
            setResultFailure()
        }
        return true
    }

    private fun cropAndSaveImage() {
        mCompressFormat?.let {
            mCropImageView?.cropAndSaveImage(it, mCompressQuality, cropCallback)
        }
    }

    private val cropCallback = object : BitmapCropCallback {
        override fun onBitmapCropped(imagePath: String, imageWidth: Int, imageHeight: Int) {
            mOutputPathList?.add(imagePath)
            cropNextImage()
        }

        override fun onCropFailure(t: Throwable) {
            cropNextImage()
        }
    }

    private val it = View.OnClickListener { v ->
        val id = v?.id
        when (id) {
            R.id.layout_controller_rotation_left -> {
                mCropImageView?.postRotate(-90f)
                mCropImageView?.setImageToWrapCropBounds()
            }
            R.id.layout_controller_rotation_right -> {
                mCropImageView?.postRotate(90f)
                mCropImageView?.setImageToWrapCropBounds()
            }
            R.id.layout_controller_scale_big -> {
                mCropImageView?.let {
                    it.zoomOutImage(it.getCurrentScale() + ((it.getMaxScale() - it.getMinScale()) / 10))
                    it.setImageToWrapCropBounds()
                }
            }
            R.id.layout_controller_scale_small -> {
                mCropImageView?.let {
                    it.zoomInImage(it.getCurrentScale() - ((it.getMaxScale() - it.getMinScale()) / 10))
                    it.setImageToWrapCropBounds()
                }
            }
        }
    }

    private val mImageListener = object : TransformImageView.TransformImageListener {
        override fun onLoadComplete() {
            mCropView?.animate()
                ?.alpha(1f)
                ?.setDuration(300)
                ?.setInterpolator(AccelerateInterpolator())
                ?.start()
        }

        override fun onLoadFailure() {
            cropNextImage()
        }

        override fun onRotate(currentAngle: Float) {
        }

        override fun onScale(currentScale: Float) {
        }
    }

    /**
     * Start cropping and request permission if there is no permission.
     */
    private fun cropNextImage() {
        resetRotation()
        processNextImageForCrop()
    }

    /**
     * Restore the rotation angle.
     */
    private fun resetRotation() {
        mCropImageView?.postRotate(-(mCropImageView?.getCurrentAngle() ?: 0f))
        mCropImageView?.setImageToWrapCropBounds()
    }

    private fun processNextImageForCrop() {
        if (mInputPathList != null) {
            if ((mInputPathList?.size ?: 0) > 0) {
                val currentPath = mInputPathList?.removeAt(0)
                try {
                    mCropImageView?.setImagePath(currentPath.orEmpty())
                } catch (e: Exception) {
                    cropNextImage()
                }
            } else {
                if ((mOutputPathList?.size ?: 0) > 0) {
                    setResultSuccessful()
                } else {
                    setResultFailure()
                }
            }
        } else {
            Log.e("Durban", "The file list is empty.")
            setResultFailure()
        }
    }

    private fun setResultSuccessful() {
        val intent = Intent()
        intent.putStringArrayListExtra(Durban.KEY_OUTPUT_IMAGE_LIST, mOutputPathList)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun setResultFailure() {
        val intent = Intent()
        intent.putStringArrayListExtra(Durban.KEY_OUTPUT_IMAGE_LIST, mOutputPathList)
        setResult(RESULT_CANCELED, intent)
        finish()
    }

}
