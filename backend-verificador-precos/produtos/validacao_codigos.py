
import re


def verificar_digito_verificador(codigo: str) -> bool | None:
    """
    Verifica o dígito verificador de códigos EAN-8,
    UPC-A e EAN-13 utilizando o algoritmo módulo 10.

    Retorna:
        True: dígito verificador correto.
        False: dígito verificador incorreto.
        None: verificação não aplicável.
    """

    # Somente códigos numéricos de 8, 12 ou 13 dígitos.
    if not isinstance(codigo, str):
        return None

    if not re.fullmatch(r"[0-9]{8}|[0-9]{12}|[0-9]{13}", codigo):
        return None

    # Separa o dígito verificador informado.
    digito_informado = int(codigo[-1])

    # Considera os dígitos anteriores ao verificador.
    numeros = codigo[:-1]

    soma = 0

    # Aplica pesos alternados de 3 e 1, da direita para a esquerda.
    for posicao, numero in enumerate(reversed(numeros)):

        peso = 3 if posicao % 2 == 0 else 1

        soma += int(numero) * peso

    # Calcula o dígito verificador esperado.
    digito_calculado = (10 - (soma % 10)) % 10

    return digito_informado == digito_calculado
