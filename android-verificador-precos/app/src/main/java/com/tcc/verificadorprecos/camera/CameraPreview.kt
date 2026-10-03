package com.tcc.verificadorprecos.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onCodigoDetectado: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val callbackAtual =
        rememberUpdatedState(onCodigoDetectado)

    var permissaoConcedida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val solicitarPermissao =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { concedida ->

            permissaoConcedida = concedida
        }

    LaunchedEffect(Unit) {

        if (!permissaoConcedida) {

            solicitarPermissao.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    val barcodeAnalyzer = remember {

        BarcodeAnalyzer { codigo ->

            callbackAtual.value(codigo)
        }
    }

    DisposableEffect(Unit) {

        onDispose {

            barcodeAnalyzer.fechar()
            cameraExecutor.shutdown()
        }
    }

    if (permissaoConcedida) {

        AndroidView(
            modifier = modifier,

            factory = { ctx ->

                val previewView = PreviewView(ctx)

                previewView.scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    try {

                        val cameraProvider =
                            cameraProviderFuture.get()

                        val preview =
                            Preview.Builder()
                                .build()

                        preview.surfaceProvider =
                            previewView.surfaceProvider

                        val imageAnalysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        imageAnalysis.setAnalyzer(
                            cameraExecutor,
                            barcodeAnalyzer
                        )

                        val cameraSelector =
                            CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )

                    } catch (e: Exception) {

                        Log.e(
                            "CameraPreview",
                            "Erro ao iniciar câmera",
                            e
                        )
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

    } else {

        Text(
            text = "Permissão da câmera não concedida."
        )
    }
}