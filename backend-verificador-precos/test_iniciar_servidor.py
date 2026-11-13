"""Testes do inicializador sem criar processos, acessar a rede ou importar Django."""

import io
import subprocess
import sys
import unittest
from contextlib import redirect_stderr, redirect_stdout
from types import SimpleNamespace
from unittest.mock import Mock, call, patch

import iniciar_servidor as inicializador


IP_TESTE = "192.0.2.10"


class TesteSelecaoInterface(unittest.TestCase):
    @patch("iniciar_servidor.ifaddr.get_adapters")
    def test_lista_ipv4_validos_sem_ipv6_loopback_apipa_ou_duplicatas(self, adaptadores):
        adaptadores.return_value = [
            SimpleNamespace(
                nice_name="Adaptador sem fio", name="interface-1",
                ips=[SimpleNamespace(ip=ip, nice_name="Wi-Fi") for ip in (
                    IP_TESTE, "127.0.0.1", "169.254.10.20", "224.0.0.251",
                    "0.0.0.0", ("fe80::1", 0, 0),
                )],
            ),
            SimpleNamespace(
                nice_name="", name="Ethernet",
                ips=[SimpleNamespace(ip=IP_TESTE), SimpleNamespace(ip="198.51.100.20")],
            ),
        ]

        self.assertEqual(
            inicializador.listar_interfaces(),
            [("Wi-Fi", IP_TESTE), ("Ethernet", "198.51.100.20")],
        )

    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.listar_interfaces", return_value=[("Wi-Fi", IP_TESTE)])
    @patch("builtins.input", return_value="1")
    def test_selecao_por_numero_valida_que_ip_ainda_pertence_a_maquina(
        self, entrada, interfaces, validar_interface
    ):
        saida = io.StringIO()
        with redirect_stdout(saida):
            ip = inicializador.selecionar_ip()

        self.assertEqual(ip, IP_TESTE)
        self.assertIn(f"Wi-Fi: {IP_TESTE}", saida.getvalue())
        validar_interface.assert_called_once_with(IP_TESTE)

    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.listar_interfaces", return_value=[])
    @patch("builtins.input", side_effect=["m", f"  {IP_TESTE}  "])
    def test_manual_funciona_sem_interface_listada(self, entrada, interfaces, validar_interface):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.selecionar_ip(), IP_TESTE)
        validar_interface.assert_called_once_with(IP_TESTE)

    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.listar_interfaces", return_value=[("Wi-Fi", IP_TESTE)])
    @patch("builtins.input", side_effect=["", "99", "abc", "١", "1"])
    def test_entrada_invalida_nao_e_tratada_como_indice_da_lista(
        self, entrada, interfaces, validar_interface
    ):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.selecionar_ip(), IP_TESTE)
        validar_interface.assert_called_once_with(IP_TESTE)

    @patch("iniciar_servidor.anuncio.validar_interface", side_effect=ValueError("IP de outra maquina"))
    @patch("iniciar_servidor.listar_interfaces", return_value=[])
    @patch("builtins.input", side_effect=["M", "127.0.0.1", "M", IP_TESTE, "0"])
    def test_manual_invalido_ou_nao_local_nao_e_aceito(
        self, entrada, interfaces, validar_interface
    ):
        with redirect_stdout(io.StringIO()):
            self.assertIsNone(inicializador.selecionar_ip())
        validar_interface.assert_called_once_with(IP_TESTE)

    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.listar_interfaces", return_value=[("Wi-Fi", IP_TESTE)])
    @patch("builtins.input", return_value="0")
    def test_voltar_nao_escolhe_interface_automaticamente(
        self, entrada, interfaces, validar_interface
    ):
        with redirect_stdout(io.StringIO()):
            self.assertIsNone(inicializador.selecionar_ip())
        validar_interface.assert_not_called()

    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.listar_interfaces", side_effect=OSError("indisponivel"))
    @patch("builtins.input", side_effect=["M", IP_TESTE])
    def test_falha_na_listagem_preserva_alternativa_manual(
        self, entrada, interfaces, validar_interface
    ):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.selecionar_ip(), IP_TESTE)
        validar_interface.assert_called_once_with(IP_TESTE)


