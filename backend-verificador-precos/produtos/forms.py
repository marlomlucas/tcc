
import re

from django import forms
from django.core.exceptions import ValidationError

from .models import Produto


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

    def clean_codigo_barra(self):
        codigo = self.cleaned_data["codigo_barra"].strip()

        if not re.fullmatch(r"[0-9]{1,20}", codigo):
            raise ValidationError(
                "O código de barras deve conter de 1 a 20 dígitos."
            )

        return codigo

    def clean_imagem_upload(self):
        arquivo = self.cleaned_data.get("imagem_upload")

        # A imagem é opcional.
        if arquivo is None:
            return None

        # Limite de 5 MB.
        if arquivo.size > 5 * 1024 * 1024:
            raise ValidationError(
                "A imagem não pode ultrapassar 5 MB."
            )

        # O ImageField utiliza Pillow para validar a imagem.
        if arquivo.image.format not in {"JPEG", "PNG", "WEBP"}:
            raise ValidationError(
                "Formato inválido. Utilize JPG, PNG ou WebP."
            )

        return arquivo
