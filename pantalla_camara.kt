package com.example.cameraxapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
//import com.example.cameraxapp.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.cameraxapp.databinding.ActivityPantallaCamaraBinding
import kotlin.text.append
import kotlin.text.format

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import android.graphics.Bitmap
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.Context
import android.widget.TextView
//import androidx.compose.ui.semantics.text
import org.opencv.core.CvType
import kotlin.text.toDouble

class pantalla_camara : AppCompatActivity() {

    //Declaracion de variables como propiedad de la clase MainActivity
    private lateinit var binding: ActivityPantallaCamaraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    // Variables para el archivo CSV
    private var csvFile: java.io.File? = null
    private var fileWriter: java.io.FileWriter? = null
    private var isRecording = false
    private var recordingStartTimeMillis: Long = 0L

    companion object {
        private const val TAG = "pantalla_camara_cv"
    }

    private var referenceCenterX: Double? = null
    private var referenceCenterY: Double? = null

    private lateinit var lowerHsv: Scalar
    private lateinit var upperHsv: Scalar

    //FUNCIONES

    // Función que verifica si todos los permisos que necesita la app están concedidos, devuelve
    // true si todos los permisos de la lista REQUIRED_PERMISSION están concedidos
    private fun allPermissionGranted(): Boolean = REQUIRED_PERMISSION.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Función que inicia la cámara y la vista previa
    private fun startCamera() {
        Log.d(TAG, "startCamera() called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        Log.d(TAG, "ProcessCameraProvider.getInstance() called")
        cameraProviderFuture.addListener({
            Log.d(TAG, "cameraProviderFuture listener triggered")

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            Log.d(TAG, "CameraProvider obtained")
            val preview = Preview.Builder() //para la vista previa de la cámara
                .build()
                .also { mPreview ->
                    Log.d(TAG, "Preview built")
                    mPreview.setSurfaceProvider(
                        binding.viewFinder.surfaceProvider
                    )
                    Log.d(TAG, "SurfaceProvider set for viewFinder")

                }

            val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                it.setAnalyzer(cameraExecutor, ObjectDetectionAnalyzer())
                }
            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA // selección de cámara trasera
            Log.d(TAG, "CameraSelector set to back camera")

            try {
                cameraProvider.unbindAll() //desvincula la cámara de la vista previa
                Log.d(TAG, "cameraProvider.unbindAll() called")
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                )
                Log.d(TAG, "cameraProvider.bindToLifecycle() called successfully")

            } catch (e: Exception) {
                Log.d(TAG, "No se pudo acceder a la cámara por este error: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Función que inicia el archivo CSV
    private fun startCsvRecording() {
        if (csvFile != null && fileWriter != null) {
            Log.w(TAG, "CSV recording already in progress. Stopping previous one.")
            stopCsvRecording() // si hay un anterior, lo detiene
        }
        try {
            recordingStartTimeMillis = System.currentTimeMillis()
            val currentTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()).format(Date())
            val fileName = "recording_$currentTime.csv" // el archivo tiene como nombre la fecha y hora en la que se inició
            csvFile = File(filesDir, fileName)

            fileWriter = FileWriter(csvFile)

            // Se añade la primera línea con los nombres de las columnas para después guardar los datos
            fileWriter?.append("Time (s),Position X (px),Position Y (px)\n")
            Log.i(TAG, "CSV file created: ${csvFile?.absolutePath}")
            Toast.makeText(this, "Ha comenzado la grabación: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error creating CSV file: ", e)
            Toast.makeText(this, "Error al iniciar la grabación", Toast.LENGTH_SHORT).show()
            csvFile = null
            fileWriter = null
            isRecording = false
        }
    }

    // Función que para el archivo CSV
    private fun stopCsvRecording() {
        try {
            fileWriter?.flush() //Asegura que todos los datos se escriban antes de finalizar
            fileWriter?.close()
            Log.i(TAG, "CSV file saved and closed: ${csvFile?.absolutePath}")
            if (csvFile != null) {
                Toast.makeText(this, "La grabación ha finalizado: ${csvFile?.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error closing CSV file: ", e)
            Toast.makeText(this, "Error al finalizar la grabación", Toast.LENGTH_SHORT).show()
        }

        fileWriter = null
        recordingStartTimeMillis = 0L
    }

    //Función que muestra la imagen procesada, convierte la imagen procesada por OpenCV en formato
    // Mat a una imagen en formato Bitmap, el necesario para mostrar imágenes en Android
    private fun displayProcessedImage(processedMat: Mat) {
        if (processedMat.empty() || processedMat.cols() == 0 || processedMat.rows() == 0) {

            Log.e(TAG, "Processed Mat is empty, cannot display.")
            runOnUiThread { binding.processedImageView.setImageDrawable(null) }
            return
        }

        val bitmap: Bitmap = try {
            Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(processedMat, it)
            }
        }catch (e: Exception) {
            Log.e(TAG, "Error converting Mat to Bitmap: ${e.message}")
            e.printStackTrace()
            runOnUiThread { binding.processedImageView.setImageDrawable(null) }
            return
        }

        runOnUiThread {
            binding.processedImageView.setImageBitmap(bitmap)
        }
    }

    // Función que carga la configuración HSV desde las SharedPreferences
    private fun loadHsvConfiguration() {
        val sharedPreferences = getSharedPreferences(Constants.HSV_CONFIG, Context.MODE_PRIVATE)

        //Valores por defecto por si no se cambia la configuracion
        val defaultLH = 35.0f
        val defaultLS = 50.0f
        val defaultLV = 50.0f
        val defaultUH = 85.0f
        val defaultUS = 255.0f
        val defaultUV = 255.0f

        // Se obtienen los valores de las SharedPreferences
        val lHFloat = sharedPreferences.getFloat(Constants.KEY_LOWER_H, defaultLH)
        val lH = lHFloat.toDouble()

        val lSFloat = sharedPreferences.getFloat(Constants.KEY_LOWER_S, defaultLS)
        val lS = lSFloat.toDouble()

        val lVFloat = sharedPreferences.getFloat(Constants.KEY_LOWER_V, defaultLV)
        val lV = lVFloat.toDouble()

        val uHFloat = sharedPreferences.getFloat(Constants.KEY_UPPER_H, defaultUH)
        val uH = uHFloat.toDouble()

        val uSFloat = sharedPreferences.getFloat(Constants.KEY_UPPER_S, defaultUS)
        val uS = uSFloat.toDouble()

        val uVFloat = sharedPreferences.getFloat(Constants.KEY_UPPER_V, defaultUV)
        val uV = uVFloat.toDouble()

        lowerHsv = Scalar(lH, lS, lV)
        upperHsv = Scalar(uH, uS, uV)

        Log.i(TAG, "Loaded HSV configuration: Lower=$lowerHsv, Upper=$upperHsv")

    }

    // Función que escribe en el archivo CSV
    fun writeDataToCsv(timeSec: String, posX: String, posY: String) {
        if (!isRecording || fileWriter == null) {
            return
        }
        try {
            fileWriter?.append("$timeSec,$posX,$posY\n")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to CSV file: ", e)
        }
    }

    private inner class ObjectDetectionAnalyzer : ImageAnalysis.Analyzer {
        private var lastProcessedTimestamp = 0L

        override fun analyze(image: ImageProxy) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedTimestamp < 33) { // dice la frecuencia en ms en la que se procesa una imagen de la siguiente
                // 100 ms procesa 10 veces por segundo (FPS): 1000 ms / 100 ms = 10 FPS

                 // intentar hacerlo con 30 fps minimo
                image.close()
                return
            }
            lastProcessedTimestamp = currentTime

            var matImage = imageProxyToMat(image)
            if (matImage == null || matImage.empty()) {
                image.close()
                return
            }

            val rotationDegrees = image.imageInfo.rotationDegrees


            if (rotationDegrees != 0) {
                val tempMat = Mat()
                var rotatedMat = Mat()

                when (rotationDegrees) {
                    90 -> Core.rotate(matImage, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                    180 -> Core.rotate(matImage, rotatedMat, Core.ROTATE_180)
                    270 -> Core.rotate(matImage, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                    else -> rotatedMat = matImage
                }

                if (rotatedMat.dataAddr() != matImage.dataAddr()) {
                    matImage.release()
                    matImage = rotatedMat
                } else if (rotationDegrees != 0) {
                 }
            }

            val hsvMat = Mat()
            Imgproc.cvtColor(matImage, hsvMat, Imgproc.COLOR_RGB2HSV) // conversión a formato HSV

            val mask = Mat()
            Core.inRange(hsvMat, lowerHsv, upperHsv, mask) //aplica la máscara en un color específico

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.erode(mask, mask, kernel)
            Imgproc.dilate(mask, mask, kernel)

            val displayableMask = Mat()
            Imgproc.cvtColor(mask, displayableMask, Imgproc.COLOR_GRAY2RGB)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE) //selecciona el objeto más grande localizado

            var largestContour: MatOfPoint? = null
            var maxArea = 0.0
            var boundingBox: org.opencv.core.Rect? = null
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                Log.d(TAG, "Contour area: $area")
                if (area > maxArea) {
                    maxArea = area
                    largestContour = contour
                    boundingBox = Imgproc.boundingRect(contour) //para que se vea un rectangulo alrededor del objeto
                }
            }
            Log.d(TAG, "MAX AREA FOUND: $maxArea")
            var relXText = "Rel X: N/A"
            var relYText = "Rel Y: N/A"

            // si ha encontrado un objeto
            if (largestContour != null && boundingBox != null && maxArea > 50) {
                val moments = Imgproc.moments(largestContour)
                if (moments.m00 > 0) {
                    //calcula el centro del objeto
                    val centerX = (moments.m10 / moments.m00) //m00 es el área del contorno
                    val centerY = (moments.m01 / moments.m00)

                    Imgproc.rectangle(displayableMask, boundingBox.tl(), boundingBox.br(), Scalar(0.0, 255.0, 0.0), 2)
                    Imgproc.circle(displayableMask, Point(centerX, centerY), 5, Scalar(255.0, 0.0, 0.0), -1) //centro con círculo rojo

                    if (isRecording) {
                        if (referenceCenterX == null || referenceCenterY == null) {
                            referenceCenterX = centerX
                            referenceCenterY = centerY
                            Log.i(
                                TAG,
                                "Reference center set: ($referenceCenterX, $referenceCenterY)"
                            )
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "Centro de referencia fijado",
                                    Toast.LENGTH_SHORT
                                ).show()
                                binding.textViewRelativeX.text = "Rel X: 0.00"
                                binding.textViewRelativeY.text = "Rel Y: 0.00"
                            }
                        }
                        if (referenceCenterX != null && referenceCenterY != null) {
                            Imgproc.circle(
                                displayableMask,
                                Point(referenceCenterX!!, referenceCenterY!!),
                                7,
                                Scalar(0.0, 0.0, 255.0),
                                2
                            ) //se dibuja un círculo azul de referencia (posición central inicial)
                            val relativeX = centerX - referenceCenterX!! //diferencia entre el centro del objeto y el centro de referencia
                            val relativeY = centerY - referenceCenterY!!
                            relXText = String.format(Locale.US, "Rel X: %.2f", relativeX)
                            relYText = String.format(
                                Locale.US,
                                "Rel Y: %.2f",
                                relativeY
                            )

                            val elapsedTimeSeconds =
                                (System.currentTimeMillis() - recordingStartTimeMillis) / 1000.0
                            writeDataToCsv(
                                String.format(Locale.US, "%.3f", elapsedTimeSeconds),
                                String.format(Locale.US, "%.2f", relativeX),
                                String.format(Locale.US, "%.2f", relativeY)
                            )
                        } else {
                            relXText = "Rel X: (Not Rec)"
                            relYText = "Rel Y: (Not Rec)"
                        }
                    }

                }
            }

            else {
                if (isRecording && referenceCenterX != null && referenceCenterY != null) {
                    Imgproc.circle(
                        displayableMask,
                        Point(referenceCenterX!!, referenceCenterY!!),
                        7,
                        Scalar(0.0, 0.0, 255.0),3)
                    relXText = "Rel X: Lost"
                    relYText = "Rel Y: Lost"
                }
            }
            runOnUiThread {
                binding.textViewRelativeX.text = relXText
                binding.textViewRelativeY.text = relYText
            }

            displayProcessedImage(displayableMask)

            matImage.release()
            hsvMat.release()
            mask.release()
            if (displayableMask.dataAddr() != mask.dataAddr() && displayableMask.dataAddr() != matImage.dataAddr()) {
                displayableMask.release()
            }
            hierarchy.release()
            contours.forEach { it.release() }
            image.close()
        }

