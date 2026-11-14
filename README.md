# Verificador de Preços

**Trabalho de Conclusão de Curso (TCC) — Bacharelado em Engenharia de Software**

**Aluno:** Marlom Camaran Lucas  
**RA:** 21069724-5

## Sobre o projeto

O **Verificador de Preços** é um protótipo acadêmico para consulta de produtos por código de barras. O sistema é composto por um aplicativo Android, desenvolvido em Kotlin, e por uma API REST em Python/Django, integrada a um banco de dados MySQL.

O aplicativo permite ler o código de barras pela câmera ou informá-lo manualmente. Após a consulta ao servidor na rede local, exibe o nome, a descrição, o preço e a imagem do produto, quando disponível.

## Arquivos do repositório

- **`backend-verificador-precos/`** — API Django, painel administrativo, configurações e inicializador do servidor.
- **`android-verificador-precos/`** — código-fonte do aplicativo Android.
- **`Backup_banco/produtos_demonstracao.sql`** — dados de cinco produtos para testes, a serem importados após a execução das migrations do Django.
- **`Docs/`** — documentação de desenvolvimento, manual de instalação e materiais de teste.
- **`varificador_precos.apk`** — APK *debug* assinado, disponibilizado para instalação e avaliação acadêmica.

## Instalação e execução

As instruções para preparar o Windows, configurar o MySQL, instalar as dependências, executar o Django e instalar o aplicativo Android estão no [Manual de Instalação](Docs/MANUAL_INSTALACAO_Verificador_de_Precos.docx).

Para realizar as consultas pelo celular, o computador que executa o backend e o dispositivo Android devem estar conectados à mesma rede local, com comunicação permitida entre eles.

## Observações sobre a entrega

Na etapa final do desenvolvimento, identifiquei oportunidades de melhoria e implementei ajustes adicionais, inclusive próximos à data de entrega, em **13 de novembro de 2026**. As alterações foram implementadas e testadas antes da disponibilização desta versão do projeto.

O sistema foi desenvolvido para demonstração acadêmica. O APK disponibilizado é uma versão *debug* assinada; o backend de desenvolvimento não se destina à exposição direta na internet ou à operação em produção.

## Contato

Permaneço disponível para esclarecer dúvidas e auxiliar na resolução de eventuais problemas de instalação ou execução. O contato pode ser realizado a qualquer horário, e responderei assim que possível.

**E-mail:** marlomlucas@hotmail.com  
**Telefone/WhatsApp:** (51) 99470-9009