class TesteAberturaTerminais(unittest.TestCase):
    @patch("iniciar_servidor.subprocess.Popen")
    def test_terminal_recebe_argumentos_separados_sem_shell(self, popen):
        processo = inicializador.abrir_terminal("anuncio", IP_TESTE)

        popen.assert_called_once_with(
            [sys.executable, "-B", str(inicializador.BASE_DIR / "iniciar_servidor.py"),
             "--terminal", "anuncio", "--ip", IP_TESTE],
            cwd=inicializador.BASE_DIR,
            creationflags=subprocess.CREATE_NEW_CONSOLE,
        )
        self.assertIs(processo, popen.return_value)

    @patch("iniciar_servidor.abrir_terminal")
    @patch("iniciar_servidor.verificar_porta")
    def test_modo_local_abre_apenas_django_sem_anuncio(self, porta, abrir):
        with redirect_stdout(io.StringIO()):
            inicializador.iniciar_local()
        porta.assert_called_once_with("127.0.0.1")
        abrir.assert_called_once_with("django-local")

    @patch("iniciar_servidor.abrir_terminal")
    @patch("iniciar_servidor.verificar_porta")
    @patch("iniciar_servidor.anuncio.validar_interface")
    def test_modo_rede_valida_ip_e_abre_django_e_mdns(self, interface, porta, abrir):
        with redirect_stdout(io.StringIO()):
            inicializador.iniciar_rede(IP_TESTE)
        interface.assert_called_once_with(IP_TESTE)
        porta.assert_called_once_with("0.0.0.0")
        self.assertEqual(abrir.call_args_list, [call("django-rede"), call("anuncio", IP_TESTE)])

    @patch("iniciar_servidor.abrir_terminal")
    @patch("iniciar_servidor.verificar_porta", side_effect=RuntimeError("porta ocupada"))
    @patch("iniciar_servidor.anuncio.validar_interface")
    def test_porta_ocupada_impede_abrir_os_dois_terminais(self, interface, porta, abrir):
        with self.assertRaisesRegex(RuntimeError, "porta ocupada"):
            inicializador.iniciar_rede(IP_TESTE)
        abrir.assert_not_called()

    @patch("iniciar_servidor.abrir_terminal", side_effect=[Mock(), OSError("falha")])
    @patch("iniciar_servidor.verificar_porta")
    @patch("iniciar_servidor.anuncio.validar_interface")
    def test_falha_no_segundo_terminal_informa_que_django_precisa_ser_encerrado(
        self, interface, porta, abrir
    ):
        with self.assertRaisesRegex(RuntimeError, "Encerre Django com Ctrl\\+C"):
            inicializador.iniciar_rede(IP_TESTE)

    @patch("iniciar_servidor.socket.socket")
    def test_porta_verificada_sem_iniciar_servidor(self, socket):
        inicializador.verificar_porta("0.0.0.0")
        conexao = socket.return_value.__enter__.return_value
        conexao.bind.assert_called_once_with(("0.0.0.0", 8000))
        conexao.listen.assert_not_called()
        socket.return_value.__exit__.assert_called_once()


class TesteProntidaoBackend(unittest.TestCase):
    @patch("iniciar_servidor.time.sleep")
    @patch("iniciar_servidor.anuncio.verificar_backend")
    def test_api_pronta_nao_utiliza_atraso_fixo(self, verificar, dormir):
        inicializador.aguardar_backend(IP_TESTE)
        verificar.assert_called_once_with(IP_TESTE)
        dormir.assert_not_called()

    @patch("iniciar_servidor.time.monotonic", side_effect=[0, 1])
    @patch("iniciar_servidor.time.sleep")
    @patch("iniciar_servidor.anuncio.verificar_backend", side_effect=[RuntimeError("iniciando"), None])
    def test_api_demora_e_verificacao_e_repetida(self, verificar, dormir, relogio):
        inicializador.aguardar_backend(IP_TESTE)
        self.assertEqual(verificar.call_count, 2)
        dormir.assert_called_once_with(1)

    @patch("iniciar_servidor.time.monotonic", side_effect=[0, 31])
    @patch("iniciar_servidor.time.sleep")
    @patch("iniciar_servidor.anuncio.verificar_backend", side_effect=RuntimeError("HTTP 400"))
    def test_timeout_orienta_configuracao_e_preserva_causa(self, verificar, dormir, relogio):
        with self.assertRaisesRegex(RuntimeError, "DJANGO_ALLOWED_HOSTS") as contexto:
            inicializador.aguardar_backend(IP_TESTE)
        self.assertIs(contexto.exception.__cause__, verificar.side_effect)
        self.assertIn("Nenhum anuncio", str(contexto.exception))
        dormir.assert_not_called()


