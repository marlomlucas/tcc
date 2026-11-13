# Etapa 3 — Conexão dinâmica do Android ao servidor

## Escopo

Esta etapa integra a descoberta mDNS da Etapa 2 à consulta de produtos. O aplicativo deixa de utilizar um IP fixo no Gradle. O backend Django, seu contrato JSON, o MySQL e as imagens WebP permanecem inalterados.

O endereço da API e o endereço público das imagens são independentes. A descoberta mDNS configura exclusivamente o acesso Retrofit à API; o servidor de imagens pode utilizar outro host, outra porta e outro caminho.

A documentação da descoberta nativa permanece em `DESCOBERTA_ANDROID.md`. Este documento descreve o fluxo de conexão que substitui a tela exclusivamente de diagnóstico.

## Arquitetura

- `EnderecoServidor`: valida IPv4, porta e URL HTTP raiz, sem executar DNS. A URL base sempre termina em `/`.
- `VerificadorApiHttp`: consulta `/api/schema/` por HTTP assíncrono, com limite de resposta de 1 MiB, prazo total de cinco segundos e redirecionamentos desativados. Exige HTTP 200, OpenAPI 3, título `API Verificador de Preços` e operação GET `/api/produtos/{codigo_barra}/`.
- `GerenciadorConexao`: publica `EstadoConexao` e `SessaoServidor` por `StateFlow`. Somente publica uma sessão após validar a API. Cada ativação recebe uma revisão nova, mesmo quando o endereço é o mesmo.
- `ConexaoServidorViewModel`: coordena uma única instância da descoberta já existente, a configuração manual e a validação HTTP. Não cria outro mecanismo de mDNS.
- `PreferenciaServidorAndroid`: armazena somente a URL manual validada em `SharedPreferences` privadas do aplicativo. Não armazena credenciais.
- `RetrofitClient`: cria a API a partir do endereço ativo, preservando Moshi e o DTO. Compartilha o cliente HTTP, com timeout e sem redirecionamentos, mas não conserva uma API permanente associada ao IP anterior.
- `ProdutoRepositoryImpl`: captura a sessão da consulta, utiliza seu endereço para a API e invalida respostas de outra revisão. Após essas verificações, preserva a resposta e o DTO recebidos, incluindo a URL da imagem.
- `ProdutoViewModel`: mantém a injeção de repositórios simulados, preserva o tratamento de HTTP 200/400/404 e cancela/limpa consultas quando a sessão muda.

A câmera e os resultados só são apresentados quando há uma sessão validada e permissão de rede local. A troca de sessão limpa produtos e códigos anteriores. Recriar a Activity mantendo a mesma sessão não deve apagar o código digitado nem repetir uma consulta.

## Escolha automática

Ao iniciar, o aplicativo confere a permissão necessária e procura o servidor automaticamente, exceto quando existe uma preferência manual salva.

A descoberta mantém o prazo de dez segundos. A decisão ocorre ao terminar a busca, permitindo identificar vários servidores antes de escolher:

- Nenhuma opção compatível: informa indisponibilidade e oferece nova busca ou configuração manual.
- Uma única opção: verifica a API e ativa a conexão somente após sucesso.
- Mais de uma opção: apresenta a lista para escolha explícita. Não escolhe o primeiro silenciosamente. Anúncios com nomes distintos são preservados mesmo quando apontam para o mesmo endereço. Um serviço com vários IPv4 também pode gerar opções distintas.

Os TXT `app` e `version` continuam sendo validados pela descoberta. A verificação HTTP acrescenta uma conferência de compatibilidade; nenhuma das duas constitui autenticação criptográfica do servidor.

## Configuração manual e prioridade

Na tela de conexão, utilize **Configurar servidor manualmente**. Informe um IPv4 e a porta ou uma URL HTTP equivalente, sem credenciais, caminhos adicionais, query ou fragmento.

O campo porta vazio utiliza 8000 quando a entrada é um IPv4 simples. Quando a entrada é uma URL, prevalece sua porta explícita; uma URL HTTP sem porta utiliza 80. Não são aceitos hostnames ou IPv6 nesta etapa.

**Testar e conectar** valida a entrada, consulta o OpenAPI e somente então salva a URL e ativa a sessão. Uma tentativa inválida ou uma API incompatível não grava o novo endereço.

Uma preferência manual salva tem prioridade. Ao abrir novamente o aplicativo, ela passa pela verificação HTTP antes de permitir consultas. Se falhar, o aplicativo informa o problema e não troca silenciosamente para outro servidor. É possível reconectar ao endereço salvo, corrigir a entrada ou escolher **Usar modo automático**.

Voltar explicitamente ao modo automático remove a preferência manual salva e inicia nova descoberta. Descobertas ou verificações antigas não podem substituir uma escolha manual mais recente.

## Consultas, falhas e ciclo de vida

O aplicativo mostra busca, verificação, seleção entre servidores, conexão ativa, indisponibilidade e falhas. Quando conectado, apresenta o endereço e a ação **Alterar**.

