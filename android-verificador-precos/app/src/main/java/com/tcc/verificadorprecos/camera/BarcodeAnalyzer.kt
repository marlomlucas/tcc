package com.tcc.verificadorprecos.camera

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer internal constructor(
    private val processar: (
        ImageProxy, (String?) -> Unit, (Exception) -> Unit, () -> Unit
    ) -> Unit,
    private val liberarScanner: () -> Unit,
    private val registrarErro: (Exception) -> Unit,
    private val onCodigoDetectado: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val lock = Any()
    private var fechado = false
    private var analisesPendentes = 0

    constructor(onCodigoDetectado: (String) -> Unit) : this(
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        ),
        onCodigoDetectado
    )

    @OptIn(ExperimentalGetImage::class)
    private constructor(
        scanner: BarcodeScanner,
        onCodigoDetectado: (String) -> Unit
    ) : this(
        processar = { proxy, sucesso, falha, concluido ->
            val mediaImage = proxy.image
            if (mediaImage == null) {
                concluido()
            } else {
                val imagem = InputImage.fromMediaImage(
                    mediaImage, proxy.imageInfo.rotationDegrees
                )
                scanner.process(imagem)
                    .addOnSuccessListener { sucesso(it.firstOrNull()?.rawValue) }
                    .addOnFailureListener { falha(it) }
                    .addOnCompleteListener { concluido() }
            }
        },
        liberarScanner = scanner::close,
        registrarErro = {
            Log.e("BarcodeAnalyzer", "Erro durante a leitura do código de barras.", it)
        },
        onCodigoDetectado = onCodigoDetectado
    )

    override fun analyze(imageProxy: ImageProxy) {
        val concluido = AtomicBoolean(false)

        fun finalizar() {
            if (!concluido.compareAndSet(false, true)) return
            try {
                imageProxy.close()
            } finally {
                val liberar = synchronized(lock) {
                    analisesPendentes--
                    fechado && analisesPendentes == 0
                }
                if (liberar) liberarScanner()
            }
        }

        synchronized(lock) {
            if (fechado) {
                imageProxy.close()
                return
            }
            analisesPendentes++
            try {
                // O lock cobre apenas o início, nunca a espera pela Task.
                processar(
                    imageProxy,
                    { codigo ->
                        synchronized(lock) {
                            if (!fechado && !concluido.get() && !codigo.isNullOrBlank()) {
                                onCodigoDetectado(codigo)
                            }
                        }
                    },
                    registrarErro,
                    ::finalizar
                )
            } catch (erro: Exception) {
                try {
                    registrarErro(erro)
                } finally {
                    finalizar()
                }
            }
        }
    }

    fun fechar() {
        val liberar = synchronized(lock) {
            if (fechado) return
            fechado = true
            analisesPendentes == 0
        }
        // Uma análise em andamento termina e fecha o proxy antes do scanner.
        if (liberar) liberarScanner()
    }
}
