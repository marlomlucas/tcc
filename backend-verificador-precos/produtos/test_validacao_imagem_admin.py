from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse

from PIL import Image

from .forms import ProdutoAdminForm
from .imagens import TAMANHO_MAXIMO, caminho_imagem_produto, salvar_imagem_produto
from .models import Produto


class TesteValidacaoImagemAdmin(TestCase):
    def setUp(self):
        self.diretorio = TemporaryDirectory()
        self.addCleanup(self.diretorio.cleanup)
        self.media_root = Path(self.diretorio.name)
        self.configuracao = override_settings(MEDIA_ROOT=self.media_root)
        self.configuracao.enable()
        self.addCleanup(self.configuracao.disable)

        usuario = get_user_model().objects.create_superuser(
            username="admin_validacao_imagem",
            password=None,
        )
        self.client.force_login(usuario)

    def dados_produto(self, **alteracoes):
        dados = {
            "codigo_barra": "12345670",
            "nome": "Produto de teste",
            "descricao": "Descricao original",
            "preco": "10.00",
        }
        dados.update(alteracoes)
        return dados

    def criar_imagem(self, formato="PNG", cor="red", tamanho=(40, 20), modo="RGB"):
        with BytesIO() as buffer:
            with Image.new(modo, tamanho, cor) as imagem:
                imagem.save(buffer, format=formato)
            conteudo = buffer.getvalue()
        return SimpleUploadedFile(
            "imagem." + formato.lower(),
            conteudo,
            content_type="image/" + formato.lower(),
        )

    def criar_formulario(self, arquivo=None, **alteracoes):
        arquivos = {"imagem_upload": arquivo} if arquivo is not None else {}
        return ProdutoAdminForm(data=self.dados_produto(**alteracoes), files=arquivos)

    def verificar_formato_valido(self, formato):
        formulario = self.criar_formulario(self.criar_imagem(formato))

        self.assertTrue(formulario.is_valid(), formulario.errors)
        self.assertIsInstance(formulario.imagem_preparada, bytes)
        with Image.open(BytesIO(formulario.imagem_preparada)) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")
            self.assertEqual(imagem.size, (40, 20))
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(list(self.media_root.iterdir()), [])

    def criar_produto_com_imagem(self):
        produto = Produto.objects.create(**self.dados_produto())
        caminho = salvar_imagem_produto(produto.codigo_barra, self.criar_imagem())
        estado = Produto.objects.filter(pk=produto.pk).values().get()
        return produto, caminho, estado, caminho.read_bytes()

    def verificar_edicao_rejeitada(self, produto, caminho, estado, conteudo, resposta):
        self.assertEqual(resposta.status_code, 200)
        self.assertIn("imagem_upload", resposta.context["adminform"].form.errors)
        self.assertEqual(Produto.objects.filter(pk=produto.pk).values().get(), estado)
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertFalse(caminho_imagem_produto("96385074").exists())
        self.assertEqual(list(caminho.parent.glob("*.webp")), [caminho])

    def postar_com_contagem_de_conversoes(self, url, dados):
        salvar_original = Image.Image.save
        conversoes = []

        def salvar(imagem, arquivo, format=None, **parametros):
            if format == "WEBP":
                conversoes.append(True)
            return salvar_original(imagem, arquivo, format=format, **parametros)

        with patch.object(Image.Image, "save", new=salvar):
            resposta = self.client.post(url, dados, HTTP_HOST="localhost")

        self.assertEqual(resposta.status_code, 302)
        self.assertEqual(len(conversoes), 1)
        return resposta

    def test_formulario_aceita_jpeg(self):
        self.verificar_formato_valido("JPEG")

    def test_formulario_aceita_png(self):
        self.verificar_formato_valido("PNG")

    def test_formulario_aceita_webp(self):
        self.verificar_formato_valido("WEBP")

    def test_formulario_aceita_imagem_ausente(self):
        formulario = self.criar_formulario()

        self.assertTrue(formulario.is_valid(), formulario.errors)
        self.assertIsNone(formulario.imagem_preparada)
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_formulario_rejeita_arquivo_invalido(self):
        arquivo = SimpleUploadedFile("imagem.png", b"arquivo invalido", "image/png")
        formulario = self.criar_formulario(arquivo)

        self.assertFalse(formulario.is_valid())
        self.assertIn("imagem_upload", formulario.errors)
        self.assertIsNone(formulario.imagem_preparada)

    def test_formulario_rejeita_imagem_truncada(self):
        arquivo = self.criar_imagem()
        corrompido = SimpleUploadedFile("imagem.png", arquivo.read()[:40], "image/png")
        formulario = self.criar_formulario(corrompido)

        self.assertFalse(formulario.is_valid())
        self.assertIn("imagem_upload", formulario.errors)

    def test_formulario_rejeita_gif_valido(self):
        formulario = self.criar_formulario(self.criar_imagem("GIF"))

        self.assertFalse(formulario.is_valid())
        self.assertIn("imagem_upload", formulario.errors)

    def test_formulario_rejeita_arquivo_acima_de_cinco_mb(self):
        conteudo = self.criar_imagem().read()
        conteudo += b"\0" * (TAMANHO_MAXIMO + 1 - len(conteudo))
        formulario = self.criar_formulario(
            SimpleUploadedFile("imagem.png", conteudo, "image/png")
        )

        self.assertFalse(formulario.is_valid())
        self.assertIn("imagem_upload", formulario.errors)
        self.assertIsNone(formulario.imagem_preparada)
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_formulario_aceita_arquivo_no_limite_de_cinco_mb(self):
        conteudo = self.criar_imagem().read()
        conteudo += b"\0" * (TAMANHO_MAXIMO - len(conteudo))
        formulario = self.criar_formulario(
            SimpleUploadedFile("imagem.png", conteudo, "image/png")
        )

        self.assertTrue(formulario.is_valid(), formulario.errors)
        self.assertIsInstance(formulario.imagem_preparada, bytes)
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_formulario_rejeita_mais_de_vinte_milhoes_de_pixels(self):
        arquivo = self.criar_imagem(tamanho=(5000, 4001), modo="L", cor=0)
        self.assertLess(arquivo.size, TAMANHO_MAXIMO)
        formulario = self.criar_formulario(arquivo)

        self.assertFalse(formulario.is_valid())
        self.assertIn("imagem_upload", formulario.errors)
        self.assertIsNone(formulario.imagem_preparada)
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_formulario_rejeita_falha_na_conversao_webp(self):
        arquivo = self.criar_imagem()
        formulario = self.criar_formulario(arquivo)

        with patch.object(Image.Image, "save", side_effect=OSError("falha simulada")):
            self.assertFalse(formulario.is_valid())

        self.assertIn("imagem_upload", formulario.errors)
        self.assertIsNone(formulario.imagem_preparada)
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_outro_campo_invalido_nao_publica_imagem(self):
        formulario = self.criar_formulario(self.criar_imagem(), preco="invalido")

        self.assertFalse(formulario.is_valid())
        self.assertIn("preco", formulario.errors)
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_cadastro_com_imagem_invalida_nao_persiste_produto(self):
        dados = self.dados_produto()
        dados["imagem_upload"] = SimpleUploadedFile(
            "imagem.png", b"arquivo invalido", "image/png"
        )

        resposta = self.client.post(
            reverse("admin:produtos_produto_add"), dados, HTTP_HOST="localhost"
        )

        self.assertEqual(resposta.status_code, 200)
        self.assertIn("imagem_upload", resposta.context["adminform"].form.errors)
        self.assertFalse(Produto.objects.exists())
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_cadastro_converte_uma_vez_e_salva_webp(self):
        dados = self.dados_produto()
        dados["imagem_upload"] = self.criar_imagem()

        self.postar_com_contagem_de_conversoes(
            reverse("admin:produtos_produto_add"), dados
        )

        produto = Produto.objects.get(codigo_barra=dados["codigo_barra"])
        caminho = caminho_imagem_produto(produto.codigo_barra)
        with Image.open(caminho) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")
        self.assertEqual(list(caminho.parent.glob("*.webp")), [caminho])

    def test_cadastro_sem_imagem_persiste_normalmente(self):
        resposta = self.client.post(
            reverse("admin:produtos_produto_add"),
            self.dados_produto(),
            HTTP_HOST="localhost",
        )

        self.assertEqual(resposta.status_code, 302)
        self.assertEqual(Produto.objects.count(), 1)
        self.assertEqual(list(self.media_root.iterdir()), [])

    def test_edicao_com_imagem_invalida_preserva_dados_e_arquivo(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        dados = self.dados_produto(
            codigo_barra="96385074",
            nome="Nome alterado",
            descricao="Descricao alterada",
            preco="99.99",
        )
        dados["imagem_upload"] = SimpleUploadedFile(
            "imagem.png", b"arquivo invalido", "image/png"
        )

        resposta = self.client.post(
            reverse("admin:produtos_produto_change", args=[produto.pk]),
            dados,
            HTTP_HOST="localhost",
        )

        self.verificar_edicao_rejeitada(produto, caminho, estado, conteudo, resposta)

    def test_edicao_com_falha_de_conversao_preserva_dados_e_arquivo(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        dados = self.dados_produto(
            codigo_barra="96385074", nome="Nome alterado", preco="99.99"
        )
        dados["imagem_upload"] = self.criar_imagem(cor="blue")

        with patch.object(Image.Image, "save", side_effect=OSError("falha simulada")):
            resposta = self.client.post(
                reverse("admin:produtos_produto_change", args=[produto.pk]),
                dados,
                HTTP_HOST="localhost",
            )

        self.verificar_edicao_rejeitada(produto, caminho, estado, conteudo, resposta)

    def test_edicao_com_exif_corrompido_preserva_dados_e_arquivo(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        for formato in ("PNG", "WEBP"):
            with self.subTest(formato=formato):
                with BytesIO() as buffer:
                    with Image.new("RGB", (40, 20), "red") as original:
                        original.save(
                            buffer, format=formato, exif=b"Exif\0\0invalid metadata"
                        )
                    arquivo = SimpleUploadedFile(
                        f"corrompida.{formato.lower()}", buffer.getvalue()
                    )
                dados = self.dados_produto(
                    codigo_barra="96385074", nome="Nome alterado", preco="99.99"
                )
                dados["imagem_upload"] = arquivo

                resposta = self.client.post(
                    reverse("admin:produtos_produto_change", args=[produto.pk]),
                    dados,
                    HTTP_HOST="localhost",
                )

                self.verificar_edicao_rejeitada(
                    produto, caminho, estado, conteudo, resposta
                )

    def test_edicao_valida_converte_uma_vez_e_substitui_imagem(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        dados = self.dados_produto(nome="Nome alterado", preco="20.00")
        dados["imagem_upload"] = self.criar_imagem(cor="blue")

        self.postar_com_contagem_de_conversoes(
            reverse("admin:produtos_produto_change", args=[produto.pk]), dados
        )

        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Nome alterado")
        self.assertEqual(str(produto.preco), "20.00")
        self.assertNotEqual(caminho.read_bytes(), conteudo)
        with Image.open(caminho) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")

    def test_edicao_sem_upload_preserva_imagem(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        modificacao_anterior = caminho.stat().st_mtime_ns

        resposta = self.client.post(
            reverse("admin:produtos_produto_change", args=[produto.pk]),
            self.dados_produto(nome="Nome alterado", preco="20.00"),
            HTTP_HOST="localhost",
        )

        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.nome, "Nome alterado")
        self.assertEqual(str(produto.preco), "20.00")
        self.assertEqual(caminho.read_bytes(), conteudo)
        self.assertEqual(caminho.stat().st_mtime_ns, modificacao_anterior)
        self.assertEqual(list(caminho.parent.iterdir()), [caminho])

    def test_alterar_codigo_sem_upload_preserva_bytes(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()

        resposta = self.client.post(
            reverse("admin:produtos_produto_change", args=[produto.pk]),
            self.dados_produto(codigo_barra="96385074"),
            HTTP_HOST="localhost",
        )

        self.assertEqual(resposta.status_code, 302)
        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, "96385074")
        caminho_novo = caminho_imagem_produto(produto.codigo_barra)
        self.assertFalse(caminho.exists())
        self.assertEqual(caminho_novo.read_bytes(), conteudo)
        self.assertEqual(list(caminho_novo.parent.iterdir()), [caminho_novo])

    def test_alterar_codigo_com_upload_converte_uma_vez(self):
        produto, caminho, estado, conteudo = self.criar_produto_com_imagem()
        dados = self.dados_produto(codigo_barra="96385074")
        dados["imagem_upload"] = self.criar_imagem(cor="blue")

        self.postar_com_contagem_de_conversoes(
            reverse("admin:produtos_produto_change", args=[produto.pk]), dados
        )

        produto.refresh_from_db()
        self.assertEqual(produto.codigo_barra, "96385074")
        caminho_novo = caminho_imagem_produto(produto.codigo_barra)
        self.assertFalse(caminho.exists())
        self.assertNotEqual(caminho_novo.read_bytes(), conteudo)
        with Image.open(caminho_novo) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")
        self.assertEqual(list(caminho_novo.parent.iterdir()), [caminho_novo])
