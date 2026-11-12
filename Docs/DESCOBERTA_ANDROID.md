# Descoberta do servidor no Android — diagnóstico

## Escopo

Esta etapa utiliza a API nativa `NsdManager` para localizar anúncios DNS-SD do backend e mostrar seus dados. O diagnóstico não seleciona um servidor, não altera `BuildConfig.BASE_URL` e não modifica Retrofit, Moshi, Repository ou o ViewModel de produtos. A conexão dinâmica da API permanece para a Etapa 3.

A opção **Procurar servidor** fica na tela inicial de leitura. A descoberta começa somente ao tocar no botão dentro do diagnóstico, dura até dez segundos e pode ser interrompida ou repetida. A câmera sai da composição enquanto o diagnóstico está aberto e retorna ao voltar à leitura.

## Arquitetura

- `discovery/ContratoDescoberta.kt`: modelos de anúncio e servidor, estados, contrato injetável da plataforma e validação de tipo, TXT, IPv4 e porta. A validação de IPv4 não realiza resolução DNS.
- `discovery/DescobertaNsdAndroid.kt`: adaptação das APIs Android, listeners, informações originais `NsdServiceInfo`, controle da recepção multicast e callbacks serializados na thread principal.
- `discovery/DescobertaServidorViewModel.kt`: `StateFlow`, prazo da busca, fila de resoluções e isolamento entre sessões. Não depende do fluxo de produtos.
- `ui/TelaDescobertaServidor.kt`: apresentação dos estados, servidores e avisos; ações do usuário, permissão e ciclo de vida.

Os callbacks NSD são assíncronos. O adaptador não abre conexões HTTP nem executa varredura de IPs/portas. O controlador resolve um serviço por vez, inclusive quando há vários anúncios. Todos os servidores compatíveis encontrados são apresentados, sem escolha automática.

## Tipo e identidade do serviço

O Python anuncia `_vprecos._tcp.local.`. No Android, a chamada nativa usa `_vprecos._tcp.` com `NsdManager.PROTOCOL_DNS_SD`; o domínio local é acrescentado pelo sistema.

A comparação normaliza as formas históricas dos callbacks, incluindo `_vprecos._tcp.`, `._vprecos._tcp` e a forma com `.local.`. Após a normalização, a comparação é exata, sem aceitar nomes apenas por prefixo.

Somente serviços com os TXT `app=verificador-precos` e `version=1.0`, porta entre 1 e 65535 e ao menos um IPv4 unicast válido entram na lista. Endereços IPv6, loopback, multicast, link-local e endereços inválidos não são utilizados. Serviços incompatíveis geram avisos no diagnóstico.

Cada cartão apresenta nome, endereços IPv4, porta, versão e identificação da aplicação. A validação TXT identifica o protocolo esperado; não constitui autenticação criptográfica do servidor.

## APIs e permissões

O projeto mantém `minSdk=23`, `compileSdk=37` e `targetSdk=37`, sem novas dependências.

| Versão | Implementação |
|---|---|
| Android 12 e versões anteriores suportadas | `discoverServices` e `resolveService` legados; IPv4 obtido de `NsdServiceInfo.host`; `MulticastLock` durante a busca |
| Android 13 | Mantém resolução legada e `host`, sem depender de SDK Extensions |
| Android 14 ou superior | A lista `hostAddresses` é utilizada somente com guarda `SDK_INT >= 34`; mantém a fila de resolução |
| Android 17, API 37 ou superior | Exige `ACCESS_LOCAL_NETWORK` antes da busca; concessão, recusa e revogação são tratadas na interface e há verificação defensiva no adaptador |

As permissões `INTERNET` e `CAMERA` existentes foram preservadas. Foram adicionadas:

- `CHANGE_WIFI_MULTICAST_STATE`: permite adquirir `WifiManager.MulticastLock`, necessário para a recepção mDNS no Android 12. O lock é limitado à sessão em primeiro plano e também é liberado em parada, erro e prazo esgotado. Em versões recentes, o sistema já gerencia a recepção em primeiro plano; manter o lock durante a curta sessão preserva o caminho único de compatibilidade.
- `ACCESS_LOCAL_NETWORK`: declarada para o target 37 e solicitada em tempo de execução apenas em Android 17 ou superior. Não é solicitada no Moto G22 com Android 12.

A solicitação de rede local pertence ao diagnóstico. O fluxo de produtos permanece com sua conexão fixa anterior; esta etapa não acrescenta um fluxo independente de permissão à consulta de produtos no Android 17. Nesse sistema, o acesso local também dependerá da concessão dessa permissão.

Não foram adicionadas permissões de localização, acesso ao estado da rede ou bibliotecas externas de descoberta. O `targetSdk` não foi reduzido.

## Ciclo de vida e falhas

A tela observa o estado do ViewModel sem iniciar buscas por recomposição. Ao sair do diagnóstico, pressionar Voltar ou receber `ON_STOP`, encerra a busca e libera a recepção multicast. Voltar ao primeiro plano não reinicia a busca automaticamente. Respostas de permissão posteriores à saída não iniciam uma sessão em segundo plano.

