from rest_framework import status
from rest_framework.generics import RetrieveAPIView
from rest_framework.response import Response

from drf_spectacular.utils import extend_schema, OpenApiParameter

from .models import Produto
from .serializers import ProdutoSerializer, ErroSerializer
from .validador import codigo_barra_valido


class ProdutoPorCodigoAPIView(RetrieveAPIView):
    serializer_class = ProdutoSerializer
    lookup_field = "codigo_barra"
    queryset = Produto.objects.all()

    @extend_schema(
        summary="Consultar produto pelo código de barras",
        description=(
            "Consulta um produto cadastrado no sistema utilizando "
            "o código de barras informado."
        ),
        parameters=[
            OpenApiParameter(
                name="codigo_barra",
                type=str,
                location=OpenApiParameter.PATH,
                required=True,
                description="Código de barras utilizado para localizar o produto."
            )
        ],
        responses={
            200: ProdutoSerializer,
            400: ErroSerializer,
            404: ErroSerializer,
        },
        tags=["Produtos"],
    )
    def retrieve(self, request, *args, **kwargs):

        codigo_barra = kwargs.get("codigo_barra")

        if not codigo_barra_valido(codigo_barra):
            return Response(
                {"erro": "Código de barras inválido."},
                status=status.HTTP_400_BAD_REQUEST
            )

        try:
            produto = Produto.objects.get(
                codigo_barra=codigo_barra
            )

        except Produto.DoesNotExist:
            return Response(
                {"erro": "Produto não encontrado."},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = self.get_serializer(produto)

        return Response(
            serializer.data,
            status=status.HTTP_200_OK
        )