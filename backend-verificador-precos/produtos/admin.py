from django.contrib import admin, messages
from django.core.exceptions import ValidationError
from .validacao_codigos import verificar_digito_verificador

from .forms import ProdutoAdminForm
from .imagens import (
    caminho_imagem_produto,
    publicar_imagem_produto,
    excluir_imagem_produto,
)

from .models import Produto
import logging
from django.db import DatabaseError, router, transaction

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
        Salva o produto e agenda a atualização da imagem após o commit.

        A imagem é armazenada em arquivo WebP,
        identificada pelo código de barras.
        """

        codigo_anterior = None
        using = router.db_for_write(self.model, instance=obj)

        # Identifica o código anterior durante uma edição.
        if change:
            codigo_anterior = (
                Produto.objects
                .using(using)
                .filter(pk=obj.pk)
                .values_list("codigo_barra", flat=True)
                .first()
            )

        # Obtém a imagem enviada pelo formulário.
        nova_imagem = form.cleaned_data.get("imagem_upload")
        imagem_preparada = form.imagem_preparada

        if nova_imagem and imagem_preparada is None:
            raise ValidationError("A imagem deve ser validada antes de salvar o produto.")

        # Sem upload, copia os bytes antes de salvar, sem mover a imagem antiga.
        if codigo_anterior and codigo_anterior != obj.codigo_barra and not nova_imagem:
            try:
                imagem_preparada = caminho_imagem_produto(codigo_anterior).read_bytes()
            except FileNotFoundError:
                pass
            except OSError:
                logger.exception(
                    "Erro ao ler a imagem antes de salvar o produto %s (código %s)",
                    obj.pk,
                    codigo_anterior
                )
                raise

        # Salva os dados do produto no banco.
        super().save_model(request, obj, form, change)

        if imagem_preparada is not None:
            # Captura valores estáveis; o callback não depende do upload ou de obj.
            produto_pk = obj.pk
            codigo_atual = obj.codigo_barra

            def atualizar_imagem():
                try:
                    publicar_imagem_produto(codigo_atual, imagem_preparada)
                except (ValidationError, OSError):
                    logger.exception(
                        "Erro ao publicar a imagem após commit do produto %s "
                        "(código anterior %s, atual %s)",
                        produto_pk,
                        codigo_anterior,
                        codigo_atual
                    )
                    self.message_user(
                        request,
                        "Os dados do produto foram salvos, mas não foi possível "
                        "atualizar a imagem. Reenvie a imagem pelo formulário.",
                        level=messages.ERROR
                    )
                    return

                # A imagem antiga só é removida depois de publicar a nova.
                if codigo_anterior and codigo_anterior != codigo_atual:
                    try:
                        excluir_imagem_produto(codigo_anterior)
                    except (ValidationError, OSError):
                        logger.exception(
                            "Erro ao remover a imagem antiga após publicação do "
                            "produto %s (código anterior %s, atual %s)",
                            produto_pk,
                            codigo_anterior,
                            codigo_atual
                        )
                        self.message_user(
                            request,
                            "Os dados do produto e a nova imagem foram salvos, "
                            "mas não foi possível remover a imagem do código anterior.",
                            level=messages.WARNING
                        )

            transaction.on_commit(atualizar_imagem, using=obj._state.db)
        
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
        produto_pk = obj.pk
        using = router.db_for_write(self.model, instance=obj)
        caminho = caminho_imagem_produto(codigo_barra)
        imagem_original = None
        erro_identificacao = False

        # A identificação fica apenas em memória e não altera o arquivo.
        try:
            imagem_original = caminho.stat()
        except FileNotFoundError:
            pass
        except OSError:
            erro_identificacao = True
            logger.exception(
                "Erro ao identificar a imagem antes de excluir o produto %s "
                "(código %s, arquivo %s)",
                produto_pk, codigo_barra, caminho
            )

        # Exclui o produto do banco de dados.
        super().delete_model(request, obj)

        # Executa a exclusão da imagem após o commit.
        def remover_imagem():
            aviso = (
                "O produto foi excluído, mas não foi possível concluir a remoção "
                "de sua imagem. Verifique os registros e regularize o arquivo "
                "manualmente."
            )

            if erro_identificacao:
                self.message_user(request, aviso, level=messages.WARNING)
                return

            # Um arquivo criado posteriormente não pertence a esta exclusão.
            if imagem_original is None:
                return

            try:
                if Produto.objects.using(using).filter(codigo_barra=codigo_barra).exists():
                    logger.warning(
                        "Imagem preservada após excluir o produto %s: código %s "
                        "reutilizado por outro produto (arquivo %s)",
                        produto_pk, codigo_barra, caminho
                    )
                else:
                    imagem_atual = caminho.stat()
                    campos = ("st_dev", "st_ino", "st_size", "st_mtime_ns")
                    if any(
                        getattr(imagem_atual, campo) != getattr(imagem_original, campo)
                        for campo in campos
                    ):
                        logger.warning(
                            "Imagem preservada após excluir o produto %s: arquivo "
                            "alterado (código %s, arquivo %s)",
                            produto_pk, codigo_barra, caminho
                        )
                    else:
                        excluir_imagem_produto(codigo_barra)
                        return

            except FileNotFoundError:
                return
            except (OSError, ValidationError, DatabaseError):
                logger.exception(
                    "Erro ao remover a imagem após commit da exclusão do produto %s "
                    "(código %s, arquivo %s)",
                    produto_pk, codigo_barra, caminho
                )

            self.message_user(request, aviso, level=messages.WARNING)

        transaction.on_commit(remover_imagem, using=using)
