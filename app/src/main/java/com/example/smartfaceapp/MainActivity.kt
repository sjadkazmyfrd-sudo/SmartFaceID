package com.example.smartfaceapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var edtNameInput: EditText
    private lateinit var btnRegister: Button
    private lateinit var cameraExecutor: ExecutorService

    private data class RegisteredFace(val name: String, val landmarks: Map<Int, android.graphics.PointF>)
    private val registeredFaces = mutableListOf<RegisteredFace>()
    private var captureNextFaceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        faceOverlay = findViewById(R.id.faceOverlay)
        edtNameInput = findViewById(R.id.edtNameInput)
        btnRegister = findViewById(R.id.btnRegister)
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnRegister.setOnClickListener {
            val name = edtNameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name first!", Toast.LENGTH_SHORT).show()
            } else if (registeredFaces.size >= 10) {
                Toast.makeText(this, "Maximum limit of 10 faces reached!", Toast.LENGTH_SHORT).show()
            } else {
                captureNextFaceName = name
                Toast.makeText(this, "Look at the camera to register '$name'", Toast.LENGTH_SHORT).show()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        private val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        private val detector = FaceDetection.getClient(options)

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val imageWidth = imageProxy.width.toFloat()
                val imageHeight = imageProxy.height.toFloat()

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        val detectedList = mutableListOf<DetectedFaceInfo>()

                        for (face in faces) {
                            val currentCaptureName = captureNextFaceName
                            if (currentCaptureName != null) {
                                val landmarkMap = extractLandmarks(face)
                                if (landmarkMap.isNotEmpty()) {
                                    registeredFaces.add(RegisteredFace(currentCaptureName, landmarkMap))
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Registered: $currentCaptureName", Toast.LENGTH_SHORT).show()
                                        edtNameInput.text.clear()
                                    }
                                    captureNextFaceName = null
                                }
                            }

                            val matchedName = identifyFace(face)
                            val box = scaleRect(face.boundingBox, imageWidth, imageHeight, viewFinder.width, viewFinder.height)
                            detectedList.add(DetectedFaceInfo(box, matchedName))
                        }

                        faceOverlay.setFaces(detectedList)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        private fun extractLandmarks(face: Face): Map<Int, android.graphics.PointF> {
            val map = mutableMapOf<Int, android.graphics.PointF>()
            val landmarkTypes = listOf(
                com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE,
                com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE,
                com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE,
                com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT,
                com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT
            )
            for (type in landmarkTypes) {
                face.getLandmark(type)?.let { landmark ->
                    val x = (landmark.position.x - face.boundingBox.left) / face.boundingBox.width()
                    val y = (landmark.position.y - face.boundingBox.top) / face.boundingBox.height()
                    map[type] = android.graphics.PointF(x, y)
                }
            }
            return map
        }

        private fun identifyFace(face: Face): String {
            if (registeredFaces.isEmpty()) return "Unknown"
            val currentLandmarks = extractLandmarks(face)
            if (currentLandmarks.isEmpty()) return "Unknown"

            var bestMatchName = "Unknown"
            var minDistance = Float.MAX_VALUE

            for (reg in registeredFaces) {
                var totalDiff = 0f
                var count = 0
                for ((type, pt) in reg.landmarks) {
                    val currPt = currentLandmarks[type]
                    if (currPt != null) {
                        val diff = kotlin.math.hypot((currPt.x - pt.x).toDouble(), (currPt.y - pt.y).toDouble()).toFloat()
                        totalDiff += diff
                        count++
                    }
                }
                if (count > 0) {
                    val avgDiff = totalDiff / count
                    if (avgDiff < minDistance) {
                        minDistance = avgDiff
                        if (avgDiff < 0.16f) { // Strict threshold for high precision
                            bestMatchName = reg.name
                        }
                    }
                }
            }
            return bestMatchName
        }

        private fun scaleRect(rect: android.graphics.Rect, imgW: Float, imgH: Float, viewW: Int, viewH: Int): RectF {
            val scaleX = viewW.toFloat() / imgH
            val scaleY = viewH.toFloat() / imgW
            val left = rect.top * scaleX
            val top = rect.left * scaleY
            val right = rect.bottom * scaleX
            val bottom = rect.right * scaleY
            return RectF(viewW - right, top, viewW - left, bottom)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
