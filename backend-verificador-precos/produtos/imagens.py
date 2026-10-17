
import os
import re
import tempfile

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


def salvar_imagem_produto(codigo_barra: str, arquivo) -> Path:
    """
    Recebe uma imagem, converte para WebP e salva
    utilizando o código de barras como nome.

    Se o arquivo já existir, será substituído.
    """

    destino = caminho_imagem_produto(codigo_barra)

    # Valida o tamanho do arquivo enviado.
    tamanho = getattr(arquivo, "size", None)

    if tamanho is None or not (0 < tamanho <= TAMANHO_MAXIMO):
        raise ValidationError(
            "A imagem deve possuir até 5 MB e não pode estar vazia."
        )

    temporario = None

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

                    # Cria a pasta, caso ainda não exista.
                    destino.parent.mkdir(
                        parents=True,
                        exist_ok=True
                    )

                    # Cria um arquivo temporário.
                    with tempfile.NamedTemporaryFile(
                        dir=destino.parent,
                        prefix=f".{codigo_barra}_",
                        suffix=".webp",
                        delete=False
                    ) as arquivo_temporario:
                        temporario = Path(
                            arquivo_temporario.name
                        )

                    # Salva a imagem convertida.
                    convertida.save(
                        temporario,
                        format="WEBP",
                        quality=QUALIDADE_WEBP,
                        method=6
                    )

                    # Substitui o arquivo anterior.
                    os.replace(temporario, destino)

    except ValidationError:
        raise

    except (
        UnidentifiedImageError,
        Image.DecompressionBombError,
        OSError,
        ValueError
    ) as erro:
        raise ValidationError(
            "Não foi possível processar ou salvar a imagem."
        ) from erro

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
