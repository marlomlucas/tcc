"""Testes da descoberta mDNS sem acesso real à rede ou ao MySQL."""

import io
import json
import unittest
from contextlib import redirect_stderr, redirect_stdout
from http.client import BadStatusLine, IncompleteRead
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError

from zeroconf import IPVersion, ServiceInfo

import anunciar_servidor as anuncio
import verificar_descoberta as descoberta


IP_TESTE = "192.0.2.10"
CAMINHO_PRODUTO = "/api/produtos/{codigo_barra}/"


def schema_valido():
    return {
        "openapi": "3.0.3",
        "paths": {CAMINHO_PRODUTO: {"get": {"responses": {"200": {}}}}},
    }


class TesteValidacaoEndereco(unittest.TestCase):
    def test_aceita_ipv4_unicast(self):
        self.assertEqual(anuncio.validar_ipv4(IP_TESTE), IP_TESTE)

    def test_rejeita_enderecos_inadequados(self):
        for valor in (
            "", "abc", "192.0.2.999", "::1", "127.0.0.1",
            "0.0.0.0", "169.254.10.20", "224.0.0.251", "255.255.255.255",
        ):
            with self.subTest(valor=valor):
                with self.assertRaises(ValueError):
                    anuncio.validar_ipv4(valor)

    @patch("anunciar_servidor.socket.socket")
    def test_confirma_endereco_local_sem_enviar_pacotes(self, socket):
        anuncio.validar_interface(IP_TESTE)

        socket.return_value.__enter__.return_value.bind.assert_called_once_with(
            (IP_TESTE, 0)
        )
        socket.return_value.__enter__.return_value.sendto.assert_not_called()

    @patch("anunciar_servidor.socket.socket")
    def test_rejeita_endereco_que_nao_pertence_ao_computador(self, socket):
        socket.return_value.__enter__.return_value.bind.side_effect = OSError(
            "endereco indisponivel"
        )

        with self.assertRaises(ValueError):
            anuncio.validar_interface(IP_TESTE)


class TesteVerificacaoBackend(unittest.TestCase):
    def setUp(self):
        self.opener_patch = patch("anunciar_servidor.build_opener")
        self.criar_cliente = self.opener_patch.start()
        self.opener = self.criar_cliente.return_value
        self.addCleanup(self.opener_patch.stop)
        self.resposta = self.opener.open.return_value.__enter__.return_value
        self.resposta.status = 200
        self.resposta.read.return_value = json.dumps(schema_valido()).encode()

    def test_consulta_endpoint_existente_com_timeout_e_json(self):
        anuncio.verificar_backend(IP_TESTE)

        argumentos = self.opener.open.call_args
        requisicao = argumentos.args[0]
        self.assertEqual(
            requisicao.full_url, f"http://{IP_TESTE}:8000/api/schema/"
        )
        self.assertEqual(requisicao.get_method(), "GET")
        self.assertEqual(
            requisicao.get_header("Accept"), "application/vnd.oai.openapi+json"
        )
        self.assertGreater(argumentos.kwargs["timeout"], 0)
        self.resposta.read.assert_called_once_with(anuncio.LIMITE_SCHEMA + 1)
        handlers = self.criar_cliente.call_args.args
        self.assertEqual(handlers[0].proxies, {})
        self.assertIsInstance(handlers[1], anuncio.SemRedirecionamento)

    def test_rejeita_http_diferente_de_200(self):
        self.resposta.status = 204

        with self.assertRaises((ValueError, RuntimeError)):
            anuncio.verificar_backend(IP_TESTE)

    def test_rejeita_resposta_que_nao_e_schema_da_api(self):
        documentos = (
            b"<html>Outro servidor</html>",
            b"[]",
            json.dumps({"paths": {}}).encode(),
            json.dumps({"openapi": "3.0.3", "paths": {}}).encode(),
            json.dumps({"openapi": "3.0.3", "paths": {CAMINHO_PRODUTO: {}}}).encode(),
        )
        for documento in documentos:
            with self.subTest(documento=documento):
                self.resposta.read.return_value = documento
                with self.assertRaises((ValueError, RuntimeError)):
                    anuncio.verificar_backend(IP_TESTE)

    def test_rejeita_resposta_acima_do_limite(self):
        self.resposta.read.return_value = b" " * (anuncio.LIMITE_SCHEMA + 1)

        with self.assertRaises((ValueError, RuntimeError)):
            anuncio.verificar_backend(IP_TESTE)

    def test_falhas_http_e_timeout_impedem_verificacao(self):
        for erro in (
            TimeoutError("tempo esgotado"),
            URLError("conexao indisponivel"),
            HTTPError("http://exemplo.invalid/", 400, "Host invalido", {}, None),
        ):
            with self.subTest(erro=type(erro).__name__):
                self.opener.open.side_effect = erro
                with self.assertRaises((OSError, RuntimeError)):
                    anuncio.verificar_backend(IP_TESTE)

    def test_redirecionamento_nao_e_seguido(self):
        handler = anuncio.SemRedirecionamento()

        self.assertIsNone(
            handler.redirect_request(Mock(), Mock(), 302, "Found", {},
                                     "http://exemplo.invalid/")
        )

    def test_resposta_http_malformada_recebe_erro_de_comunicacao(self):
        for erro in (BadStatusLine("resposta invalida"), IncompleteRead(b"parcial")):
            with self.subTest(erro=type(erro).__name__):
                self.resposta.read.side_effect = erro
                with self.assertRaises(RuntimeError) as contexto:
                    anuncio.verificar_backend(IP_TESTE)

                self.assertIs(contexto.exception.__cause__, erro)
                self.opener.open.return_value.__exit__.assert_called()


