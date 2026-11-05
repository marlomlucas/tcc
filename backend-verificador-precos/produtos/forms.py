
import logging
import re

from django import forms
from django.core.exceptions import ValidationError

from .models import Produto
from .imagens import caminho_imagem_produto, preparar_imagem_produto


logger = logging.getLogger(__name__)


class ProdutoAdminForm(forms.ModelForm):
    """
    Formulário de cadastro e edição de produtos.

    O campo imagem_upload não pertence ao modelo Produto
    e não será armazenado no banco de dados.
    """

    imagem_upload = forms.ImageField(
        required=False,
        label="Imagem do Produto",
        help_text="Formatos aceitos: JPG, PNG ou WebP. Máximo de 5 MB."
    )

    class Meta:
        model = Produto
        fields = [
            "codigo_barra",
            "nome",
            "descricao",
            "preco"
        ]

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.imagem_preparada = None
        # ModelForm pode atualizar instance durante a validação.
        self._codigo_barra_original = (
            None if self.instance._state.adding else self.instance.codigo_barra
        )

    def clean_codigo_barra(self):
        codigo = self.cleaned_data["codigo_barra"].strip()

        if not re.fullmatch(r"[0-9]{1,20}", codigo):
            raise ValidationError(
                "O código de barras deve conter de 1 a 20 dígitos."
            )

        if codigo != self._codigo_barra_original:
            try:
                caminho_imagem_produto(codigo).stat()
            except FileNotFoundError:
                pass
            except OSError:
                logger.exception(
                    "Erro ao verificar a imagem do código de barras %s", codigo
                )
                raise ValidationError(
                    "Não foi possível verificar a imagem deste código de barras. "
                    "Verifique o acesso ao diretório de imagens e tente novamente."
                )
            else:
                # Códigos de outros produtos seguem a validação de unicidade.
                if not Produto.objects.filter(codigo_barra=codigo).exists():
                    raise ValidationError(
                        "Já existe um arquivo de imagem para este código de barras "
                        "sem produto associado. Regularize o arquivo manualmente "
                        "antes de usar este código."
                    )

        return codigo

    def clean_imagem_upload(self):
        arquivo = self.cleaned_data.get("imagem_upload")
        self.imagem_preparada = None

        # A imagem é opcional.
        if arquivo is None:
            return None

        # Decodifica e codifica o WebP antes de o Admin persistir o produto.
        self.imagem_preparada = preparar_imagem_produto(arquivo)

        return arquivo
