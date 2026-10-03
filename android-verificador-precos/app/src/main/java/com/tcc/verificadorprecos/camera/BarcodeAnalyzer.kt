package com.tcc.verificadorprecos.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onCodigoDetectado: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val opcoes = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        )
        .build()

    private val scanner =
        BarcodeScanning.getClient(opcoes)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val imagem = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(imagem)
            .addOnSuccessListener { codigos ->

                val codigo = codigos
                    .firstOrNull()
                    ?.rawValue

                if (!codigo.isNullOrBlank()) {
                    onCodigoDetectado(codigo)
                }
            }
            .addOnFailureListener { erro ->

                Log.e(
                    "BarcodeAnalyzer",
                    "Erro durante a leitura do código de barras.",
                    erro
                )
            }
            .addOnCompleteListener {

                imageProxy.close()
            }
    }

    fun fechar() {
        scanner.close()
    }
}