Sem conexão válida, a câmera não faz consultas repetidas e a consulta manual não é liberada. Falhas de comunicação ou respostas 5xx invalidam a sessão atual e disponibilizam reconexão. HTTP 400 e 404 continuam sendo resultados da consulta, sem derrubar uma conexão válida.

Ao trocar de endereço, o cancelamento das coroutines solicita a interrupção da chamada Retrofit. As verificações de sessão também impedem que uma implementação não cooperativa publique uma resposta antiga. Isso não desfaz uma requisição que o servidor já recebeu.

Entrar em segundo plano interrompe descoberta e verificações pendentes. Uma conexão já validada pode permanecer selecionada; a retomada confere novamente a permissão antes de novas consultas. Não há uma nova busca a cada recomposição.

Permanece a limitação da resolução NSD legada do Android 12: um pedido já enviado não pode ser cancelado fisicamente pela API pública. Seus resultados são invalidados e outra resolução aguarda o callback terminal, evitando concorrência.

A descoberta não monitora permanentemente o anúncio após conectar. Encerrar o anunciador não desliga Django; uma API ainda acessível pode continuar atendendo. Utilize nova busca para conferir anúncios ou uma consulta para detectar falha HTTP. Não há heartbeat, serviço de segundo plano ou varredura de IPs.

## URLs das fotografias

O Django continua responsável por montar a URL absoluta no campo `imagem`, utilizando `IMAGENS_BASE_URL` e o comportamento existente de `ProdutoSerializer.get_imagem()`. O Android entrega `ProdutoDto.imagem` diretamente ao Coil, sem reescrever, normalizar ou filtrar o valor em função do endereço da API.

- API e imagens podem utilizar o mesmo host e porta ou servidores e portas diferentes, inclusive um domínio próprio de mídia.
- A URL completa recebida é preservada, incluindo caminho e query `?v=...`, mantendo o comportamento de atualização das fotografias e da chave de cache do Coil.
- Imagem ausente continua sendo `null`; o produto permanece consultável sem fotografia.
- Se a imagem estiver indisponível, a tela mantém a mensagem existente **Imagem indisponível** no espaço da fotografia. Nome, descrição e preço continuam apresentados independentemente do resultado do carregamento no Coil.
- Trocar o endereço da API invalida consultas e resultados antigos, mas não modifica a URL de imagem recebida na próxima resposta.
- Um endereço de imagem incorreto no JSON deve ser regularizado na configuração responsável pela mídia. A descoberta mDNS não corrige nem substitui esse endereço.

Não são modificados arquivos WebP, nomes de imagens, banco, serializer ou configurações `.env`. Não existe configuração de mídia derivada da preferência manual da API.

### Confiança nas URLs e eventual validação futura

O aplicativo confia nas URLs fornecidas pelo backend selecionado. Uma API adulterada ou uma configuração de mídia indevida pode induzir o dispositivo a buscar uma imagem em outro destino, revelando ao servidor de mídia o IP e os metadados da requisição; HTTP também permite alteração do tráfego na rede. A identificação por OpenAPI/TXT não autentica criptograficamente o backend.

Como medida futura, se necessária, a validação poderia aceitar somente URLs HTTP/HTTPS absolutas com host e porta válidos, sem credenciais embutidas ou caracteres de controle. Essa verificação deve ser independente da API e preservar literalmente URLs aprovadas, incluindo host, porta, caminho e query. Não deve restringir as imagens ao IP descoberto nem presumir o diretório de mídia do Django. HTTPS é preferível quando disponível; uma política opcional de destinos permitidos precisaria de configuração própria para o servidor de imagens. Nenhum filtro novo foi implementado neste ajuste.

## Permissões e compatibilidade

O projeto mantém minSdk 23 e target/compileSdk 37. Não foram adicionadas dependências ou reduzido o target.

No Moto G22 com Android 12, não há pedido de permissão de rede local. `INTERNET` já declarada atende ao HTTP, e `CHANGE_WIFI_MULTICAST_STATE` permite o lock durante a descoberta.

No Android 17, `ACCESS_LOCAL_NETWORK` passa a ser controlada pelo host principal antes da descoberta, da verificação HTTP, das consultas e da apresentação das imagens. A recusa impede a conexão; a revogação é conferida ao retomar e antes de consultar. Uma resposta de permissão posterior à saída não inicia trabalho em segundo plano.

## Verificação automatizada

Na pasta `android-verificador-precos`:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Na raiz:

```powershell
git diff --check
git status --short
```

Os testes usam fakes para descoberta, verificação HTTP, preferências e APIs de produtos. Não comprovam multicast físico, o diálogo de permissão ou a interface no aparelho. A persistência real e a integração no Moto G22 precisam do roteiro abaixo.

