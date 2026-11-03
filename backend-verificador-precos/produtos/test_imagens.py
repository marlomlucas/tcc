from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings

from PIL import Image

from .imagens import (
    preparar_imagem_produto,
    salvar_imagem_produto,
    excluir_imagem_produto,
)


class TesteImagens(SimpleTestCase):

    def setUp(self):
        self.diretorio = TemporaryDirectory()

        self.configuracao = override_settings(
            MEDIA_ROOT=Path(self.diretorio.name)
        )
        self.configuracao.enable()

    def tearDown(self):
        self.configuracao.disable()
        self.diretorio.cleanup()

    def criar_imagem(self, cor, *, formato="PNG", tamanho=(100, 100), modo="RGB"):
        with BytesIO() as buffer:
            with Image.new(modo, tamanho, cor) as imagem:
                imagem.save(buffer, format=formato)

            return SimpleUploadedFile(
                f"imagem.{formato.lower()}",
                buffer.getvalue(),
                content_type=f"image/{formato.lower()}"
            )

    def test_preparar_webp_sem_criar_arquivos(self):
        conteudo = preparar_imagem_produto(self.criar_imagem("red"))

        self.assertIsInstance(conteudo, bytes)
        self.assertEqual(list(Path(self.diretorio.name).iterdir()), [])
        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.format, "WEBP")
            imagem.load()

    def test_redimensionar_preservando_proporcoes(self):
        arquivo = self.criar_imagem("blue", tamanho=(2400, 1200))

        conteudo = preparar_imagem_produto(arquivo)

        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.size, (1200, 600))

    def test_preservar_orientacao_exif(self):
        exif = Image.Exif()
        exif[274] = 6
        with BytesIO() as buffer:
            with Image.new("RGB", (160, 80), "red") as original:
                original.save(buffer, format="JPEG", exif=exif)
            arquivo = SimpleUploadedFile("imagem.jpg", buffer.getvalue())

        conteudo = preparar_imagem_produto(arquivo)

        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.size, (80, 160))

    def test_preservar_transparencia_rgba(self):
        arquivo = self.criar_imagem((30, 60, 90, 80), modo="RGBA")

        conteudo = preparar_imagem_produto(arquivo)

        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.mode, "RGBA")
            self.assertEqual(imagem.getpixel((0, 0))[3], 80)

    def test_preservar_transparencia_de_paleta(self):
        with BytesIO() as buffer:
            with Image.new("P", (4, 4), 0) as original:
                original.putpalette([255, 0, 0, 0, 0, 255] + [0] * 762)
                original.putpixel((1, 1), 1)
                original.save(buffer, format="PNG", transparency=0)
            arquivo = SimpleUploadedFile("imagem.png", buffer.getvalue())

        conteudo = preparar_imagem_produto(arquivo)

        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.getpixel((0, 0))[3], 0)
            self.assertEqual(imagem.getpixel((1, 1))[3], 255)

    def test_rejeitar_falha_na_decodificacao_completa(self):
        original = self.criar_imagem("red", formato="JPEG")
        truncada = original.read()[:-20]
        # O cabeçalho passa na verificação; a decodificação dos pixels falha.
        with Image.open(BytesIO(truncada)) as imagem:
            imagem.verify()
        arquivo = SimpleUploadedFile("truncada.jpg", truncada)

        with self.assertRaises(ValidationError):
            preparar_imagem_produto(arquivo)

        self.assertEqual(list(Path(self.diretorio.name).iterdir()), [])

    def test_rejeitar_falha_na_conversao_sem_criar_arquivos(self):
        arquivo = self.criar_imagem("red")

        with patch("PIL.Image.Image.save", side_effect=OSError("Falha de conversão")):
            with self.assertRaises(ValidationError):
                preparar_imagem_produto(arquivo)

        self.assertEqual(list(Path(self.diretorio.name).iterdir()), [])

    def test_rejeitar_metadados_exif_corrompidos(self):
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

                with self.assertRaises(ValidationError):
                    preparar_imagem_produto(arquivo)

        self.assertEqual(list(Path(self.diretorio.name).iterdir()), [])

    def test_reutilizar_bytes_preparados_sem_reabrir_upload(self):
        arquivo = self.criar_imagem("green")
        conteudo = preparar_imagem_produto(arquivo)
        arquivo.close()

        with patch("produtos.imagens.preparar_imagem_produto") as preparar:
            caminho = salvar_imagem_produto(
                "12345670", arquivo, imagem_preparada=conteudo
            )

        preparar.assert_not_called()
        self.assertEqual(caminho.read_bytes(), conteudo)

    def test_aceitar_limite_exato_de_pixels(self):
        arquivo = self.criar_imagem(128, tamanho=(5000, 4000), modo="L")

        conteudo = preparar_imagem_produto(arquivo)

        with Image.open(BytesIO(conteudo)) as imagem:
            self.assertEqual(imagem.size, (1200, 960))

    def test_converter_e_renomear(self):
        arquivo = self.criar_imagem("red")

        caminho = salvar_imagem_produto(
            "12345670",
            arquivo
        )

        self.assertEqual(
            caminho.name,
            "12345670.webp"
        )

        self.assertTrue(caminho.exists())

        with Image.open(caminho) as imagem:
            self.assertEqual(imagem.format, "WEBP")

    def test_substituir_imagem_existente(self):
        codigo = "12345670"

        caminho = salvar_imagem_produto(
            codigo,
            self.criar_imagem("red")
        )

        conteudo_anterior = caminho.read_bytes()

        salvar_imagem_produto(
            codigo,
            self.criar_imagem("blue")
        )

        self.assertNotEqual(
            conteudo_anterior,
            caminho.read_bytes()
        )

        arquivos = list(
            caminho.parent.glob("*.webp")
        )

        self.assertEqual(len(arquivos), 1)

    def test_imagem_invalida_preserva_anterior(self):
        codigo = "12345670"

        caminho = salvar_imagem_produto(
            codigo,
            self.criar_imagem("green")
        )

        conteudo_anterior = caminho.read_bytes()

        arquivo_invalido = SimpleUploadedFile(
            "arquivo.png",
            b"arquivo invalido",
            content_type="image/png"
        )

        with self.assertRaises(ValidationError):
            salvar_imagem_produto(
                codigo,
                arquivo_invalido
            )

        self.assertEqual(
            caminho.read_bytes(),
            conteudo_anterior
        )

    def test_codigo_barra_invalido(self):
        with self.assertRaises(ValidationError):
            salvar_imagem_produto(
                "../produto",
                self.criar_imagem("red")
            )



    def test_excluir_imagem_existente(self):
        codigo = "12345670"

        # Cria uma imagem de teste.
        caminho = salvar_imagem_produto(
            codigo,
            self.criar_imagem("red")
        )

        self.assertTrue(caminho.exists())

        # Exclui a imagem.
        resultado = excluir_imagem_produto(codigo)

        self.assertTrue(resultado)
        self.assertFalse(caminho.exists())

    def test_excluir_imagem_inexistente(self):
        codigo = "12345670"

        # Tenta excluir uma imagem inexistente.
        resultado = excluir_imagem_produto(codigo)

        self.assertFalse(resultado)
