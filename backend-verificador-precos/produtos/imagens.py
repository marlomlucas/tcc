
import os
import re
import tempfile

from io import BytesIO
from pathlib import Path

from django.conf import settings
from django.core.exceptions import ValidationError

from PIL import Image, ImageOps, UnidentifiedImageError


# Configurações de processamento.
TAMANHO_MAXIMO = 5 * 1024 * 1024  # 5 MB
MAXIMO_PIXELS = 20_000_000
RESOLUCAO_MAXIMA = (1200, 1200)
QUALIDADE_WEBP = 80


def caminho_imagem_produto(codigo_barra: str) -> Path:
    """
    Retorna o caminho físico da imagem do produto.
    """

    # Validação do código utilizado no nome do arquivo.
    if (
        not isinstance(codigo_barra, str)
        or not re.fullmatch(r"[0-9]{1,20}", codigo_barra)
    ):
        raise ValidationError(
            "O código de barras deve conter de 1 a 20 dígitos."
        )

    return (
        Path(settings.MEDIA_ROOT)
        / settings.IMAGENS_SUBPASTA
        / f"{codigo_barra}.webp"
    )


def preparar_imagem_produto(arquivo) -> bytes:
    """
    Valida e converte a imagem integralmente em memória, sem criar arquivos.
    """

    # Valida o tamanho do arquivo enviado.
    tamanho = getattr(arquivo, "size", None)

    if tamanho is None or not (0 < tamanho <= TAMANHO_MAXIMO):
        raise ValidationError(
            "A imagem deve possuir até 5 MB e não pode estar vazia."
        )

    try:
        arquivo.seek(0)

        with Image.open(arquivo) as original:

            # Confere o formato real da imagem.
            if original.format not in {"JPEG", "PNG", "WEBP"}:
                raise ValidationError(
                    "Formato inválido. Utilize JPG, PNG ou WebP."
                )

            # Evita imagens com resolução excessiva.
            if original.width * original.height > MAXIMO_PIXELS:
                raise ValidationError(
                    "A resolução da imagem é muito grande."
                )

            # Detecta falhas de decodificação antes de persistir o produto.
            original.load()

            # Corrige a orientação da imagem.
            with ImageOps.exif_transpose(original) as imagem:

                # Redimensiona preservando as proporções.
                imagem.thumbnail(
                    RESOLUCAO_MAXIMA,
                    Image.Resampling.LANCZOS
                )

                # Preserva a transparência quando presente.
                if (
                    "A" in imagem.getbands()
                    or "transparency" in imagem.info
                ):
                    modo = "RGBA"
                else:
                    modo = "RGB"

                with imagem.convert(modo) as convertida:
                    with BytesIO() as buffer:
                        convertida.save(
                            buffer,
                            format="WEBP",
                            quality=QUALIDADE_WEBP,
                            method=6
                        )
                        return buffer.getvalue()

    except ValidationError:
        raise

    except (
        UnidentifiedImageError,
        Image.DecompressionBombError,
        OSError,
        SyntaxError,
        ValueError
    ) as erro:
        raise ValidationError(
            "Não foi possível processar ou converter a imagem para WebP."
        ) from erro


def salvar_imagem_produto(
    codigo_barra: str,
    arquivo,
    *,
    imagem_preparada: bytes | None = None
) -> Path:
    """
    Salva o WebP pelo código de barras, substituindo o arquivo anterior.

    Sem imagem_preparada, mantém a preparação para os chamadores existentes.
    O formulário pode fornecer seus bytes já validados para evitar reconversão.
    """

    destino = caminho_imagem_produto(codigo_barra)

    if imagem_preparada is None:
        imagem_preparada = preparar_imagem_produto(arquivo)

    if not isinstance(imagem_preparada, bytes) or not imagem_preparada:
        raise ValidationError("A imagem preparada está vazia ou é inválida.")

    temporario = None

    try:
        destino.parent.mkdir(parents=True, exist_ok=True)

        with tempfile.NamedTemporaryFile(
            dir=destino.parent,
            prefix=f".{codigo_barra}_",
            suffix=".webp",
            delete=False
        ) as arquivo_temporario:
            temporario = Path(arquivo_temporario.name)
            arquivo_temporario.write(imagem_preparada)

        # O temporário está fechado antes da substituição, inclusive no Windows.
        os.replace(temporario, destino)

    except (OSError, ValueError) as erro:
        raise ValidationError("Não foi possível salvar a imagem.") from erro

    finally:
        # Remove arquivos temporários remanescentes.
        if temporario is not None:
            temporario.unlink(missing_ok=True)

    return destino


def excluir_imagem_produto(codigo_barra: str) -> bool:
    """
    Exclui a imagem associada ao código de barras.

    Retorna True quando a imagem é excluída.
    Retorna False quando a imagem não existe.
    """

    caminho = caminho_imagem_produto(codigo_barra)

    try:
        caminho.unlink()
        return True

    except FileNotFoundError:
        return False