Verificação do ajuste de endereços independentes concluída em 13/11/2026: **141 testes aprovados**, sem falhas, erros ou testes ignorados. Os 59 testes anteriores permanecem aprovados. `:app:assembleDebug` e `:app:lintDebug` concluíram com sucesso, com zero erros e os mesmos 35 avisos existentes. `git diff --check` não apresentou erros.

A contagem anterior era 148: foram retirados os 14 testes do adaptador de URLs eliminado, substituído um teste de reescrita do repositório por sete testes de preservação e acrescentado um teste do ViewModel. Os cenários cobrem mídia no mesmo endpoint, IP distinto (incluindo IP privado), porta distinta, domínio próprio HTTP/HTTPS, query literal, `null` e troca do endereço da API sem alterar a imagem. O teste do ViewModel confirma que o estado de sucesso mantém nome, preço e URL sem esperar a mídia; ele não executa o Coil nem comprova o erro visual.

Os testes manuais anteriores foram confirmados pelo usuário antes desta alteração. Os cenários físicos específicos de mídia independente e imagem indisponível ainda precisam ser revalidados. Backend, banco e imagens existentes não foram alterados. Não houve commit nem push neste ajuste.

## Validação no Moto G22

1. Coloque computador e celular na mesma rede Wi-Fi, com comunicação entre os dispositivos permitida. Confira se a configuração atual de `DJANGO_ALLOWED_HOSTS` aceita o IPv4 do computador e se `IMAGENS_BASE_URL` informa um endereço de mídia acessível pelo celular. Essas configurações são independentes e não são alteradas pela descoberta. Para os cenários abaixo, utilize servidores e dados de teste já preparados, sem modificar produtos reais.
2. Na pasta do backend, execute em um terminal:

   ```powershell
   .\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000
   ```

3. Em outro terminal da mesma pasta, execute:

   ```powershell
   $ipServidor = Read-Host 'IPv4 atual do computador'
   .\.venv\Scripts\python.exe anunciar_servidor.py --ip $ipServidor
   ```

4. Instale o APK debug gerado e abra o aplicativo sem configurar um IP no Gradle. Na primeira execução sem preferência manual, aguarde a descoberta e a verificação da API.
5. Confira o endereço conectado. Consulte um produto pela câmera e outro manualmente. Verifique código, nome, preço, descrição, produto com foto e produto sem foto.
6. Confira uma fotografia cuja URL absoluta no JSON utilize o mesmo IP e porta da API; repita com uma URL em outro IP e com outra porta. O Coil deverá utilizar exatamente cada URL recebida. Confira uma URL com `?v=...`, um produto com `imagem = null` e uma URL de imagem indisponível: neste último caso, deverá aparecer **Imagem indisponível**, mantendo nome e preço visíveis. Repita com uma URL HTTP e uma HTTPS válidas quando disponíveis.
7. Encerre somente o anúncio com Ctrl+C e peça nova busca: após o prazo, não deve haver resultado, salvo outro serviço compatível. Reinicie o anúncio e repita.
8. Encerre Django e consulte: deve aparecer falha de comunicação sem repetição contínua pela câmera. Restaure o servidor e reconecte.
9. Teste entrada manual inválida, endereço válido com API desligada e endereço manual válido. Somente a última condição deve ativar e salvar a conexão.
10. Feche e abra o aplicativo: o endereço manual salvo deve ser revalidado, sem descoberta que substitua essa escolha.
11. Quando houver dois servidores de teste com nomes distintos, confira a lista e a seleção explícita. Troque de servidor durante uma consulta pendente e confirme que nenhum produto antigo reaparece. Se os dois servidores fornecerem a mesma URL de imagem, confirme que ela permanece idêntica após a troca; a conexão com outra API não pode mudar host, porta, caminho ou query da fotografia.
12. Entre em segundo plano durante descoberta/verificação e retorne. Confira a interrupção e a recuperação controlada. Recrie a Activity com código digitado e conexão inalterada: o texto deve ser preservado.
13. Volte ao modo automático e confira que a preferência manual deixa de ser reutilizada após reiniciar.
14. Em Android 17, valide adicionalmente concessão, recusa e revogação da permissão. Esta versão não é simulada pelos testes JVM.

Utilize somente dados de teste para qualquer alteração de produtos. O roteiro não exige modificar dados reais.

## Limitações de rede

Firewall, bloqueio de UDP 5353, isolamento Wi-Fi, rede de convidados ou VLAN podem impedir mDNS. Uma configuração manual pode resolver a ausência de descoberta, mas não contorna um bloqueio de TCP ou uma API incompatível. O aplicativo não altera regras de firewall.

HTTP na LAN e a identificação por OpenAPI/TXT não autenticam um servidor contra falsificação deliberada. Esta etapa preserva o ambiente acadêmico existente e não inclui TLS, credenciais ou novas funções de segurança.

## Referências

- [Permissão de rede local no Android](https://developer.android.com/privacy-and-security/local-network-permission)
- [API nativa NSD](https://developer.android.com/reference/android/net/nsd/NsdManager)
