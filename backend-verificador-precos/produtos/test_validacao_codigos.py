
from django.test import SimpleTestCase

from .validacao_codigos import verificar_digito_verificador


class TesteValidacaoCodigos(SimpleTestCase):

    def test_ean8_valido(self):
        resultado = verificar_digito_verificador("12345670")
        self.assertTrue(resultado)

    def test_ean8_invalido(self):
        resultado = verificar_digito_verificador("12345671")
        self.assertFalse(resultado)

    def test_upca_valido(self):
        resultado = verificar_digito_verificador("036000291452")
        self.assertTrue(resultado)

    def test_ean13_valido(self):
        resultado = verificar_digito_verificador("4006381333931")
        self.assertTrue(resultado)

    def test_ean13_invalido(self):
        resultado = verificar_digito_verificador("4006381333932")
        self.assertFalse(resultado)

    def test_codigo_interno(self):
        resultado = verificar_digito_verificador("12345")
        self.assertIsNone(resultado)

    def test_codigo_com_letras(self):
        resultado = verificar_digito_verificador("ABC12345")
        self.assertIsNone(resultado)

    def test_codigo_com_zero_inicial(self):
        resultado = verificar_digito_verificador("036000291452")
        self.assertTrue(resultado)
