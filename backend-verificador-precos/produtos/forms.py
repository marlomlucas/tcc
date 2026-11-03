
import re

from django import forms
from django.core.exceptions import ValidationError

from .models import Produto
from .imagens import preparar_imagem_produto


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

    def clean_codigo_barra(self):
        codigo = self.cleaned_data["codigo_barra"].strip()

        if not re.fullmatch(r"[0-9]{1,20}", codigo):
            raise ValidationError(
                "O código de barras deve conter de 1 a 20 dígitos."
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
