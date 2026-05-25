package com.p2pchat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
    private lateinit var previewView: PreviewView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                ErrorLogger.w("QrScanner", "QRS001", "Camera permission denied")
                setResult(RESULT_CANCELED, intent.apply {
                    putExtra(EXTRA_ERROR, "Camera permission denied")
                })
                finish()
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { scanFromUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = FrameLayout(this).apply {
            id = android.R.id.content
            setBackgroundColor(0xFF000000.toInt())
        }
        
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        val galleryBtn = Button(this).apply {
            text = "Gallery"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 100)
            }
            setOnClickListener { galleryLauncher.launch("image/*") }
        }
        root.addView(galleryBtn)

        setContentView(root)
        ErrorLogger.i("QrScanner", "Activity created")
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                ErrorLogger.i("QrScanner", "Camera started")
            } catch (e: Exception) {
                ErrorLogger.e("QrScanner", "QRS002", "Failed to start camera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun scanBarcode(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrEmpty() && !scanned) {
                        scanned = true
                        onSuccess(rawValue)
                        break
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun scanFromUri(uri: android.net.Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, uri)
            val scanner = BarcodeScanning.getClient()
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        scanned = true
                        onSuccess(barcodes[0].rawValue ?: "")
                    } else {
                        Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    ErrorLogger.e("QrScanner", "QRS005", "Gallery scan failed", e)
                }
        } catch (e: Exception) {
            ErrorLogger.e("QrScanner", "QRS006", "Failed to process gallery image", e)
        }
    }

    private fun onSuccess(result: String) {
        ErrorLogger.i("QrScanner", "QR code scanned: $result")
        setResult(RESULT_OK, android.content.Intent().apply {
            putExtra(EXTRA_RESULT, result)
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
