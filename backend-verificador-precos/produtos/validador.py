import re


def codigo_barra_valido(codigo_barra):
    # Verifica se o código possui somente números
    # e se tem entre 1 e 20 caracteres.
    return bool(re.fullmatch(r"[0-9]{1,20}", codigo_barra))