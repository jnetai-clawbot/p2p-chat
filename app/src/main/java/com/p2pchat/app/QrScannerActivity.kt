package com.p2pchat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_RESULT = "qr_scan_result"
        const val EXTRA_ERROR = "qr_scan_error"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var scanned = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                ErrorLogger.w("QrScanner", "QRS001", "Camera permission denied")
                val intent = intent.apply {
                    putExtra(EXTRA_ERROR, "Camera permission denied")
                }
                setResult(RESULT_CANCELED, intent)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.widget.FrameLayout(this).apply {
            id = android.R.id.content
            setBackgroundColor(0xFF000000.toInt())
        })
        ErrorLogger.i("QrScanner", "Activity created")
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, R.string.camera_permission_rationale, Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!scanned) {
                                scanBarcode(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                ErrorLogger.i("QrScanner", "Camera started")
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            ErrorLogger.e("QrScanner", "QRS002", "Failed to start camera", e)
            setResult(RESULT_CANCELED, intent.apply {
                putExtra(EXTRA_ERROR, "Camera start failed: ${e.message}")
            })
            finish()
        }
    }

    private fun scanBarcode(imageProxy: androidx.camera.core.ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage, imageProxy.imageInfo.rotationDegrees
                )
                val scanner = BarcodeScanning.getClient()

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (!rawValue.isNullOrEmpty() && !scanned) {
                                scanned = true
                                ErrorLogger.i("QrScanner", "QR code scanned", mapOf(
                                    "type" to barcode.format.toString(),
                                    "value_length" to rawValue.length.toString()
                                ))
                                setResult(RESULT_OK, intent.apply {
                                    putExtra(EXTRA_RESULT, rawValue)
                                })
                                finish()
                                break
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        ErrorLogger.e("QrScanner", "QRS003", "Barcode scan failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } catch (e: Exception) {
            ErrorLogger.e("QrScanner", "QRS004", "Error during barcode analysis", e)
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ErrorLogger.i("QrScanner", "Activity destroyed")
    }
}