class TesteAnuncioServidor(unittest.TestCase):
    def test_contrato_do_servico_dns_sd(self):
        servico = anuncio.criar_servico(IP_TESTE)

        self.assertIsInstance(servico, ServiceInfo)
        self.assertEqual(servico.type, "_vprecos._tcp.local.")
        self.assertEqual(
            servico.name, "Verificador de Precos._vprecos._tcp.local."
        )
        self.assertEqual(servico.port, 8000)
        self.assertEqual(servico.parsed_addresses(IPVersion.V4Only), [IP_TESTE])
        self.assertEqual(servico.properties[b"app"], b"verificador-precos")
        self.assertEqual(servico.properties[b"version"], b"1.0")

    @patch("anunciar_servidor.Event")
    @patch("anunciar_servidor.Zeroconf")
    @patch("anunciar_servidor.verificar_backend")
    @patch("anunciar_servidor.validar_interface")
    def test_ctrl_c_desregistra_e_fecha_na_interface_escolhida(
        self, interface, verificar, zeroconf, evento
    ):
        evento.return_value.wait.side_effect = KeyboardInterrupt
        with redirect_stdout(io.StringIO()):
            anuncio.anunciar(IP_TESTE)

        interface.assert_called_once_with(IP_TESTE)
        verificar.assert_called_once_with(IP_TESTE)
        zeroconf.assert_called_once_with(
            interfaces=[IP_TESTE], ip_version=IPVersion.V4Only
        )
        zc = zeroconf.return_value
        servico = zc.register_service.call_args.args[0]
        zc.unregister_service.assert_called_once_with(servico)
        zc.close.assert_called_once_with()

    @patch("anunciar_servidor.Zeroconf")
    @patch("anunciar_servidor.verificar_backend", side_effect=RuntimeError("offline"))
    @patch("anunciar_servidor.validar_interface")
    def test_nao_anuncia_quando_backend_indisponivel(self, interface, verificar, zc):
        with redirect_stdout(io.StringIO()):
            with self.assertRaises(RuntimeError):
                anuncio.anunciar(IP_TESTE)

        zc.assert_not_called()

    @patch("anunciar_servidor.Zeroconf")
    @patch("anunciar_servidor.verificar_backend")
    @patch("anunciar_servidor.validar_interface")
    def test_falha_de_registro_fecha_recursos_sem_mascarar_erro(
        self, interface, verificar, zeroconf
    ):
        erro = RuntimeError("registro falhou")
        zc = zeroconf.return_value
        zc.register_service.side_effect = erro
        zc.close.side_effect = OSError("limpeza falhou")

        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(RuntimeError) as contexto:
                anuncio.anunciar(IP_TESTE)

        self.assertIs(contexto.exception, erro)
        zc.close.assert_called_once_with()

    def test_falha_ao_desregistrar_ainda_fecha_zeroconf(self):
        zc = Mock()
        zc.unregister_service.side_effect = OSError("falha de multicast")
        with redirect_stderr(io.StringIO()):
            resultado = anuncio.encerrar_zeroconf(zc, Mock())

        self.assertFalse(resultado)
        zc.close.assert_called_once_with()

    @patch("anunciar_servidor.anunciar")
    @patch("builtins.input", return_value=IP_TESTE)
    def test_argumento_ip_opcional_solicita_selecao_manual(self, entrada, anunciar):
        with redirect_stdout(io.StringIO()):
            codigo = anuncio.main([])

        self.assertEqual(codigo, 0)
        entrada.assert_called_once()
        anunciar.assert_called_once_with(IP_TESTE)

    @patch("anunciar_servidor.anunciar")
    @patch("builtins.input", side_effect=EOFError)
    def test_sem_entrada_padrao_orienta_argumento_ip(self, entrada, anunciar):
        saida = io.StringIO()
        with redirect_stderr(saida), redirect_stdout(io.StringIO()):
            codigo = anuncio.main([])

        self.assertNotEqual(codigo, 0)
        self.assertIn("--ip", saida.getvalue())
        anunciar.assert_not_called()


