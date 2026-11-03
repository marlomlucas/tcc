import os
import tempfile
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings

from PIL import Image

from .imagens import (
    caminho_imagem_produto,
    preparar_imagem_produto,
    publicar_imagem_produto,
    salvar_imagem_produto,
)


class TestePublicacaoImagens(SimpleTestCase):
    def setUp(self):
        self.diretorio = TemporaryDirectory()
        self.addCleanup(self.diretorio.cleanup)
        self.media_root = Path(self.diretorio.name)
        configuracao = override_settings(MEDIA_ROOT=self.media_root)
        configuracao.enable()
        self.addCleanup(configuracao.disable)
        self.codigo = "12345670"
        self.destino = caminho_imagem_produto(self.codigo)

    def criar_upload(self, cor="red"):
        with BytesIO() as buffer:
            with Image.new("RGB", (40, 20), cor) as imagem:
                imagem.save(buffer, format="PNG")
            return SimpleUploadedFile("imagem.png", buffer.getvalue(), "image/png")

    def criar_webp(self, cor="red"):
        return preparar_imagem_produto(self.criar_upload(cor))

    def criar_imagem_anterior(self):
        conteudo = self.criar_webp("green")
        self.destino.parent.mkdir(parents=True, exist_ok=True)
        self.destino.write_bytes(conteudo)
        return conteudo

    def verificar_apenas_imagem_anterior(self, conteudo):
        self.assertEqual(self.destino.read_bytes(), conteudo)
        self.assertEqual(list(self.destino.parent.iterdir()), [self.destino])

    def test_preparacao_nao_modifica_imagem_definitiva(self):
        anterior = self.criar_imagem_anterior()

        preparado = preparar_imagem_produto(self.criar_upload("blue"))

        self.assertNotEqual(preparado, anterior)
        self.verificar_apenas_imagem_anterior(anterior)

    def test_publicar_bytes_preparados_sem_converter(self):
        preparado = self.criar_webp()

        with patch("produtos.imagens.preparar_imagem_produto") as preparar:
            with patch.object(Image.Image, "save") as converter:
                resultado = publicar_imagem_produto(self.codigo, preparado)

        preparar.assert_not_called()
        converter.assert_not_called()
        self.assertEqual(resultado, self.destino)
        self.assertEqual(self.destino.read_bytes(), preparado)
        self.assertEqual(list(self.destino.parent.iterdir()), [self.destino])

    def test_publicar_substitui_imagem_existente(self):
        anterior = self.criar_imagem_anterior()
        preparado = self.criar_webp("blue")

        publicar_imagem_produto(self.codigo, preparado)

        self.assertNotEqual(self.destino.read_bytes(), anterior)
        self.assertEqual(self.destino.read_bytes(), preparado)
        self.assertEqual(list(self.destino.parent.iterdir()), [self.destino])

    def test_temporario_no_destino_fechado_antes_da_substituicao(self):
        preparado = self.criar_webp()
        criar_temporario = tempfile.NamedTemporaryFile
        substituir = os.replace
        arquivos_temporarios = []

        def criar(*args, **kwargs):
            self.assertEqual(Path(kwargs["dir"]), self.destino.parent)
            arquivo = criar_temporario(*args, **kwargs)
            arquivos_temporarios.append(arquivo)
            return arquivo

        def promover(origem, destino):
            self.assertEqual(len(arquivos_temporarios), 1)
            self.assertTrue(arquivos_temporarios[0].closed)
            self.assertEqual(Path(origem).parent, self.destino.parent)
            self.assertEqual(Path(destino), self.destino)
            self.assertEqual(Path(origem).read_bytes(), preparado)
            return substituir(origem, destino)

        with patch("produtos.imagens.tempfile.NamedTemporaryFile", side_effect=criar):
            with patch("produtos.imagens.os.replace", side_effect=promover) as promover_mock:
                publicar_imagem_produto(self.codigo, preparado)

        promover_mock.assert_called_once()
        self.assertEqual(self.destino.read_bytes(), preparado)
        self.assertEqual(list(self.destino.parent.iterdir()), [self.destino])

    def test_falha_de_escrita_preserva_anterior_e_limpa_temporario(self):
        anterior = self.criar_imagem_anterior()
        preparado = self.criar_webp("blue")
        falha = OSError("escrita parcial simulada")
        criar_temporario = tempfile.NamedTemporaryFile

        class EscritaComFalha:
            def __init__(self, arquivo):
                self.arquivo = arquivo
                self.name = arquivo.name

            def __enter__(self):
                self.arquivo.__enter__()
                return self

            def __exit__(self, *args):
                return self.arquivo.__exit__(*args)

            def __getattr__(self, nome):
                return getattr(self.arquivo, nome)

            def write(self, conteudo):
                self.arquivo.write(conteudo[: len(conteudo) // 2])
                self.arquivo.flush()
                raise falha

        def criar(*args, **kwargs):
            return EscritaComFalha(criar_temporario(*args, **kwargs))

        with patch("produtos.imagens.tempfile.NamedTemporaryFile", side_effect=criar):
            with patch("produtos.imagens.os.replace") as promover:
                with self.assertRaises(ValidationError) as resultado:
                    publicar_imagem_produto(self.codigo, preparado)

        promover.assert_not_called()
        self.assertIs(resultado.exception.__cause__, falha)
        self.verificar_apenas_imagem_anterior(anterior)

    def test_falha_no_replace_preserva_anterior_e_limpa_temporario(self):
        anterior = self.criar_imagem_anterior()
        preparado = self.criar_webp("blue")
        falha = OSError("substituicao simulada")

        with patch("produtos.imagens.os.replace", side_effect=falha):
            with self.assertRaises(ValidationError) as resultado:
                publicar_imagem_produto(self.codigo, preparado)

        self.assertIs(resultado.exception.__cause__, falha)
        self.verificar_apenas_imagem_anterior(anterior)

    def test_falha_no_replace_nao_publica_cadastro_novo(self):
        preparado = self.criar_webp()

        with patch("produtos.imagens.os.replace", side_effect=OSError("falha simulada")):
            with self.assertRaises(ValidationError):
                publicar_imagem_produto(self.codigo, preparado)

        self.assertFalse(self.destino.exists())
        self.assertEqual(list(self.destino.parent.iterdir()), [])

    def test_falha_na_limpeza_nao_mascara_erro_de_publicacao(self):
        anterior = self.criar_imagem_anterior()
        preparado = self.criar_webp("blue")
        falha_publicacao = OSError("substituicao simulada")
        remover = Path.unlink

        def limpar(caminho, *args, **kwargs):
            if caminho.name.startswith("." + self.codigo + "_"):
                raise PermissionError("limpeza simulada")
            return remover(caminho, *args, **kwargs)

        with patch("produtos.imagens.os.replace", side_effect=falha_publicacao):
            with patch.object(Path, "unlink", new=limpar):
                with self.assertLogs("produtos.imagens", level="ERROR") as registros:
                    with self.assertRaises(ValidationError) as resultado:
                        publicar_imagem_produto(self.codigo, preparado)

        self.assertIs(resultado.exception.__cause__, falha_publicacao)
        self.assertEqual(self.destino.read_bytes(), anterior)
        temporarios = list(self.destino.parent.glob("." + self.codigo + "_*"))
        self.assertEqual(len(temporarios), 1)
        self.assertIn("limpeza simulada", "\n".join(registros.output))

    def test_salvar_mantem_chamada_existente_e_delega_publicacao(self):
        arquivo = self.criar_upload()

        with patch(
            "produtos.imagens.preparar_imagem_produto", wraps=preparar_imagem_produto
        ) as preparar:
            with patch(
                "produtos.imagens.publicar_imagem_produto", wraps=publicar_imagem_produto
            ) as publicar:
                resultado = salvar_imagem_produto(self.codigo, arquivo)

        preparar.assert_called_once_with(arquivo)
        publicar.assert_called_once()
        self.assertEqual(resultado, self.destino)
        with Image.open(self.destino) as imagem:
            imagem.load()
            self.assertEqual(imagem.format, "WEBP")
            self.assertEqual(imagem.size, (40, 20))

    def test_salvar_reutiliza_preparacao_sem_processar_upload_novamente(self):
        arquivo = self.criar_upload()
        preparado = preparar_imagem_produto(arquivo)
        arquivo.close()

        with patch("produtos.imagens.preparar_imagem_produto") as preparar:
            with patch(
                "produtos.imagens.publicar_imagem_produto", wraps=publicar_imagem_produto
            ) as publicar:
                salvar_imagem_produto(
                    self.codigo, arquivo, imagem_preparada=preparado
                )

        preparar.assert_not_called()
        publicar.assert_called_once()
        self.assertEqual(self.destino.read_bytes(), preparado)

    def test_publicador_rejeita_argumentos_invalidos_sem_criar_arquivos(self):
        for conteudo in (b"", None, "nao sao bytes"):
            with self.subTest(conteudo=conteudo):
                with self.assertRaises(ValidationError):
                    publicar_imagem_produto(self.codigo, conteudo)

        with self.assertRaises(ValidationError):
            publicar_imagem_produto("../produto", self.criar_webp())

        self.assertEqual(list(self.media_root.iterdir()), [])
