from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.conf import settings
from django.contrib import messages
from django.contrib.auth import get_user_model
from django.contrib.messages import get_messages
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TransactionTestCase, override_settings
from django.urls import reverse

from PIL import Image

from .forms import ProdutoAdminForm
from .imagens import caminho_imagem_produto, salvar_imagem_produto
from .models import Produto


class TesteProtecaoImagensOrfas(TransactionTestCase):
    def setUp(self):
        diretorio = TemporaryDirectory()
        self.addCleanup(diretorio.cleanup)
        self.media_root = Path(diretorio.name)
        configuracao = override_settings(MEDIA_ROOT=self.media_root)
        configuracao.enable()
        self.addCleanup(configuracao.disable)
        usuario = get_user_model().objects.create_superuser(
            username="admin_orfas", password=None
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

    def criar_produto(self, com_imagem=True, **alteracoes):
        produto = Produto.objects.create(**self.dados(**alteracoes))
        if com_imagem:
            self.criar_imagem(produto.codigo_barra)
        return produto

    def criar_imagem(self, codigo, cor="red"):
        return salvar_imagem_produto(codigo, self.criar_upload(cor))

    def postar(self, dados, produto=None, **kwargs):
        url = reverse("admin:produtos_produto_add") if produto is None else reverse(
            "admin:produtos_produto_change", args=[produto.pk]
        )
        return self.client.post(url, dados, HTTP_HOST="localhost", **kwargs)

    def consultar(self, codigo, status=200):
        resposta = self.client.get(
            reverse("produto-por-codigo", kwargs={"codigo_barra": codigo}),
            HTTP_HOST="localhost",
        )
        self.assertEqual(resposta.status_code, status)
        return resposta.json()

    def verificar_contrato(self, produto, com_imagem):
        conteudo = self.consultar(produto.codigo_barra)
        self.assertEqual(set(conteudo), {
            "codigo_barra", "nome", "descricao", "preco",
            "data_atualizacao", "imagem",
        })
        self.assertEqual(conteudo["codigo_barra"], produto.codigo_barra)
        self.assertEqual(conteudo["nome"], produto.nome)
        self.assertEqual(conteudo["descricao"], produto.descricao)
        self.assertEqual(conteudo["preco"], str(produto.preco))
        if com_imagem:
            caminho = caminho_imagem_produto(produto.codigo_barra)
            self.assertEqual(conteudo["imagem"], (
                f"{settings.IMAGENS_BASE_URL}media/{settings.IMAGENS_SUBPASTA}/"
                f"{produto.codigo_barra}.webp?v={caminho.stat().st_mtime_ns}"
            ))
        else:
            self.assertIsNone(conteudo["imagem"])
        return conteudo

    def estado_arquivos(self):
        return {
            str(caminho.relative_to(self.media_root)): (
                caminho.read_bytes(), caminho.stat().st_mtime_ns
            )
            for caminho in self.media_root.rglob("*") if caminho.is_file()
        }

    def verificar_conflito(self, dados, produto=None):
        estado_banco = list(Produto.objects.order_by("pk").values())
        estado_arquivos = self.estado_arquivos()
        api_anterior = self.consultar(produto.codigo_barra) if produto else None
        with patch("produtos.admin.publicar_imagem_produto") as publicar:
            with patch("produtos.admin.excluir_imagem_produto") as excluir:
                resposta = self.postar(dados, produto)
        self.assertEqual(resposta.status_code, 200)
        erros = resposta.context["adminform"].form.errors
        self.assertIn("codigo_barra", erros)
        mensagem = " ".join(erros["codigo_barra"]).lower()
        self.assertIn("imagem", mensagem)
        self.assertIn("regulariz", mensagem)
        self.assertContains(resposta, "errorlist")
        publicar.assert_not_called()
        excluir.assert_not_called()
        self.assertEqual(list(Produto.objects.order_by("pk").values()), estado_banco)
        self.assertEqual(self.estado_arquivos(), estado_arquivos)
        if produto:
            self.assertEqual(self.consultar(produto.codigo_barra), api_anterior)
        self.consultar(dados["codigo_barra"], status=404)
        return resposta

    def test_cadastro_sem_imagem_e_sem_residual(self):
        resposta = self.postar(self.dados())
        self.assertEqual(resposta.status_code, 302)
        produto = Produto.objects.get()
        self.verificar_contrato(produto, com_imagem=False)
        self.assertEqual(self.estado_arquivos(), {})

    def test_cadastro_com_upload_e_sem_residual(self):
        resposta = self.postar(self.dados(imagem_upload=self.criar_upload()))
        self.assertEqual(resposta.status_code, 302)
        self.verificar_contrato(Produto.objects.get(), com_imagem=True)

    def test_cadastro_sem_upload_bloqueia_imagem_orfa(self):
        self.criar_imagem(self.codigo_antigo)
        self.consultar(self.codigo_antigo, status=404)
        self.verificar_conflito(self.dados())

    def test_cadastro_com_upload_bloqueia_imagem_orfa(self):
        self.criar_imagem(self.codigo_antigo)
        self.verificar_conflito(self.dados(imagem_upload=self.criar_upload("blue")))

    def verificar_troca_com_conflito(self, com_imagem, com_upload):
        produto = self.criar_produto(com_imagem=com_imagem)
        self.criar_imagem(self.codigo_novo, "green")
        dados = self.dados(
            codigo_barra=self.codigo_novo,
            nome="Produto alterado",
            descricao="Descricao alterada",
            preco="99.99",
        )
        if com_upload:
            dados["imagem_upload"] = self.criar_upload("blue")
        self.verificar_conflito(dados, produto)

    def test_troca_sem_upload_bloqueia_imagem_orfa_no_destino(self):
        self.verificar_troca_com_conflito(com_imagem=True, com_upload=False)

    def test_troca_com_upload_bloqueia_imagem_orfa_no_destino(self):
        self.verificar_troca_com_conflito(com_imagem=True, com_upload=True)

    def test_troca_sem_imagem_de_origem_bloqueia_destino_orfao(self):
        self.verificar_troca_com_conflito(com_imagem=False, com_upload=False)

    def test_troca_com_upload_sem_imagem_de_origem_bloqueia_destino_orfao(self):
        self.verificar_troca_com_conflito(com_imagem=False, com_upload=True)

    def verificar_troca_sem_conflito(self, com_upload):
        produto = self.criar_produto()
        antigo = caminho_imagem_produto(produto.codigo_barra)
        conteudo = antigo.read_bytes()
        dados = self.dados(codigo_barra=self.codigo_novo)
        if com_upload:
            dados["imagem_upload"] = self.criar_upload("blue")
        resposta = self.postar(dados, produto)
        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, self.codigo_novo)
        novo = caminho_imagem_produto(produto.codigo_barra)
        self.assertFalse(antigo.exists())
        if com_upload:
            self.assertNotEqual(novo.read_bytes(), conteudo)
        else:
            self.assertEqual(novo.read_bytes(), conteudo)
        self.verificar_contrato(produto, com_imagem=True)
        self.consultar(self.codigo_antigo, status=404)

    def test_troca_sem_upload_e_sem_conflito(self):
        self.verificar_troca_sem_conflito(com_upload=False)

    def test_troca_com_upload_e_sem_conflito(self):
        self.verificar_troca_sem_conflito(com_upload=True)

    def test_edicao_do_proprio_codigo_preserva_imagem(self):
        produto = self.criar_produto()
        estado_arquivos = self.estado_arquivos()
        resposta = self.postar(self.dados(nome="Produto alterado"), produto)
        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Produto alterado")
        self.assertEqual(self.estado_arquivos(), estado_arquivos)
        self.verificar_contrato(produto, com_imagem=True)

    def test_edicao_do_proprio_codigo_sem_imagem(self):
        produto = self.criar_produto(com_imagem=False)
        resposta = self.postar(self.dados(nome="Produto alterado"), produto)
        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Produto alterado")
        self.verificar_contrato(produto, com_imagem=False)

    def test_substituicao_da_propria_imagem_continua_permitida(self):
        produto = self.criar_produto()
        caminho = caminho_imagem_produto(produto.codigo_barra)
        anterior = caminho.read_bytes()
        resposta = self.postar(
            self.dados(imagem_upload=self.criar_upload("blue")), produto
        )
        self.assertEqual(resposta.status_code, 302)
        self.assertNotEqual(caminho.read_bytes(), anterior)
        self.verificar_contrato(produto, com_imagem=True)

    def test_codigo_de_outro_produto_mantem_erro_de_unicidade(self):
        produto = self.criar_produto()
        self.criar_produto(codigo_barra=self.codigo_novo, nome="Outro produto")
        estado_banco = list(Produto.objects.order_by("pk").values())
        estado_arquivos = self.estado_arquivos()
        resposta = self.postar(self.dados(codigo_barra=self.codigo_novo), produto)
        self.assertEqual(resposta.status_code, 200)
        erros = resposta.context["adminform"].form.errors.as_data()
        self.assertIn("codigo_barra", erros)
        self.assertEqual(erros["codigo_barra"][0].code, "unique")
        self.assertEqual(list(Produto.objects.order_by("pk").values()), estado_banco)
        self.assertEqual(self.estado_arquivos(), estado_arquivos)

    def test_instancia_nova_com_codigo_preenchido_nao_legitima_orfa(self):
        self.criar_imagem(self.codigo_antigo)
        formulario = ProdutoAdminForm(
            data=self.dados(), instance=Produto(**self.dados())
        )
        self.assertFalse(formulario.is_valid())
        self.assertIn("codigo_barra", formulario.errors)
        self.assertFalse(Produto.objects.exists())

    def test_revalidacao_apos_primeiro_clean_mantem_codigo_original(self):
        produto = self.criar_produto()
        estado = Produto.objects.filter(pk=produto.pk).values().get()
        formulario = ProdutoAdminForm(
            data=self.dados(codigo_barra=self.codigo_novo), instance=produto
        )
        self.assertTrue(formulario.is_valid(), formulario.errors)
        self.criar_imagem(self.codigo_novo, "green")
        estado_arquivos = self.estado_arquivos()
        formulario.full_clean()
        self.assertFalse(formulario.is_valid())
        self.assertIn("codigo_barra", formulario.errors)
        self.assertEqual(Produto.objects.filter(pk=produto.pk).values().get(), estado)
        self.assertEqual(self.estado_arquivos(), estado_arquivos)

    def test_codigo_interno_com_zeros_iniciais_continua_permitido(self):
        resposta = self.postar(self.dados(codigo_barra="00123"))
        self.assertEqual(resposta.status_code, 302)
        produto = Produto.objects.get()
        self.assertEqual(produto.codigo_barra, "00123")
        self.verificar_contrato(produto, com_imagem=False)

    def test_digito_verificador_incorreto_continua_apenas_aviso(self):
        resposta = self.postar(self.dados(codigo_barra="12345671"), follow=True)
        self.assertEqual(resposta.status_code, 200)
        produto = Produto.objects.get()
        self.assertEqual(produto.codigo_barra, "12345671")
        avisos = list(get_messages(resposta.wsgi_request))
        self.assertTrue(any(
            aviso.level == messages.WARNING and "dígito verificador" in str(aviso)
            for aviso in avisos
        ))
        self.verificar_contrato(produto, com_imagem=False)

    def verificar_falha_na_consulta_do_destino(self, com_edicao):
        produto = self.criar_produto() if com_edicao else None
        codigo = self.codigo_novo if com_edicao else self.codigo_antigo
        destino = caminho_imagem_produto(codigo)
        estado_banco = list(Produto.objects.order_by("pk").values())
        estado_arquivos = self.estado_arquivos()
        stat_original = Path.stat

        def consultar_stat(caminho, *args, **kwargs):
            if caminho == destino:
                raise PermissionError("acesso ao destino negado")
            return stat_original(caminho, *args, **kwargs)

        with patch.object(Path, "stat", new=consultar_stat):
            with patch("produtos.admin.publicar_imagem_produto") as publicar:
                with self.assertLogs("produtos.forms", level="ERROR") as registros:
                    resposta = self.postar(self.dados(codigo_barra=codigo), produto)
        self.assertEqual(resposta.status_code, 200)
        self.assertIn("codigo_barra", resposta.context["adminform"].form.errors)
        self.assertContains(resposta, "Não foi possível verificar a imagem")
        self.assertTrue(all(registro.exc_info for registro in registros.records))
        publicar.assert_not_called()
        self.assertEqual(list(Produto.objects.order_by("pk").values()), estado_banco)
        self.assertEqual(self.estado_arquivos(), estado_arquivos)

    def test_cadastro_com_falha_na_consulta_do_destino_nao_persiste(self):
        self.verificar_falha_na_consulta_do_destino(com_edicao=False)

    def test_troca_com_falha_na_consulta_do_destino_preserva_produto(self):
        self.verificar_falha_na_consulta_do_destino(com_edicao=True)
