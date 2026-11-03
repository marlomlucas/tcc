from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib import messages
from django.contrib.auth import get_user_model
from django.contrib.messages import get_messages
from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import DatabaseError, connection, connections, transaction
from django.test import TransactionTestCase, override_settings
from django.urls import reverse

from PIL import Image

from .admin import ProdutoAdmin
from .imagens import (
    caminho_imagem_produto,
    excluir_imagem_produto,
    publicar_imagem_produto,
    salvar_imagem_produto,
)
from .models import Produto


class TestePublicacaoAposCommit(TransactionTestCase):
    def setUp(self):
        diretorio = TemporaryDirectory()
        self.addCleanup(diretorio.cleanup)
        self.media_root = Path(diretorio.name)
        configuracao = override_settings(MEDIA_ROOT=self.media_root)
        configuracao.enable()
        self.addCleanup(configuracao.disable)
        usuario = get_user_model().objects.create_superuser(
            username="admin_transacao", password=None
        )
        self.client.force_login(usuario)
        self.codigo_antigo = "12345670"
        self.codigo_novo = "96385074"

    def criar_upload(self, cor="red"):
        with BytesIO() as buffer:
            with Image.new("RGB", (40, 20), cor) as imagem:
                imagem.save(buffer, format="PNG")
            return SimpleUploadedFile("imagem.png", buffer.getvalue(), "image/png")

    def dados(self, **alteracoes):
        dados = {
            "codigo_barra": self.codigo_antigo,
            "nome": "Produto original",
            "descricao": "Descricao original",
            "preco": "10.00",
        }
        dados.update(alteracoes)
        return dados

    def criar_produto(self, com_imagem=True):
        produto = Produto.objects.create(**self.dados())
        caminho = caminho_imagem_produto(produto.codigo_barra)
        if com_imagem:
            salvar_imagem_produto(produto.codigo_barra, self.criar_upload())
        estado = Produto.objects.filter(pk=produto.pk).values().get()
        conteudo = caminho.read_bytes() if com_imagem else None
        return produto, caminho, estado, conteudo

    def postar(self, dados, produto=None, **kwargs):
        url = reverse("admin:produtos_produto_add") if produto is None else reverse(
            "admin:produtos_produto_change", args=[produto.pk]
        )
        return self.client.post(url, dados, HTTP_HOST="localhost", **kwargs)

    def observar_log(self, metodo, verificar):
        original = getattr(ProdutoAdmin, metodo)

        def observar(admin, request, obj, mensagem):
            self.assertTrue(connections[obj._state.db].in_atomic_block)
            verificar(obj)
            return original(admin, request, obj, mensagem)

        return patch.object(ProdutoAdmin, metodo, new=observar)

    def postar_com_rollback(self, dados, produto, verificar):
        metodo = "log_addition" if produto is None else "log_change"

        def falhar(obj):
            verificar(obj)
            raise RuntimeError("falha simulada no log administrativo")

        with self.observar_log(metodo, falhar):
            with self.assertLogs("django.request", level="ERROR"):
                with self.assertRaisesRegex(RuntimeError, "falha simulada no log"):
                    self.postar(dados, produto)

    def verificar_estado_original(self, produto, caminho, estado, conteudo):
        self.assertEqual(Produto.objects.filter(pk=produto.pk).values().get(), estado)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertFalse(caminho_imagem_produto(self.codigo_novo).exists())
        self.assertEqual(list(caminho.parent.iterdir()), [caminho])

    def verificar_webp(self, caminho):
        with Image.open(caminho) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")

    def verificar_aviso(self, resposta, nivel, trecho):
        self.assertEqual(resposta.status_code, 200)
        self.assertContains(resposta, trecho)
        avisos = list(get_messages(resposta.wsgi_request))
        self.assertTrue(any(aviso.level == nivel and trecho in str(aviso) for aviso in avisos))

    def test_cadastro_publica_apos_commit_na_conexao_correta(self):
        destino = caminho_imagem_produto(self.codigo_antigo)
        dados = self.dados(imagem_upload=self.criar_upload())
        registrar_original = transaction.on_commit

        def antes_do_commit(obj):
            self.assertFalse(destino.exists())
            publicar.assert_not_called()
            self.assertTrue(Produto.objects.filter(pk=obj.pk).exists())

        with patch("produtos.admin.publicar_imagem_produto", wraps=publicar_imagem_produto) as publicar:
            with patch("produtos.admin.transaction.on_commit", wraps=registrar_original) as registrar:
                with self.observar_log("log_addition", antes_do_commit):
                    resposta = self.postar(dados)

        self.assertEqual(resposta.status_code, 302)
        produto = Produto.objects.get(codigo_barra=self.codigo_antigo)
        self.assertEqual(registrar.call_args.kwargs["using"], produto._state.db)
        publicar.assert_called_once()
        self.assertEqual(destino.read_bytes(), publicar.call_args.args[1])
        self.verificar_webp(destino)
        self.assertFalse(connection.in_atomic_block)

    def test_cadastro_com_rollback_nao_publica(self):
        dados = self.dados(imagem_upload=self.criar_upload())

        def antes_do_rollback(obj):
            self.assertEqual(list(self.media_root.iterdir()), [])

        with patch("produtos.admin.publicar_imagem_produto", wraps=publicar_imagem_produto) as publicar:
            self.postar_com_rollback(dados, None, antes_do_rollback)

        publicar.assert_not_called()
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_substituicao_publica_apos_commit(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(nome="Produto alterado", imagem_upload=self.criar_upload("blue"))

        with self.observar_log("log_change", lambda obj: self.assertEqual(caminho.read_bytes(), conteudo)):
            resposta = self.postar(dados, produto)

        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Produto alterado")
        self.assertNotEqual(caminho.read_bytes(), conteudo)
        self.verificar_webp(caminho)
        self.assertEqual(list(caminho.parent.iterdir()), [caminho])

    def test_substituicao_com_rollback_preserva_dados_e_bytes(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(nome="Produto alterado", preco="99.99", imagem_upload=self.criar_upload("blue"))
        with patch("produtos.admin.publicar_imagem_produto", wraps=publicar_imagem_produto) as publicar:
            self.postar_com_rollback(dados, produto, lambda obj: self.assertEqual(caminho.read_bytes(), conteudo))

        publicar.assert_not_called()
        self.verificar_estado_original(produto, caminho, estado, conteudo)

    def test_alteracao_de_codigo_com_upload_publica_antes_de_remover(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        novo = caminho_imagem_produto(self.codigo_novo)
        dados = self.dados(codigo_barra=self.codigo_novo, imagem_upload=self.criar_upload("blue"))

        def antes_do_commit(obj):
            self.assertEqual(caminho.read_bytes(), conteudo)
            self.assertFalse(novo.exists())

        def remover(codigo):
            self.assertEqual(codigo, self.codigo_antigo)
            self.verificar_webp(novo)
            self.assertNotEqual(novo.read_bytes(), conteudo)
            self.assertEqual(caminho.read_bytes(), conteudo)
            return excluir_imagem_produto(codigo)

        with patch("produtos.admin.excluir_imagem_produto", side_effect=remover) as excluir:
            with self.observar_log("log_change", antes_do_commit):
                resposta = self.postar(dados, produto)

        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        excluir.assert_called_once_with(self.codigo_antigo)
        self.assertFalse(caminho.exists())
        self.assertEqual(list(novo.parent.iterdir()), [novo])

    def test_alteracao_de_codigo_sem_upload_copia_bytes_apos_commit(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        novo = caminho_imagem_produto(self.codigo_novo)

        def antes_do_commit(obj):
            self.assertEqual(caminho.read_bytes(), conteudo)
            self.assertFalse(novo.exists())

        with patch("produtos.forms.preparar_imagem_produto") as preparar:
            with self.observar_log("log_change", antes_do_commit):
                resposta = self.postar(self.dados(codigo_barra=self.codigo_novo), produto)

        self.assertEqual(resposta.status_code, 302)
        preparar.assert_not_called()
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        self.assertEqual(novo.read_bytes(), conteudo)
        self.assertFalse(caminho.exists())
        self.assertEqual(list(novo.parent.iterdir()), [novo])

    def verificar_rollback_de_codigo(self, com_upload):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(codigo_barra=self.codigo_novo, nome="Produto alterado")
        if com_upload:
            dados["imagem_upload"] = self.criar_upload("blue")
        with patch("produtos.admin.publicar_imagem_produto", wraps=publicar_imagem_produto) as publicar:
            self.postar_com_rollback(dados, produto, lambda obj: self.verificar_estado_original_arquivos(caminho, conteudo))
        publicar.assert_not_called()
        self.verificar_estado_original(produto, caminho, estado, conteudo)

    def verificar_estado_original_arquivos(self, caminho, conteudo):
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertFalse(caminho_imagem_produto(self.codigo_novo).exists())

    def test_alteracao_de_codigo_com_upload_e_rollback(self):
        self.verificar_rollback_de_codigo(com_upload=True)

    def test_alteracao_de_codigo_sem_upload_e_rollback(self):
        self.verificar_rollback_de_codigo(com_upload=False)

    def test_edicao_sem_mudanca_de_imagem_nao_publica(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        modificacao = caminho.stat().st_mtime_ns
        with patch("produtos.admin.publicar_imagem_produto") as publicar:
            resposta = self.postar(self.dados(nome="Produto alterado"), produto)
        self.assertEqual(resposta.status_code, 302)
        publicar.assert_not_called()
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertEqual(caminho.stat().st_mtime_ns, modificacao)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Produto alterado")

    def test_cadastro_sem_imagem_nao_cria_arquivo(self):
        with patch("produtos.admin.publicar_imagem_produto") as publicar:
            resposta = self.postar(self.dados())
        self.assertEqual(resposta.status_code, 302)
        self.assertEqual(Produto.objects.count(), 1)
        publicar.assert_not_called()
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_alteracao_de_codigo_sem_imagem_nao_cria_arquivo(self):
        produto, caminho, estado, conteudo = self.criar_produto(com_imagem=False)
        with patch("produtos.admin.publicar_imagem_produto") as publicar:
            resposta = self.postar(self.dados(codigo_barra=self.codigo_novo), produto)
        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        publicar.assert_not_called()
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_falha_de_publicacao_preserva_imagem_e_informa_banco_salvo(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(nome="Produto confirmado", imagem_upload=self.criar_upload("blue"))
        with patch("produtos.admin.publicar_imagem_produto", side_effect=ValidationError("falha simulada")):
            with self.assertLogs("produtos.admin", level="ERROR") as registros:
                resposta = self.postar(dados, produto, follow=True)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Produto confirmado")
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.verificar_aviso(resposta, messages.ERROR, "Os dados do produto foram salvos")
        self.assertIn(str(produto.pk), "\n".join(registros.output))
        self.assertTrue(all(registro.exc_info for registro in registros.records))

    def test_falha_de_publicacao_do_codigo_novo_preserva_antigo(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(codigo_barra=self.codigo_novo, imagem_upload=self.criar_upload("blue"))
        with patch("produtos.admin.publicar_imagem_produto", side_effect=OSError("falha simulada")):
            with patch("produtos.admin.excluir_imagem_produto") as excluir:
                with self.assertLogs("produtos.admin", level="ERROR") as registros:
                    resposta = self.postar(dados, produto, follow=True)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertFalse(caminho_imagem_produto(self.codigo_novo).exists())
        excluir.assert_not_called()
        self.verificar_aviso(resposta, messages.ERROR, "não foi possível atualizar a imagem")
        self.assertIn(self.codigo_antigo, "\n".join(registros.output))
        self.assertIn(self.codigo_novo, "\n".join(registros.output))

    def test_falha_de_remocao_preserva_nova_imagem_e_informa_limpeza(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(codigo_barra=self.codigo_novo, imagem_upload=self.criar_upload("blue"))
        with patch("produtos.admin.excluir_imagem_produto", side_effect=PermissionError("limpeza simulada")):
            with self.assertLogs("produtos.admin", level="ERROR") as registros:
                resposta = self.postar(dados, produto, follow=True)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        novo = caminho_imagem_produto(self.codigo_novo)
        self.verificar_webp(novo)
        self.assertNotEqual(novo.read_bytes(), conteudo)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.verificar_aviso(resposta, messages.WARNING, "a nova imagem foram salvos")
        self.assertContains(resposta, "não foi possível remover a imagem do código anterior")
        self.assertTrue(all(registro.exc_info for registro in registros.records))

    def test_callback_independe_do_upload_fechado(self):
        original = ProdutoAdmin.save_related

        def fechar(admin, request, form, formsets, change):
            form.cleaned_data["imagem_upload"].close()
            self.assertIsInstance(form.imagem_preparada, bytes)
            return original(admin, request, form, formsets, change)

        with patch.object(ProdutoAdmin, "save_related", new=fechar):
            resposta = self.postar(self.dados(imagem_upload=self.criar_upload()))
        self.assertEqual(resposta.status_code, 302)
        self.verificar_webp(caminho_imagem_produto(self.codigo_antigo))

    def test_falha_de_leitura_da_imagem_impede_salvamento(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        ler_original = Path.read_bytes

        def ler(arquivo):
            if arquivo == caminho:
                raise PermissionError("leitura simulada")
            return ler_original(arquivo)

        with patch.object(Path, "read_bytes", new=ler):
            with self.assertLogs("produtos.admin", level="ERROR"):
                with self.assertLogs("django.request", level="ERROR"):
                    with self.assertRaisesRegex(PermissionError, "leitura simulada"):
                        self.postar(self.dados(codigo_barra=self.codigo_novo), produto)
        self.verificar_estado_original(produto, caminho, estado, conteudo)

    def test_falha_no_commit_preserva_imagem_anterior(self):
        produto, caminho, estado, conteudo = self.criar_produto()
        dados = self.dados(nome="Produto alterado", imagem_upload=self.criar_upload("blue"))
        with patch.object(connection, "commit", side_effect=DatabaseError("commit simulado")):
            with patch("produtos.admin.publicar_imagem_produto", wraps=publicar_imagem_produto) as publicar:
                with self.assertLogs("django.request", level="ERROR"):
                    with self.assertRaisesRegex(DatabaseError, "commit simulado"):
                        self.postar(dados, produto)
        publicar.assert_not_called()
        self.verificar_estado_original(produto, caminho, estado, conteudo)
