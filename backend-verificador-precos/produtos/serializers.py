from rest_framework import serializers
from .models import Produto


class ProdutoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Produto
        fields = [
            "codigo_barra",
            "nome",
            "descricao",
            "preco",
            "data_atualizacao"
        ]

# Define o formato das mensagens de erro retornadas pela API.
class ErroSerializer(serializers.Serializer):
    erro = serializers.CharField()
