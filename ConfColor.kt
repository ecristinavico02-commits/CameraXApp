package com.example.cameraxapp

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Button
import android.content.Context

class ConfColor : AppCompatActivity() {


    private lateinit var previewView: PreviewView
    private lateinit var maskImageView: ImageView

    private lateinit var tvHueLowValue: TextView
    private lateinit var tvSaturationLowValue: TextView
    private lateinit var tvValueLowValue: TextView
    private lateinit var tvHueHighValue: TextView
    private lateinit var tvSaturationHighValue: TextView
    private lateinit var tvValueHighValue: TextView

    private lateinit var seekBarHueLow: SeekBar
    private lateinit var seekBarSaturationLow: SeekBar
    private lateinit var seekBarValueLow: SeekBar
    private lateinit var seekBarHueHigh: SeekBar
    private lateinit var seekBarSaturationHigh: SeekBar
    private lateinit var seekBarValueHigh: SeekBar

    private lateinit var buttonSaveConfig: Button

    private lateinit var cameraExecutor: ExecutorService

    private var lowerHsv = Scalar(0.0, 0.0, 0.0)
    private var upperHsv = Scalar(180.0, 255.0, 255.0)

    companion object {
        private const val TAG = "ConfColor"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContentView(R.layout.activity_conf_color)

        loadHsvValues()

        previewView = findViewById(R.id.previewViewConf)
        maskImageView = findViewById(R.id.maskImageView)

        initializeViews()

        // Se inicia OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
            finish()
            return
        } else {
            Log.d(TAG, "OpenCV initialized successfully!")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupSeekBars()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        buttonSaveConfig.setOnClickListener {
            saveHsvValues()
            Log.d(TAG, "Configuration Saved")
            Toast.makeText(this, "Configuration Saved (Placeholder)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        tvHueLowValue = findViewById(R.id.tvHueLowValue)
        seekBarHueLow = findViewById(R.id.seekBarHueLow)

        tvSaturationLowValue = findViewById(R.id.tvSaturationLowValue)
        seekBarSaturationLow = findViewById(R.id.seekBarSaturationLow)

        tvValueLowValue = findViewById(R.id.tvValueLowValue)
        seekBarValueLow = findViewById(R.id.seekBarValueLow)

        tvHueHighValue = findViewById(R.id.tvHueHighValue)
        seekBarHueHigh = findViewById(R.id.seekBarHueHigh)

        tvSaturationHighValue = findViewById(R.id.tvSaturationHighValue)
        seekBarSaturationHigh = findViewById(R.id.seekBarSaturationHigh)

        tvValueHighValue = findViewById(R.id.tvValueHighValue)
        seekBarValueHigh = findViewById(R.id.seekBarValueHigh)

        buttonSaveConfig = findViewById(R.id.buttonSaveConfig)
    }

    private fun setupSeekBars() {
        // Valores inferiores
        // Hue Low
        seekBarHueLow.max = 180
        seekBarHueLow.progress = lowerHsv.`val`[0].toInt()
        tvHueLowValue.text = lowerHsv.`val`[0].toInt().toString()
        seekBarHueLow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvHueLowValue.text = progress.toString()
                    lowerHsv.`val`[0] = progress.toDouble()
                    Log.d(TAG, "Hue Low changed to: ${lowerHsv.`val`[0]}")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        seekBarSaturationLow.max = 255
        seekBarSaturationLow.progress = lowerHsv.`val`[1].toInt()
        tvSaturationLowValue.text = lowerHsv.`val`[1].toInt().toString()
        seekBarSaturationLow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvSaturationLowValue.text = progress.toString()
                    lowerHsv.`val`[1] = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        seekBarValueLow.max = 255
        seekBarValueLow.progress = lowerHsv.`val`[2].toInt()
        tvValueLowValue.text = lowerHsv.`val`[2].toInt().toString()
        seekBarValueLow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvValueLowValue.text = progress.toString()
                    lowerHsv.`val`[2] = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Valores superiores

        seekBarHueHigh.max = 180
        seekBarHueHigh.progress = upperHsv.`val`[0].toInt()
        tvHueHighValue.text = upperHsv.`val`[0].toInt().toString()
        seekBarHueHigh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvHueHighValue.text = progress.toString()
                    upperHsv.`val`[0] = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        seekBarSaturationHigh.max = 255
        seekBarSaturationHigh.progress = upperHsv.`val`[1].toInt()
        tvSaturationHighValue.text = upperHsv.`val`[1].toInt().toString()
        seekBarSaturationHigh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvSaturationHighValue.text = progress.toString()
                    upperHsv.`val`[1] = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        seekBarValueHigh.max = 255
        seekBarValueHigh.progress = upperHsv.`val`[2].toInt()
        tvValueHighValue.text = upperHsv.`val`[2].toInt().toString()
        seekBarValueHigh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvValueHighValue.text = progress.toString()
                    upperHsv.`val`[2] = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS){
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso no concedido por el usuario", Toast.LENGTH_SHORT ).show()
                finish()
            }
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }
            val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MaskGeneratorAnalyzer())
                }
            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA //SELECCIONAMOS CAMARA TRASERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner = this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo acceder a la cámara por este error: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class MaskGeneratorAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            //val yuvBytes = ByteArray(image.width * image.height * 3 / 2)
            val yPlane = image.planes[0].buffer
            val uPlane = image.planes[1].buffer
            val vPlane = image.planes[2].buffer
            val ySize = yPlane.remaining()
            val uSize = uPlane.remaining()
            val vSize = vPlane.remaining()

            val yuvBytes = ByteArray(ySize + uSize + vSize)

            yPlane.get(yuvBytes, 0, ySize)
            vPlane.get(yuvBytes, ySize, vSize)
            uPlane.get(yuvBytes, ySize + vSize, uSize)

            val yuvMat = Mat(image.height + image.height / 2, image.width, org.opencv.core.CvType.CV_8UC1)
            yuvMat.put(0, 0, yuvBytes)
            val rgbMat = Mat()
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21, 3)

            val rotatedRgbMat = Mat()
            val rotationDegrees = image.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                if (rotationDegrees == 90) Core.rotate(rgbMat, rotatedRgbMat, Core.ROTATE_90_CLOCKWISE)
                else if (rotationDegrees == 270) Core.rotate(rgbMat, rotatedRgbMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                else if (rotationDegrees == 180) Core.rotate(rgbMat, rotatedRgbMat, Core.ROTATE_180)
                else rgbMat.copyTo(rotatedRgbMat)
            } else {
                rgbMat.copyTo(rotatedRgbMat)
            }

            val hsvMat = Mat()
            Imgproc.cvtColor(rotatedRgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)
            val mask = Mat()
            Core.inRange(hsvMat, lowerHsv, upperHsv, mask)


            val displayMat = Mat()
            mask.copyTo(displayMat)

            val maskBitmap = Bitmap.createBitmap(displayMat.cols(), displayMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(displayMat, maskBitmap)

            runOnUiThread {
                maskImageView.setImageBitmap(maskBitmap)
            }

            yuvMat.release()
            rgbMat.release()
            rotatedRgbMat.release()
            hsvMat.release()
            mask.release()
            displayMat.release()
            image.close()
        }
    }

    private fun saveHsvValues() {
        Log.d(TAG, "saveHsvValues() called.")
        val sharedPreferences = getSharedPreferences(Constants.HSV_CONFIG, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        Log.d(TAG, "Attempting to save Lower H: ${lowerHsv.`val`[0].toFloat()}")
        editor.putFloat(Constants.KEY_LOWER_H, lowerHsv.`val`[0].toFloat())
        Log.d(TAG, "Attempting to save Lower S: ${lowerHsv.`val`[1].toFloat()}")
        editor.putFloat(Constants.KEY_LOWER_S, lowerHsv.`val`[1].toFloat())
        Log.d(TAG, "Attempting to save Lower V: ${lowerHsv.`val`[2].toFloat()}")
        editor.putFloat(Constants.KEY_LOWER_V, lowerHsv.`val`[2].toFloat())

        Log.d(TAG, "Attempting to save Upper H: ${upperHsv.`val`[0].toFloat()}")
        editor.putFloat(Constants.KEY_UPPER_H, upperHsv.`val`[0].toFloat())
        Log.d(TAG, "Attempting to save Upper S: ${upperHsv.`val`[1].toFloat()}")
        editor.putFloat(Constants.KEY_UPPER_S, upperHsv.`val`[1].toFloat())
        Log.d(TAG, "Attempting to save Upper V: ${upperHsv.`val`[2].toFloat()}")
        editor.putFloat(Constants.KEY_UPPER_V, upperHsv.`val`[2].toFloat())

        editor.apply()

        Log.i(TAG, "HSV configuration save attempted. Current values in memory: Lower=$lowerHsv, Upper=$upperHsv")
        Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadHsvValues() {
        val sharedPreferences = getSharedPreferences(Constants.HSV_CONFIG, Context.MODE_PRIVATE)
        val lH = sharedPreferences.getFloat(Constants.KEY_LOWER_H, 0f).toDouble()
        val lS = sharedPreferences.getFloat(Constants.KEY_LOWER_S, 0f).toDouble()
        val lV = sharedPreferences.getFloat(Constants.KEY_LOWER_V, 0f).toDouble()

        val uH = sharedPreferences.getFloat(Constants.KEY_UPPER_H, 180f).toDouble()
        val uS = sharedPreferences.getFloat(Constants.KEY_UPPER_S, 255f).toDouble()
        val uV = sharedPreferences.getFloat(Constants.KEY_UPPER_V, 255f).toDouble()

        lowerHsv = Scalar(lH, lS, lV)
        upperHsv = Scalar(uH, uS, uV)
        Log.d(TAG, "Loaded/Default HSV Lower: H=${lowerHsv.`val`[0]}, S=${lowerHsv.`val`[1]}, V=${lowerHsv.`val`[2]}")
        Log.d(TAG, "Loaded/Default HSV Upper: H=${upperHsv.`val`[0]}, S=${upperHsv.`val`[1]}, V=${upperHsv.`val`[2]}")
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}