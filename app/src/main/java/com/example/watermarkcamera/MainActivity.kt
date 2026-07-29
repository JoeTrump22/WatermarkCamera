package com.example.watermarkcamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.watermarkcamera.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var locationHelper: LocationHelper
    private lateinit var weatherHelper: WeatherHelper

    private var watermarkData = WatermarkData()
    private var isFrontCamera = false
    private var lastLocation: android.location.Location? = null

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            watermarkData.timeText = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.CHINA
            ).format(Date())
            binding.watermarkTime.text = watermarkData.timeText
            timeHandler.postDelayed(this, 1000)
        }
    }

    data class WatermarkData(
        var timeText: String = "",
        var locationText: String = "",
        var weatherText: String = ""
    )

    companion object {
        private const val TAG = "WatermarkCamera"
        private const val REQUEST_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationHelper = LocationHelper(this)
        weatherHelper = WeatherHelper()

        if (allPermissionsGranted()) {
            startCamera()
            startRealTimeClock()
            updateWatermarkInfo()
        } else {
            requestPermissions()
        }

        setupClickListeners()
    }

    private fun startRealTimeClock() {
        timeHandler.post(timeRunnable)
    }

    private fun stopRealTimeClock() {
        timeHandler.removeCallbacks(timeRunnable)
    }

    private fun setupClickListeners() {
        binding.imageCaptureButton.setOnClickListener {
            takePhoto()
        }

        binding.switchCameraButton.setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }

        binding.refreshButton.setOnClickListener {
            updateWatermarkInfo()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "相机绑定失败", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = java.io.File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                            if (bitmap != null) {
                                val watermarkedBitmap =
                                    WatermarkUtils.createWatermarkBitmap(
                                        bitmap,
                                        watermarkData.timeText,
                                        watermarkData.locationText,
                                        watermarkData.weatherText
                                    )

                                    val saved = saveToGallery(watermarkedBitmap)
                                    if (saved) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "照片已保存到相册",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    photoFile.delete()
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "保存照片失败", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "保存失败: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exception)
                    Toast.makeText(
                        this@MainActivity,
                        "拍照失败: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private suspend fun saveToGallery(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val fileName = "Watermark_${timeStamp}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/WatermarkCamera"
                    )
                }

                val contentResolver = contentResolver
                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    val outputStream: OutputStream? = contentResolver.openOutputStream(it)
                    outputStream?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                    }
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(
                    "${Environment.DIRECTORY_PICTURES}/WatermarkCamera"
                )
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val imageFile = java.io.File(imagesDir, fileName)
                java.io.FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存到相册失败", e)
            false
        }
    }

    private fun updateWatermarkInfo() {
        watermarkData.timeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.CHINA
        ).format(Date())
        binding.watermarkTime.text = watermarkData.timeText

        CoroutineScope(Dispatchers.IO).launch {
            var location: android.location.Location? = null
            var locationName: String? = null

            try {
                location = locationHelper.getCurrentLocation()
                if (location != null) {
                    lastLocation = location
                    locationName = locationHelper.getLocationName(location)
                    watermarkData.locationText = locationName ?: "未知位置"
                } else {
                    watermarkData.locationText = "位置获取失败，请开启定位"
                }
            } catch (e: Exception) {
                Log.e(TAG, "定位失败", e)
                watermarkData.locationText = "位置获取失败"
            }

            withContext(Dispatchers.Main) {
                binding.watermarkLocation.text = watermarkData.locationText
            }

            val weatherLocation = location ?: lastLocation
            if (weatherLocation != null) {
                try {
                    val weather = weatherHelper.getWeatherByOpenMeteo(
                        weatherLocation.latitude,
                        weatherLocation.longitude
                    )
                    watermarkData.weatherText = weather ?: "天气获取失败"
                } catch (e: Exception) {
                    Log.e(TAG, "天气获取失败", e)
                    watermarkData.weatherText = "天气获取失败"
                }
            } else {
                watermarkData.weatherText = "天气获取失败，请先获取位置"
            }

            withContext(Dispatchers.Main) {
                binding.watermarkWeather.text = watermarkData.weatherText
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            REQUEST_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                startRealTimeClock()
                updateWatermarkInfo()
            } else {
                Toast.makeText(this, "需要授予所有权限才能使用此功能", Toast.LENGTH_LONG).show()
                if (!allPermissionsGranted()) {
                    requestPermissions()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRealTimeClock()
        cameraExecutor.shutdown()
    }
}