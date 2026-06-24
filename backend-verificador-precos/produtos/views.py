from rest_framework.generics import RetrieveAPIView
from .models import Produto
from .serializers import ProdutoSerializer


class ProdutoPorCodigoAPIView(RetrieveAPIView):
    serializer_class = ProdutoSerializer
    lookup_field = "codigo_barra"
    queryset = Produto.objects.all()
