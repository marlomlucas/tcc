"""Anuncia o backend existente por mDNS, em um processo separado do Django."""

import argparse
import ipaddress
import json
import socket
import sys
from http.client import HTTPException
from threading import Event
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

from zeroconf import Error, IPVersion, NonUniqueNameException, ServiceInfo, Zeroconf


TIPO_SERVICO = "_vprecos._tcp.local."
NOME_SERVICO = f"Verificador de Precos.{TIPO_SERVICO}"
PORTA_SERVICO = 8000
PROPRIEDADES = {"app": "verificador-precos", "version": "1.0"}
CAMINHO_PRODUTO = "/api/produtos/{codigo_barra}/"
LIMITE_SCHEMA = 1024 * 1024


def validar_ipv4(valor: str) -> str:
    """Aceita somente um IPv4 unicast adequado a uma interface de rede."""
    try:
        endereco = ipaddress.IPv4Address(valor.strip())
    except (ipaddress.AddressValueError, AttributeError) as erro:
        raise ValueError("Informe um IPv4 válido da interface Wi-Fi ou Ethernet.") from erro
    if (
        endereco.is_loopback
        or endereco.is_unspecified
        or endereco.is_multicast
        or endereco.is_link_local
        or endereco.is_reserved
    ):
        raise ValueError("O IPv4 deve ser da rede local, sem loopback, multicast ou endereço reservado.")
    return str(endereco)


def validar_interface(ip: str) -> None:
    """Confirma que o endereço informado está atribuído a este computador."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as conexao:
            conexao.bind((ip, 0))
    except OSError as erro:
        raise ValueError(
            f"O IPv4 {ip} não está disponível neste computador. Confira a interface com ipconfig."
        ) from erro


class SemRedirecionamento(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def verificar_backend(ip: str) -> None:
    """Consulta o OpenAPI na própria interface anunciada, sem proxy ou redirects."""
    url = f"http://{ip}:{PORTA_SERVICO}/api/schema/"
    requisicao = Request(url, headers={"Accept": "application/vnd.oai.openapi+json"})
    cliente = build_opener(ProxyHandler({}), SemRedirecionamento())
    try:
        with cliente.open(requisicao, timeout=3) as resposta:
            if resposta.status != 200:
                raise RuntimeError(f"O backend respondeu HTTP {resposta.status} em {url}.")
            conteudo = resposta.read(LIMITE_SCHEMA + 1)
    except HTTPError as erro:
        codigo = erro.code
        erro.close()
        raise RuntimeError(
            f"HTTP {codigo} em {url}. Confira o Django e ALLOWED_HOSTS no ambiente local."
        ) from erro
    except (URLError, OSError, HTTPException) as erro:
        raise RuntimeError(
            f"Não foi possível acessar {url}. Inicie o Django em 0.0.0.0:{PORTA_SERVICO}."
        ) from erro
    if len(conteudo) > LIMITE_SCHEMA:
        raise RuntimeError("A resposta do schema excedeu o limite de 1 MB.")
    try:
        documento = json.loads(conteudo)
    except (ValueError, UnicodeError) as erro:
        raise RuntimeError("O servidor não retornou um schema OpenAPI JSON válido.") from erro
    if not isinstance(documento, dict):
        raise RuntimeError("O servidor não retornou um objeto OpenAPI.")
    versao = documento.get("openapi")
    caminhos = documento.get("paths")
    produto = caminhos.get(CAMINHO_PRODUTO) if isinstance(caminhos, dict) else None
    if (
        not isinstance(versao, str)
        or not versao.startswith("3.")
        or not isinstance(produto, dict)
        or not isinstance(produto.get("get"), dict)
    ):
        raise RuntimeError("O schema recebido não identifica a API esperada do Verificador de Preços.")


def criar_servico(ip: str) -> ServiceInfo:
    return ServiceInfo(
        TIPO_SERVICO,
        NOME_SERVICO,
        addresses=[socket.inet_aton(ip)],
        port=PORTA_SERVICO,
        properties=PROPRIEDADES,
        server=f"verificador-precos-{ip.replace('.', '-')}.local.",
    )


def encerrar_zeroconf(rede: Zeroconf, servico: ServiceInfo | None = None) -> bool:
    """Tenta todas as liberações, preservando uma eventual exceção original."""
    sucesso = True
    if servico is not None:
        try:
            rede.unregister_service(servico)
        except Exception as erro:
            sucesso = False
            print(f"Falha ao desregistrar o anúncio: {erro}", file=sys.stderr)
    try:
        rede.close()
    except Exception as erro:
        sucesso = False
        print(f"Falha ao fechar o mDNS: {erro}", file=sys.stderr)
    return sucesso


def anunciar(ip: str) -> None:
    ip = validar_ipv4(ip)
    validar_interface(ip)
    print(f"Verificando o backend em http://{ip}:{PORTA_SERVICO}/ ...", flush=True)
    verificar_backend(ip)
    servico = criar_servico(ip)
    rede = Zeroconf(interfaces=[ip], ip_version=IPVersion.V4Only)
    registrado = False
    try:
        rede.register_service(servico)
        registrado = True
        print(
            f"Anúncio ativo: {NOME_SERVICO}\n"
            f"Endereço: http://{ip}:{PORTA_SERVICO}/\n"
            "Mantenha este terminal aberto. Ctrl+C encerra e desregistra o serviço.",
            flush=True,
        )
        try:
            Event().wait()
        except KeyboardInterrupt:
            print("\nEncerrando o anúncio...", flush=True)
    finally:
        finalizado = encerrar_zeroconf(rede, servico if registrado else None)
    if not finalizado:
        raise RuntimeError("O encerramento do mDNS apresentou falhas; consulte as mensagens anteriores.")
    print("Anúncio encerrado e recursos liberados.", flush=True)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ip", help="IPv4 da interface local utilizada pelo Django.")
    argumentos = parser.parse_args(argv)
    try:
        ip = argumentos.ip
        if ip is None:
            print("Não há seleção automática de interfaces. Consulte ipconfig e escolha a rede local.")
            ip = input("IPv4 do computador (ou execute com --ip): ")
        anunciar(ip)
    except EOFError:
        print("Entrada indisponível. Execute novamente informando --ip IPV4_DA_INTERFACE.", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nInicialização cancelada.")
        return 0
    except NonUniqueNameException:
        print("Já existe um anúncio com este nome na rede. Encerre a instância anterior.", file=sys.stderr)
        return 1
    except (ValueError, OSError, RuntimeError, Error) as erro:
        print(f"Não foi possível manter o anúncio: {erro}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
