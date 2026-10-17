from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory

from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings

from PIL import Image

from .imagens import (
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

    def criar_imagem(self, cor):
        buffer = BytesIO()

        Image.new(
            "RGB",
            (100, 100),
            cor
        ).save(buffer, format="PNG")

        return SimpleUploadedFile(
            "imagem.png",
            buffer.getvalue(),
            content_type="image/png"
        )

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
