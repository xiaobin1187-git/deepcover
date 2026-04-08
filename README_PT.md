# DeepCover - Agente de coleta de cobertura de codigo em cadeia completa

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | [Francais](README_FR.md) | **Portugues** | [Русский](README_RU.md)

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-1.8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5-blue)
![Tests](https://img.shields.io/badge/Tests-52_passed-brightgreen)

</div>

> Ferramenta de coleta de cobertura de codigo Java baseada em JVM Sandbox - Monitoramento nao intrusivo de cobertura em nivel de linha

## Introducao

DeepCover e um **agente de coleta de cobertura de codigo Java nao intrusivo** baseado no Alibaba JVM Sandbox. Ele coleta dados de execucao de linhas de codigo em tempo real sem modificar o codigo-fonte da aplicacao.

### Principais Recursos

- **Coleta nao intrusiva** -- Baseado em tecnologia de melhoria de bytecode JVM Sandbox, sem modificacao do codigo-fonte
- **Cobertura em nivel de linha** -- Registros de execucao precisos para cada linha de codigo
- **Rastreamento de requisicoes HTTP** -- Identificacao e rastreamento automaticos de requisicoes HTTP Servlet
- **Design de alta performance** -- Filas assincronas + envio em lote para minimizar o impacto na aplicacao
- **Configuracao flexivel** -- Controle refinado de nomes de classes, metodos, taxas de amostragem, com recarregamento a quente via centro de configuracao
- **Multiplos metodos de exportacao** -- Suporta exportacao via HTTP e Kafka
- **Integracao OpenTelemetry** -- Compativel com o padrao OpenTelemetry para rastreamento distribuido

### Arquitetura do Sistema

O pipeline completo da coleta de codigo ao processamento e armazenamento de dados:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JVM da Aplicacao Alvo                            │
│                                                                     │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ HTTP Request │───>│  JVM Sandbox     │───>│  DeepCover Agent  │  │
│  │              │    │  (javaagent)      │    │  (Coleta)         │  │
│  └─────────────┘    └──────────────────┘    └────────┬──────────┘  │
│                                                        │            │
│                              ┌──────────────────────────┘            │
│                              │                                       │
│                     ┌────────┴────────┐                              │
│                     │  Fila Assincrona │  Agregacao em lote           │
│                     │  Local           │  (queue x N)                 │
│                     └────────┬────────┘                              │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────┴────────┐              ┌─────────┴─────────┐
     │  Centro de       │              │  Cluster Kafka     │
     │  Dados (HTTP)    │              │  code-coverage     │
     │  /api/collect    │              │                    │
     └────────┬────────┘              └─────────┬─────────┘
              │                                  │
              └────────────┬─────────────────────┘
                           │
              ┌────────────┴────────────┐
              │  Processamento de       │
              │  Dados / Armazenamento  │
              │                         │
              │  - Calculo de cobertura │
              │  - Analise diferencial  │
              │  - Persistencia dados   │
              │  - Geracao relatorios   │
              └─────────────────────────┘
```

### Arquitetura Interna do Agente

O fluxo de coleta dentro de uma unica JVM:

```
                        ┌─────────────────────────────────────┐
                        │          Requisicao HTTP             │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │       HttpServlet.service()          │
                        │  (Ponto de instrumentacao bytecode)   │
                        └──────────────┬──────────────────────┘
                                       │
 Event Flow:                           │
 ┌─────────────────────────────────────┼──────────────────────────┐
 │                                     │                          │
 │  [before]                                                      │
 │  ├─ Criar CodeEntity, vincular ao ProcessTop (ThreadLocal)     │
 │  ├─ Verificacao de taxa de amostragem (hash traceId)           │
 │  ├─ Filtragem de URL (regex ignoreUrls)                        │
 │  │                                                             │
 │  [beforeLine]  <── disparado a cada chamada de metodo          │
 │  ├─ Obter CodeEntity do ProcessTop                             │
 │  ├─ Protecao NPE: codeEntity==null / isSend==1                 │
 │  ├─ Construir LineEntity (className, methodName, num linha)    │
 │  │   └─ Deduplicacao automatica (LinkedHashSet, O(1))          │
 │  ├─ Verificacao de limite de execucoes de linha                │
 │  │   └─ Excedido: marcar REFUSE, parar coleta do metodo        │
 │  │                                                             │
 │  [after]                                                       │
 │  ├─ Verificacao de limite de nos coletados                     │
 │  ├─ Marcar isSend=1, prevenir envio duplicado                  │
 │  ├─ Enfileirar na LocalAsyncQueue                              │
 │  └─ finally: limpar ThreadLocal, prevenir vazamento memoria    │
 │                                                                │
 └────────────────────────────────────────────────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │        LocalAsyncEngine              │
                        │                                     │
                        │  ┌─────────┐  ┌─────────┐           │
                        │  │ Queue 0 │  │ Queue 1 │  ...       │
                        │  │ (lotes) │  │         │           │
                        │  └────┬────┘  └────┬────┘           │
                        │       │             │                │
                        │  ┌────┴─────────────┴────┐           │
                        │  │   Thread Consumidor     │           │
                        │  │   Envio HTTP / Kafka    │           │
                        │  └────────────────────────┘           │
                        │                                     │
                        │  Disjuntor: exceptionOverflow()      │
                        │  └─ Janela de tempo excedida ->       │
                        │     pausa na coleta                   │
                        └─────────────────────────────────────┘
```

### Mecanismo de Recarregamento a Quente

A configuracao em tempo de execucao pode ser atualizada dinamicamente sem reinicializacao:

```
┌────────────────┐   Sondagem periodica (reportPeriod) ┌──────────────────┐
│  Centro de      │ ─────────────────────────────────> │  DeepCover Agent │
│  Configuracao   │                                    │                  │
│  (deepcover-    │ <───────────────────────────────── │  reportServerInfo│
│   brain)        │   Retorna config + versao          │                  │
└────────────────┘                                    │  Comparacao:     │
                                                      │  info.version >  │
┌────────────────┐   Comando syncConfig               │  configVersion   │
│  HTTP Sandbox  │ ─────────────────────────────────> │  -> recarregar   │
│  /deepcover/   │                                    │     18 params    │
│  syncCfg       │                                    │                  │
└────────────────┘                                    └──────────────────┘
```

## Requisitos

- Java 8+ (recomendado: Java 1.8.0_202+)
- Maven 3.5+
- Alibaba JVM Sandbox 1.4.0
- Aplicacao executando em JVM

## Inicio Rapido

### 1. Compilar o Projeto

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Apos compilacao bem-sucedida, `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar` e gerado no diretorio `target/`.

### 2. Configurar Parametros JVM

Adicione aos parametros JVM da aplicacao alvo:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=sua-aplicacao \
  -Denv=test
```

**Atencao**: Se usar SkyWalking APM, coloque os parametros do DeepCover **antes** dos parametros do SkyWalking.

### 3. Implantar Modulo DeepCover

```bash
# Metodo 1: Copiar para diretorio de modulos Sandbox
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/

# Metodo 2: Carregamento dinamico via HTTP Sandbox
curl -X POST \
  "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"
```

### 4. Verificar Instalacao

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

### 5. Executar Testes

```bash
mvn clean test -Dmaven.javadoc.skip=true
```

41 testes unitarios cobrindo classes utilitarias e entidades principais.

## Configuracao

### Configuracao Basica

Copie o modelo de configuracao:

```bash
cp src/main/resources/deepcover.properties.example src/main/resources/deepcover.properties
```

### Referencia de Parametros

| Parametro | Descricao | Padrao/Exemplo |
|-----------|-----------|----------------|
| `serviceName` | Nome da aplicacao | - |
| `env` | Ambiente: test/pre/pro | - |
| `packageName` | Padrao de pacotes (regex) | `com.myapp.*` |
| `ignoreClasses` | Classes ignoradas (ponto-e-virgula) | `*.logger;*.frame;` |
| `ignoreUrls` | URLs ignoradas (ponto-e-virgula) | `/health;/metrics` |
| `ignoreAnnos` | Anotacoes ignoradas | - |
| `sampleRate` | Taxa de amostragem (10000 = 100%) | `10000` |
| `limitCodeMethodSize` | Max metodos coletados por requisicao | `500` |
| `limitCodeMethodLineSize` | Max coletas de linha por metodo | `500` |
| `sendDataCenterType` | Metodo de envio: 1=HTTP, 2=Kafka | `1` |
| `dataCenterAddr` | Endereco do centro de dados (HTTP) | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Endereco do cluster Kafka | - |
| `KAFKA_TOPIC` | Topico Kafka | - |
| `exceptionThreshold` | Limite do disjuntor | `10` |
| `exceptionCalcTime` | Janela de calculo de excecoes (min) | `1` |
| `exceptionPauseTime` | Tempo de pausa do disjuntor (seg) | `5` |

Todos os parametros suportam recarregamento a quente via comando `syncConfig`, sem reinicializacao.

### Taxa de Amostragem

```
sampleRate=10000   # 100% amostragem
sampleRate=5000    # 50% amostragem
sampleRate=100     # 1% amostragem
```

A taxa e calculada com base no hash do traceId, garantindo resultados consistentes entre instancias.

## API de Controle Dinamico

Controle o modulo dinamicamente via servico HTTP do JVM Sandbox:

```bash
# Ativar modulo
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Desativar modulo
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Descarregar modulo (descarga completa, reinicializacao necessaria para reativar)
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=sua-aplicacao"

# Sincronizar configuracao
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20"
```

## Design de Performance

DeepCover minimiza o impacto na performance da aplicacao atraves de:

- **Filas assincronas**: Pool de threads independente (core=4, max=8, queue=256) com CallerRunsPolicy
- **Processamento em lote**: Agregacao local para envio em lote, reduzindo custos de rede
- **Amostragem**: Amostragem deterministica baseada em traceId
- **Limitacao inteligente**: Parada automatica da coleta quando o limite e atingido
- **Disjuntor**: Pausa automatica quando excedecest excedem o limite
- **Cache de regex**: Cache de resultados de compilacao Pattern

## Estrutura do Projeto

```
deepcover/
├── src/
│   ├── main/java/io/deepcover/agent/
│   │   ├── CodeCollecter.java       # Entrada do modulo, gerenciamento de ciclo de vida
│   │   ├── HttpCodeModule.java      # Logica de coleta principal
│   │   ├── entity/                  # Entidades de dados
│   │   │   ├── CodeEntity.java      # Dados de coleta por requisicao
│   │   │   └── LineEntity.java      # Info de linha por metodo
│   │   ├── config/
│   │   │   ├── DeepCoverConfig.java # Configuracao global
│   │   │   ├── ExecutorThreadPoolConfig.java  # Config pool de threads
│   │   │   ├── queue/               # Motor de fila assincrona
│   │   │   └── kafka/               # Motor de envio Kafka
│   │   ├── ext/                     # Extensoes JVM Sandbox
│   │   │   ├── CodeAdviceListener.java
│   │   │   ├── CodeAdviceAdapterListener.java
│   │   │   └── CodeEventWatchBuilder.java
│   │   └── util/                    # Classes utilitarias
│   │       ├── TraceContext.java     # Gerenciamento TraceId
│   │       ├── TraceUtil.java       # Calculo de amostragem
│   │       ├── ExceptionAwareUtil.java  # Disjuntor
│   │       └── http/HttpClient2.java # Cliente HTTP
│   └── test/java/                   # Testes unitarios (41 casos)
├── src/main/resources/
│   ├── deepcover.properties.example  # Modelo de config
│   └── logback.xml
├── sandbox/                          # Binarios e config JVM Sandbox
├── LICENSE                           # Apache 2.0
├── CONTRIBUTING.md                   # Guia de contribuicao
├── CHANGELOG.md                      # Registro de alteracoes
└── README.md
```

## Seguranca

- `deepcover.properties` esta no `.gitignore` para prevenir commits acidentais de dados sensiveis
- Modelos usam placeholders `YOUR_*` em vez de enderecos e chaves reais
- Todas as dependencias sao componentes open source, sem dependencias privadas internas

### Dependencias

| Dependencia | Versao | Uso |
|-------------|--------|-----|
| Alibaba JVM Sandbox | 1.4.0 | Framework de melhoria de bytecode |
| OpenTelemetry API | 1.30.0 | Padrao de rastreamento distribuido |
| Apache HttpClient | 4.5.6 | Envio de dados HTTP |
| Hutool | 5.8.9 | Utilitarios HTTP (relatorio ao centro de config) |
| FastJSON | 2.0.25 | Serializacao JSON |
| Logback | 1.2.1 | Framework de logging |
| Lombok | 1.18.12 | Simplificacao de codigo |
| Kafka Clients | 2.4.1 | Envio de dados Kafka |
| Guava | 18.0 | Classes utilitarias |

## Observacoes

1. **Impacto na performance**: A coleta tem custo; configure uma taxa de amostragem adequada em producao
2. **Compatibilidade SkyWalking**: O agente DeepCover deve ser carregado antes do agente SkyWalking
3. **Uso de recursos**: Dados coletados consomem memoria; ajuste `limitCodeMethodSize` conforme necessario
4. **Teste primeiro**: Verifique completamente em ambiente de teste antes do deploy em producao

## Documentacao

- [CONTRIBUTING.md](CONTRIBUTING.md) - Guia de contribuicao
- [CHANGELOG.md](CHANGELOG.md) - Registro de alteracoes
- [LICENSE](LICENSE) - Licenca Apache 2.0

## Licenca

Este projeto esta licenciado sob a [Apache License 2.0](LICENSE).
