package com.aeroscanner

import android.content.Context
import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import java.util.concurrent.Executors

class BoardingPassScanner(private val context: Context) {
    private var onResult: ((ScanData) -> Unit)? = null
    private var hasTriggered = false

    fun setCallback(callback: (ScanData) -> Unit) {
        onResult = callback
        hasTriggered = false
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun processImage(image: ImageProxy) {
        if (hasTriggered) {
            image.close()
            return
        }
        try {
            val mediaImage = image.image ?: run { image.close(); return }
            val inputImage = InputImage.fromMediaImage(
                mediaImage, image.imageInfo.rotationDegrees
            )
            val recognizer = TextRecognition.getClient()
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    val extractedCodes = RouteTools.extractIataCodes(rawText)
                    val knownAirportCodes = extractedCodes.filter { AirportDatabase.get(it) != null }
                    val iataCodes = knownAirportCodes.ifEmpty { extractedCodes }
                    val origin = iataCodes.firstOrNull()
                    val dest = if (iataCodes.size >= 2) iataCodes.last() else null

                    val priceRegex = Regex("""(?:RMB|CNY|¥|￥)\s*[\d,]+\.?\d*|[\d,]+\.?\d*\s*(?:RMB|CNY|¥|￥)""")
                    val priceMatch = priceRegex.find(rawText)
                    val price = priceMatch?.let {
                        val numStr = it.value
                            .replace(",", "")
                            .replace(Regex("""(?i)(RMB|CNY|¥|￥|\s)"""), "")
                        numStr.toDoubleOrNull()
                    }

                    if (iataCodes.size < 2 && price == null) {
                        return@addOnSuccessListener
                    }

                    hasTriggered = true
                    onResult?.invoke(
                        ScanData(
                            originCode = origin,
                            destinationCode = dest,
                            price = price,
                            rawText = rawText.take(500),
                        )
                    )
                }
                .addOnCompleteListener {
                    image.close()
                }
        } catch (e: Exception) {
            image.close()
        }
    }
}

@Composable
fun BoardingPassCamera(
    onResult: (ScanData) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BoardingPassScanner(context) }

    LaunchedEffect(Unit) {
        scanner.setCallback(onResult)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                                    scanner.processImage(image)
                                }
                            }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageAnalysis
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
        )

        // Scan guide overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.base),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Text(
                    "Point at boarding pass",
                    style = AeroscannerTypography.bodyLarge,
                    color = Color.White,
                )
                Spacer(Modifier.width(48.dp))
            }

            // Guide frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(Radius.md),
                        ),
                ) {
                    // Corner guides via bordered box
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.base),
                    ) {
                        // Visual target area indicator
                        Text(
                            text = "Align boarding pass text within frame",
                            style = AeroscannerTypography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            // Bottom hint
            Text(
                "Text will be scanned automatically",
                style = AeroscannerTypography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
            )
        }
    }
}
