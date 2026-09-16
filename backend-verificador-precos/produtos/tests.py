from decimal import Decimal

from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase

from .models import Produto


class ProdutoAPITests(APITestCase):

    def setUp(self):
        # Cria um produto que será utilizado durante os testes.
        self.produto = Produto.objects.create(
            codigo_barra="7891234567890",
            nome="Arroz Tipo 1",
            descricao="Arroz branco 5kg",
            preco=Decimal("24.90")
        )

    def test_consulta_produto_existente(self):
        # Monta a URL utilizando o código do produto criado no teste.
        url = reverse(
            "produto-por-codigo",
            kwargs={"codigo_barra": self.produto.codigo_barra}
        )

        # Realiza uma requisição GET para a API.
        response = self.client.get(url)

        # Verifica se a API retornou HTTP 200.
        self.assertEqual(
            response.status_code,
            status.HTTP_200_OK
        )

        # Confirma se o código retornado é o mesmo cadastrado.
        self.assertEqual(
            response.data["codigo_barra"],
            self.produto.codigo_barra
        )

        # Confirma se o nome retornado está correto.
        self.assertEqual(
            response.data["nome"],
            self.produto.nome
        )

    def test_produto_nao_encontrado(self):
        # Código válido, mas que não existe no banco de teste.
        url = reverse(
            "produto-por-codigo",
            kwargs={"codigo_barra": "9999999999999"}
        )

        response = self.client.get(url)

        # Deve retornar HTTP 404.
        self.assertEqual(
            response.status_code,
            status.HTTP_404_NOT_FOUND
        )

        # Confirma a mensagem retornada.
        self.assertEqual(
            response.data["erro"],
            "Produto não encontrado."
        )

    def test_codigo_barra_invalido(self):
        # Código contendo letras.
        url = reverse(
            "produto-por-codigo",
            kwargs={"codigo_barra": "ABC123"}
        )

        response = self.client.get(url)

        # Deve retornar HTTP 400.
        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST
        )

        self.assertEqual(
            response.data["erro"],
            "Código de barras inválido."
        )

    def test_codigo_barra_acima_do_limite(self):
        # Código com mais de 20 caracteres.
        url = reverse(
            "produto-por-codigo",
            kwargs={"codigo_barra": "123456789012345678901"}
        )

        response = self.client.get(url)

        # Deve retornar HTTP 400.
        self.assertEqual(
            response.status_code,
            status.HTTP_400_BAD_REQUEST
        )

        self.assertEqual(
            response.data["erro"],
            "Código de barras inválido."
        )