from django.conf import settings
from django.core.exceptions import ValidationError
from rest_framework import serializers
from drf_spectacular.utils import extend_schema_field

from .models import Produto
from .imagens import caminho_imagem_produto


class ProdutoSerializer(serializers.ModelSerializer):

    # Campo calculado, independente do banco de dados.
    imagem = serializers.SerializerMethodField()

    class Meta:
        model = Produto
        fields = [
            "codigo_barra",
            "nome",
            "descricao",
            "preco",
            "data_atualizacao",
            "imagem"
        ]
    
    @extend_schema_field(serializers.URLField(allow_null=True))
    def get_imagem(self, obj):
        """
        Localiza a imagem pelo código de barras e retorna
        sua URL pública, definida nas configurações.
        """

        try:
            caminho = caminho_imagem_produto(
                obj.codigo_barra
            )

            # Produto sem imagem cadastrada.
            if not caminho.is_file():
                return None

            # Versão para evitar cache desatualizado.
            versao = caminho.stat().st_mtime_ns

        except (ValidationError, OSError):
            return None

        # Constrói a URL pública da imagem.
        return (
            f"{settings.IMAGENS_BASE_URL}"
            f"media/{settings.IMAGENS_SUBPASTA}/"
            f"{obj.codigo_barra}.webp"
            f"?v={versao}"
        )


# Define o formato das mensagens de erro retornadas pela API.
class ErroSerializer(serializers.Serializer):
    erro = serializers.CharField()
