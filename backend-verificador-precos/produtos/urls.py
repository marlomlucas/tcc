from django.urls import path
from .views import ProdutoPorCodigoAPIView

urlpatterns = [
    path(
        "produtos/<str:codigo_barra>/",
        ProdutoPorCodigoAPIView.as_view(),
        name="produto-por-codigo"
    ),
]