        // Función que convierte una imagen de tipo ImageProxy a un objeto Mat
        private fun imageProxyToMat(image: ImageProxy): Mat? {
            if (image.format != android.graphics.ImageFormat.YUV_420_888) {
                Log.e(TAG, "Unsupported image format: ${image.format}")
                return null
            }

            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvMat = Mat(image.height + image.height / 2, image.width, org.opencv.core.CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21)

            val rgbMat = Mat()
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21, 3)

            yuvMat.release()
            return rgbMat
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadHsvConfiguration()

        //Inicializa OpenCV
        val TAGcv = "OpenCVInit"
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAGcv, "Error al cargar OpenCV")
        } else {
            Log.i(TAGcv, "OpenCV cargó exitosamente")
        }
        Log.d(TAG, "OpenCV initialization finished")

        enableEdgeToEdge()
        binding = ActivityPantallaCamaraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "Content view set")
//        setContentView(R.layout.activity_pantalla_camara)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val btn: Button = findViewById(R.id.button)
        val recordSwitch: android.widget.Switch = findViewById(R.id.switch1)

        btn.setOnClickListener {
            Log.d(TAG, "Stop recording button found")
            if (isRecording){
                stopCsvRecording()
                //onDestroy()
            }
            finish()
        }

        binding.switch1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Switch ON
                isRecording = true
                startCsvRecording()
                Log.d(TAG, "Guardando datos en archivo CSV")
                runOnUiThread {
                    binding.textViewRelativeX.text = "Rel X: --"
                    binding.textViewRelativeY.text = "Rel Y: --"
                }
            } else {
                // Switch OFF
                isRecording = false
                stopCsvRecording()
                referenceCenterX = null
                referenceCenterY = null
                Log.d(TAG, "Archivo CSV guardado correctamente")
                runOnUiThread {
                    binding.textViewRelativeX.text = "Rel X: N/A"
                    binding.textViewRelativeY.text = "Rel Y: N/A"
                }
            }
        }

        if (allPermissionGranted()) {
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSION,
                REQUEST_CODE_PERMISSIONS
            )
        }
        runOnUiThread {
            binding.textViewRelativeX.text = "Rel X: N/A"
            binding.textViewRelativeY.text = "Rel Y: N/A"
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            this.cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called. Releasing camera resources.")
        if (isRecording) {
            stopCsvRecording()
        }
        cameraExecutor?.shutdown()
        cameraProvider?.unbindAll()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso no concedido por el usuario", Toast.LENGTH_SHORT)
                    .show()
                finish() //si el usuario no da permiso se cierra la app
            }
        }
    }
}





