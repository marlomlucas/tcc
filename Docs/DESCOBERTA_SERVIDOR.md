# Descoberta do servidor Django por mDNS

## Finalidade e dependências

O script independente `anunciar_servidor.py` anuncia o servidor Django na rede local por mDNS/DNS-SD. O processo permanece separado do `runserver`, inclusive de seu mecanismo de recarga automática. Nesta etapa, o aplicativo Android permanece inalterado; a integração futura poderá utilizar `NsdManager`.

O ambiente utilizado é Python 3.14 no Windows. As dependências acrescentadas ao `requirements.txt` são `zeroconf==0.154.0` e `ifaddr==0.2.0`, esta última necessária ao zeroconf. As demais bibliotecas do backend permanecem nas versões existentes.

Execute os comandos abaixo no PowerShell, na pasta `backend-verificador-precos`. Utilize o ambiente virtual existente:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## Preparação da rede

Consulte `ipconfig` e identifique o IPv4 da interface Wi-Fi ou Ethernet que compartilha a rede com os dispositivos de teste. Não utilize o endereço de loopback nem uma interface de VPN por engano.

Antes do anúncio, a configuração local do Django deve aceitar o endereço atual em `DJANGO_ALLOWED_HOSTS`, sem substituir a lista por `*`. A variável `IMAGENS_BASE_URL` também deve apontar para o endereço público correto do servidor, mantendo sua independência do diretório físico `IMAGENS_STORAGE_ROOT`.

Os scripts não editam o `.env`. A descoberta do servidor não corrige automaticamente configurações anteriores: após uma mudança de IP, um host não autorizado pode impedir a verificação HTTP, e uma URL pública antiga pode impedir o carregamento das imagens mesmo quando a API estiver acessível.

## Iniciar o Django

No primeiro terminal:

```powershell
.\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000
```

O endereço `0.0.0.0` configura a escuta nas interfaces locais; ele não é o endereço a anunciar ou acessar pelo aplicativo.

## Anunciar o servidor

No segundo terminal, informe o IPv4 escolhido:

```powershell
$ipServidor = Read-Host 'IPv4 da interface de rede do servidor'
.\.venv\Scripts\python.exe anunciar_servidor.py --ip $ipServidor
```

Também é possível omitir o argumento:

```powershell
.\.venv\Scripts\python.exe anunciar_servidor.py
```

Nesse caso, o programa solicita o endereço no terminal. Não há seleção automática de interfaces. Para execução sem entrada interativa, utilize `--ip`.

O programa valida o IPv4, verifica por `bind` se o endereço pertence ao computador e consulta `GET /api/schema/` na porta 8000, solicitando JSON. O anúncio somente começa após confirmar uma resposta HTTP 200 com o schema esperado da API. Proxies e redirecionamentos HTTP não são utilizados nessa verificação.

O serviço anunciado possui:

| Elemento | Valor |
|---|---|
| Nome da instância | `Verificador de Precos` |
| Tipo | `_vprecos._tcp.local.` |
| Porta TCP | `8000` |
| Endereço | IPv4 informado |
| TXT `app` | `verificador-precos` |
| TXT `version` | `1.0` |

O anúncio utiliza somente a interface IPv4 informada. Um conflito com o mesmo nome de serviço gera falha compreensível; o programa não renomeia silenciosamente a instância.

## Verificar a descoberta

Mantenha Django e anúncio ativos. Em um terceiro terminal:

```powershell
.\.venv\Scripts\python.exe verificar_descoberta.py --tempo 10
```

Para restringir a consulta a uma interface local específica:

```powershell
$ipConsulta = Read-Host 'IPv4 local da interface usada para consultar'
.\.venv\Scripts\python.exe verificar_descoberta.py --ip $ipConsulta --tempo 10
```

Sem `--ip`, o diagnóstico utiliza as interfaces IPv4 disponíveis. Se for executado em outro computador, o argumento deve identificar uma interface desse computador, não o IP do servidor. Esse computador também precisa dos scripts e de suas dependências.

O diagnóstico procura exclusivamente `_vprecos._tcp.local.` por mDNS/DNS-SD, sem varredura de portas ou da sub-rede. A saída apresenta os serviços compatíveis em JSON, incluindo nome, endereços IPv4, porta e propriedades TXT. O código de saída é `0` quando encontra serviços e `1` quando não encontra resultados ou ocorre uma falha. A ausência de resultados dentro do prazo não comprova que o servidor está desligado.

## Problemas de rede e limitações

- mDNS utiliza multicast IPv4 `224.0.0.251`, UDP 5353. O acesso à API utiliza TCP 8000. O firewall deve permitir o tráfego necessário na rede de testes; os scripts não alteram suas regras.
- Isolamento de clientes Wi-Fi, rede de convidados, VLANs, VPNs ou bloqueio de multicast podem impedir a descoberta. Os dispositivos devem estar em uma rede que permita comunicação entre eles.
- Descobrir o serviço não garante que outro dispositivo consiga consultar a API ou carregar suas imagens. Validar também pelo dispositivo de destino. Uma descoberta no próprio computador não comprova a comunicação pelo Wi-Fi do celular.
- mDNS e os metadados TXT não autenticam o servidor. Esta implementação se destina à rede local de testes e não acrescenta um mecanismo de segurança ou autenticação.
- A verificação HTTP ocorre antes do registro e não é um monitoramento contínuo. Se Django parar depois, encerre também o anúncio para não divulgar um serviço indisponível. Se o IP mudar, encerre o anúncio e execute-o novamente com o endereço atual.

## Encerramento

Pressione `Ctrl+C` no terminal do anúncio para desregistrar o serviço e fechar o zeroconf. Depois encerre Django com `Ctrl+C` em seu próprio terminal. Uma interrupção abrupta do processo ou do computador pode impedir o envio da retirada do anúncio; nesse caso, outros dispositivos podem manter informações em cache até a expiração.

O diagnóstico termina após o intervalo informado em `--tempo`; também pode ser interrompido por `Ctrl+C`. Em ambos os casos, cancela a busca e libera os recursos de rede.

## Verificações automatizadas

Na pasta do backend:

```powershell
.\.venv\Scripts\python.exe -m unittest -v test_descoberta_servidor
.\.venv\Scripts\python.exe manage.py check
.\.venv\Scripts\python.exe manage.py test produtos --verbosity 2
git diff --check
```

Os testes unitários da descoberta utilizam mocks, sem depender da rede real. A suíte de produtos deve utilizar o banco MySQL de testes e diretórios temporários de mídia, preservando o banco e as imagens originais. Não execute migrations no banco principal para testar esta funcionalidade.