class TesteOuvinteDescoberta(unittest.TestCase):
    def setUp(self):
        self.ouvinte = descoberta.OuvinteServicos()
        self.zc = Mock()
        self.info = anuncio.criar_servico(IP_TESTE)
        self.zc.get_service_info.return_value = self.info

    def adicionar(self):
        self.ouvinte.add_service(self.zc, self.info.type, self.info.name)

    def test_resolve_endereco_porta_e_metadados(self):
        self.adicionar()

        self.zc.get_service_info.assert_called_once_with(
            self.info.type, self.info.name, timeout=2000
        )
        resultados = self.ouvinte.resultados()
        self.assertEqual(len(resultados), 1)
        self.assertEqual(resultados[0]["nome"], self.info.name)
        self.assertEqual(resultados[0]["enderecos"], [IP_TESTE])
        self.assertEqual(resultados[0]["porta"], 8000)
        self.assertEqual(
            resultados[0]["propriedades"]["app"], "verificador-precos"
        )

    def test_atualizacao_substitui_endereco_sem_duplicar(self):
        self.adicionar()
        nova = anuncio.criar_servico("192.0.2.11")
        self.zc.get_service_info.return_value = nova
        self.ouvinte.update_service(self.zc, nova.type, nova.name)

        self.assertEqual(len(self.ouvinte.resultados()), 1)
        self.assertEqual(self.ouvinte.resultados()[0]["enderecos"], ["192.0.2.11"])

    def test_falha_na_atualizacao_remove_endereco_anterior(self):
        self.adicionar()
        self.zc.get_service_info.side_effect = OSError("falha na resolucao")
        saida = io.StringIO()

        with redirect_stderr(saida):
            self.ouvinte.update_service(self.zc, self.info.type, self.info.name)

        self.assertEqual(self.ouvinte.resultados(), [])
        self.assertIn("falha na resolucao", saida.getvalue())

    def test_remocao_retira_resultado(self):
        self.adicionar()
        self.ouvinte.remove_service(self.zc, self.info.type, self.info.name)

        self.assertEqual(self.ouvinte.resultados(), [])

    def test_resultado_nao_resolvido_e_ignorado(self):
        self.zc.get_service_info.return_value = None
        self.adicionar()

        self.assertEqual(self.ouvinte.resultados(), [])

    def test_servico_de_outra_aplicacao_e_ignorado(self):
        self.adicionar()
        self.zc.get_service_info.return_value = ServiceInfo(
            self.info.type, self.info.name, port=8000,
            parsed_addresses=[IP_TESTE], properties={"app": "outra-aplicacao"},
        )
        self.adicionar()

        self.assertEqual(self.ouvinte.resultados(), [])

    def test_servico_sem_ipv4_e_ignorado(self):
        self.zc.get_service_info.return_value = ServiceInfo(
            self.info.type, self.info.name, port=8000,
            parsed_addresses=["2001:db8::1"],
            properties={"app": "verificador-precos", "version": "1.0"},
        )
        self.adicionar()

        self.assertEqual(self.ouvinte.resultados(), [])