Os estados distinguem espera, descoberta, resolução, validação, resultados, ausência de resultados e erro. Há avisos específicos para resolução malsucedida, identidade incompatível e perda de serviço durante a busca.

Uma nova busca limpa os resultados anteriores. Callbacks de sessões encerradas não podem repor dados antigos. Perda e reencontro do mesmo nome também invalidam a resolução anterior daquele anúncio.

No Android 12, não existe API pública para cancelar imediatamente uma resolução legada já enviada ao sistema. Ao parar, seus efeitos são invalidados, mas o controlador aguarda o callback terminal antes de resolver outro serviço. Uma nova busca pode aguardar essa conclusão e encerrar pelo prazo sem obter resultados. Não há duas resoluções concorrentes do controlador.

O adaptador também aguarda a confirmação de parada de uma descoberta antes de iniciar outra. Se o Android informar que não conseguiu encerrar a descoberta anterior, a interface orienta reiniciar o aplicativo em vez de criar uma busca paralela.

Os resultados exibidos após o encerramento são um retrato da última busca. Para verificar mudança de IP, TXT ou disponibilidade, utilize **Procurar novamente**. Os metadados de um serviço já resolvido não são monitorados continuamente nesta etapa.

## Teste no Motorola Moto G22 — Android 12

Não é possível comprovar multicast físico com testes JVM simulados. A validação no aparelho deve ser feita pelo operador, com computador e celular na mesma rede Wi-Fi.

Na pasta `backend-verificador-precos`, utilize o ambiente virtual existente. No primeiro terminal:

```powershell
.\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000
```

No segundo terminal:

```powershell
$ipServidor = Read-Host 'IPv4 da interface do computador'
.\.venv\Scripts\python.exe anunciar_servidor.py --ip $ipServidor
```

Antes do anúncio, `DJANGO_ALLOWED_HOSTS` e `IMAGENS_BASE_URL` precisam continuar coerentes com o ambiente. Os scripts e o Android não editam essas configurações. Mais informações em `DESCOBERTA_SERVIDOR.md`.

Instale o APK debug gerado em `android-verificador-precos/app/build/outputs/apk/debug/app-debug.apk` e execute:

| Cenário | Resultado esperado |
|---|---|
| Abrir Procurar servidor | Aguardando busca; nenhuma pesquisa automática |
| Tocar Procurar servidor com anúncio ativo | Serviço listado com nome, IPv4 atual do computador, porta 8000, versão 1.0 e TXT da aplicação |
| Aguardar dez segundos | Busca encerrada; dados da última busca permanecem identificados como resultados anteriores |
| Parar antes do prazo | Busca interrompida; sem novos resultados da sessão encerrada |
| Desligar o anúncio Python com Ctrl+C e iniciar outra busca | Nenhum servidor compatível ao término, salvo outro anúncio válido na rede |
| Reiniciar o anúncio e procurar novamente | Serviço volta a ser localizado, sem resultados obsoletos |
| Encerrar anúncio durante busca ativa | Serviço é removido quando o sistema recebe sua retirada; aviso de indisponibilidade |
| Encontrar vários anúncios compatíveis | Todos são listados, sem seleção automática |
| Anúncio com TXT incompatível em ambiente controlado | Serviço não entra na lista; aviso explica a rejeição |
| Sair/colocar app em segundo plano e retornar | A pesquisa não continua nem reinicia automaticamente |
| Parar e repetir rapidamente | Sem buscas/resoluções paralelas; pode aguardar resolução legada anterior |
| Voltar à leitura | Câmera reiniciada; consulta manual e apresentação dos produtos preservadas |

No Android 17, testar adicionalmente concessão, recusa e revogação da permissão nas configurações do sistema. Após revogação, o diagnóstico não deve continuar buscando sem autorização. O funcionamento real nessa versão depende de validação em dispositivo ou emulador apropriado.

## Verificações automatizadas

Na pasta Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Na raiz do repositório:

```powershell
git diff --check
git status --short
```

Os testes JVM utilizam uma plataforma simulada e verificam identidade, IPv4/porta, estados, prazo, serialização, perda de serviço, cancelamento e eventos tardios. Eles não executam o NSD do aparelho, o rádio Wi-Fi, o diálogo de permissão ou a interface Compose real.

Na verificação local desta implementação, os 25 testes anteriores e 34 novos passaram, totalizando 59, sem falhas ou testes ignorados. A compilação debug passou e o lint terminou sem erros, mantendo os 35 avisos preexistentes. O teste físico de descoberta no Moto G22 e a validação em Android 17 permanecem pendentes.

## Referências oficiais

- [NSD no Android](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [NsdManager, compatibilidade e recepção multicast](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [Permissão de rede local](https://developer.android.com/privacy-and-security/local-network-permission)

## Próxima etapa

A Etapa 3 poderá fornecer o endereço descoberto a uma configuração dinâmica da API. Esta implementação apenas apresenta os dados; não muda o contrato JSON, os produtos, as imagens ou o endereço utilizado por Retrofit.
