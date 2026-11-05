
from pathlib import Path
from tempfile import TemporaryDirectory

from django.contrib import admin
from django.test import (
    RequestFactory,
    TestCase,
    override_settings,
)

from .admin import ProdutoAdmin
from .imagens import caminho_imagem_produto
from .models import Produto


class TesteExclusaoProdutoAdmin(TestCase):

    def setUp(self):
        # Utiliza uma pasta temporária para proteger as imagens reais.
        self.diretorio = TemporaryDirectory()
        self.addCleanup(self.diretorio.cleanup)

        self.configuracao = override_settings(
            MEDIA_ROOT=Path(self.diretorio.name)
        )
        self.configuracao.enable()
        self.addCleanup(self.configuracao.disable)

        self.produto_admin = ProdutoAdmin(Produto, admin.site)
        self.request = RequestFactory().post(
            "/admin/produtos/produto/"
        )

    def criar_produto(self, codigo):
        return Produto.objects.create(
            codigo_barra=codigo,
            nome="Produto de Teste",
            descricao="Teste de exclusão",
            preco="10.00"
        )

    def test_excluir_produto_com_imagem(self):
        produto = self.criar_produto("12345670")
        produto_pk = produto.pk

        caminho = caminho_imagem_produto(
            produto.codigo_barra
        )
        caminho.parent.mkdir(parents=True, exist_ok=True)
        caminho.write_bytes(b"imagem de teste")

        # Executa também o callback de transaction.on_commit().
        with self.captureOnCommitCallbacks(execute=True):
            self.produto_admin.delete_model(
                self.request,
                produto
            )

        self.assertFalse(
            Produto.objects.filter(pk=produto_pk).exists()
        )
        self.assertFalse(caminho.exists())

    def test_excluir_produto_sem_imagem(self):
        produto = self.criar_produto("12345671")
        produto_pk = produto.pk

        with self.captureOnCommitCallbacks(execute=True):
            self.produto_admin.delete_model(
                self.request,
                produto
            )

        self.assertFalse(
            Produto.objects.filter(pk=produto_pk).exists()
        )

    def test_acoes_em_lote_desabilitadas(self):
        self.assertIsNone(self.produto_admin.actions)
