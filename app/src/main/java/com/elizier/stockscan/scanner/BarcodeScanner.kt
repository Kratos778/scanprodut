package com.elizier.stockscan.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class BarcodeScannerHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onBarcode: (String) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var lastCode = ""
    private var lastTime = 0L

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            Log.e("BarcodeScanner", "Bind failed", e)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val raw = barcode.rawValue ?: continue
                    val now = System.currentTimeMillis()
                    if (raw == lastCode && now - lastTime < 800) continue
                    lastCode = raw
                    lastTime = now
                    onBarcode(raw)
                    break
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun toggleTorch(on: Boolean) {
        try {
            camera?.cameraControl?.enableTorch(on)
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        camera = null
    }

    fun release() {
        stop()
        try { scanner.close() } catch (_: Exception) {}
        try { executor.shutdown() } catch (_: Exception) {}
    }
}
