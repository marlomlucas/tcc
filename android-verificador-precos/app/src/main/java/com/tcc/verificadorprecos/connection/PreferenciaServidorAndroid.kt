package com.tcc.verificadorprecos.connection

import android.content.Context
import androidx.core.content.edit

/** Guarda somente a URL manual validada; nunca credenciais. */
class PreferenciaServidorAndroid(context: Context) : PreferenciaServidor {
    private val contexto = context.applicationContext
    private val preferencias by lazy {
        contexto.getSharedPreferences("conexao_servidor", Context.MODE_PRIVATE)
    }

    override fun carregar(): String? = preferencias.getString("servidor_manual", null)

    override fun salvar(endereco: String?) {
        preferencias.edit {
            if (endereco == null) remove("servidor_manual")
            else putString("servidor_manual", endereco)
        }
    }
}
