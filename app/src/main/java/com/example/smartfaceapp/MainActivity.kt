package com.example.smartfaceid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        faceOverlay = findViewById(R.id.faceOverlay)
        cameraExecutor = Executors.newSingleThreadExecutor()

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
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        private val detector = FaceDetection.getClient(options)

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val imgW = imageProxy.width.toFloat()
                val imgH = imageProxy.height.toFloat()

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        val detectedList = mutableListOf<DetectedFaceInfo>()
                        for (face in faces) {
                            val rect = scaleRect(face.boundingBox, imgW, imgH, viewFinder.width, viewFinder.height)
                            detectedList.add(DetectedFaceInfo(rect, "Face Detected"))
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
