"""Menu do inicializador Windows: Django local ou Django e mDNS em dois terminais."""

import argparse
import ctypes
import os
from pathlib import Path
import socket
import subprocess
import sys
import time

import ifaddr

import anunciar_servidor as anuncio


BASE_DIR = Path(__file__).resolve().parent


def listar_interfaces() -> list[tuple[str, str]]:
    """Lista IPv4 atribuídos à máquina; o usuário escolhe a rede do celular."""
    encontrados = {}
    for adaptador in ifaddr.get_adapters():
        nome = adaptador.nice_name or adaptador.name
        for endereco in adaptador.ips:
            if not isinstance(endereco.ip, str):
                continue
            try:
                ip = anuncio.validar_ipv4(endereco.ip)
            except ValueError:
                continue
            encontrados.setdefault(ip, str(getattr(endereco, "nice_name", None) or nome))
    return [(nome, ip) for ip, nome in encontrados.items()]


def selecionar_ip() -> str | None:
    try:
        interfaces = listar_interfaces()
    except OSError as erro:
        print(f"Nao foi possivel listar as interfaces: {erro}")
        interfaces = []
    print("\nEscolha a interface que compartilha a rede com o celular:")
    for numero, (nome, ip) in enumerate(interfaces, 1):
        print(f"  {numero} - {nome}: {ip}")
    if not interfaces:
        print("  Nenhum IPv4 adequado foi encontrado automaticamente.")
    print("  M - Digitar o IPv4 manualmente")
    print("  0 - Voltar ao menu")
    while True:
        escolha = input("Interface ou M: ").strip()
        if escolha == "0":
            return None
        if escolha.upper() == "M":
            valor = input("IPv4 deste computador: ").strip()
        elif escolha.isascii() and escolha.isdecimal() and 1 <= int(escolha) <= len(interfaces):
            valor = interfaces[int(escolha) - 1][1]
        else:
            print("Opcao invalida. Escolha um numero da lista, M ou 0.")
            continue
        try:
            ip = anuncio.validar_ipv4(valor)
            anuncio.validar_interface(ip)
            return ip
        except ValueError as erro:
            print(f"IP nao aceito: {erro}")


