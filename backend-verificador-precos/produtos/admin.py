from django.contrib import admin
from .models import Produto


@admin.register(Produto)
class ProdutoAdmin(admin.ModelAdmin):
    list_display = ("nome", "codigo_barra", "preco", "data_atualizacao")
    search_fields = ("nome", "codigo_barra")
    list_filter = ("data_atualizacao",)