import os

from django.contrib import admin, messages
from django.core.exceptions import ValidationError
from .validacao_codigos import verificar_digito_verificador

from .forms import ProdutoAdminForm
from .imagens import (
    caminho_imagem_produto,
    salvar_imagem_produto,
    excluir_imagem_produto,
)

from .models import Produto
import logging
from django.db import transaction

logger = logging.getLogger(__name__)

@admin.register(Produto)
class ProdutoAdmin(admin.ModelAdmin):

    # Formulário personalizado para receber a imagem.
    form = ProdutoAdminForm
    
    # Desabilita ações em lote no Django Admin. "Trava de segurança."
    actions = None
    
    # Validação no navegador.
    class Media:
        js = ("produtos/js/validacao_produto.js",)
    
    list_display = (
        "nome",
        "codigo_barra",
        "preco",
        "data_atualizacao",
    )

    search_fields = ("nome", "codigo_barra")
    list_filter = ("data_atualizacao",)

    def save_model(self, request, obj, form, change):
        """
        Salva o produto e processa a imagem enviada.

        A imagem é armazenada em arquivo WebP,
        identificada pelo código de barras.
        """

        codigo_anterior = None

        # Identifica o código anterior durante uma edição.
        if change:
            codigo_anterior = (
                Produto.objects
                .filter(pk=obj.pk)
                .values_list("codigo_barra", flat=True)
                .first()
            )

        # Obtém a imagem enviada pelo formulário.
        nova_imagem = form.cleaned_data.get("imagem_upload")

        # Salva os dados do produto no banco.
        super().save_model(request, obj, form, change)

        try:
            # Processa a imagem, caso uma nova tenha sido enviada.
            if nova_imagem:
                salvar_imagem_produto(
                    obj.codigo_barra,
                    nova_imagem
                )

            # Verifica se o código de barras foi alterado.
            if (
                codigo_anterior
                and codigo_anterior != obj.codigo_barra
            ):

                caminho_antigo = caminho_imagem_produto(
                    codigo_anterior
                )

                caminho_novo = caminho_imagem_produto(
                    obj.codigo_barra
                )

                if nova_imagem:
                    # Uma nova imagem já foi salva com o novo código.
                    # Remove a imagem associada ao código anterior.
                    caminho_antigo.unlink(missing_ok=True)

                elif caminho_antigo.exists():
                    # Sem novo upload, preserva a imagem existente
                    # utilizando o novo código de barras.
                    caminho_novo.parent.mkdir(
                        parents=True,
                        exist_ok=True
                    )

                    os.replace(
                        caminho_antigo,
                        caminho_novo
                    )

        except (ValidationError, OSError) as erro:
            self.message_user(
                request,
                (
                    "Os dados do produto foram processados, "
                    "mas ocorreu um erro ao gerenciar a imagem: "
                    f"{erro}"
                ),
                level=messages.ERROR
            )
        
        # Verifica o dígito somente em novos cadastros
        # ou quando o código de barras for alterado.
        codigo_alterado = (
            not change
            or "codigo_barra" in form.changed_data
        )

        if codigo_alterado:

            resultado = verificar_digito_verificador(
                obj.codigo_barra
            )

            # A inconsistência gera apenas um aviso.
            if resultado is False:
                self.message_user(
                    request,
                    (
                        "Produto cadastrado, porém o código de barras "
                        "possui um possível erro no dígito verificador. "
                        "** Caso for um código interno do estabelecimento, "
                        "o cadastro pode ser mantido."
                    ),
                    level=messages.WARNING
                )



    def delete_model(self, request, obj):
        """
        Exclui o produto e agenda a remoção da imagem
        após a confirmação da transação no banco.
        """

        codigo_barra = obj.codigo_barra

        # Exclui o produto do banco de dados.
        super().delete_model(request, obj)

        # Executa a exclusão da imagem após o commit.
        def remover_imagem():
            try:
                excluir_imagem_produto(codigo_barra)

            except OSError:
                logger.exception(
                    "Erro ao excluir a imagem do produto %s",
                    codigo_barra
                )

        transaction.on_commit(remover_imagem)