class TesteDiagnosticoDescoberta(unittest.TestCase):
    @patch("verificar_descoberta.Event")
    @patch("verificar_descoberta.ServiceBrowser")
    @patch("verificar_descoberta.Zeroconf")
    def test_busca_apenas_tipo_mdns_e_libera_recursos(self, zeroconf, browser, evento):
        resultado = descoberta.descobrir_servidores(tempo=0.1)

        self.assertEqual(resultado, [])
        zeroconf.assert_called_once_with(ip_version=IPVersion.V4Only)
        self.assertEqual(browser.call_args.args[:2], (
            zeroconf.return_value, "_vprecos._tcp.local."
        ))
        evento.return_value.wait.assert_called_once_with(0.1)
        browser.return_value.cancel.assert_called_once_with()
        zeroconf.return_value.close.assert_called_once_with()

    @patch("verificar_descoberta.Zeroconf")
    def test_rejeita_tempo_nao_finito_ou_nao_positivo(self, zeroconf):
        for tempo in (0, -1, float("inf"), float("nan")):
            with self.subTest(tempo=tempo):
                with self.assertRaises(ValueError):
                    descoberta.descobrir_servidores(tempo=tempo)

        zeroconf.assert_not_called()

    @patch("verificar_descoberta.ServiceBrowser", side_effect=RuntimeError("falhou"))
    @patch("verificar_descoberta.Zeroconf")
    def test_falha_ao_iniciar_busca_fecha_zeroconf(self, zeroconf, browser):
        with self.assertRaises(RuntimeError):
            descoberta.descobrir_servidores()

        zeroconf.return_value.close.assert_called_once_with()

    @patch("verificar_descoberta.Event")
    @patch("verificar_descoberta.ServiceBrowser")
    @patch("verificar_descoberta.Zeroconf")
    @patch("verificar_descoberta.validar_interface")
    def test_interface_explicita_restringe_busca_sem_selecao_automatica(
        self, interface, zeroconf, browser, evento
    ):
        descoberta.descobrir_servidores(ip=IP_TESTE, tempo=0.1)

        interface.assert_called_once_with(IP_TESTE)
        zeroconf.assert_called_once_with(
            ip_version=IPVersion.V4Only, interfaces=[IP_TESTE]
        )

    @patch("verificar_descoberta.Event")
    @patch("verificar_descoberta.ServiceBrowser")
    @patch("verificar_descoberta.Zeroconf")
    def test_falha_ao_cancelar_busca_ainda_fecha_zeroconf(
        self, zeroconf, browser, evento
    ):
        browser.return_value.cancel.side_effect = OSError("cancelamento falhou")

        with redirect_stderr(io.StringIO()):
            with self.assertRaises(RuntimeError):
                descoberta.descobrir_servidores(tempo=0.1)

        zeroconf.return_value.close.assert_called_once_with()

    @patch("verificar_descoberta.Event")
    @patch("verificar_descoberta.ServiceBrowser")
    @patch("verificar_descoberta.Zeroconf")
    def test_ctrl_c_cancela_browser_e_fecha_zeroconf(self, zeroconf, browser, evento):
        evento.return_value.wait.side_effect = KeyboardInterrupt

        with self.assertRaises(KeyboardInterrupt):
            descoberta.descobrir_servidores()

        browser.return_value.cancel.assert_called_once_with()
        zeroconf.return_value.close.assert_called_once_with()

    @patch("verificar_descoberta.descobrir_servidores", return_value=[])
    def test_cli_informa_ausencia_sem_declarar_sucesso(self, buscar):
        saida = io.StringIO()
        with redirect_stdout(saida), redirect_stderr(io.StringIO()):
            codigo = descoberta.main(["--tempo", "0.1"])

        self.assertNotEqual(codigo, 0)
        self.assertTrue(saida.getvalue())

    @patch("verificar_descoberta.descobrir_servidores")
    def test_cli_apresenta_endereco_encontrado_como_json(self, buscar):
        registro = {
            "nome": anuncio.NOME_SERVICO,
            "enderecos": [IP_TESTE],
            "porta": 8000,
            "propriedades": {"app": "verificador-precos", "version": "1.0"},
        }
        buscar.return_value = [registro]
        saida = io.StringIO()

        with redirect_stdout(saida):
            codigo = descoberta.main(["--ip", IP_TESTE, "--tempo", "0.1"])

        self.assertEqual(codigo, 0)
        self.assertEqual(json.loads(saida.getvalue().splitlines()[-1]), registro)
        buscar.assert_called_once_with(IP_TESTE, 0.1)


if __name__ == "__main__":
    unittest.main()