class TesteExecucaoTerminal(unittest.TestCase):
    @patch("builtins.input", return_value="")
    @patch("iniciar_servidor.definir_titulo")
    @patch("iniciar_servidor.subprocess.run", return_value=SimpleNamespace(returncode=0))
    def test_terminal_django_preserva_escuta_local_e_rede(self, executar, titulo, entrada):
        for tipo, endereco in (("django-local", "127.0.0.1:8000"), ("django-rede", "0.0.0.0:8000")):
            with self.subTest(tipo=tipo), redirect_stdout(io.StringIO()):
                self.assertEqual(inicializador.executar_terminal(tipo), 0)
                executar.assert_called_with(
                    [sys.executable, "-B", str(inicializador.BASE_DIR / "manage.py"), "runserver", endereco],
                    cwd=inicializador.BASE_DIR, check=False,
                )

    @patch("builtins.input", return_value="")
    @patch("iniciar_servidor.definir_titulo")
    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.aguardar_backend")
    @patch("iniciar_servidor.anuncio.main", return_value=0)
    def test_mdns_so_e_iniciado_depois_da_api_pronta(
        self, anunciar, aguardar, interface, titulo, entrada
    ):
        ordem = Mock()
        ordem.attach_mock(aguardar, "aguardar")
        ordem.attach_mock(anunciar, "anunciar")
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.executar_terminal("anuncio", IP_TESTE), 0)
        self.assertEqual(ordem.mock_calls, [call.aguardar(IP_TESTE), call.anunciar(["--ip", IP_TESTE])])

    @patch("builtins.input", return_value="")
    @patch("iniciar_servidor.definir_titulo")
    @patch("iniciar_servidor.anuncio.validar_interface")
    @patch("iniciar_servidor.aguardar_backend", side_effect=RuntimeError("API indisponivel"))
    @patch("iniciar_servidor.anuncio.main")
    def test_api_indisponivel_nao_cria_anuncio(self, anunciar, aguardar, interface, titulo, entrada):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            self.assertEqual(inicializador.executar_terminal("anuncio", IP_TESTE), 1)
        anunciar.assert_not_called()


class TesteMenuPrincipal(unittest.TestCase):
    @patch("iniciar_servidor.iniciar_rede")
    @patch("iniciar_servidor.iniciar_local")
    @patch("builtins.input", side_effect=["invalida", "0"])
    def test_opcao_invalida_permanece_no_menu_e_sair_nao_abre_servicos(self, entrada, local, rede):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.main([]), 0)
        local.assert_not_called()
        rede.assert_not_called()

    @patch("iniciar_servidor.iniciar_rede")
    @patch("iniciar_servidor.iniciar_local")
    @patch("builtins.input", return_value="1")
    def test_opcao_local_nao_seleciona_ou_anuncia_ip(self, entrada, local, rede):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.main([]), 0)
        local.assert_called_once_with()
        rede.assert_not_called()

    @patch("iniciar_servidor.iniciar_rede")
    @patch("iniciar_servidor.selecionar_ip", side_effect=[None, IP_TESTE])
    @patch("builtins.input", side_effect=["2", "2"])
    def test_rede_permite_voltar_e_depois_escolher_ip(self, entrada, selecionar, rede):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(inicializador.main([]), 0)
        self.assertEqual(selecionar.call_count, 2)
        rede.assert_called_once_with(IP_TESTE)

    @patch("iniciar_servidor.iniciar_local", side_effect=OSError("Python indisponivel"))
    @patch("builtins.input", return_value="1")
    def test_falha_ao_abrir_terminal_retorna_erro_sem_oculta_lo(self, entrada, local):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            self.assertEqual(inicializador.main([]), 1)

    @patch("iniciar_servidor.executar_terminal")
    def test_terminal_de_anuncio_exige_ip(self, executar):
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as contexto:
            inicializador.main(["--terminal", "anuncio"])
        self.assertEqual(contexto.exception.code, 2)
        executar.assert_not_called()


if __name__ == "__main__":
    unittest.main()
