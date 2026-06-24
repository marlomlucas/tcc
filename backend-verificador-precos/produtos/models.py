from django.db import models


# Classe para representar os produtos cadastrados no sistema.
class Produto(models.Model):

    # Código de barras para identificação do produto..
    # Defini como único para evitar registros duplicados.
    codigo_barra = models.CharField(
        max_length=20,
        unique=True,
        verbose_name="Código de Barras"
    )

    # Nome que será exibido no aplicativo.
    nome = models.CharField(
        max_length=150,
        verbose_name="Nome"
    )

    # Descrição do produto.
    # Aceita vazio, produtos que não necessitam descrição.
    descricao = models.TextField(
        blank=True,
        verbose_name="Descrição"
    )

    # Preço do produto.
    # Coloquei DecimalField por ser mais adequado para valores monetários.
    preco = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        verbose_name="Preço"
    )

    # Data da última alteração do produto.
    # É atualizada automaticamente pelo Django.
    data_atualizacao = models.DateTimeField(
        auto_now=True,
        verbose_name="Data de Atualização"
    )

    class Meta:

        # Nome da tabela que será criada no MySQL.
        db_table = "produtos"

        # Nome utilizado pelo painel administrativo do Django.
        verbose_name = "Produto"
        verbose_name_plural = "Produtos"

        # Lista os produtos em ordem alfabética pelo nome.
        ordering = ["nome"]

    # Define como o produto será exibido no painel administrativo.
    def __str__(self):
        return f"{self.nome} - R$ {self.preco}"