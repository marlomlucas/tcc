"""Diagnóstico de descoberta mDNS/DNS-SD, sem varredura de portas ou de sub-rede."""

import argparse
import json
import math
import sys
from threading import Event, Lock

from zeroconf import Error, IPVersion, ServiceBrowser, ServiceListener, Zeroconf

from anunciar_servidor import (
    PROPRIEDADES,
    TIPO_SERVICO,
    encerrar_zeroconf,
    validar_interface,
    validar_ipv4,
)


class OuvinteServicos(ServiceListener):
    def __init__(self):
        self._servicos = {}
        self._lock = Lock()

    def add_service(self, zc, type_, name):
        try:
            info = zc.get_service_info(type_, name, timeout=2000)
        except (OSError, Error) as erro:
            print(f"Falha ao resolver {name!r}: {erro}", file=sys.stderr)
            self.remove_service(zc, type_, name)
            return
        if info is None or info.properties.get(b"app") != PROPRIEDADES["app"].encode():
            self.remove_service(zc, type_, name)
            return
        enderecos = []
        for endereco in info.parsed_addresses(IPVersion.V4Only):
            try:
                enderecos.append(validar_ipv4(endereco))
            except ValueError:
                continue
        if not enderecos or not isinstance(info.port, int) or not 1 <= info.port <= 65535:
            self.remove_service(zc, type_, name)
            return
        propriedades = {
            chave.decode("utf-8", errors="replace"): (
                valor.decode("utf-8", errors="replace") if valor is not None else ""
            )
            for chave, valor in info.properties.items()
        }
        with self._lock:
            self._servicos[name] = {
                "nome": name,
                "enderecos": sorted(set(enderecos)),
                "porta": info.port,
                "propriedades": propriedades,
            }

    def update_service(self, zc, type_, name):
        self.add_service(zc, type_, name)

    def remove_service(self, zc, type_, name):
        with self._lock:
            self._servicos.pop(name, None)

    def resultados(self):
        with self._lock:
            return [self._servicos[nome] for nome in sorted(self._servicos)]


def descobrir_servidores(ip=None, tempo=10.0):
    if not math.isfinite(tempo) or tempo <= 0:
        raise ValueError("O tempo de descoberta deve ser um número positivo e finito.")
    opcoes = {"ip_version": IPVersion.V4Only}
    if ip is not None:
        ip = validar_ipv4(ip)
        validar_interface(ip)
        opcoes["interfaces"] = [ip]
    ouvinte = OuvinteServicos()
    rede = Zeroconf(**opcoes)
    navegador = None
    cancelado = True
    try:
        navegador = ServiceBrowser(rede, TIPO_SERVICO, listener=ouvinte)
        Event().wait(tempo)
    finally:
        if navegador is not None:
            try:
                navegador.cancel()
            except Exception as erro:
                cancelado = False
                print(f"Falha ao encerrar a busca mDNS: {erro}", file=sys.stderr)
        fechado = encerrar_zeroconf(rede)
    if not cancelado or not fechado:
        raise RuntimeError("O encerramento da descoberta apresentou falhas.")
    return ouvinte.resultados()


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ip", help="Restringe a busca a um IPv4 deste computador.")
    parser.add_argument("--tempo", type=float, default=10.0, help="Duração em segundos (padrão: 10).")
    argumentos = parser.parse_args(argv)
    try:
        print(f"Procurando {TIPO_SERVICO} por {argumentos.tempo:g} segundos...", flush=True)
        servicos = descobrir_servidores(argumentos.ip, argumentos.tempo)
    except KeyboardInterrupt:
        print("\nBusca interrompida.")
        return 0
    except (ValueError, OSError, RuntimeError, Error) as erro:
        print(f"Falha na descoberta: {erro}", file=sys.stderr)
        return 1
    if not servicos:
        print("Nenhum serviço compatível encontrado. Confira o anúncio, a interface e o multicast.")
        return 1
    for servico in servicos:
        print(json.dumps(servico, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
