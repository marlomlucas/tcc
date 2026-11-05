from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib import messages
from django.contrib.auth import get_user_model
from django.contrib.messages import get_messages
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import DatabaseError, connection, transaction
from django.db.models.query import QuerySet
from django.test import TransactionTestCase, override_settings
from django.urls import reverse

from PIL import Image

from .admin import ProdutoAdmin
from .imagens import caminho_imagem_produto, salvar_imagem_produto
from .models import Produto


class TesteExclusaoAposCommit(TransactionTestCase):
    def setUp(self):
        diretorio = TemporaryDirectory()
        self.addCleanup(diretorio.cleanup)
        self.media_root = Path(diretorio.name)
        configuracao = override_settings(MEDIA_ROOT=self.media_root)
        configuracao.enable()
        self.addCleanup(configuracao.disable)
        usuario = get_user_model().objects.create_superuser(
            username="admin_exclusao", password=None
        )
        self.client.force_login(usuario)
        self.codigo = "12345670"

    def criar_upload(self, cor="red"):
        with BytesIO() as buffer:
            with Image.new("RGB", (40, 20), cor) as imagem:
                imagem.save(buffer, format="PNG")
            return SimpleUploadedFile("imagem.png", buffer.getvalue(), "image/png")

    def dados(self, **alteracoes):
        dados = {
            "codigo_barra": self.codigo,
            "nome": "Produto original",
            "descricao": "Descricao original",
            "preco": "10.00",
        }
        dados.update(alteracoes)
        return dados

    def criar_produto(self, com_imagem=True):
        produto = Produto.objects.create(**self.dados())
        caminho = caminho_imagem_produto(self.codigo)
        if com_imagem:
            salvar_imagem_produto(self.codigo, self.criar_upload())
        return produto, caminho

    def excluir(self, produto, **kwargs):
        return self.client.post(
            reverse("admin:produtos_produto_delete", args=[produto.pk]),
            {"post": "yes"}, HTTP_HOST="localhost", **kwargs
        )

    def observar_resposta(self, verificar):
        original = ProdutoAdmin.response_delete

        def observar(admin, request, obj_display, obj_id):
            self.assertTrue(connection.in_atomic_block)
            verificar()
            return original(admin, request, obj_display, obj_id)

        return patch.object(ProdutoAdmin, "response_delete", new=observar)

    def verificar_aviso(self, resposta):
        self.assertEqual(resposta.status_code, 200)
        avisos = list(get_messages(resposta.wsgi_request))
        self.assertTrue(any(
            aviso.level == messages.WARNING
            and "produto foi excluído" in str(aviso).lower()
            and "imagem" in str(aviso).lower()
            and "manual" in str(aviso).lower()
            for aviso in avisos
        ), [str(aviso) for aviso in avisos])

    def verificar_diagnostico(self, registros, produto_pk, caminho):
        texto = "\n".join(registros.output)
        self.assertIn(str(produto_pk), texto)
        self.assertIn(self.codigo, texto)
        self.assertIn(str(caminho), texto)
        self.assertTrue(any(
            isinstance(registro.args, tuple) and produto_pk in registro.args
            for registro in registros.records
        ))
        self.assertTrue(any(registro.exc_info for registro in registros.records))

    def test_exclusao_individual_remove_arquivo_somente_apos_commit(self):
        produto, caminho = self.criar_produto()
        produto_pk = produto.pk
        conteudo = caminho.read_bytes()
        unlink_original = Path.unlink
        on_commit_original = transaction.on_commit

        def remover(arquivo, *args, **kwargs):
            if arquivo == caminho:
                self.assertFalse(connection.in_atomic_block)
                self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
            return unlink_original(arquivo, *args, **kwargs)

        def antes_do_commit():
            self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
            self.assertEqual(caminho.read_bytes(), conteudo)

        with patch.object(Path, "unlink", new=remover):
            with patch("produtos.admin.transaction.on_commit", wraps=on_commit_original) as registrar:
                with self.observar_resposta(antes_do_commit):
                    resposta = self.excluir(produto)
        self.assertEqual(resposta.status_code, 302)
        self.assertEqual(registrar.call_args.kwargs["using"], produto._state.db)
        self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
        self.assertFalse(caminho.exists())

    def test_rollback_da_exclusao_preserva_registro_e_imagem(self):
        produto, caminho = self.criar_produto()
        estado = Produto.objects.filter(pk=produto.pk).values().get()
        conteudo = caminho.read_bytes()
        modificacao = caminho.stat().st_mtime_ns

        def falhar():
            self.assertFalse(Produto.objects.filter(pk=produto.pk).exists())
            self.assertEqual(caminho.read_bytes(), conteudo)
            raise RuntimeError("rollback simulado depois da exclusao")

        with self.observar_resposta(falhar):
            with self.assertLogs("django.request", level="ERROR"):
                with self.assertRaisesRegex(RuntimeError, "rollback simulado"):
                    self.excluir(produto)
        self.assertEqual(Produto.objects.filter(pk=produto.pk).values().get(), estado)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertEqual(caminho.stat().st_mtime_ns, modificacao)

    def test_falha_no_commit_da_exclusao_preserva_registro_e_imagem(self):
        produto, caminho = self.criar_produto()
        estado = Produto.objects.filter(pk=produto.pk).values().get()
        conteudo = caminho.read_bytes()
        with patch.object(connection, "commit", side_effect=DatabaseError("commit simulado")):
            with self.assertLogs("django.request", level="ERROR"):
                with self.assertRaisesRegex(DatabaseError, "commit simulado"):
                    self.excluir(produto)
        self.assertEqual(Produto.objects.filter(pk=produto.pk).values().get(), estado)
        self.assertEqual(caminho.read_bytes(), conteudo)

    def test_arquivo_removido_antes_do_callback_nao_causa_erro(self):
        produto, caminho = self.criar_produto()
        with self.observar_resposta(caminho.unlink):
            resposta = self.excluir(produto)
        self.assertEqual(resposta.status_code, 302)
        self.assertFalse(Produto.objects.filter(pk=produto.pk).exists())
        self.assertFalse(caminho.exists())

    def test_falha_de_permissao_informa_exclusao_e_mantem_protecao_de_orfa(self):
        produto, caminho = self.criar_produto()
        produto_pk = produto.pk
        conteudo = caminho.read_bytes()
        modificacao = caminho.stat().st_mtime_ns
        unlink_original = Path.unlink

        def impedir_remocao(arquivo, *args, **kwargs):
            if arquivo == caminho:
                raise PermissionError("remocao negada")
            return unlink_original(arquivo, *args, **kwargs)

        with patch.object(Path, "unlink", new=impedir_remocao):
            with self.assertLogs("produtos.admin", level="ERROR") as registros:
                resposta = self.excluir(produto, follow=True)
        self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
        self.verificar_aviso(resposta)
        self.verificar_diagnostico(registros, produto_pk, caminho)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertEqual(caminho.stat().st_mtime_ns, modificacao)
        for com_upload in (False, True):
            with self.subTest(com_upload=com_upload):
                dados = self.dados(nome="Novo produto")
                if com_upload:
                    dados["imagem_upload"] = self.criar_upload("blue")
                resposta = self.client.post(
                    reverse("admin:produtos_produto_add"), dados, HTTP_HOST="localhost"
                )
                self.assertEqual(resposta.status_code, 200)
                self.assertIn("codigo_barra", resposta.context["adminform"].form.errors)
                self.assertContains(resposta, "Regularize o arquivo manualmente")
                self.assertFalse(Produto.objects.filter(codigo_barra=self.codigo).exists())
                self.assertEqual(caminho.read_bytes(), conteudo)
                self.assertEqual(caminho.stat().st_mtime_ns, modificacao)

    def test_callback_antigo_preserva_arquivo_quando_codigo_tem_novo_produto(self):
        produto, caminho = self.criar_produto()
        conteudo = caminho.read_bytes()
        novo_pk = []

        def reutilizar_codigo():
            novo = Produto.objects.create(**self.dados(nome="Novo produto"))
            novo_pk.append(novo.pk)

        with self.assertLogs("produtos.admin", level="WARNING") as registros:
            with transaction.atomic():
                transaction.on_commit(reutilizar_codigo)
                resposta = self.excluir(produto)
                self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertIn("reutilizado", "\n".join(registros.output))
        self.assertEqual(resposta.status_code, 302)
        self.assertNotEqual(novo_pk[0], produto.pk)
        self.assertTrue(Produto.objects.filter(pk=novo_pk[0], codigo_barra=self.codigo).exists())
        self.assertEqual(caminho.read_bytes(), conteudo)

    def test_callback_antigo_preserva_imagem_publicada_para_novo_produto(self):
        produto, caminho = self.criar_produto()
        conteudo_novo = []

        def reutilizar_codigo_e_publicar():
            Produto.objects.create(**self.dados(nome="Novo produto"))
            salvar_imagem_produto(self.codigo, self.criar_upload("blue"))
            conteudo_novo.append(caminho.read_bytes())

        with self.assertLogs("produtos.admin", level="WARNING") as registros:
            with transaction.atomic():
                transaction.on_commit(reutilizar_codigo_e_publicar)
                resposta = self.excluir(produto)
        self.assertIn("reutilizado", "\n".join(registros.output))
        self.assertEqual(resposta.status_code, 302)
        self.assertEqual(Produto.objects.get().nome, "Novo produto")
        self.assertEqual(caminho.read_bytes(), conteudo_novo[0])

    def test_arquivo_substituido_antes_do_callback_e_preservado_sem_novo_produto(self):
        produto, caminho = self.criar_produto()
        conteudo_antigo = caminho.read_bytes()
        conteudo_novo = []

        def substituir():
            salvar_imagem_produto(self.codigo, self.criar_upload("blue"))
            conteudo_novo.append(caminho.read_bytes())

        with self.assertLogs("produtos.admin", level="WARNING") as registros:
            with transaction.atomic():
                transaction.on_commit(substituir)
                resposta = self.excluir(produto)
        self.assertIn("alterado", "\n".join(registros.output))
        self.assertEqual(resposta.status_code, 302)
        self.assertFalse(Produto.objects.exists())
        self.assertNotEqual(conteudo_antigo, conteudo_novo[0])
        self.assertEqual(caminho.read_bytes(), conteudo_novo[0])

    def test_arquivo_que_nao_existia_na_exclusao_nao_e_removido_pelo_callback(self):
        produto, caminho = self.criar_produto(com_imagem=False)
        conteudo_novo = []

        def publicar():
            salvar_imagem_produto(self.codigo, self.criar_upload("blue"))
            conteudo_novo.append(caminho.read_bytes())

        with transaction.atomic():
            transaction.on_commit(publicar)
            resposta = self.excluir(produto)
        self.assertEqual(resposta.status_code, 302)
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(caminho.read_bytes(), conteudo_novo[0])

    def verificar_falha_de_identificacao(self, depois_do_salvamento):
        produto, caminho = self.criar_produto()
        produto_pk = produto.pk
        conteudo = caminho.read_bytes()
        stat_original = Path.stat
        fase_callback = [False]

        def consultar(arquivo, *args, **kwargs):
            if arquivo == caminho and (
                not depois_do_salvamento or fase_callback[0]
            ):
                raise PermissionError("identificacao negada")
            return stat_original(arquivo, *args, **kwargs)

        def marcar_callback():
            fase_callback[0] = True

        with patch.object(Path, "stat", new=consultar):
            with self.observar_resposta(marcar_callback):
                with self.assertLogs("produtos.admin", level="ERROR") as registros:
                    resposta = self.excluir(produto, follow=True)
        self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.verificar_aviso(resposta)
        self.verificar_diagnostico(registros, produto_pk, caminho)

    def test_falha_ao_identificar_arquivo_original_preserva_imagem_e_informa_admin(self):
        self.verificar_falha_de_identificacao(depois_do_salvamento=False)

    def test_falha_ao_identificar_arquivo_no_callback_preserva_imagem_e_informa_admin(self):
        self.verificar_falha_de_identificacao(depois_do_salvamento=True)

    def test_falha_ao_consultar_novo_proprietario_preserva_imagem_apos_commit(self):
        produto, caminho = self.criar_produto()
        produto_pk = produto.pk
        conteudo = caminho.read_bytes()
        existe_original = QuerySet.exists

        def consultar(queryset):
            if queryset.model is Produto and not connection.in_atomic_block:
                raise DatabaseError("consulta de proprietario indisponivel")
            return existe_original(queryset)

        with patch.object(QuerySet, "exists", new=consultar):
            with self.assertLogs("produtos.admin", level="ERROR") as registros:
                resposta = self.excluir(produto, follow=True)
        self.assertFalse(Produto.objects.filter(pk=produto_pk).exists())
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.verificar_aviso(resposta)
        self.verificar_diagnostico(registros, produto_pk, caminho)
