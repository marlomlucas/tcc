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
