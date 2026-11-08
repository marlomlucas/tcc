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

    if (permissaoConcedida) {

        val previewView = remember(context) {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { previewView }
        )

        DisposableEffect(previewView, lifecycleOwner) {
            // O listener e o descarte desta sessão executam na thread principal.
            var ativa = true
            var cameraProvider: ProcessCameraProvider? = null
            val cameraExecutor = Executors.newSingleThreadExecutor()
            val barcodeAnalyzer = BarcodeAnalyzer { codigo ->
                if (ativa) {
                    callbackAtual.value(codigo)
                }
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(cameraExecutor, barcodeAnalyzer)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                // Uma inicialização antiga não pode vincular outra sessão.
                if (ativa) {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Erro ao iniciar câmera", e)
                    }
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                ativa = false
                try {
                    imageAnalysis.clearAnalyzer()
                    cameraProvider?.unbind(preview, imageAnalysis)
                } finally {
                    try {
                        barcodeAnalyzer.fechar()
                    } finally {
                        cameraExecutor.shutdown()
                    }
                }
            }
        }

    } else {

        Text(
            text = "Permissão da câmera não concedida."
        )
    }
}