def verificar_porta(host: str) -> None:
    """Evita abrir outro Django quando a porta já está ocupada."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as conexao:
            if os.name == "nt":
                conexao.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            conexao.bind((host, anuncio.PORTA_SERVICO))
    except OSError as erro:
        raise RuntimeError(
            f"A porta {anuncio.PORTA_SERVICO} nao esta disponivel. "
            "Confira se outro servidor ja esta aberto e encerre-o antes de tentar novamente."
        ) from erro


def abrir_terminal(tipo: str, ip: str | None = None) -> subprocess.Popen:
    argumentos = [sys.executable, "-B", str(BASE_DIR / "iniciar_servidor.py"), "--terminal", tipo]
    if ip is not None:
        argumentos.extend(["--ip", ip])
    return subprocess.Popen(
        argumentos,
        cwd=BASE_DIR,
        creationflags=subprocess.CREATE_NEW_CONSOLE,
    )


def iniciar_local() -> None:
    verificar_porta("127.0.0.1")
    abrir_terminal("django-local")
    print("\nTerminal DJANGO LOCAL aberto: http://127.0.0.1:8000/")
    print("Modo off-line: somente este computador, sem anuncio mDNS.")


def iniciar_rede(ip: str) -> None:
    ip = anuncio.validar_ipv4(ip)
    anuncio.validar_interface(ip)
    verificar_porta("0.0.0.0")
    abrir_terminal("django-rede")
    try:
        abrir_terminal("anuncio", ip)
    except OSError as erro:
        raise RuntimeError(
            "O terminal Django foi aberto, mas o terminal mDNS falhou. "
            "Encerre Django com Ctrl+C antes de tentar novamente."
        ) from erro
    print(f"\nTerminais DJANGO REDE e MDNS abertos. API: http://{ip}:8000/")
    print("Confira no terminal MDNS a confirmacao de anuncio ativo.")
    print("Para encerrar: Ctrl+C no MDNS e depois Ctrl+C no DJANGO.")


def aguardar_backend(ip: str, limite: float = 30) -> None:
    """Aguarda prontidão, sem substituir as validações do anunciador."""
    prazo = time.monotonic() + limite
    while True:
        try:
            anuncio.verificar_backend(ip)
            return
        except RuntimeError as erro:
            restante = prazo - time.monotonic()
            if restante <= 0:
                raise RuntimeError(
                    f"A API nao ficou disponivel. {erro} "
                    "Confira o terminal Django, o MySQL e DJANGO_ALLOWED_HOSTS. "
                    "Nenhum anuncio foi registrado."
                ) from erro
            time.sleep(min(1, restante))


def definir_titulo(titulo: str) -> None:
    if os.name == "nt":
        ctypes.windll.kernel32.SetConsoleTitleW(titulo)


def executar_terminal(tipo: str, ip: str | None = None) -> int:
    codigo = 0
    try:
        if tipo == "anuncio":
            definir_titulo("Verificador de Precos - MDNS")
            ip = anuncio.validar_ipv4(ip)
            anuncio.validar_interface(ip)
            print(f"Aguardando a API em http://{ip}:8000/ (aproximadamente 30 segundos)...", flush=True)
            print("A URL das imagens continua independente da API.", flush=True)
            aguardar_backend(ip)
            codigo = anuncio.main(["--ip", ip])
        else:
            local = tipo == "django-local"
            definir_titulo("Verificador de Precos - DJANGO " + ("LOCAL" if local else "REDE"))
            endereco = "127.0.0.1:8000" if local else "0.0.0.0:8000"
            print(f"Iniciando Django em {endereco}. Ctrl+C encerra este servidor.", flush=True)
            codigo = subprocess.run(
                [sys.executable, "-B", str(BASE_DIR / "manage.py"), "runserver", endereco],
                cwd=BASE_DIR,
                check=False,
            ).returncode
    except KeyboardInterrupt:
        print("\nInicializacao ou servidor interrompido.")
    except (OSError, ValueError, RuntimeError) as erro:
        print(f"\nNao foi possivel iniciar: {erro}", file=sys.stderr)
        codigo = 1
    if tipo == "django-rede":
        print("Encerre tambem o anuncio com Ctrl+C no terminal MDNS.")
    elif tipo == "anuncio":
        print("O anuncio terminou. O servidor Django possui encerramento separado.")
    try:
        input("\nPressione Enter para fechar este terminal...")
    except (EOFError, KeyboardInterrupt):
        pass
    return codigo


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--terminal", choices=("django-local", "django-rede", "anuncio"))
    parser.add_argument("--ip", help="IPv4 selecionado para o terminal mDNS.")
    argumentos = parser.parse_args(argv)
    if argumentos.terminal:
        if argumentos.terminal == "anuncio" and argumentos.ip is None:
            parser.error("O terminal de anuncio exige --ip.")
        return executar_terminal(argumentos.terminal, argumentos.ip)
    try:
        if not (BASE_DIR / "manage.py").is_file():
            raise RuntimeError("manage.py nao encontrado na pasta do inicializador.")
        while True:
            print("\n==========================================")
            print("       VERIFICADOR DE PRECOS")
            print("==========================================")
            print("1 - LOCAL (OFF-LINE): somente este computador")
            print("2 - REDE (ON-LINE): aplicativo Android na rede local")
            print("0 - Sair")
            print("Internet nao e necessaria; MySQL deve estar disponivel.")
            opcao = input("Escolha uma opcao: ").strip()
            if opcao == "0":
                return 0
            if opcao == "1":
                iniciar_local()
                return 0
            if opcao == "2":
                ip = selecionar_ip()
                if ip is not None:
                    iniciar_rede(ip)
                    return 0
            else:
                print("Opcao invalida. Digite 1, 2 ou 0.")
    except (EOFError, KeyboardInterrupt):
        print("\nInicializacao cancelada.")
        return 0
    except (OSError, ValueError, RuntimeError) as erro:
        print(f"Nao foi possivel iniciar: {erro}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
